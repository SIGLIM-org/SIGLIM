package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * B-03.1 contra PostgreSQL real, con {@link InterpretacionReglaService} completo: el alta en
 * {@code PENDIENTE}, el SQL del listado con su {@code COUNT}, la resolución del Presidente sobre el
 * enum {@code estado_interpretacion}, la temporada cerrada (RF-003), la separación de funciones
 * (DEC-57) y la auditoría. La tabla no tiene trigger de estado: la máquina la valida el servicio.
 *
 * <p>Sigue el flujo P-01 del ERS: el Administrador general registra la interpretación (RF-007) y el
 * Presidente la autoriza o la rechaza, con los roles sembrados tal cual los da la matriz inicial.
 */
class InterpretacionesIT extends BaseDatosPrueba {

    private static final LocalDate INICIO = LocalDate.parse("2032-08-01");
    private static final LocalDate FIN = LocalDate.parse("2032-12-15");
    private static final String DESCRIPCION = "Se interpreta que el empate en la liguilla se define por"
            + " penales.";
    private static final String OBSERVACIONES = "No procede: el reglamento ya lo define en el artículo 12.";
    private static final String MOTIVO_CIERRE = "Fin del calendario deportivo";

    @Autowired
    private InterpretacionReglaService interpretaciones;

    @Autowired
    private TemporadaService temporadas;

    @Autowired
    private ParametrosService parametros;

    @BeforeEach
    void cerrarTemporadasActivas() {
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE estado = 'ACTIVA'").update();
    }

    @Test
    @DisplayName("B-03.1: el Administrador registra en PENDIENTE con su autoría y queda auditado")
    void registraPendiente() {
        UUID administrador = iniciarSesionComoAdministrador();
        UUID temporada = nuevaTemporada().id();

        InterpretacionReglaDto registrada = interpretaciones.registrar(
                new CrearInterpretacionRequest(temporada, "  " + DESCRIPCION + "  "));

        assertThat(registrada.temporadaId()).isEqualTo(temporada);
        assertThat(registrada.descripcion()).isEqualTo(DESCRIPCION);
        assertThat(registrada.estado()).isEqualTo(EstadoInterpretacion.PENDIENTE);
        assertThat(registrada.registradaPor()).isEqualTo(administrador);
        assertThat(registrada.resueltaPor()).isNull();

        Fila fila = fila(registrada.id());
        assertThat(fila.estado()).isEqualTo("PENDIENTE");
        assertThat(fila.registradaPor()).isEqualTo(administrador);
        assertThat(fila.registradaEn()).isNotNull();
        assertThat(fila.resueltaPor()).as("nadie la resolvió").isNull();
        assertThat(fila.observaciones()).as("el rechazo es el único que guarda motivo").isNull();

        Evento registro = auditoria(registrada.id()).getFirst();
        assertThat(registro.accion()).isEqualTo("REGISTRAR_INTERPRETACION");
        assertThat(registro.perfilId()).isEqualTo(administrador);
        assertThat(registro.detalle()).contains("temporadaId", "PENDIENTE")
                .as("no se copia el texto de la interpretación").doesNotContain(DESCRIPCION);
    }

    @Test
    @DisplayName("RF-007: el Presidente autoriza o rechaza; la resolución no se reabre")
    void presidenteResuelve() {
        UUID presidente = perfilConRol("PRESIDENTE");
        UUID temporada = prepararTemporada();

        InterpretacionReglaDto pendiente = nuevaInterpretacion(temporada);
        iniciarSesion(presidente);
        InterpretacionReglaDto autorizada = interpretaciones.resolver(pendiente.id(),
                new ResolverInterpretacionRequest(EstadoInterpretacion.AUTORIZADA, null));

        assertThat(autorizada.resueltaPor()).isEqualTo(presidente);
        assertThat(autorizada.resueltaEn()).isNotNull();
        Fila fila = fila(pendiente.id());
        assertThat(fila.estado()).isEqualTo("AUTORIZADA");
        assertThat(fila.resueltaPor()).isEqualTo(presidente);
        assertThat(fila.observaciones()).as("autorizar no guarda motivo").isNull();

        // La resolución es definitiva: repetirla sobre la misma fila responde 409 RF-007.
        assertThatThrownBy(() -> interpretaciones.resolver(pendiente.id(),
                new ResolverInterpretacionRequest(EstadoInterpretacion.RECHAZADA, OBSERVACIONES)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-007"));

        InterpretacionReglaDto otraPendiente = nuevaInterpretacion(temporada);
        iniciarSesion(presidente);
        InterpretacionReglaDto rechazada = interpretaciones.resolver(otraPendiente.id(),
                new ResolverInterpretacionRequest(EstadoInterpretacion.RECHAZADA, "  " + OBSERVACIONES + " "));

        assertThat(rechazada.observaciones()).isEqualTo(OBSERVACIONES);
        assertThat(fila(rechazada.id()).estado()).isEqualTo("RECHAZADA");

        List<Evento> eventos = auditoria(rechazada.id());
        assertThat(eventos).extracting(Evento::accion)
                .containsExactly("REGISTRAR_INTERPRETACION", "RESOLVER_INTERPRETACION");
        assertThat(eventos.getLast().motivo()).as("las observaciones son el motivo del rechazo")
                .isEqualTo(OBSERVACIONES);
        assertThat(eventos.getLast().perfilId()).isEqualTo(presidente);
    }

    @Test
    @DisplayName("RF-003, DEC-59: una temporada cerrada no admite interpretaciones nuevas, y las pendientes sí se resuelven")
    void temporadaCerrada() {
        UUID presidente = perfilConRol("PRESIDENTE");
        UUID temporada = prepararTemporada();
        InterpretacionReglaDto pendiente = nuevaInterpretacion(temporada);

        iniciarSesionComoAdministrador();
        parametros.registrar(temporada, ParametrosPrueba.valido());
        activar(temporada);
        temporadas.cerrar(temporada, new CerrarTemporadaRequest(MOTIVO_CIERRE));

        assertThatThrownBy(() -> interpretaciones.registrar(
                new CrearInterpretacionRequest(temporada, "Otra interpretación de la temporada cerrada.")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));

        // Lo ya registrado no se pierde con el cierre: el Presidente aún puede resolverlo (DEC-59).
        iniciarSesion(presidente);
        InterpretacionReglaDto resuelta = interpretaciones.resolver(pendiente.id(),
                new ResolverInterpretacionRequest(EstadoInterpretacion.AUTORIZADA, null));

        assertThat(resuelta.resueltaEn()).as("la temporada cerrada conserva y permite resolver lo pendiente")
                .isNotNull();
        assertThat(fila(pendiente.id()).estado()).isEqualTo("AUTORIZADA");
    }

    @Test
    @DisplayName("RF-007, DEC-57: quien registró la interpretación no puede resolverla, aunque tenga el permiso de aprobar")
    void separacionDeFunciones() {
        UUID temporada = prepararTemporada();
        UUID desempenaDosCargos = perfilConRol("ADMINISTRADOR_GENERAL");
        agregarRol(desempenaDosCargos, "PRESIDENTE");
        iniciarSesion(desempenaDosCargos);

        InterpretacionReglaDto registrada = interpretaciones.registrar(
                new CrearInterpretacionRequest(temporada, DESCRIPCION));

        assertThatThrownBy(() -> interpretaciones.resolver(registrada.id(),
                new ResolverInterpretacionRequest(EstadoInterpretacion.AUTORIZADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "RF-007"));
        assertThat(fila(registrada.id()).estado()).as("la interpretación sigue pendiente")
                .isEqualTo("PENDIENTE");

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        InterpretacionReglaDto resuelta = interpretaciones.resolver(registrada.id(),
                new ResolverInterpretacionRequest(EstadoInterpretacion.AUTORIZADA, null));

        assertThat(resuelta.resueltaPor()).isEqualTo(presidente);
    }

    @Test
    @DisplayName("VAL-015: sin el permiso no se registra ni se resuelve, y no queda auditoría")
    void permisoNecesario() {
        UUID temporada = prepararTemporada();
        InterpretacionReglaDto pendiente = nuevaInterpretacion(temporada);
        iniciarSesion(perfilConRol("PROGRAMADOR"));

        assertThatThrownBy(() -> interpretaciones.registrar(
                new CrearInterpretacionRequest(temporada, DESCRIPCION)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThatThrownBy(() -> interpretaciones.resolver(pendiente.id(),
                new ResolverInterpretacionRequest(EstadoInterpretacion.AUTORIZADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));

        assertThat(auditoria(pendiente.id())).as("solo el registro del Administrador está auditado")
                .extracting(Evento::accion).containsExactly("REGISTRAR_INTERPRETACION");
    }

    @Test
    @DisplayName("RF-007: el listado pagina, filtra por temporada y estado y cuenta sus filas")
    void listadoConFiltros() {
        UUID temporada = prepararTemporada();
        InterpretacionReglaDto primera = nuevaInterpretacion(temporada);
        nuevaInterpretacion(temporada);

        var pendientes = interpretaciones.listar(temporada, EstadoInterpretacion.PENDIENTE, 0, 10);

        assertThat(pendientes.totalElementos()).isEqualTo(2);
        assertThat(pendientes.contenido()).extracting(InterpretacionReglaDto::estado)
                .containsOnly(EstadoInterpretacion.PENDIENTE);

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        interpretaciones.resolver(primera.id(),
                new ResolverInterpretacionRequest(EstadoInterpretacion.AUTORIZADA, null));

        var autorizadas = interpretaciones.listar(temporada, EstadoInterpretacion.AUTORIZADA, 0, 10);

        assertThat(autorizadas.totalElementos()).isEqualTo(1);
        assertThat(autorizadas.contenido()).extracting(InterpretacionReglaDto::id)
                .containsExactly(primera.id());
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────────────────────────

    private TemporadaDto nuevaTemporada() {
        return temporadas.crear(new CrearTemporadaRequest("Interpretaciones " + UUID.randomUUID(), INICIO, FIN));
    }

    /**
     * Crea una temporada como Administrador y deja su sesión activa sin tráfico de más: el P-01 del
     * ERS registra la interpretación desde el carril del Administrador general (RF-007).
     */
    private UUID prepararTemporada() {
        iniciarSesionComoAdministrador();
        return nuevaTemporada().id();
    }

    /**
     * Registra una interpretación como Administrador (quien tiene
     * {@code temporadas.interpretacion:REGISTRAR}) y deja la sesión en su cuenta.
     */
    private InterpretacionReglaDto nuevaInterpretacion(UUID temporada) {
        iniciarSesionComoAdministrador();
        return interpretaciones.registrar(new CrearInterpretacionRequest(temporada, DESCRIPCION));
    }

    /**
     * Activa la temporada: los datos que exige RF-100 a RF-005 los prepara el Administrador, que es
     * la sesión esperada al momento de llamarla.
     */
    private void activar(UUID temporada) {
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT t.liga_id, :nombre, 'LIBRE',
                               coalesce((SELECT max(orden) FROM siglim.categorias), 0) + 1
                          FROM siglim.temporadas t
                         WHERE t.id = :temporada
                        RETURNING id
                        """)
                .param("nombre", "Categoría " + UUID.randomUUID())
                .param("temporada", temporada)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria)
                        """)
                .param("temporada", temporada).param("categoria", categoriaTemporada).update();
        jdbc.sql("""
                        INSERT INTO siglim.grupos (categoria_temporada_id, nombre)
                        SELECT ct.id, 'A' FROM siglim.categorias_temporada ct
                         WHERE ct.temporada_id = :temporada
                        """)
                .param("temporada", temporada).update();
        temporadas.activar(temporada);
    }

    private Fila fila(UUID id) {
        return jdbc.sql("""
                        SELECT estado, registrada_en, registrada_por, resuelta_por, observaciones
                          FROM siglim.interpretaciones_regla
                         WHERE id = :id
                        """)
                .param("id", id)
                .query((fila, numero) -> new Fila(fila.getString("estado"),
                        fila.getObject("registrada_por", UUID.class),
                        fila.getObject("registrada_en", OffsetDateTime.class),
                        fila.getObject("resuelta_por", UUID.class),
                        fila.getString("observaciones")))
                .single();
    }

    private List<Evento> auditoria(UUID id) {
        return jdbc.sql("""
                        SELECT accion, perfil_id, motivo, detalle
                          FROM siglim.auditoria
                         WHERE modulo = 'M-02' AND entidad = 'interpretaciones_regla' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", id.toString())
                .query((fila, numero) -> new Evento(fila.getString("accion"),
                        fila.getObject("perfil_id", UUID.class), fila.getString("motivo"),
                        fila.getString("detalle")))
                .list();
    }

    private UUID perfilConRol(String rol) {
        UUID perfil = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
        agregarRol(perfil, rol);
        return perfil;
    }

    /**
     * Agrega una asignación de rol más a un perfil que ya la tiene, para probar una cuenta con los
     * dos permisos de la interpretación (DEC-57).
     */
    private void agregarRol(UUID perfil, String rol) {
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private record Fila(String estado, UUID registradaPor, OffsetDateTime registradaEn, UUID resueltaPor,
                        String observaciones) {
    }

    private record Evento(String accion, UUID perfilId, String motivo, String detalle) {
    }
}

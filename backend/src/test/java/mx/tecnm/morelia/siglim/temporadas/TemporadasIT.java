package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
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
 * H-04.1 contra PostgreSQL real, con {@link TemporadaService} completo: autorización, SQL, RLS y
 * los triggers de estado. Antes de cada prueba se cierra cualquier temporada activa para que
 * RN-058 no arrastre estado de otra prueba.
 */
class TemporadasIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final LocalDate INICIO = LocalDate.parse("2032-08-01");
    private static final LocalDate FIN = LocalDate.parse("2032-12-15");
    private static final String MOTIVO = "Fin del calendario deportivo";

    @Autowired
    private TemporadaService servicio;

    @Autowired
    private ParametrosService parametros;

    @BeforeEach
    void cerrarTemporadasActivas() {
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE estado = 'ACTIVA'").update();
    }

    // ── Ciclo de vida ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-001, RF-003, RF-100, RN-058: crear, verificar, activar, cerrar y proteger la cerrada")
    void cicloDeVida() {
        UUID perfil = iniciarSesionComoAdministrador();
        String nombre = "Ciclo " + UUID.randomUUID();
        TemporadaDto temporada = servicio.crear(new CrearTemporadaRequest(nombre, INICIO, FIN));

        assertThat(temporada.estado()).isEqualTo(EstadoTemporada.CONFIGURACION);
        assertThat(temporada.activacion()).isNotNull();
        assertThat(temporada.activacion().puedeActivarse()).isFalse();
        assertThat(temporada.activacion().requisitos())
                .filteredOn(requisito -> !requisito.cumple())
                .extracting(TemporadaDto.RequisitoActivacionDto::codigo)
                .contains("RF-100", "RF-004", "RF-005");
        assertThatThrownBy(() -> servicio.activar(temporada.id()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-100"));

        completarRequisitos(temporada.id());
        assertThat(servicio.consultar(temporada.id()).activacion().puedeActivarse()).isTrue();
        assertThat(servicio.activar(temporada.id()).estado()).isEqualTo(EstadoTemporada.ACTIVA);

        String editada = nombre + " editada";
        assertThat(servicio.actualizar(temporada.id(),
                new ActualizarTemporadaRequest(editada, null, null)).nombre()).isEqualTo(editada);

        TemporadaDto segunda = servicio.crear(new CrearTemporadaRequest("Segunda " + UUID.randomUUID(), INICIO, FIN));
        assertThatThrownBy(() -> servicio.activar(segunda.id()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "RN-058").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains(editada);
                });

        assertThat(servicio.cerrar(temporada.id(), new CerrarTemporadaRequest(MOTIVO)).estado())
                .isEqualTo(EstadoTemporada.CERRADA);
        assertThatThrownBy(() -> servicio.actualizar(temporada.id(),
                new ActualizarTemporadaRequest("No permitido", null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        TemporadaDto despues = servicio.consultar(temporada.id());
        assertThat(despues.nombre()).as("los datos se conservan").isEqualTo(editada);
        assertThat(despues.estado()).isEqualTo(EstadoTemporada.CERRADA);

        List<Evento> eventos = eventosDe(temporada.id());
        assertThat(eventos).extracting(Evento::accion)
                .containsExactly("REGISTRAR_TEMPORADA", "ACTIVAR_TEMPORADA", "EDITAR_TEMPORADA", "CERRAR_TEMPORADA");
        assertThat(eventos).allSatisfy(evento -> assertThat(evento.perfilId()).isEqualTo(perfil));
        assertThat(eventos).filteredOn(evento -> evento.accion().equals("CERRAR_TEMPORADA"))
                .extracting(Evento::motivo).containsExactly(MOTIVO);
    }

    @Test
    @DisplayName("RF-004, RF-005, RF-100: la activación pide parámetros, después categoría y después grupo")
    void activacionPideRequisitosEnOrden() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = servicio.crear(new CrearTemporadaRequest("Requisitos " + UUID.randomUUID(), INICIO, FIN));

        parametros.registrar(temporada.id(), ParametrosPrueba.valido());
        assertThatThrownBy(() -> servicio.activar(temporada.id()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-004"));

        UUID categoriaTemporada = agregarCategoria(temporada.id());
        assertThatThrownBy(() -> servicio.activar(temporada.id()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-005"));

        jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:categoria, 'A')")
                .param("categoria", categoriaTemporada).update();
        assertThat(servicio.activar(temporada.id()).estado()).isEqualTo(EstadoTemporada.ACTIVA);
    }

    @Test
    @DisplayName("RN-021: cerrar sin motivo responde 400 y la temporada sigue activa")
    void cierreExigeMotivo() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = crearYActivar("Cierre " + UUID.randomUUID());

        assertThatThrownBy(() -> servicio.cerrar(temporada.id(), new CerrarTemporadaRequest(null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThatThrownBy(() -> servicio.cerrar(temporada.id(), new CerrarTemporadaRequest("Corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThat(servicio.consultar(temporada.id()).estado()).isEqualTo(EstadoTemporada.ACTIVA);

        assertThat(servicio.cerrar(temporada.id(), new CerrarTemporadaRequest(MOTIVO)).estado())
                .isEqualTo(EstadoTemporada.CERRADA);
    }

    // ── Listado y unicidad ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-001: el listado ordena por fecha de inicio descendente y pagina")
    void listadoOrdenadoYPaginado() {
        iniciarSesionComoAdministrador();
        String token = "Orden " + UUID.randomUUID();
        TemporadaDto anterior = servicio.crear(new CrearTemporadaRequest(token + " anterior",
                LocalDate.parse("2099-12-01"), LocalDate.parse("2100-01-31")));
        TemporadaDto reciente = servicio.crear(new CrearTemporadaRequest(token + " reciente",
                LocalDate.parse("2099-12-02"), LocalDate.parse("2100-02-28")));

        var pagina = servicio.listar(0, 2);

        assertThat(pagina.contenido()).extracting(TemporadaDto::id).containsExactly(reciente.id(), anterior.id());
        assertThat(pagina.contenido().getFirst().activacion()).as("en configuración trae la verificación")
                .isNotNull();
        assertThat(pagina.totalElementos()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("RF-001: un nombre repetido en la liga responde 409 DUPLICADO")
    void nombreDuplicado() {
        iniciarSesionComoAdministrador();
        String nombre = "Duplicada " + UUID.randomUUID();
        servicio.crear(new CrearTemporadaRequest(nombre, INICIO, FIN));

        assertThatThrownBy(() -> servicio.crear(new CrearTemporadaRequest(nombre, INICIO, FIN)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
    }

    // ── Permisos con la matriz de la migración ────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-015: el Programador consulta temporadas pero no las crea, activa ni cierra")
    void programadorSoloLee() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = servicio.crear(new CrearTemporadaRequest("Permisos " + UUID.randomUUID(), INICIO, FIN));
        iniciarSesion(perfilConRol("PROGRAMADOR"));

        assertThat(servicio.consultar(temporada.id()).id()).isEqualTo(temporada.id());
        assertThat(servicio.listar(0, 20).contenido()).isNotEmpty();
        assertThatThrownBy(() -> servicio.crear(new CrearTemporadaRequest("No debe " + UUID.randomUUID(), INICIO, FIN)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThatThrownBy(() -> servicio.activar(temporada.id()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThatThrownBy(() -> servicio.cerrar(temporada.id(), new CerrarTemporadaRequest(MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private TemporadaDto crearYActivar(String nombre) {
        TemporadaDto temporada = servicio.crear(new CrearTemporadaRequest(nombre, INICIO, FIN));
        completarRequisitos(temporada.id());
        return servicio.activar(temporada.id());
    }

    private void completarRequisitos(UUID temporada) {
        parametros.registrar(temporada, ParametrosPrueba.valido());
        UUID categoriaTemporada = agregarCategoria(temporada);
        jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:categoria, 'A')")
                .param("categoria", categoriaTemporada).update();
    }

    private UUID agregarCategoria(UUID temporada) {
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1
                          FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", "Categoría " + UUID.randomUUID())
                .query(UUID.class)
                .single();
        return jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria)
                        RETURNING id
                        """)
                .param("temporada", temporada)
                .param("categoria", categoria)
                .query(UUID.class)
                .single();
    }

    private List<Evento> eventosDe(UUID temporada) {
        return jdbc.sql("""
                        SELECT accion, perfil_id, motivo
                          FROM siglim.auditoria
                         WHERE modulo = 'M-02' AND entidad = 'temporadas' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", temporada.toString())
                .query((fila, numero) -> new Evento(fila.getString("accion"),
                        fila.getObject("perfil_id", UUID.class), fila.getString("motivo")))
                .list();
    }

    private UUID perfilConRol(String rol) {
        UUID perfil = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
        return perfil;
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

    private record Evento(String accion, UUID perfilId, String motivo) {
    }
}

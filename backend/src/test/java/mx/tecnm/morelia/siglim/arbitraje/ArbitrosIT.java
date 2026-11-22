package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H-11.1 contra PostgreSQL real, con {@link ArbitroService} completo: autorización con la matriz de
 * la migración, SQL, restricciones y auditoría. Demuestra los criterios de H-11 que cubren los
 * endpoints: el club presenta al árbitro y queda {@code PROPUESTO}; la Comisión lo valida o rechaza
 * con observaciones; solo los {@code VALIDADO} entran al catálogo de designación; y el ámbito de
 * club impide ver u operar árbitros de otro club (RF-021, RF-112, RF-113, VAL-024).
 */
class ArbitrosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final String OBSERVACIONES = "No acreditó la constancia de la escuela de árbitros";

    @Autowired
    private ArbitroService arbitros;

    @Autowired
    private ArbitroRepository repositorio;

    @Autowired
    private PlatformTransactionManager transacciones;

    // ── Criterios 1 y 2: presentación, validación y auditoría ───────────────────────────────────

    @Test
    @DisplayName("H-11 criterios 1 y 2 · RF-112, RF-113: la Directiva presenta y la Comisión valida")
    void directivaPresentaYComisionValida() {
        Club club = club("Halcones");
        UUID persona = persona();
        UUID directiva = perfilDeDirectiva(club.id());

        iniciarSesion(directiva);
        ArbitroDto propuesto = arbitros.crear(new CrearArbitroRequest(persona, club.id()));

        assertThat(propuesto.estado()).isEqualTo(EstadoArbitro.PROPUESTO);
        assertThat(propuesto.club().id()).isEqualTo(club.id());
        assertThat(propuesto.persona().id()).isEqualTo(persona);
        assertThat(propuesto.propuestoPor().id()).isEqualTo(directiva);
        assertThat(propuesto.validadoPor()).isNull();
        assertThat(eventos(propuesto.id())).extracting(Evento::accion)
                .containsExactly("PROPONER_ARBITRO");
        assertThat(eventos(propuesto.id()).getFirst().perfilId()).isEqualTo(directiva);

        assertThatThrownBy(() -> arbitros.crear(new CrearArbitroRequest(persona, club.id())))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "DUPLICADO").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("Halcones")
                            .doesNotContain("PROPUESTO").doesNotContain("propuesto");
                    assertThat(excepcion.propiedades()).containsOnlyKeys("club");
                    assertThat(excepcion.propiedades().get("club").toString()).startsWith("Halcones");
                });

        UUID comision = perfilConRol("COMISION_ARBITRAJE");
        iniciarSesion(comision);
        ArbitroDto validado = arbitros.validar(propuesto.id(),
                new ValidarArbitroRequest(DecisionArbitro.VALIDADO, "Cumple los requisitos"));

        assertThat(validado.estado()).isEqualTo(EstadoArbitro.VALIDADO);
        assertThat(validado.validadoPor().id()).isEqualTo(comision);
        assertThat(validado.validadoEn()).isNotNull();
        assertThat(validado.observaciones()).isEqualTo("Cumple los requisitos");
        assertThat(eventos(propuesto.id())).extracting(Evento::accion)
                .containsExactly("PROPONER_ARBITRO", "VALIDAR_ARBITRO");
        assertThat(eventos(propuesto.id()).getLast().perfilId()).isEqualTo(comision);
        assertThat(eventos(propuesto.id()).getLast().detalle())
                .contains("\"observaciones\": \"Cumple los requisitos\"");

        assertThatThrownBy(() -> arbitros.validar(propuesto.id(),
                new ValidarArbitroRequest(DecisionArbitro.RECHAZADO, OBSERVACIONES)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "RF-113"));
    }

    @Test
    @DisplayName("H-11 criterio 2 · RF-113: el rechazo exige observaciones y las guarda con el motivo")
    void comisionRechazaConObservaciones() {
        Club club = club("Toros");
        UUID persona = persona();

        iniciarSesion(perfilDeDirectiva(club.id()));
        ArbitroDto propuesto = arbitros.crear(new CrearArbitroRequest(persona, club.id()));

        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        assertThatThrownBy(() -> arbitros.validar(propuesto.id(),
                new ValidarArbitroRequest(DecisionArbitro.RECHAZADO, "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "RF-113"));
        assertThat(estadoEnBase(propuesto.id())).isEqualTo("PROPUESTO");

        ArbitroDto rechazado = arbitros.validar(propuesto.id(),
                new ValidarArbitroRequest(DecisionArbitro.RECHAZADO, " " + OBSERVACIONES + " "));

        assertThat(rechazado.estado()).isEqualTo(EstadoArbitro.RECHAZADO);
        assertThat(rechazado.observaciones()).isEqualTo(OBSERVACIONES);
        List<Evento> eventos = eventos(propuesto.id());
        assertThat(eventos).extracting(Evento::accion)
                .containsExactly("PROPONER_ARBITRO", "RECHAZAR_ARBITRO");
        assertThat(eventos.getLast().motivo()).isEqualTo(OBSERVACIONES);
    }

    @Test
    @DisplayName("DEC-90: el ON CONFLICT de persona_id no aborta la transacción y deja consultar")
    void onConflictNoAbortaLaTransaccion() {
        Club club = club("Repetido");
        UUID persona = persona();
        UUID perfil = iniciarSesionComoAdministrador();

        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            assertThat(repositorio.insertar(club.id(), persona, perfil)).isPresent();
            assertThat(repositorio.insertar(club.id(), persona, perfil)).isEmpty();
            assertThat(jdbc.sql("SELECT count(*) FROM siglim.arbitros WHERE persona_id = :persona")
                    .param("persona", persona)
                    .query(Long.class)
                    .single()).isEqualTo(1);
        });
    }

    // ── Criterio 3: ámbito por club ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("H-11 criterio 3 · RF-021, VAL-024: la Directiva solo ve y opera los árbitros de su club")
    void directivaSoloVeSuClub() {
        Club propio = club("Propio");
        Club ajeno = club("Ajeno");
        UUID personaPropia = persona();
        UUID personaAjena = persona();
        UUID directivaPropia = perfilDeDirectiva(propio.id());
        UUID directivaAjena = perfilDeDirectiva(ajeno.id());

        iniciarSesion(directivaPropia);
        ArbitroDto dePropio = arbitros.crear(new CrearArbitroRequest(personaPropia, propio.id()));

        iniciarSesion(directivaAjena);
        ArbitroDto delAjeno = arbitros.crear(new CrearArbitroRequest(personaAjena, ajeno.id()));

        iniciarSesion(directivaPropia);
        assertThat(arbitros.listar(new FiltroArbitros(null, null, 0, 100)).contenido())
                .extracting(ArbitroDto::id).containsExactly(dePropio.id());
        assertThat(arbitros.listar(new FiltroArbitros(null, null, 0, 100)).totalElementos())
                .isEqualTo(1);
        assertThatThrownBy(() -> arbitros.crear(new CrearArbitroRequest(persona(), ajeno.id())))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> arbitros.validar(dePropio.id(),
                new ValidarArbitroRequest(DecisionArbitro.VALIDADO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "VAL-015"));

        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        assertThat(arbitros.listar(new FiltroArbitros(null, null, 0, 100)).contenido())
                .extracting(ArbitroDto::id).containsExactlyInAnyOrder(dePropio.id(), delAjeno.id());
        assertThat(arbitros.listar(new FiltroArbitros(null, null, 0, 100)).totalElementos())
                .isEqualTo(2);
    }

    // ── Cuota de árbitros (B-08.1, RF-114, DEC-17) ─────────────────────────────────────────────

    @Test
    @DisplayName("B-08.1 · RF-114: 5 equipos activos con 4 por árbitro requieren 2 y solo cuenta el VALIDADO")
    void cuotaRedondeaHaciaArriba() {
        Club club = club("Cuota");
        UUID temporada = temporadaConParametros(1, 4);
        UUID grupo = grupoDe(temporada);
        for (int numero = 1; numero <= 5; numero++) {
            equipoActivo(club.id(), grupo, "Equipo " + numero);
        }
        equipoDeBaja(club.id(), grupo, "Equipo dado de baja");
        arbitro(club.id(), EstadoArbitro.VALIDADO);
        arbitro(club.id(), EstadoArbitro.PROPUESTO);
        arbitro(club.id(), EstadoArbitro.RECHAZADO);

        iniciarSesion(perfilDeDirectiva(club.id()));
        CuotaArbitrosDto cuota = arbitros.consultarCuota(club.id(), temporada);

        assertThat(cuota.club().id()).isEqualTo(club.id());
        assertThat(cuota.temporada().id()).isEqualTo(temporada);
        assertThat(cuota.equiposActivos()).as("los equipos dados de baja no cuentan").isEqualTo(5);
        assertThat(cuota.equiposPorArbitro()).isEqualTo(4);
        assertThat(cuota.cuotaArbitros().requeridos()).as("ceil(5 / 4)").isEqualTo(2);
        assertThat(cuota.cuotaArbitros().validados()).as("solo los VALIDADO cubren la cuota").isEqualTo(1);
        assertThat(cuota.cuotaArbitros().cumple()).isFalse();
    }

    @Test
    @DisplayName("B-08.1 · RF-114, DEC-72: la cuota usa la versión vigente del parámetro de la temporada")
    void cuotaUsaParametroDeLaTemporada() {
        Club club = club("Parámetro");
        UUID temporada = temporadaConParametros(1, 5);
        parametros(temporada, 2, 2);
        UUID grupo = grupoDe(temporada);
        for (int numero = 1; numero <= 5; numero++) {
            equipoActivo(club.id(), grupo, "Equipo " + numero);
        }
        arbitro(club.id(), EstadoArbitro.VALIDADO);
        arbitro(club.id(), EstadoArbitro.VALIDADO);

        iniciarSesion(perfilDeDirectiva(club.id()));
        CuotaArbitrosDto cuota = arbitros.consultarCuota(club.id(), temporada);

        assertThat(cuota.equiposPorArbitro()).as("se ignora la versión 1 y se usa la 2").isEqualTo(2);
        assertThat(cuota.cuotaArbitros().requeridos()).as("ceil(5 / 2)").isEqualTo(3);
        assertThat(cuota.cuotaArbitros().validados()).isEqualTo(2);
        assertThat(cuota.cuotaArbitros().cumple()).isFalse();
    }

    @Test
    @DisplayName("B-08.1 · VAL-024: con ámbito CLUB la cuota de otro club responde 404 como una inexistente")
    void cuotaFueraDeAmbito() {
        Club propio = club("Cuota propia");
        Club ajeno = club("Cuota ajena");
        UUID temporada = temporadaConParametros(1, 4);

        iniciarSesion(perfilDeDirectiva(propio.id()));

        assertThatThrownBy(() -> arbitros.consultarCuota(ajeno.id(), temporada))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> arbitros.consultarCuota(UUID.randomUUID(), temporada))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    /** Temporada en configuración (no compite por la única ACTIVA) con una versión de parámetros. */
    private UUID temporadaConParametros(int version, int equiposPorArbitro) {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2035-08-01')
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class)
                .single();
        parametros(temporada, version, equiposPorArbitro);
        return temporada;
    }

    private void parametros(UUID temporada, int version, int equiposPorArbitro) {
        jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada
                            (temporada_id, version, equipos_por_arbitro, motivo)
                        VALUES (:temporada, :version, :equiposPorArbitro, 'Parámetros de la cuota de prueba')
                        """)
                .param("temporada", temporada)
                .param("version", version)
                .param("equiposPorArbitro", equiposPorArbitro)
                .update();
    }

    /** Categoría, categoría de la temporada y grupo donde se dan de alta los equipos del club. */
    private UUID grupoDe(UUID temporada) {
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1
                          FROM siglim.categorias
                         WHERE liga_id = :liga
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", "Categoría " + UUID.randomUUID())
                .query(UUID.class)
                .single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria)
                        RETURNING id
                        """)
                .param("temporada", temporada)
                .param("categoria", categoria)
                .query(UUID.class)
                .single();
        return jdbc.sql("""
                        INSERT INTO siglim.grupos (categoria_temporada_id, nombre)
                        VALUES (:categoriaTemporada, 'A')
                        RETURNING id
                        """)
                .param("categoriaTemporada", categoriaTemporada)
                .query(UUID.class)
                .single();
    }

    private void equipoActivo(UUID club, UUID grupo, String nombre) {
        jdbc.sql("""
                        INSERT INTO siglim.equipos (club_id, grupo_id, nombre)
                        VALUES (:club, :grupo, :nombre)
                        """)
                .param("club", club)
                .param("grupo", grupo)
                .param("nombre", nombre)
                .update();
    }

    private void equipoDeBaja(UUID club, UUID grupo, String nombre) {
        jdbc.sql("""
                        INSERT INTO siglim.equipos
                            (club_id, grupo_id, nombre, estado, baja_en, motivo_baja)
                        VALUES (:club, :grupo, :nombre, 'BAJA', CURRENT_DATE, 'Baja de prueba')
                        """)
                .param("club", club)
                .param("grupo", grupo)
                .param("nombre", nombre)
                .update();
    }

    private UUID arbitro(UUID club, EstadoArbitro estado) {
        return jdbc.sql("""
                        INSERT INTO siglim.arbitros (persona_id, club_id, estado)
                        VALUES (:persona, :club, CAST(:estado AS siglim.estado_arbitro))
                        RETURNING id
                        """)
                .param("persona", persona())
                .param("club", club)
                .param("estado", estado.name())
                .query(UUID.class)
                .single();
    }

    private Club club(String nombre) {
        UUID id = jdbc.sql("""
                        INSERT INTO siglim.clubes (liga_id, nombre)
                        VALUES (:liga, :nombre)
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", nombre + " " + UUID.randomUUID())
                .query(UUID.class)
                .single();
        return new Club(id, nombre);
    }

    /** Persona con una CURP válida y única; su identidad es global (RF-010, DEC-10). */
    private UUID persona() {
        String sufijo = "" + letra() + letra() + letra();
        return jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, fecha_nacimiento)
                        VALUES (:curp, 'Carlos Alberto', 'Prado', :nacimiento)
                        RETURNING id
                        """)
                .param("curp", "GAPA800101HMN" + sufijo + "01")
                .param("nacimiento", LocalDate.of(1980, 1, 1))
                .query(UUID.class)
                .single();
    }

    private static char letra() {
        return (char) ('A' + ThreadLocalRandom.current().nextInt(26));
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class)
                .single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado. */
    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", rol)
                .update();
        return perfil;
    }

    /** Perfil con el rol DIRECTIVA_CLUB de la matriz, en ámbito CLUB de ese club. */
    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil)
                .param("club", club)
                .update();
        return perfil;
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private String estadoEnBase(UUID arbitro) {
        return jdbc.sql("SELECT estado::text FROM siglim.arbitros WHERE id = :id")
                .param("id", arbitro)
                .query(String.class)
                .single();
    }

    private List<Evento> eventos(UUID arbitro) {
        return jdbc.sql("""
                        SELECT accion, perfil_id, motivo, CAST(detalle AS text) AS detalle
                          FROM siglim.auditoria
                         WHERE modulo = 'M-05' AND entidad = 'arbitros' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", arbitro.toString())
                .query((fila, numero) -> new Evento(fila.getString("accion"),
                        fila.getObject("perfil_id", UUID.class), fila.getString("motivo"),
                        fila.getString("detalle")))
                .list();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private record Club(UUID id, String nombre) {
    }

    private record Evento(String accion, UUID perfilId, String motivo, String detalle) {
    }
}

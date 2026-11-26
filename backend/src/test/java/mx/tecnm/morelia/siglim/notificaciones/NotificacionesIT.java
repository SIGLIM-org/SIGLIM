package mx.tecnm.morelia.siglim.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.PartidosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.notificaciones.NotificacionService.AvisosDelUsuario;
import mx.tecnm.morelia.siglim.partidos.eventos.CedulaEnviada;
import mx.tecnm.morelia.siglim.programacion.eventos.JornadaPublicada;
import mx.tecnm.morelia.siglim.tesoreria.VencimientoCobrosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * H-25.1 contra PostgreSQL real: un caso por evento de dominio que escucha M-14 (con sus
 * destinatarios por permiso y RLS) y la consulta y lectura de los avisos propios.
 *
 * <p>Los oyentes se invocan dentro de una transacción con la sesión de quien publica el evento, como
 * lo hace Spring al publicarlo. El vencimiento de cobros se ejecuta con la tarea real del contexto
 * para comprobar el camino completo con el perfil Sistema.
 */
class NotificacionesIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    @Autowired
    private OyenteAvisos oyente;

    @Autowired
    private NotificacionService servicio;

    @Autowired
    private VencimientoCobrosService vencimiento;

    @Autowired
    private PlatformTransactionManager transacciones;

    /** Árbitros que crea la prueba: se borran al final para no alterar los listados de ArbitrosIT. */
    private final List<UUID> arbitrosCreados = new ArrayList<>();

    @AfterEach
    void borrarArbitros() {
        for (UUID arbitro : arbitrosCreados) {
            jdbc.sql("DELETE FROM siglim.designaciones WHERE arbitro_id = :id").param("id", arbitro).update();
            jdbc.sql("DELETE FROM siglim.arbitros WHERE id = :id").param("id", arbitro).update();
        }
        arbitrosCreados.clear();
    }

    // ── CedulaEnviada ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CedulaEnviada → árbitro central designado (RF-122)")
    class CedulaPendiente {

        @Test
        @DisplayName("RF-142 · H-25 criterio 1: el árbitro central designado recibe el aviso con enlace al partido; el asistente no")
        void avisaAlCentral() {
            UUID partido = partido();
            UUID central = perfilDeArbitro(designar(partido, "CENTRAL"));
            UUID asistente = perfilDeArbitro(designar(partido, "ASISTENTE"));
            OffsetDateTime plazo = OffsetDateTime.parse("2036-03-10T18:30:00Z");

            int escritos = comoUsuario(perfilConRol("CAPTURISTA"),
                    () -> oyente.alEnviarCedula(new CedulaEnviada(LIGA, UUID.randomUUID(), partido, plazo)));

            assertThat(escritos).isEqualTo(1);
            List<Aviso> avisos = avisos(central);
            assertThat(avisos).singleElement().satisfies(aviso -> {
                assertThat(aviso.evento()).isEqualTo("CEDULA_PENDIENTE");
                assertThat(aviso.titulo()).isEqualTo("Cédula pendiente de validación");
                assertThat(aviso.cuerpo()).contains("Local vs Visitante (jornada 1)", "antes del 10/03/2036 12:30");
                assertThat(aviso.enlace()).isEqualTo("/app/partidos/" + partido);
                assertThat(aviso.leidaEn()).isNull();
            });
            assertThat(avisos(asistente)).as("solo el central valida la cédula (H-17)").isEmpty();
        }

        @Test
        @DisplayName("ADR-0007: el perfil del árbitro sin partidos.cedula:APROBAR o desactivado no recibe el aviso")
        void sinPermisoNoRecibe() {
            UUID partido = partido();
            UUID arbitro = designar(partido, "CENTRAL");
            UUID sinRol = perfilDePersona(personaDe(arbitro));

            int escritos = comoUsuario(perfilConRol("CAPTURISTA"),
                    () -> oyente.alEnviarCedula(new CedulaEnviada(LIGA, UUID.randomUUID(), partido, null)));

            assertThat(escritos).isZero();
            assertThat(avisos(sinRol)).isEmpty();
        }

        @Test
        @DisplayName("RNF-004: sin árbitro designado no se escribe nada y la operación sigue")
        void sinDesignacion() {
            UUID partido = partido();

            int escritos = comoUsuario(perfilConRol("CAPTURISTA"),
                    () -> oyente.alEnviarCedula(new CedulaEnviada(LIGA, UUID.randomUUID(), partido, null)));

            assertThat(escritos).isZero();
        }

        @Test
        @DisplayName("ADR-0010 §1: el oyente exige la transacción de quien publica el evento")
        void exigeTransaccion() {
            UUID partido = partido();

            assertThatThrownBy(() -> oyente.alEnviarCedula(new CedulaEnviada(LIGA, UUID.randomUUID(), partido, null)))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }
    }

    // ── CobroVencido ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CobroVencido → directiva del club deudor (RF-141)")
    class CobroVencidoAvisos {

        @Test
        @DisplayName("RF-142: al vencer un cobro con la tarea real, la directiva del club recibe el aviso; otro club y la liga no")
        void avisaALaDirectiva() {
            RequestContextHolder.resetRequestAttributes();
            UUID club = club();
            UUID directiva = perfilDeDirectiva(club);
            UUID otraDirectiva = perfilDeDirectiva(club());
            UUID tesorero = perfilConRol("TESORERO");
            LocalDate ayer = LocalDate.now(ZONA_DE_NEGOCIO).minusDays(1);
            cobroPendiente(club, ayer);

            vencimiento.vencer();

            assertThat(avisos(directiva)).singleElement().satisfies(aviso -> {
                assertThat(aviso.evento()).isEqualTo("COBRO_VENCIDO");
                assertThat(aviso.titulo()).isEqualTo("Cobro vencido");
                assertThat(aviso.cuerpo()).contains("cuota", "1,500.00", "MXN",
                        ayer.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                assertThat(aviso.enlace()).isEqualTo("/app/tesoreria");
            });
            assertThat(avisos(otraDirectiva)).isEmpty();
            assertThat(avisos(tesorero)).as("[PROPUESTA] la liga no recibe los avisos de cada club").isEmpty();
        }
    }

    // ── JornadaPublicada ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JornadaPublicada → directivas de los clubes de la jornada (RF-121)")
    class ProgramacionPublicada {

        @Test
        @DisplayName("RF-142: cada club que juega en la jornada recibe el aviso de programación publicada")
        void avisaALosClubes() {
            UUID partido = partido();
            UUID jornada = jdbc.sql("SELECT jornada_id FROM siglim.partidos WHERE id = :id")
                    .param("id", partido).query(UUID.class).single();
            List<UUID> clubes = jdbc.sql("""
                            SELECT e.club_id FROM siglim.partidos p
                              JOIN siglim.equipos e ON e.id IN (p.equipo_local_id, p.equipo_visitante_id)
                             WHERE p.id = :id
                            """).param("id", partido).query(UUID.class).list();
            UUID local = perfilDeDirectiva(clubes.get(0));
            UUID visitante = perfilDeDirectiva(clubes.get(1));
            UUID ajena = perfilDeDirectiva(club());
            UUID presidente = perfilConRol("PRESIDENTE");

            int escritos = comoUsuario(presidente,
                    () -> oyente.alPublicarJornada(new JornadaPublicada(LIGA, jornada, presidente)));

            assertThat(escritos).isEqualTo(2);
            for (UUID directiva : List.of(local, visitante)) {
                assertThat(avisos(directiva)).singleElement().satisfies(aviso -> {
                    assertThat(aviso.evento()).isEqualTo("PROGRAMACION_PUBLICADA");
                    assertThat(aviso.cuerpo()).startsWith("Se publicó la jornada 1 de Categoría ").contains("– A.");
                    assertThat(aviso.enlace()).isEqualTo("/app/programacion/jornadas");
                });
            }
            assertThat(avisos(ajena)).isEmpty();
            assertThat(avisos(presidente)).isEmpty();
        }

        @Test
        @DisplayName("ADR-0010 §1: si la publicación se revierte, sus avisos también")
        void reversionSinAvisos() {
            UUID partido = partido();
            UUID jornada = jdbc.sql("SELECT jornada_id FROM siglim.partidos WHERE id = :id")
                    .param("id", partido).query(UUID.class).single();
            UUID club = jdbc.sql("""
                            SELECT e.club_id FROM siglim.partidos p JOIN siglim.equipos e ON e.id = p.equipo_local_id
                             WHERE p.id = :id
                            """).param("id", partido).query(UUID.class).single();
            UUID directiva = perfilDeDirectiva(club);
            UUID presidente = perfilConRol("PRESIDENTE");

            assertThatThrownBy(() -> comoUsuario(presidente, () -> {
                assertThat(oyente.alPublicarJornada(new JornadaPublicada(LIGA, jornada, presidente))).isEqualTo(1);
                throw new IllegalStateException("falla posterior de la publicación");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(avisos(directiva)).isEmpty();
        }
    }

    // ── Consulta y lectura ────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Consulta y lectura de los avisos propios (convenciones de API §6.14)")
    class ConsultaYLectura {

        @Test
        @DisplayName("RF-142: lista solo los avisos propios, más recientes primero, con el conteo de no leídos")
        void listaLosPropios() {
            UUID usuario = perfilConRol("CAPTURISTA");
            UUID otro = perfilConRol("CAPTURISTA");
            UUID viejo = aviso(usuario, "2036-01-01T10:00:00Z", true);
            UUID nuevo = aviso(usuario, "2036-01-02T10:00:00Z", false);
            aviso(otro, "2036-01-03T10:00:00Z", false);

            AvisosDelUsuario todos = como(usuario, () -> servicio.listar(null, 0, 20));
            AvisosDelUsuario noLeidos = como(usuario, () -> servicio.listar(false, 0, 20));

            assertThat(todos.pagina().contenido()).extracting(NotificacionDto::id).containsExactly(nuevo, viejo);
            assertThat(todos.pagina().totalElementos()).isEqualTo(2);
            assertThat(todos.noLeidas()).isEqualTo(1);
            assertThat(noLeidos.pagina().contenido()).extracting(NotificacionDto::id).containsExactly(nuevo);
        }

        @Test
        @DisplayName("H-25 criterio 2: abrir un aviso lo deja leído y conserva la primera lectura")
        void marcarLeida() {
            UUID usuario = perfilConRol("CAPTURISTA");
            UUID id = aviso(usuario, "2036-01-02T10:00:00Z", false);

            NotificacionDto primera = como(usuario, () -> servicio.marcarLeida(id));
            NotificacionDto segunda = como(usuario, () -> servicio.marcarLeida(id));

            assertThat(primera.leidaEn()).isNotNull();
            assertThat(segunda.leidaEn()).isEqualTo(primera.leidaEn());
            assertThat(segunda.enlace()).isEqualTo("/app/partidos/prueba");
            assertThat(como(usuario, () -> servicio.listar(null, 0, 20)).noLeidas()).isZero();
        }

        @Test
        @DisplayName("VAL-024: el aviso de otro usuario responde 404 y no se marca")
        void ajenoEs404() {
            UUID usuario = perfilConRol("CAPTURISTA");
            UUID ajeno = aviso(perfilConRol("CAPTURISTA"), "2036-01-02T10:00:00Z", false);

            assertThatThrownBy(() -> como(usuario, () -> servicio.marcarLeida(ajeno)))
                    .isInstanceOf(RecursoNoEncontradoException.class);
            assertThat(jdbc.sql("SELECT leida_en IS NULL FROM siglim.notificaciones WHERE id = :id").param("id", ajeno)
                    .query(Boolean.class).single()).isTrue();
        }

        @Test
        @DisplayName("RF-142: marcar todas deja leídos solo los propios y responde cuántos cambió")
        void marcarTodas() {
            UUID usuario = perfilConRol("CAPTURISTA");
            UUID otro = perfilConRol("CAPTURISTA");
            aviso(usuario, "2036-01-01T10:00:00Z", false);
            aviso(usuario, "2036-01-02T10:00:00Z", false);
            aviso(usuario, "2036-01-03T10:00:00Z", true);
            aviso(otro, "2036-01-03T10:00:00Z", false);

            LecturaMasivaDto resultado = como(usuario, servicio::marcarTodas);

            assertThat(resultado.marcadas()).isEqualTo(2);
            assertThat(como(usuario, () -> servicio.listar(false, 0, 20)).pagina().contenido()).isEmpty();
            assertThat(como(otro, () -> servicio.listar(false, 0, 20)).pagina().contenido()).hasSize(1);
        }

        @Test
        @DisplayName("VAL-015: sin notificaciones.aviso:CONSULTAR responde 403")
        void sinPermiso() {
            UUID sinRol = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Sin rol') RETURNING id")
                    .query(UUID.class).single();

            assertThatThrownBy(() -> como(sinRol, () -> servicio.listar(null, 0, 20)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.codigo()).isEqualTo("VAL-015");
                        assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                    });
        }

        @Test
        @DisplayName("Convenciones §4: el tamaño de página va de 1 a 100")
        void tamanoInvalido() {
            UUID usuario = perfilConRol("CAPTURISTA");

            assertThatThrownBy(() -> como(usuario, () -> servicio.listar(null, 0, 101)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class,
                            error -> assertThat(error.codigo()).isEqualTo("VALIDACION"));
        }
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private <T> T comoUsuario(UUID perfil, Supplier<T> accion) {
        iniciarSesion(perfil);
        return new TransactionTemplate(transacciones).execute(estado -> accion.get());
    }

    private <T> T como(UUID perfil, Supplier<T> accion) {
        iniciarSesion(perfil);
        return accion.get();
    }

    private UUID partido() {
        UUID cancha = jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA') RETURNING id
                        """).param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID())
                .query(UUID.class).single();
        // Cada partido en un horario propio para que el árbitro no choque consigo mismo (RF-039).
        OffsetDateTime inicio = OffsetDateTime.parse("2036-03-08T10:00:00-06:00")
                .plusDays(ThreadLocalRandom.current().nextInt(1, 3000));
        return new PartidosPrueba(jdbc).programar(cancha, inicio);
    }

    /** Árbitro validado de un club ajeno al partido, designado con esa función. */
    private UUID designar(UUID partido, String funcion) {
        UUID persona = persona();
        UUID arbitro = jdbc.sql("""
                        INSERT INTO siglim.arbitros (persona_id, club_id, estado) VALUES (:persona, :club, 'VALIDADO')
                        RETURNING id
                        """).param("persona", persona).param("club", club()).query(UUID.class).single();
        arbitrosCreados.add(arbitro);
        jdbc.sql("""
                        INSERT INTO siglim.designaciones (partido_id, arbitro_id, funcion)
                        VALUES (:partido, :arbitro, CAST(:funcion AS siglim.funcion_arbitral))
                        """).param("partido", partido).param("arbitro", arbitro).param("funcion", funcion).update();
        return arbitro;
    }

    private UUID personaDe(UUID arbitro) {
        return jdbc.sql("SELECT persona_id FROM siglim.arbitros WHERE id = :id").param("id", arbitro)
                .query(UUID.class).single();
    }

    /** Perfil de la persona del árbitro con el rol ARBITRO de ámbito propio. */
    private UUID perfilDeArbitro(UUID arbitro) {
        UUID perfil = perfilDePersona(personaDe(arbitro));
        asignar(perfil, "ARBITRO", "PROPIO", null);
        return perfil;
    }

    private UUID perfilDePersona(UUID persona) {
        return jdbc.sql("""
                        INSERT INTO siglim.perfiles (persona_id, nombre_mostrado) VALUES (:persona, 'Árbitro de prueba')
                        RETURNING id
                        """).param("persona", persona).query(UUID.class).single();
    }

    private UUID persona() {
        String curp = "XEXA" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000))
                + "HMNXXX" + String.format("%02d", ThreadLocalRandom.current().nextInt(100));
        return jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, apellido_materno, fecha_nacimiento)
                        VALUES (:curp, 'Árbitro', 'Prueba', 'Aviso', DATE '1990-01-01') RETURNING id
                        """).param("curp", curp).query(UUID.class).single();
    }

    private UUID club() {
        return jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club avisos " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private void cobroPendiente(UUID club, LocalDate fechaLimite) {
        jdbc.sql("""
                        INSERT INTO siglim.cobros (liga_id, club_id, tipo, concepto, monto, periodo_inicio, fecha_limite)
                        VALUES (:liga, :club, 'MANUAL', 'Cuota de prueba', :monto, :inicio, :limite)
                        """)
                .param("liga", LIGA).param("club", club).param("monto", new BigDecimal("1500.00"))
                .param("inicio", fechaLimite.minusDays(30)).param("limite", fechaLimite).update();
    }

    private UUID perfilConRol(String rol) {
        UUID perfil = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES (:nombre) RETURNING id")
                .param("nombre", rol + " de prueba").query(UUID.class).single();
        asignar(perfil, rol, "LIGA", null);
        return perfil;
    }

    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Directiva de prueba') RETURNING id")
                .query(UUID.class).single();
        asignar(perfil, "DIRECTIVA_CLUB", "CLUB", club);
        return perfil;
    }

    private void asignar(UUID perfil, String rol, String ambito, UUID club) {
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, CAST(:ambito AS siglim.ambito), :club FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).param("ambito", ambito).param("club", club).update();
    }

    /** Aviso insertado como dueño del esquema con fecha fija; leído o no. */
    private UUID aviso(UUID perfil, String creadaEn, boolean leida) {
        return jdbc.sql("""
                        INSERT INTO siglim.notificaciones (perfil_id, evento, titulo, enlace, creada_en, leida_en)
                        VALUES (:perfil, 'CEDULA_PENDIENTE', 'Aviso de prueba', '/app/partidos/prueba', :creada,
                                CASE WHEN :leida THEN CAST(:creada AS timestamptz) END)
                        RETURNING id
                        """)
                .param("perfil", perfil).param("creada", OffsetDateTime.parse(creadaEn)).param("leida", leida)
                .query(UUID.class).single();
    }

    private List<Aviso> avisos(UUID perfil) {
        return jdbc.sql("""
                        SELECT evento, titulo, cuerpo, enlace, leida_en FROM siglim.notificaciones
                         WHERE perfil_id = :perfil ORDER BY creada_en
                        """).param("perfil", perfil).query(Aviso.class).list();
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private record Aviso(String evento, String titulo, String cuerpo, String enlace, OffsetDateTime leidaEn) {
    }
}

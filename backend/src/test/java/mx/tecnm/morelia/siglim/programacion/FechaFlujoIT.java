package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-35 de punta a punta contra PostgreSQL real, con los datos que escriben H-32 (calendario) y H-33
 * (asignación): la vista por fecha muestra lo asignado y lo que no cupo con su causa; «Asignar» liga el
 * pendiente; la fecha se envía y se aprueba completa y llega al portal. Las jornadas de la fecha las
 * crea la asignación (DEC-188) y no se envían ni se dictaminan una por una (DEC-175).
 *
 * <p>Escenario: «Libre» con 4 equipos (2 partidos el domingo), una sola cancha activa en la liga y
 * horario de juego de 08:00 a 10:00: cabe un bloque, así que un partido queda sin asignar con
 * {@code VAL-007}. Las demás canchas y los árbitros validados de otras pruebas se apartan mientras corre.
 *
 * <p>Refs: H-35.1, H-32.2, H-33.2, RF-033, RF-042, RF-090, RF-121, DEC-175, DEC-180, DEC-188, DEC-189
 */
class FechaFlujoIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    /** Sábado: la fecha 1 es sábado 4 y domingo 5 de marzo de 2035. */
    private static final LocalDate INICIO = LocalDate.parse("2035-03-03");
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final String OBSERVACIONES = "Revisar el horario del segundo partido";

    @Autowired
    private CalendarioService calendario;

    @Autowired
    private AsignacionFechaService asignacion;

    @Autowired
    private FechaService fechas;

    @Autowired
    private JornadaService jornadas;

    private List<UUID> arbitrosAjenos = List.of();
    private List<UUID> canchasAjenas = List.of();

    @BeforeEach
    void aislar() {
        arbitrosAjenos = aislarArbitrosValidados(LIGA);
        canchasAjenas = jdbc.sql("""
                        UPDATE siglim.canchas SET activa = false
                         WHERE liga_id = :liga AND activa
                        RETURNING id
                        """)
                .param("liga", LIGA).query(UUID.class).list();
    }

    @AfterEach
    void restaurar() {
        restaurarArbitros(arbitrosAjenos);
        for (UUID cancha : canchasAjenas) {
            jdbc.sql("UPDATE siglim.canchas SET activa = true WHERE id = :id").param("id", cancha).update();
        }
    }

    @Test
    @DisplayName("H-35: calendario, asignación, «Asignar» del pendiente, envío y aprobación completos hasta el portal")
    void deCalendarioAPortal() {
        UUID programador = iniciarSesion(perfilConRol("PROGRAMADOR"));
        Escenario e = escenario();
        calendario.generar(e.temporada(), null);
        UUID fecha = fechas.listar(e.temporada(), 0, 20).contenido().getFirst().id();
        assertThat(fechas.consultar(fecha).jornadas()).as("DEC-188: el calendario no crea jornadas").isZero();

        AsignacionFechaDto asignada = asignacion.asignar(fecha, new AsignarFechaRequest(ModoAsignacion.PENDIENTES, null));

        FechaDto vista = fechas.consultar(fecha);
        assertThat(asignada.totales().asignados()).isEqualTo(1);
        assertThat(vista.jornadas()).as("la jornada del grupo la crea la asignación").isEqualTo(1);
        assertThat(vista.partidos()).isEqualTo(1);
        assertThat(vista.sinAsignar()).isEqualTo(1);
        FechaDto.Bloque bloque = vista.bloques().getFirst();
        assertThat(bloque.jornadaId()).isNotNull();
        assertThat(bloque.partidos()).singleElement().satisfies(partido -> {
            assertThat(partido.asignadoAutomaticamente()).isTrue();
            assertThat(partido.emparejamientoId()).isNotNull();
            assertThat(partido.cancha().id()).isEqualTo(e.cancha());
            assertThat(partido.arbitro()).as("sin árbitros validados").isNull();
        });
        FechaDto.SinAsignar pendiente = bloque.sinAsignar().getFirst();
        assertThat(pendiente.codigo()).isEqualTo("VAL-007");
        assertThat(pendiente.causa()).isNotBlank();

        OffsetDateTime alSalir = bloque.partidos().getFirst().finBloque();
        PartidoDto manual = jornadas.registrarPartido(bloque.jornadaId(), new CrearPartidoRequest(
                pendiente.equipoLocal().id(), pendiente.equipoVisitante().id(), e.cancha(), alSalir, null,
                pendiente.emparejamientoId()));

        FechaDto completa = fechas.consultar(fecha);
        assertThat(completa.sinAsignar()).isZero();
        assertThat(completa.bloques().getFirst().partidos()).extracting(FechaDto.FilaPartido::id)
                .contains(manual.id());
        assertThat(fechas.verificar(fecha).conflictos()).isEmpty();

        assertThatThrownBy(() -> jornadas.enviarAAprobacion(bloque.jornadaId()))
                .as("DEC-175: la jornada de una fecha no se envía sola")
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("RF-090");
                });

        assertThat(fechas.enviarAAprobacion(fecha).estadoPublicacion()).isEqualTo(EstadoJornada.REVISION);

        iniciarSesion(perfilConRol("PRESIDENTE"));
        assertThatThrownBy(() -> jornadas.resolver(bloque.jornadaId(),
                new ResolverJornadaRequest(DecisionJornada.PUBLICADO, null)))
                .as("DEC-175: la jornada de una fecha no se dictamina sola")
                .isInstanceOfSatisfying(ReglaNegocioException.class, error ->
                        assertThat(error.codigo()).isEqualTo("RF-090"));
        assertThat(fechas.resolver(fecha, new ResolverFechaRequest(DecisionJornada.BORRADOR, OBSERVACIONES))
                .estadoPublicacion()).isEqualTo(EstadoJornada.BORRADOR);

        iniciarSesion(programador);
        fechas.enviarAAprobacion(fecha);
        iniciarSesion(perfilConRol("PRESIDENTE"));
        FechaDto publicada = fechas.resolver(fecha, new ResolverFechaRequest(DecisionJornada.PUBLICADO, null));

        assertThat(publicada.estadoPublicacion()).isEqualTo(EstadoJornada.PUBLICADO);
        assertThat(jdbc.sql("SELECT partido_id FROM siglim.v_publica_programacion WHERE jornada_id = :id")
                .param("id", bloque.jornadaId()).query(UUID.class).list())
                .as("los dos partidos en el portal")
                .containsExactlyInAnyOrder(bloque.partidos().getFirst().id(), manual.id());
        assertThat(manual.inicio().atZoneSameInstant(ZONA).toLocalTime()).isEqualTo(LocalTime.of(10, 0));
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    private record Escenario(UUID temporada, UUID cancha) {
    }

    private Escenario escenario() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, :inicio) RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID()).param("inicio", INICIO)
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo, hora_fin_juego)
                        VALUES (:temporada, 1, 'Parámetros de prueba', TIME '10:00')
                        """)
                .param("temporada", temporada).update();
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Libre " + UUID.randomUUID())
                .query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria) RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria)
                .query(UUID.class).single();
        UUID grupo = jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'Grupo 1') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
        for (int i = 0; i < 4; i++) {
            UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                    .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID())
                    .query(UUID.class).single();
            jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre)")
                    .param("club", club).param("grupo", grupo).param("nombre", "Equipo " + UUID.randomUUID())
                    .update();
        }
        UUID cancha = jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID())
                .query(UUID.class).single();
        return new Escenario(temporada, cancha);
    }

    private UUID iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
        return perfil;
    }

    private UUID perfilConRol(String codigoRol) {
        UUID usuarioAuth = UUID.randomUUID();
        jdbc.sql("INSERT INTO auth.users (id, email, aud, role) VALUES (:id, :correo, 'authenticated', 'authenticated')")
                .param("id", usuarioAuth).param("correo", usuarioAuth + "@prueba.mx").update();
        UUID perfil = jdbc.sql("""
                        INSERT INTO siglim.perfiles (usuario_auth_id, nombre_mostrado, correo)
                        VALUES (:usuario, :nombre, :correo)
                        RETURNING id
                        """)
                .param("usuario", usuarioAuth).param("nombre", codigoRol + " " + usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, r.id, r.liga_id, 'LIGA' FROM siglim.roles r WHERE r.codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", codigoRol).update();
        return perfil;
    }
}

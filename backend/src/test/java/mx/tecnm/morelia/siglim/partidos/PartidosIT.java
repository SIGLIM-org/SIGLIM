package mx.tecnm.morelia.siglim.partidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.CrearJornadaRequest;
import mx.tecnm.morelia.siglim.programacion.CrearPartidoRequest;
import mx.tecnm.morelia.siglim.programacion.DecisionJornada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import mx.tecnm.morelia.siglim.programacion.JornadaService;
import mx.tecnm.morelia.siglim.programacion.ResolverJornadaRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H-15.1 contra PostgreSQL real: las transiciones del partido atraviesan la autorización, la máquina
 * de estados del servicio y la del trigger {@code tg_partidos_estado}, el historial con usuario, fecha
 * y motivo ({@code tg_partidos_historial}), el recálculo del bloque al reprogramar y la auditoría. La
 * consulta respeta el ámbito del club y solo muestra acciones a quien puede cambiar el estado.
 *
 * <p>Refs: H-15.1, RF-043, RF-045, RF-046, RN-021, VAL-004, DEC-160, DEC-161, DEC-162
 */
class PartidosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    private static final OffsetDateTime NUEVO_INICIO = OffsetDateTime.parse("2031-03-17T12:00:00-06:00");
    private static final String MOTIVO = "Cancha inundada por la lluvia de la noche";

    @Autowired
    private PartidosService servicio;

    @Autowired
    private JornadaService programacion;

    @Autowired
    private MotivoOperacion motivoOperacion;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    @DisplayName("RF-045: reprogramar guarda usuario, fecha y motivo, mueve el bloque y lo audita")
    void reprogramarConHistorial() {
        Escenario datos = escenarioPublicado();
        UUID programador = iniciarSesion(datos.programador());
        UUID otraCancha = crearCancha();

        PartidoDetalleDto detalle = servicio.transicionar(datos.partido(), new TransicionPartidoRequest(
                EstadoPartido.REPROGRAMADO, MOTIVO, NUEVO_INICIO, otraCancha));

        assertThat(detalle.estado()).isEqualTo(EstadoPartido.REPROGRAMADO);
        assertThat(detalle.inicio().toInstant()).isEqualTo(NUEVO_INICIO.toInstant());
        assertThat(detalle.finBloque()).as("lo recalcula tg_partidos_programacion").isAfter(detalle.inicio());
        assertThat(detalle.cancha().id()).isEqualTo(otraCancha);
        assertThat(detalle.accionesPermitidas()).as("reprogramado espera su hora").isEmpty();
        assertThat(detalle.historial()).hasSize(2).last().satisfies(cambio -> {
            assertThat(cambio.estadoAnterior()).isEqualTo(EstadoPartido.PROGRAMADO);
            assertThat(cambio.estadoNuevo()).isEqualTo(EstadoPartido.REPROGRAMADO);
            assertThat(cambio.motivo()).isEqualTo(MOTIVO);
            assertThat(cambio.cambiadoPor().id()).isEqualTo(programador);
            assertThat(cambio.cambiadoEn()).isNotNull();
        });
        assertThat(servicio.historial(datos.partido())).isEqualTo(detalle.historial());
        assertThat(jdbc.sql("""
                        SELECT accion || ':' || motivo FROM siglim.auditoria
                         WHERE entidad = 'partidos' AND entidad_id = :id AND modulo = 'M-08'
                        """).param("id", datos.partido().toString()).query(String.class).list())
                .containsExactly("CAMBIAR_ESTADO_PARTIDO:" + MOTIVO);
    }

    @Test
    @DisplayName("RF-043: en curso se suspende, suspendido se reprograma y queda cada paso en el historial")
    void suspenderYReprogramar() {
        Escenario datos = escenarioPublicado();
        iniciarPartido(datos.partido());
        iniciarSesion(datos.programador());
        assertThat(servicio.obtener(datos.partido()).accionesPermitidas()).containsExactly(AccionPartido.SUSPENDER);

        PartidoDetalleDto suspendido = servicio.transicionar(datos.partido(),
                new TransicionPartidoRequest(EstadoPartido.SUSPENDIDO, "Tormenta eléctrica en el minuto 30", null, null));
        assertThat(suspendido.accionesPermitidas()).containsExactly(AccionPartido.REPROGRAMAR, AccionPartido.CANCELAR);

        PartidoDetalleDto reprogramado = servicio.transicionar(datos.partido(),
                new TransicionPartidoRequest(EstadoPartido.REPROGRAMADO, MOTIVO, NUEVO_INICIO, null));

        assertThat(reprogramado.cancha().id()).as("sin canchaId conserva la cancha").isEqualTo(datos.cancha());
        assertThat(reprogramado.historial()).extracting(CambioEstadoPartidoDto::estadoNuevo).containsExactly(
                EstadoPartido.PROGRAMADO, EstadoPartido.EN_CURSO, EstadoPartido.SUSPENDIDO, EstadoPartido.REPROGRAMADO);
    }

    @Test
    @DisplayName("RF-043: se suspende un partido en curso aunque su cancha quede en mantenimiento en ese horario")
    void suspenderConCanchaEnMantenimiento() {
        Escenario datos = escenarioPublicado();
        iniciarPartido(datos.partido());
        OffsetDateTime finBloque = finBloque(datos.partido());
        jdbc.sql("""
                        INSERT INTO siglim.disponibilidad_canchas (cancha_id, tipo, periodo, motivo)
                        VALUES (:cancha, 'MANTENIMIENTO',
                                tstzrange(CAST(:desde AS timestamptz), CAST(:hasta AS timestamptz), '[)'),
                                'Cancha inundada')
                        """)
                .param("cancha", datos.cancha())
                .param("desde", INICIO.minusHours(1))
                .param("hasta", INICIO.plusHours(6))
                .update();
        String motivo = "Cancha inundada durante el primer tiempo";
        iniciarSesion(datos.programador());

        PartidoDetalleDto suspendido = servicio.transicionar(datos.partido(),
                new TransicionPartidoRequest(EstadoPartido.SUSPENDIDO, motivo, null, null));

        assertThat(suspendido.estado()).isEqualTo(EstadoPartido.SUSPENDIDO);
        assertThat(suspendido.historial()).last().satisfies(cambio -> {
            assertThat(cambio.estadoNuevo()).isEqualTo(EstadoPartido.SUSPENDIDO);
            assertThat(cambio.motivo()).isEqualTo(motivo);
            assertThat(cambio.cambiadoPor().id()).isEqualTo(datos.programador());
        });
        assertThat(finBloque(datos.partido())).as("suspender no recalcula el bloque").isEqualTo(finBloque);

        PartidoDetalleDto cancelado = servicio.transicionar(datos.partido(),
                new TransicionPartidoRequest(EstadoPartido.CANCELADO, "La cancha no estará lista esta temporada",
                        null, null));
        assertThat(cancelado.estado()).as("cancelar tampoco pasa por la programación")
                .isEqualTo(EstadoPartido.CANCELADO);
    }

    @Test
    @DisplayName("RF-046: cancelado no admite más transiciones y la prohibida responde 409 sin escribir")
    void canceladoEsFinal() {
        Escenario datos = escenarioPublicado();
        iniciarSesion(datos.programador());

        assertThat(servicio.transicionar(datos.partido(),
                new TransicionPartidoRequest(EstadoPartido.CANCELADO, MOTIVO, null, null)).accionesPermitidas()).isEmpty();

        assertThatThrownBy(() -> servicio.transicionar(datos.partido(),
                new TransicionPartidoRequest(EstadoPartido.REPROGRAMADO, MOTIVO, NUEVO_INICIO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("RF-046");
                    assertThat(error.propiedades()).containsEntry("estadoActual", EstadoPartido.CANCELADO);
                });
        assertThat(servicio.historial(datos.partido())).hasSize(2);
    }

    @Test
    @DisplayName("RF-046: suspender un partido programado y finalizarlo a mano responden 409 RF-046")
    void prohibidasDesdeProgramado() {
        Escenario datos = escenarioPublicado();
        iniciarSesion(datos.programador());

        for (EstadoPartido destino : List.of(EstadoPartido.SUSPENDIDO, EstadoPartido.FINALIZADO, EstadoPartido.EN_CURSO)) {
            assertThatThrownBy(() -> servicio.transicionar(datos.partido(),
                    new TransicionPartidoRequest(destino, MOTIVO, null, null)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.codigo()).isEqualTo("RF-046");
                        assertThat(error.propiedades()).containsEntry("accionesPermitidas",
                                List.of(AccionPartido.REPROGRAMAR, AccionPartido.CANCELAR));
                    });
        }
        assertThat(estado(datos.partido())).isEqualTo("PROGRAMADO");
    }

    @Test
    @DisplayName("VAL-004: reprogramar a la cancha y hora de otro partido responde 409 y no cambia nada")
    void reprogramarConConflicto() {
        Escenario datos = escenarioPublicado();
        iniciarSesion(datos.programador());

        assertThatThrownBy(() -> servicio.transicionar(datos.partido(), new TransicionPartidoRequest(
                EstadoPartido.REPROGRAMADO, MOTIVO, datos.inicioDelOtro(), datos.cancha())))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("VAL-004");
                });
        assertThat(estado(datos.partido())).isEqualTo("PROGRAMADO");
    }

    @Test
    @DisplayName("DEC-162: en una jornada en borrador la transición responde 409 RF-090")
    void jornadaEnBorrador() {
        UUID programador = iniciarSesion(perfilConRol("PROGRAMADOR"));
        UUID grupo = crearGrupo();
        UUID jornada = programacion.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID partido = programacion.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), crearCancha(), INICIO)).id();
        iniciarSesion(programador);

        assertThat(servicio.obtener(partido).accionesPermitidas()).isEmpty();
        assertThatThrownBy(() -> servicio.transicionar(partido,
                new TransicionPartidoRequest(EstadoPartido.CANCELADO, MOTIVO, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("RF-090"));
    }

    @Test
    @DisplayName("VAL-015: el Presidente consulta sin acciones y no puede cambiar el estado")
    void presidenteSinCambiarEstado() {
        Escenario datos = escenarioPublicado();
        iniciarSesion(perfilConRol("PRESIDENTE"));

        assertThat(servicio.obtener(datos.partido()).accionesPermitidas()).isEmpty();
        assertThatThrownBy(() -> servicio.transicionar(datos.partido(),
                new TransicionPartidoRequest(EstadoPartido.CANCELADO, MOTIVO, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(estado(datos.partido())).isEqualTo("PROGRAMADO");
    }

    @Test
    @DisplayName("ADR-0007: la Directiva ve los partidos de su club y no los de otros clubes")
    void ambitoDeClub() {
        Escenario datos = escenarioPublicado();
        iniciarSesion(perfilDeClub(clubDe(datos.local())));

        FiltroPartidos filtro = new FiltroPartidos(datos.jornada(), null, null, null, 0, 20);
        assertThat(servicio.listar(filtro).contenido()).extracting(PartidoResumenDto::id)
                .containsExactly(datos.partido());
        assertThat(servicio.listar(filtro).totalElementos()).isEqualTo(1);
        assertThat(servicio.obtener(datos.partido()).accionesPermitidas()).isEmpty();
        assertThatThrownBy(() -> servicio.obtener(datos.otroPartido())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.historial(datos.otroPartido()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("RF-043: el listado filtra por estado y rango de inicio")
    void filtros() {
        Escenario datos = escenarioPublicado();
        iniciarSesion(datos.programador());
        servicio.transicionar(datos.otroPartido(), new TransicionPartidoRequest(EstadoPartido.CANCELADO, MOTIVO,
                null, null));

        assertThat(servicio.listar(new FiltroPartidos(datos.jornada(), EstadoPartido.CANCELADO, null, null, 0, 20))
                .contenido()).extracting(PartidoResumenDto::id).containsExactly(datos.otroPartido());
        assertThat(servicio.listar(new FiltroPartidos(datos.jornada(), null, INICIO, INICIO.plusHours(1), 0, 20))
                .contenido()).extracting(PartidoResumenDto::id).containsExactly(datos.partido());
        assertThat(servicio.listar(new FiltroPartidos(datos.jornada(), null, null, null, 0, 20)).contenido())
                .extracting(PartidoResumenDto::estado)
                .containsExactly(EstadoPartido.PROGRAMADO, EstadoPartido.CANCELADO);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private record Escenario(UUID programador, UUID jornada, UUID partido, UUID otroPartido, UUID local,
                             UUID cancha, OffsetDateTime inicioDelOtro) {
    }

    /** Jornada publicada con dos partidos en la misma cancha, uno a las 10:00 y otro a las 14:00. */
    private Escenario escenarioPublicado() {
        UUID programador = iniciarSesion(perfilConRol("PROGRAMADOR"));
        UUID grupo = crearGrupo();
        UUID cancha = crearCancha();
        UUID local = crearEquipo(grupo, "Halcones");
        UUID jornada = programacion.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID partido = programacion.registrarPartido(jornada, new CrearPartidoRequest(
                local, crearEquipo(grupo, "Toros"), cancha, INICIO)).id();
        OffsetDateTime inicioDelOtro = INICIO.plusHours(4);
        UUID otro = programacion.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Pumas"), crearEquipo(grupo, "Lobos"), cancha, inicioDelOtro)).id();
        programacion.enviarAAprobacion(jornada);
        iniciarSesion(perfilConRol("PRESIDENTE"));
        programacion.resolver(jornada, new ResolverJornadaRequest(DecisionJornada.PUBLICADO, null));
        SecurityContextHolder.clearContext();
        return new Escenario(programador, jornada, partido, otro, local, cancha, inicioDelOtro);
    }

    /** Pone el partido en curso, como lo hará la tarea programada de RF-127, con una sesión de la liga. */
    private void iniciarPartido(UUID partido) {
        iniciarSesionComoAdministrador();
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            motivoOperacion.fijar("Inicio del partido a su hora");
            jdbc.sql("UPDATE siglim.partidos SET estado = 'EN_CURSO' WHERE id = :id").param("id", partido).update();
        });
        SecurityContextHolder.clearContext();
    }

    private String estado(UUID partido) {
        return jdbc.sql("SELECT estado::text FROM siglim.partidos WHERE id = :id")
                .param("id", partido).query(String.class).single();
    }

    private OffsetDateTime finBloque(UUID partido) {
        return jdbc.sql("SELECT fin_bloque FROM siglim.partidos WHERE id = :id")
                .param("id", partido).query(OffsetDateTime.class).single();
    }

    private UUID clubDe(UUID equipo) {
        return jdbc.sql("SELECT club_id FROM siglim.equipos WHERE id = :id")
                .param("id", equipo).query(UUID.class).single();
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

    private UUID perfil(String nombre) {
        UUID usuarioAuth = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO auth.users (id, email, aud, role)
                        VALUES (:id, :correo, 'authenticated', 'authenticated')
                        """)
                .param("id", usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .update();
        return jdbc.sql("""
                        INSERT INTO siglim.perfiles (usuario_auth_id, nombre_mostrado, correo)
                        VALUES (:usuario, :nombre, :correo)
                        RETURNING id
                        """)
                .param("usuario", usuarioAuth)
                .param("nombre", nombre + " " + usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .query(UUID.class)
                .single();
    }

    /** Cuenta con el rol sembrado y ámbito de liga. */
    private UUID perfilConRol(String codigoRol) {
        UUID perfil = perfil(codigoRol);
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, r.id, r.liga_id, 'LIGA' FROM siglim.roles r WHERE r.codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", codigoRol).update();
        return perfil;
    }

    /** Cuenta de la Directiva con ámbito de su club. */
    private UUID perfilDeClub(UUID club) {
        UUID perfil = perfil("DIRECTIVA_CLUB");
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, r.id, r.liga_id, 'CLUB', :club FROM siglim.roles r WHERE r.codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil).param("club", club).update();
        return perfil;
    }

    /** Temporada con parámetros, categoría y grupo: lo mínimo para que el trigger calcule el bloque. */
    private UUID crearGrupo() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2031-08-01') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo)
                        VALUES (:temporada, 1, 'Parámetros de prueba')
                        """)
                .param("temporada", temporada).update();
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Categoría " + UUID.randomUUID())
                .query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria) RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria)
                .query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'A') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
    }

    private UUID crearEquipo(UUID grupo, String nombre) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID())
                .query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre) RETURNING id")
                .param("club", club).param("grupo", grupo).param("nombre", nombre + " " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID crearCancha() {
        return jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID())
                .query(UUID.class).single();
    }
}

package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErrorTraducido;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.compartido.error.TraductorErroresBd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H-12.1 a H-12.3 contra PostgreSQL real: el alta de jornadas y partidos y el ajuste motivado
 * atraviesan autorización por permiso y ámbito, el validador de conflictos (VAL-003 a VAL-006), el
 * trigger del bloque de cancha, la restricción GiST, el historial del partido y la auditoría. La
 * verificación lista los conflictos posteriores sin escribir y el trigger respalda al servicio.
 */
class ProgramacionIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    private static final String MOTIVO = "Cambio de horario pedido por la Liga";

    @Autowired
    private JornadaService servicio;

    @Autowired
    private JornadaRepository repositorio;

    @Autowired
    private TraductorErroresBd traductor;

    @Autowired
    private PlatformTransactionManager transacciones;

    // ── Registro y ajuste (RF-033, RF-036, RF-040) ─────────────────────────────────────────────

    @Test
    @DisplayName("RF-033, RF-036: una jornada en borrador admite partidos y la base calcula su bloque")
    void jornadaYPartidoEnBorrador() {
        UUID programador = iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID local = crearEquipo(grupo, "Halcones");
        UUID visitante = crearEquipo(grupo, "Toros");
        UUID cancha = crearCancha();

        JornadaDto jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1));
        PartidoDto partido = servicio.registrarPartido(jornada.id(),
                new CrearPartidoRequest(local, visitante, cancha, INICIO));

        assertThat(jornada.estadoPublicacion()).isEqualTo(EstadoJornada.BORRADOR);
        assertThat(partido.estado()).isEqualTo(EstadoPartido.PROGRAMADO);
        assertThat(partido.finBloque()).as("bloque de cancha [DEMO] de 120 minutos")
                .isEqualTo(INICIO.plusHours(2));
        assertThat(partido.equipoLocal().nombre()).startsWith("Halcones");
        assertThat(partido.cancha().id()).isEqualTo(cancha);

        JornadaDto detalle = servicio.consultar(jornada.id());
        assertThat(detalle.partidos()).extracting(PartidoDto::id).containsExactly(partido.id());

        assertThat(historialDelPartido(partido.id())).singleElement().satisfies(paso -> {
            assertThat(paso.estadoNuevo()).isEqualTo("PROGRAMADO");
            assertThat(paso.motivo()).isEqualTo("Programación del partido");
            assertThat(paso.cambiadoPor()).isEqualTo(programador);
        });
        assertThat(accionesAuditadas(partido.id()))
                .contains("REGISTRAR_PARTIDO");
        assertThat(accionesAuditadas(jornada.id())).contains("REGISTRAR_JORNADA");
    }

    @Test
    @DisplayName("RF-040, RN-018: el ajuste con motivo mueve el partido y deja el motivo en la auditoría")
    void ajusteConMotivo() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID cancha = crearCancha();
        UUID otraCancha = crearCancha();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        PartidoDto partido = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), cancha, INICIO));
        OffsetDateTime nuevoInicio = INICIO.plusDays(1);

        PartidoDto ajustado = servicio.actualizarPartido(partido.id(), new ActualizarPartidoRequest(
                null, null, otraCancha, nuevoInicio, "  " + MOTIVO + "  "));

        assertThat(ajustado.cancha().id()).isEqualTo(otraCancha);
        assertThat(ajustado.inicio()).isEqualTo(nuevoInicio);
        assertThat(ajustado.finBloque()).isEqualTo(nuevoInicio.plusHours(2));

        record Auditoria(String motivo, String detalle) {
        }
        Auditoria auditoria = jdbc.sql("""
                        SELECT motivo, CAST(detalle AS text) AS detalle
                          FROM siglim.auditoria
                         WHERE entidad = 'partidos' AND entidad_id = :id AND accion = 'AJUSTAR_PARTIDO'
                        """)
                .param("id", partido.id().toString())
                .query(Auditoria.class)
                .single();
        assertThat(auditoria.motivo()).isEqualTo(MOTIVO);
        assertThat(auditoria.detalle()).contains("canchaId", "inicio")
                .doesNotContain("equipoLocalId", "equipoVisitanteId");
    }

    @Test
    @DisplayName("RN-021: un ajuste sin motivo se rechaza y el partido no cambia")
    void ajusteSinMotivo() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 2)).id();
        PartidoDto partido = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), crearCancha(), INICIO));

        assertThatThrownBy(() -> servicio.actualizarPartido(partido.id(),
                new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), "Corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "RN-021"));

        assertThat(servicio.consultar(jornada).partidos().getFirst().inicio()).isEqualTo(INICIO);
    }

    @Test
    @DisplayName("RF-090: en revisión la jornada ya no admite partidos ni ajustes")
    void jornadaEnRevision() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        PartidoDto partido = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), crearCancha(), INICIO));

        jdbc.sql("UPDATE siglim.jornadas SET estado_publicacion = 'REVISION' WHERE id = :id")
                .param("id", jornada).update();

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Águilas"), crearEquipo(grupo, "Pumas"), crearCancha(), INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));
        assertThatThrownBy(() -> servicio.actualizarPartido(partido.id(),
                new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));

        assertThat(jdbc.sql("SELECT count(*) FROM siglim.partidos WHERE jornada_id = :id")
                .param("id", jornada).query(Long.class).single()).as("no se agregó otro partido").isEqualTo(1L);
    }

    @Test
    @DisplayName("RF-033: un equipo de otro grupo se rechaza con 422 y no queda el partido")
    void equipoDeOtroGrupo() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(crearGrupo(), "Ajenos"), crearCancha(), INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "RF-033"));
        assertThat(servicio.consultar(jornada).partidos()).isEmpty();
    }

    // ── Conflictos duros en el servicio (RF-039, VAL-003 a VAL-006) ───────────────────────────

    @Test
    @DisplayName("VAL-004: la cancha ocupada se rechaza y el mensaje nombra el otro partido y el horario")
    void canchaOcupada() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID cancha = crearCancha();
        servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), cancha, INICIO));

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Águilas"), crearEquipo(grupo, "Pumas"), cancha, INICIO.plusMinutes(30))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "VAL-004"))
                .hasMessageContaining("Halcones", "Toros", nombreDeCancha(cancha), "10/03/2031 10:00");

        assertThat(servicio.consultar(jornada).partidos()).as("solo quedó el primer partido").hasSize(1);
    }

    @Test
    @DisplayName("VAL-003: el equipo con otro partido en el bloque se rechaza y nombra el partido")
    void equipoDuplicado() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID local = crearEquipo(grupo, "Halcones");
        UUID primeraCancha = crearCancha();
        servicio.registrarPartido(jornada, new CrearPartidoRequest(
                local, crearEquipo(grupo, "Toros"), primeraCancha, INICIO));

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                local, crearEquipo(grupo, "Pumas"), crearCancha(), INICIO.plusHours(1))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "VAL-003"))
                .hasMessageContaining("Halcones", "Toros", nombreDeCancha(primeraCancha),
                        "10/03/2031 10:00");
    }

    @Test
    @DisplayName("VAL-005: una cancha inactiva se rechaza y el mensaje dice por qué")
    void canchaInactiva() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID cancha = crearCancha();
        jdbc.sql("UPDATE siglim.canchas SET activa = false WHERE id = :id")
                .param("id", cancha).update();

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), cancha, INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "VAL-005"))
                .hasMessageContaining(nombreDeCancha(cancha), "inactiva");
        assertThat(servicio.consultar(jornada).partidos()).isEmpty();
    }

    @Test
    @DisplayName("VAL-005: un bloqueo con motivo impide programar en la cancha")
    void canchaBloqueada() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID cancha = crearCancha();
        restringirCancha(cancha, "BLOQUEO", INICIO.minusHours(1), INICIO.plusHours(1),
                "Riego del césped");

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), cancha, INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "VAL-005"))
                .hasMessageContaining(nombreDeCancha(cancha), "bloqueo", "Riego del césped");
    }

    @Test
    @DisplayName("VAL-005: un mantenimiento con motivo impide programar en la cancha")
    void canchaEnMantenimiento() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID cancha = crearCancha();
        restringirCancha(cancha, "MANTENIMIENTO", INICIO, INICIO.plusDays(1), "Cambio de pasto");

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), cancha, INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "VAL-005"))
                .hasMessageContaining(nombreDeCancha(cancha), "mantenimiento", "Cambio de pasto");
    }

    @Test
    @DisplayName("VAL-006: un equipo dado de baja no se puede programar")
    void equipoDadoDeBaja() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID visitante = crearEquipo(grupo, "Toros");
        jdbc.sql("""
                        UPDATE siglim.equipos
                           SET estado = 'BAJA', baja_en = DATE '2031-03-01', motivo_baja = 'Baja de prueba'
                         WHERE id = :id
                        """).param("id", visitante).update();

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), visitante, crearCancha(), INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "VAL-006"))
                .hasMessageContaining("Toros", "baja");
    }

    @Test
    @DisplayName("VAL-006: una suspensión vigente del equipo impide programarlo")
    void equipoSancionado() {
        UUID perfil = iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID visitante = crearEquipo(grupo, "Toros");
        sancionarEquipo(grupo, visitante, perfil, "SUSPENSION_PARTIDOS", (short) 2);

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), visitante, crearCancha(), INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "VAL-006"))
                .hasMessageContaining("Toros", "suspensión vigente de 2");
    }

    @Test
    @DisplayName("VAL-006: una suspensión ya cumplida no bloquea aunque su estado siga VIGENTE")
    void equipoConSuspensionCumplida() {
        UUID perfil = iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID visitante = crearEquipo(grupo, "Toros");
        sancionarEquipo(grupo, visitante, perfil, "SUSPENSION_PARTIDOS", (short) 2, (short) 2);
        String estado = jdbc.sql("SELECT estado::text FROM siglim.sanciones WHERE equipo_id = :equipo")
                .param("equipo", visitante).query(String.class).single();
        assertThat(estado).as("el estado no se actualizó: el contador de partidos decide")
                .isEqualTo("VIGENTE");

        PartidoDto programado = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), visitante, crearCancha(), INICIO));

        assertThat(programado.estado()).isEqualTo(EstadoPartido.PROGRAMADO);
    }

    @Test
    @DisplayName("RF-039, DEC-25: el bloque de cancha sale de los parámetros de la categoría")
    void bloqueDeLaCategoria() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo(180);
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID cancha = crearCancha();
        PartidoDto primero = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), cancha, INICIO));

        assertThat(primero.finBloque()).as("bloque de la categoría: 180 minutos")
                .isEqualTo(INICIO.plusHours(3));
        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Águilas"), crearEquipo(grupo, "Pumas"), cancha, INICIO.plusHours(2))))
                .as("con el bloque [DEMO] de 120 min no habría choque; el de la categoría sí")
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "VAL-004"));
    }

    @Test
    @DisplayName("RF-039: dos partidos en la misma cancha en bloques contiguos no chocan")
    void bloquesContiguos() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo(60);
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID cancha = crearCancha();
        PartidoDto primero = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), cancha, INICIO));

        PartidoDto segundo = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Águilas"), crearEquipo(grupo, "Pumas"), cancha, INICIO.plusHours(1)));

        assertThat(primero.finBloque()).isEqualTo(INICIO.plusHours(1));
        assertThat(segundo.inicio()).isEqualTo(INICIO.plusHours(1));
    }

    @Test
    @DisplayName("VAL-004: el ajuste detecta la cancha ocupada antes de escribir")
    void ajusteConCanchaOcupada() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID ocupada = crearCancha();
        UUID libre = crearCancha();
        servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), ocupada, INICIO));
        PartidoDto ajustable = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Águilas"), crearEquipo(grupo, "Pumas"), libre, INICIO.plusHours(3)));

        assertThatThrownBy(() -> servicio.actualizarPartido(ajustable.id(), new ActualizarPartidoRequest(
                null, null, ocupada, INICIO.plusMinutes(30), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "VAL-004"))
                .hasMessageContaining(nombreDeCancha(ocupada), "10/03/2031 10:00");

        PartidoDto sinCambios = servicio.consultar(jornada).partidos().stream()
                .filter(partido -> partido.id().equals(ajustable.id()))
                .findFirst().orElseThrow();
        assertThat(sinCambios.cancha().id()).isEqualTo(libre);
        assertThat(sinCambios.inicio()).isEqualTo(INICIO.plusHours(3));
    }

    @Test
    @DisplayName("VAL-003: el ajuste no compara el partido consigo mismo")
    void ajusteDelPropioPartido() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        PartidoDto partido = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), crearCancha(), INICIO));
        OffsetDateTime nuevoInicio = INICIO.plusMinutes(30);

        PartidoDto ajustado = servicio.actualizarPartido(partido.id(), new ActualizarPartidoRequest(
                null, null, null, nuevoInicio, MOTIVO));

        assertThat(ajustado.inicio()).isEqualTo(nuevoInicio);
        assertThat(ajustado.finBloque()).isEqualTo(nuevoInicio.plusHours(2));
    }

    @Test
    @DisplayName("VAL-003: el ajuste detecta el equipo ocupado en otro partido")
    void ajusteConEquipoOcupado() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID local = crearEquipo(grupo, "Halcones");
        servicio.registrarPartido(jornada, new CrearPartidoRequest(
                local, crearEquipo(grupo, "Toros"), crearCancha(), INICIO));
        PartidoDto ajustable = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                local, crearEquipo(grupo, "Pumas"), crearCancha(), INICIO.plusHours(3)));

        assertThatThrownBy(() -> servicio.actualizarPartido(ajustable.id(), new ActualizarPartidoRequest(
                null, null, null, INICIO.plusHours(1), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "VAL-003"))
                .hasMessageContaining("Halcones", "Toros");
    }

    // ── Verificación sin guardar y respaldo del trigger (H-12.3, RF-039) ───────────────────────

    @Test
    @DisplayName("RF-039: verificar lista los conflictos posteriores de todos los partidos sin escribir")
    void verificarListaConflictosSinEscribir() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID canchaBloqueada = crearCancha();
        UUID canchaLibre = crearCancha();
        UUID visitanteDeBaja = crearEquipo(grupo, "Toros");
        PartidoDto primero = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Pumas"), canchaBloqueada, INICIO));
        PartidoDto segundo = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Águilas"), visitanteDeBaja, canchaLibre, INICIO.plusHours(3)));

        // Después de programar: se bloquea la cancha y el equipo causa baja. La base ya lo permite,
        // así que la verificación es la que debe revelar ambos conflictos.
        restringirCancha(canchaBloqueada, "BLOQUEO", INICIO.minusMinutes(30), INICIO.plusMinutes(30),
                "Riego del césped");
        jdbc.sql("""
                        UPDATE siglim.equipos
                           SET estado = 'BAJA', baja_en = DATE '2031-03-01', motivo_baja = 'Baja de prueba'
                         WHERE id = :id
                        """).param("id", visitanteDeBaja).update();
        long partidosAntes = contarPartidos(jornada);
        long auditoriaAntes = contarAuditoriaDe(jornada, primero.id(), segundo.id());

        VerificacionJornadaDto verificacion = servicio.verificar(jornada);

        assertThat(verificacion.conflictos()).extracting(VerificacionJornadaDto.Aviso::partidoId)
                .containsExactly(primero.id(), segundo.id());
        assertThat(verificacion.conflictos()).extracting(VerificacionJornadaDto.Aviso::codigo)
                .containsExactly("VAL-005", "VAL-006");
        assertThat(verificacion.conflictos().getFirst().mensaje())
                .contains(nombreDeCancha(canchaBloqueada), "bloqueo", "Riego del césped");
        assertThat(verificacion.conflictos().getLast().mensaje()).contains("Toros", "baja");
        assertThat(verificacion.advertencias()).as("la localía es backlog B-15").isEmpty();
        assertThat(contarPartidos(jornada)).as("verificar no guarda nada").isEqualTo(partidosAntes);
        assertThat(contarAuditoriaDe(jornada, primero.id(), segundo.id()))
                .as("verificar no audita").isEqualTo(auditoriaAntes);
    }

    @Test
    @DisplayName("VAL-004: la restricción GiST respalda al servicio y su error se traduce a 409 VAL-004")
    void triggerRespaldaCanchaOcupada() {
        UUID programador = iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID cancha = crearCancha();
        servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), cancha, INICIO));
        UUID local = crearEquipo(grupo, "Águilas");
        UUID visitante = crearEquipo(grupo, "Pumas");

        // Se salta el validador del servicio a propósito: el INSERT directo llega a la base.
        Throwable error = catchThrowable(() -> new TransactionTemplate(transacciones)
                .executeWithoutResult(estado -> repositorio.insertarPartido(jornada, local, visitante,
                        cancha, INICIO.plusMinutes(30), programador)));

        assertThat(error).as("la restricción GiST debía rechazar la cancha ocupada").isNotNull();
        assertThat(traductor.traducir(error, false)).isEqualTo(new ErrorTraducido(
                HttpStatus.CONFLICT, "VAL-004", "La cancha ya está ocupada en ese horario."));
    }

    @Test
    @DisplayName("VAL-003: el trigger respalda al servicio y su error se traduce a 409 VAL-003")
    void triggerRespaldaEquipoOcupado() {
        UUID programador = iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID local = crearEquipo(grupo, "Halcones");
        servicio.registrarPartido(jornada, new CrearPartidoRequest(
                local, crearEquipo(grupo, "Toros"), crearCancha(), INICIO));
        UUID visitante = crearEquipo(grupo, "Pumas");
        UUID otraCancha = crearCancha();

        Throwable error = catchThrowable(() -> new TransactionTemplate(transacciones)
                .executeWithoutResult(estado -> repositorio.insertarPartido(jornada, local, visitante,
                        otraCancha, INICIO.plusMinutes(30), programador)));

        assertThat(error).as("el trigger debía rechazar el equipo repetido").isNotNull();
        ErrorTraducido traducido = traductor.traducir(error, false);
        assertThat(traducido.estado()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(traducido.codigo()).isEqualTo("VAL-003");
        assertThat(traducido.detalle()).contains("Halcones", "se traslapa");
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private UUID iniciarSesionComoProgramador() {
        UUID perfil = perfilConRol("PROGRAMADOR");
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
        return perfil;
    }

    /** Crea una cuenta con el rol sembrado, con ámbito de liga. */
    private UUID perfilConRol(String codigoRol) {
        UUID usuarioAuth = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO auth.users (id, email, aud, role)
                        VALUES (:id, :correo, 'authenticated', 'authenticated')
                        """)
                .param("id", usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .update();
        UUID perfil = jdbc.sql("""
                        INSERT INTO siglim.perfiles (usuario_auth_id, nombre_mostrado, correo)
                        VALUES (:usuario, :nombre, :correo)
                        RETURNING id
                        """)
                .param("usuario", usuarioAuth)
                .param("nombre", codigoRol + " " + usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, r.id, r.liga_id, 'LIGA'
                          FROM siglim.roles r
                         WHERE r.codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", codigoRol)
                .update();
        return perfil;
    }

    /** Temporada con parámetros, categoría y grupo: lo mínimo para que el trigger calcule el bloque. */
    private UUID crearGrupo() {
        return crearGrupo(null);
    }

    /** Igual, con un bloque de cancha propio de la categoría cuando {@code bloqueCategoriaMin} no es nulo. */
    private UUID crearGrupo(Integer bloqueCategoriaMin) {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2031-08-01')
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
        UUID parametros = jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo)
                        VALUES (:temporada, 1, 'Parámetros de prueba')
                        RETURNING id
                        """)
                .param("temporada", temporada).query(UUID.class).single();
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", "Categoría " + UUID.randomUUID())
                .query(UUID.class).single();
        if (bloqueCategoriaMin != null) {
            jdbc.sql("""
                            INSERT INTO siglim.parametros_categoria
                                (parametros_id, categoria_id, duracion_juego_min, bloque_cancha_min)
                            VALUES (:parametros, :categoria, :duracion, :bloque)
                            """)
                    .param("parametros", parametros)
                    .param("categoria", categoria)
                    .param("duracion", Math.min(90, bloqueCategoriaMin))
                    .param("bloque", bloqueCategoriaMin)
                    .update();
        }
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria)
                        RETURNING id
                        """)
                .param("temporada", temporada)
                .param("categoria", categoria)
                .query(UUID.class).single();
        return jdbc.sql("""
                        INSERT INTO siglim.grupos (categoria_temporada_id, nombre)
                        VALUES (:ct, 'A')
                        RETURNING id
                        """)
                .param("ct", categoriaTemporada)
                .query(UUID.class).single();
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
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA')
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private String nombreDeCancha(UUID cancha) {
        return jdbc.sql("SELECT nombre FROM siglim.canchas WHERE id = :id")
                .param("id", cancha).query(String.class).single();
    }

    private void restringirCancha(UUID cancha, String tipo, OffsetDateTime desde,
            OffsetDateTime hasta, String motivo) {
        jdbc.sql("""
                        INSERT INTO siglim.disponibilidad_canchas (cancha_id, tipo, periodo, motivo)
                        VALUES (:cancha, CAST(:tipo AS siglim.tipo_disponibilidad),
                                tstzrange(CAST(:desde AS timestamptz), CAST(:hasta AS timestamptz), '[)'),
                                :motivo)
                        """)
                .param("cancha", cancha)
                .param("tipo", tipo)
                .param("desde", desde)
                .param("hasta", hasta)
                .param("motivo", motivo)
                .update();
    }

    /** Sanción vigente de equipo con su expediente y resolución: lo mínimo que exigen las tablas. */
    private void sancionarEquipo(UUID grupo, UUID equipo, UUID perfil, String tipo, short partidos) {
        sancionarEquipo(grupo, equipo, perfil, tipo, partidos, (short) 0);
    }

    /** Igual, con los partidos ya cumplidos para probar una suspensión agotada. */
    private void sancionarEquipo(UUID grupo, UUID equipo, UUID perfil, String tipo, short partidos,
            short partidosCumplidos) {
        UUID temporada = temporadaDe(grupo);
        UUID articulo = jdbc.sql("""
                        INSERT INTO siglim.articulos
                            (liga_id, numero, descripcion, tipo_sancion, sujeto, partidos_suspension)
                        VALUES (:liga, :numero, 'Artículo de prueba',
                                CAST(:tipo AS siglim.tipo_sancion), 'EQUIPO',
                                CASE WHEN :tipo = 'SUSPENSION_PARTIDOS' THEN :partidos END)
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("numero", "ART-" + UUID.randomUUID().toString().substring(0, 16))
                .param("tipo", tipo)
                .param("partidos", partidos)
                .query(UUID.class).single();
        UUID expediente = jdbc.sql("""
                        INSERT INTO siglim.expedientes (liga_id, temporada_id, folio, origen, descripcion)
                        VALUES (:liga, :temporada, :folio, 'MANUAL', 'Prueba de sanción de equipo')
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("temporada", temporada)
                .param("folio", "PRUEBA-" + UUID.randomUUID().toString().substring(0, 20))
                .query(UUID.class).single();
        UUID resolucion = jdbc.sql("""
                        INSERT INTO siglim.resoluciones (expediente_id, articulo_id, fundamentos, resuelta_por)
                        VALUES (:expediente, :articulo, 'Fundamentos de prueba', :perfil)
                        RETURNING id
                        """)
                .param("expediente", expediente)
                .param("articulo", articulo)
                .param("perfil", perfil)
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.sanciones
                            (resolucion_id, tipo, sujeto, efecto, equipo_id, temporada_id, partidos,
                             partidos_cumplidos, vigente_desde)
                        VALUES (:resolucion, CAST(:tipo AS siglim.tipo_sancion), 'EQUIPO',
                                'HACIA_ADELANTE', :equipo, :temporada, :partidos,
                                :cumplidos, DATE '2031-03-01')
                        """)
                .param("resolucion", resolucion)
                .param("tipo", tipo)
                .param("equipo", equipo)
                .param("temporada", temporada)
                .param("partidos", partidos)
                .param("cumplidos", partidosCumplidos)
                .update();
    }

    private UUID temporadaDe(UUID grupo) {
        return jdbc.sql("""
                        SELECT ct.temporada_id
                          FROM siglim.grupos g
                          JOIN siglim.categorias_temporada ct ON ct.id = g.categoria_temporada_id
                         WHERE g.id = :grupo
                        """)
                .param("grupo", grupo)
                .query(UUID.class)
                .single();
    }

    private List<Historial> historialDelPartido(UUID partido) {
        return jdbc.sql("""
                        SELECT estado_nuevo, motivo, cambiado_por
                          FROM siglim.partido_estado_historial
                         WHERE partido_id = :id
                         ORDER BY cambiado_en, id
                        """)
                .param("id", partido)
                .query((fila, numero) -> new Historial(
                        fila.getString("estado_nuevo"), fila.getString("motivo"),
                        fila.getObject("cambiado_por", UUID.class)))
                .list();
    }

    private long contarPartidos(UUID jornada) {
        return jdbc.sql("SELECT count(*) FROM siglim.partidos WHERE jornada_id = :id")
                .param("id", jornada).query(Long.class).single();
    }

    private long contarAuditoriaDe(UUID... entidades) {
        long total = 0;
        for (UUID entidad : entidades) {
            total += jdbc.sql("SELECT count(*) FROM siglim.auditoria WHERE entidad_id = :id")
                    .param("id", entidad.toString()).query(Long.class).single();
        }
        return total;
    }

    private List<String> accionesAuditadas(UUID entidadId) {
        return jdbc.sql("""
                        SELECT accion FROM siglim.auditoria
                         WHERE entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", entidadId.toString())
                .query(String.class)
                .list();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private record Historial(String estadoNuevo, String motivo, UUID cambiadoPor) {
    }
}

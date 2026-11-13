package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.CanchaDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosJornada;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosPartido;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.EquipoDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.GrupoDeLiga;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link JornadaService}: estado BORRADOR, ajuste con motivo y auditoría. Sin Spring. */
class JornadaServiceTest {

    private static final String CONSULTAR = "programacion.jornada:CONSULTAR";
    private static final String EDITAR = "programacion.jornada:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID JORNADA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PARTIDO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID OTRO_GRUPO = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID LOCAL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID VISITANTE = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CANCHA = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID PERFIL = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final Instant AHORA = Instant.parse("2031-03-01T12:00:00Z");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    private static final OffsetDateTime CREADO_EN = OffsetDateTime.parse("2031-03-01T06:00:00-06:00");
    private static final String MOTIVO = "Cambio de cancha por lluvia";

    private final JornadaRepository jornadas = mock(JornadaRepository.class);
    private final ValidadorConflictos validador = mock(ValidadorConflictos.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final JornadaService servicio = new JornadaService(jornadas, validador, autorizacion, auditoria,
            motivoOperacion, new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Datos de prueba ──────────────────────────────────────────────────────────────────────

    private static DatosJornada jornada(EstadoJornada estado) {
        return new DatosJornada(JORNADA, GRUPO, null, 1, estado, CREADO_EN, CREADO_EN);
    }

    private static DatosPartido partido(EstadoPartido estado, EstadoJornada estadoJornada, OffsetDateTime inicio) {
        return new DatosPartido(PARTIDO, JORNADA, GRUPO, estadoJornada, LOCAL, "Halcones",
                UUID.randomUUID(), VISITANTE, "Toros", UUID.randomUUID(), CANCHA, "Morelos 2",
                inicio, inicio.plusHours(2), estado, null, CREADO_EN, CREADO_EN);
    }

    private static EquipoDeLiga equipo(UUID id, String nombre, UUID grupo) {
        return new EquipoDeLiga(id, nombre, grupo, UUID.randomUUID(), "ACTIVO");
    }

    private static CanchaDeLiga cancha(boolean activa) {
        return new CanchaDeLiga(CANCHA, "Morelos 2", activa);
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    // ── Alta de la jornada (RF-033) ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-033: registrar jornada la deja en BORRADOR, usa el perfil de la sesión y audita")
    void crearJornada() {
        given(jornadas.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(new GrupoDeLiga(GRUPO, UUID.randomUUID())));
        given(jornadas.insertarJornada(GRUPO, null, 3, PERFIL)).willReturn(JORNADA);
        given(jornadas.buscarJornada(LIGA, JORNADA)).willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));

        JornadaDto creada = servicio.crear(new CrearJornadaRequest(GRUPO, null, 3));

        assertThat(creada.estadoPublicacion()).isEqualTo(EstadoJornada.BORRADOR);
        assertThat(creada.partidos()).isEmpty();
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_JORNADA");
        assertThat(evento.entidad()).isEqualTo("jornadas");
        assertThat(evento.entidadId()).isEqualTo(JORNADA.toString());
        assertThat(evento.detalle()).containsEntry("numero", 3).containsEntry("estado", "BORRADOR");
    }

    @Test
    @DisplayName("RF-033: un grupo inexistente responde 422 REFERENCIA_INVALIDA y no crea nada")
    void crearJornadaConGrupoInexistente() {
        given(jornadas.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(new CrearJornadaRequest(GRUPO, null, 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));
        verify(jornadas, never()).insertarJornada(any(), any(), anyInt(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-033: una fase de otro grupo responde 422 y un número repetido 409 DUPLICADO")
    void crearJornadaValidaFaseYNumero() {
        UUID fase = UUID.randomUUID();
        given(jornadas.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(new GrupoDeLiga(GRUPO, UUID.randomUUID())));
        given(jornadas.faseDelGrupo(GRUPO, fase)).willReturn(false);

        assertThatThrownBy(() -> servicio.crear(new CrearJornadaRequest(GRUPO, fase, 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));

        given(jornadas.faseDelGrupo(GRUPO, fase)).willReturn(true);
        given(jornadas.existeNumero(GRUPO, fase, 1)).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(new CrearJornadaRequest(GRUPO, fase, 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        verify(jornadas, never()).insertarJornada(any(), any(), anyInt(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-033: el listado exige grupo y valida la paginación antes de consultar")
    void listarValidaFiltroYPaginacion() {
        assertThatThrownBy(() -> servicio.listar(null, null, 0, 20))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.listar(GRUPO, null, 0, 101))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verifyNoInteractions(jornadas);
    }

    @Test
    @DisplayName("RF-033: el listado pagina y cuenta con el mismo filtro")
    void listar() {
        given(jornadas.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(new GrupoDeLiga(GRUPO, UUID.randomUUID())));
        given(jornadas.listarJornadas(LIGA, GRUPO, EstadoJornada.BORRADOR, 20, 0))
                .willReturn(List.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.contarJornadas(LIGA, GRUPO, EstadoJornada.BORRADOR)).willReturn(41L);

        var pagina = servicio.listar(GRUPO, EstadoJornada.BORRADOR, 0, 20);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.contenido().getFirst().partidos()).as("el listado no anida partidos").isNull();
        assertThat(pagina.totalElementos()).isEqualTo(41);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
    }

    @Test
    @DisplayName("VAL-024: consultar una jornada inexistente responde 404 y no lee partidos")
    void consultarInexistente() {
        given(jornadas.buscarJornada(LIGA, JORNADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(JORNADA))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(jornadas, never()).listarPartidos(any());
    }

    // ── Registro de partidos (RF-036) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-090: no se registra un partido en una jornada que ya no está en borrador")
    void registrarPartidoFueraDeBorrador() {
        for (EstadoJornada estado : List.of(EstadoJornada.REVISION, EstadoJornada.PUBLICADO)) {
            given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA)).willReturn(Optional.of(jornada(estado)));

            assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(LOCAL)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));
        }
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-036: un equipo de otro grupo responde 422 RF-033 y no se guarda")
    void registrarPartidoConEquipoDeOtroGrupo() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", OTRO_GRUPO)));

        assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(LOCAL)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "RF-033"));
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-036: registrar partido usa la sesión y audita los identificadores")
    void registrarPartido() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, VISITANTE)).willReturn(Optional.of(equipo(VISITANTE, "Toros", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(cancha(true)));
        given(jornadas.insertarPartido(JORNADA, LOCAL, VISITANTE, CANCHA, INICIO, PERFIL)).willReturn(PARTIDO);
        given(jornadas.buscarPartido(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));

        PartidoDto creado = servicio.registrarPartido(JORNADA, peticionPartido(CANCHA));

        assertThat(creado.estado()).isEqualTo(EstadoPartido.PROGRAMADO);
        assertThat(creado.finBloque()).as("el bloque de 120 min lo calcula la base")
                .isEqualTo(INICIO.plusHours(2));
        verify(validador).validar(eq(GRUPO), any(), any(), any(), eq(cancha(true)), eq(INICIO));
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_PARTIDO");
        assertThat(evento.entidad()).isEqualTo("partidos");
        assertThat(evento.detalle()).containsEntry("jornadaId", JORNADA.toString())
                .containsEntry("canchaId", CANCHA.toString());
    }

    @Test
    @DisplayName("RF-036: los equipos deben ser distintos y la cancha de la liga")
    void registrarPartidoValidaDatos() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));

        assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(LOCAL, LOCAL)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "VALIDACION"));

        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, VISITANTE)).willReturn(Optional.of(equipo(VISITANTE, "Toros", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(CANCHA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(validador, auditoria);
    }

    @Test
    @DisplayName("VAL-004: un conflicto detectado antes de escribir evita el alta y la auditoría")
    void registrarPartidoConConflicto() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, VISITANTE)).willReturn(Optional.of(equipo(VISITANTE, "Toros", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(cancha(true)));
        willThrow(ReglaNegocioException.conflicto("VAL-004",
                "La cancha ya está ocupada por el partido «Águilas vs. Pumas»."))
                .given(validador).validar(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(CANCHA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "VAL-004"));
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    // ── Ajuste del partido (RF-040, RN-018) ─────────────────────────────────────────────────

    @Test
    @DisplayName("RF-040: el ajuste exige motivo de al menos 10 caracteres y no escribe sin él")
    void ajustarSinMotivo() {
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));

        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, INICIO.plusHours(3), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, INICIO.plusHours(3), "Corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        verify(jornadas, never()).actualizarPartido(any(), any(), any(), any(), any());
        verifyNoInteractions(validador, motivoOperacion, auditoria);
    }

    @Test
    @DisplayName("RF-040: el ajuste con motivo actualiza, fija el motivo de la operación y audita")
    void ajustarConMotivo() {
        OffsetDateTime nuevoInicio = INICIO.plusDays(1);
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, VISITANTE)).willReturn(Optional.of(equipo(VISITANTE, "Toros", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(cancha(true)));
        given(jornadas.buscarPartido(LIGA, PARTIDO)).willReturn(Optional.of(
                partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, nuevoInicio)));

        servicio.actualizarPartido(PARTIDO, new ActualizarPartidoRequest(null, null, null, nuevoInicio,
                "  " + MOTIVO + "  "));

        verify(validador).validar(eq(GRUPO), eq(PARTIDO), any(), any(), eq(cancha(true)), eq(nuevoInicio));
        verify(jornadas).actualizarPartido(PARTIDO, LOCAL, VISITANTE, CANCHA, nuevoInicio);
        verify(motivoOperacion).fijar(MOTIVO);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("AJUSTAR_PARTIDO");
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        assertThat(evento.detalle()).containsEntry("campos", List.of("inicio"));
    }

    @Test
    @DisplayName("RF-040: el ajuste puede cambiar equipos y deja en la auditoría los campos que cambió")
    void ajustarEquipos() {
        UUID nuevoVisitante = UUID.fromString("99999999-9999-4999-8999-999999999999");
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, nuevoVisitante))
                .willReturn(Optional.of(equipo(nuevoVisitante, "Águilas", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(cancha(true)));
        given(jornadas.buscarPartido(LIGA, PARTIDO)).willReturn(Optional.of(
                partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));

        servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, nuevoVisitante, null, null, MOTIVO));

        verify(jornadas).actualizarPartido(PARTIDO, LOCAL, nuevoVisitante, CANCHA, INICIO);
        assertThat(eventoAuditado().detalle()).containsEntry("campos", List.of("equipoVisitanteId"));
    }

    @Test
    @DisplayName("RF-040: un equipo ajeno al grupo al ajustar responde 422 RF-033 y no escribe")
    void ajustarConEquipoDeOtroGrupo() {
        UUID ajeno = UUID.fromString("99999999-9999-4999-8999-999999999991");
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, ajeno))
                .willReturn(Optional.of(equipo(ajeno, "Ajenos", OTRO_GRUPO)));

        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, ajeno, null, null, MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "RF-033"));
        verify(jornadas, never()).actualizarPartido(any(), any(), any(), any(), any());
        verifyNoInteractions(validador, motivoOperacion, auditoria);
    }

    @Test
    @DisplayName("VAL-024: una jornada y un partido de otra liga responden 404")
    void fueraDeLaLiga() {
        given(jornadas.buscarJornada(LIGA, JORNADA)).willReturn(Optional.empty());
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(JORNADA))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), MOTIVO)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-040: un PATCH sin cambios no escribe, no exige motivo ni audita")
    void ajustarSinCambios() {
        DatosPartido actual = partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO);
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO)).willReturn(Optional.of(actual));

        PartidoDto resultado = servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(LOCAL, VISITANTE, CANCHA, INICIO, null));

        assertThat(resultado.id()).isEqualTo(PARTIDO);
        verify(jornadas, never()).actualizarPartido(any(), any(), any(), any(), any());
        verifyNoInteractions(validador, motivoOperacion, auditoria);
    }

    @Test
    @DisplayName("RF-046: un partido finalizado o cancelado no se ajusta")
    void ajustarPartidoCerrado() {
        for (EstadoPartido estado : List.of(EstadoPartido.FINALIZADO, EstadoPartido.CANCELADO)) {
            given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                    .willReturn(Optional.of(partido(estado, EstadoJornada.BORRADOR, INICIO)));

            assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                    new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), MOTIVO)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-046"));
        }
        verify(jornadas, never()).actualizarPartido(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-090: tampoco se ajusta si la jornada está en revisión o publicada")
    void ajustarJornadaNoBorrador() {
        for (EstadoJornada estado : List.of(EstadoJornada.REVISION, EstadoJornada.PUBLICADO)) {
            given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                    .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, estado, INICIO)));

            assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                    new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), MOTIVO)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));
        }
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin programacion.jornada:EDITAR no se registra ni se ajusta")
    void sinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(new CrearJornadaRequest(GRUPO, null, 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(CANCHA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(jornadas, auditoria);
    }

    @Test
    @DisplayName("RF-033: la consulta usa CONSULTAR y trae los partidos de la jornada")
    void consultarConPartidos() {
        given(jornadas.buscarJornada(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.listarPartidos(JORNADA))
                .willReturn(List.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));

        JornadaDto encontrada = servicio.consultar(JORNADA);

        assertThat(encontrada.partidos()).hasSize(1);
        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
    }

    private static CrearPartidoRequest peticionPartido(UUID canchaId) {
        return peticionPartido(LOCAL, VISITANTE, canchaId);
    }

    private static CrearPartidoRequest peticionPartido(UUID local, UUID visitante) {
        return peticionPartido(local, visitante, CANCHA);
    }

    private static CrearPartidoRequest peticionPartido(UUID local, UUID visitante, UUID canchaId) {
        return new CrearPartidoRequest(local, visitante, canchaId, INICIO);
    }
}

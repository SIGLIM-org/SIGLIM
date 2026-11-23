package mx.tecnm.morelia.siglim.partidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.JornadaDto;
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.partidos.PartidosRepository.DatosPartido;
import mx.tecnm.morelia.siglim.programacion.EstadoJornada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import mx.tecnm.morelia.siglim.programacion.ValidacionReprogramacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

/**
 * Reglas de la consulta y las transiciones del partido (H-15.1) con dobles del repositorio.
 *
 * <p>Refs: H-15.1, RF-043, RF-045, RF-046, RN-021, DEC-23, DEC-160, DEC-161, DEC-162
 */
class PartidosServiceTest {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PARTIDO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CANCHA = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID OTRA_CANCHA = UUID.fromString("67676767-6767-4767-8767-676767676767");
    private static final Instant AHORA = Instant.parse("2031-03-01T12:00:00Z");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    private static final OffsetDateTime NUEVO_INICIO = OffsetDateTime.parse("2031-03-17T10:00:00-06:00");
    private static final String MOTIVO = "Cancha inundada por la lluvia";
    private static final AlcanceAutorizacion LIGA_COMPLETA = new AlcanceAutorizacion(true, Set.of(), false);

    private final PartidosRepository partidos = mock(PartidosRepository.class);
    private final ValidacionReprogramacion reprogramacion = mock(ValidacionReprogramacion.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final PartidosService servicio = new PartidosService(partidos, reprogramacion, autorizacion, auditoria,
            motivoOperacion, Clock.fixed(AHORA, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void permisos() {
        given(autorizacion.alcance(PartidosService.CONSULTAR)).willReturn(LIGA_COMPLETA);
        given(autorizacion.permite(PartidosService.CAMBIAR_ESTADO, ObjetivoAutorizacion.deLiga())).willReturn(true);
    }

    private static DatosPartido partido(EstadoPartido estado, EstadoJornada jornada) {
        return new DatosPartido(PARTIDO, new JornadaDto(UUID.randomUUID(), 3, jornada),
                new ReferenciaDto(UUID.randomUUID(), "Temporada 2031"), new ReferenciaDto(UUID.randomUUID(), "Libre"),
                new ReferenciaDto(UUID.randomUUID(), "Grupo A"), new ReferenciaDto(UUID.randomUUID(), "Halcones"),
                new ReferenciaDto(UUID.randomUUID(), "Toros"), new ReferenciaDto(CANCHA, "Morelos 2"), INICIO,
                INICIO.plusHours(2), estado);
    }

    private void conPartido(EstadoPartido estado) {
        conPartido(estado, EstadoJornada.PUBLICADO);
    }

    private void conPartido(EstadoPartido estado, EstadoJornada jornada) {
        given(partidos.bloquear(LIGA, PARTIDO)).willReturn(Optional.of(partido(estado, jornada)));
        given(partidos.historial(PARTIDO)).willReturn(List.of());
    }

    private static TransicionPartidoRequest hacia(EstadoPartido estado) {
        return estado == EstadoPartido.REPROGRAMADO
                ? new TransicionPartidoRequest(estado, MOTIVO, NUEVO_INICIO, null)
                : new TransicionPartidoRequest(estado, MOTIVO, null, null);
    }

    // ── Acciones permitidas ───────────────────────────────────────────────────────────────────

    static Stream<Arguments> accionesPorEstado() {
        return Stream.of(
                Arguments.of(EstadoPartido.PROGRAMADO, List.of(AccionPartido.REPROGRAMAR, AccionPartido.CANCELAR)),
                Arguments.of(EstadoPartido.EN_CURSO, List.of(AccionPartido.SUSPENDER)),
                Arguments.of(EstadoPartido.SUSPENDIDO, List.of(AccionPartido.REPROGRAMAR, AccionPartido.CANCELAR)),
                Arguments.of(EstadoPartido.REPROGRAMADO, List.of()),
                Arguments.of(EstadoPartido.FINALIZADO, List.of()),
                Arguments.of(EstadoPartido.CANCELADO, List.of()));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("accionesPorEstado")
    @DisplayName("RF-046: el detalle muestra solo las acciones que admite el estado")
    void accionesDelDetalle(EstadoPartido estado, List<AccionPartido> esperadas) {
        given(partidos.buscarVisible(LIGA, PARTIDO, LIGA_COMPLETA))
                .willReturn(Optional.of(partido(estado, EstadoJornada.PUBLICADO)));

        assertThat(servicio.obtener(PARTIDO).accionesPermitidas()).isEqualTo(esperadas);
    }

    @Test
    @DisplayName("RF-046: sin partidos.partido:CAMBIAR_ESTADO las acciones van vacías")
    void sinPermisoNoHayAcciones() {
        given(autorizacion.permite(PartidosService.CAMBIAR_ESTADO, ObjetivoAutorizacion.deLiga())).willReturn(false);
        given(partidos.buscarVisible(LIGA, PARTIDO, LIGA_COMPLETA))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.PUBLICADO)));

        assertThat(servicio.obtener(PARTIDO).accionesPermitidas()).isEmpty();
    }

    @Test
    @DisplayName("DEC-162: en una jornada sin publicar las acciones van vacías")
    void jornadaSinPublicarNoHayAcciones() {
        given(partidos.buscarVisible(LIGA, PARTIDO, LIGA_COMPLETA))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR)));

        assertThat(servicio.obtener(PARTIDO).accionesPermitidas()).isEmpty();
    }

    @Test
    @DisplayName("RF-043: el listado calcula las acciones de cada partido y el total con el mismo alcance")
    void listar() {
        FiltroPartidos filtro = new FiltroPartidos(null, null, null, null, 0, 20);
        given(partidos.listar(LIGA, filtro, LIGA_COMPLETA)).willReturn(List.of(
                partido(EstadoPartido.EN_CURSO, EstadoJornada.PUBLICADO)));
        given(partidos.contar(LIGA, filtro, LIGA_COMPLETA)).willReturn(1L);

        Pagina<PartidoResumenDto> pagina = servicio.listar(filtro);

        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.contenido().getFirst().accionesPermitidas()).containsExactly(AccionPartido.SUSPENDER);
    }

    @Test
    @DisplayName("RF-043: el tamaño fuera de rango responde 400 sin consultar")
    void listarTamanoInvalido() {
        assertThatThrownBy(() -> servicio.listar(new FiltroPartidos(null, null, null, null, 0, 101)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", ErroresTecnicos.VALIDACION);
        verifyNoInteractions(partidos);
    }

    @Test
    @DisplayName("RF-043: un rango con hasta antes de desde responde 400")
    void listarRangoInvalido() {
        assertThatThrownBy(() -> servicio.listar(new FiltroPartidos(null, null, INICIO, INICIO, 0, 20)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("ADR-0007: un partido fuera del alcance responde 404")
    void fueraDeAlcance() {
        given(partidos.buscarVisible(LIGA, PARTIDO, LIGA_COMPLETA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(PARTIDO)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.historial(PARTIDO)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(partidos, never()).historial(PARTIDO);
    }

    // ── Transiciones permitidas ───────────────────────────────────────────────────────────────

    static Stream<Arguments> transicionesPermitidas() {
        return Stream.of(
                Arguments.of(EstadoPartido.PROGRAMADO, EstadoPartido.REPROGRAMADO),
                Arguments.of(EstadoPartido.PROGRAMADO, EstadoPartido.CANCELADO),
                Arguments.of(EstadoPartido.EN_CURSO, EstadoPartido.SUSPENDIDO),
                Arguments.of(EstadoPartido.SUSPENDIDO, EstadoPartido.REPROGRAMADO),
                Arguments.of(EstadoPartido.SUSPENDIDO, EstadoPartido.CANCELADO));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("transicionesPermitidas")
    @DisplayName("RF-043: cada transición permitida fija el motivo, cambia el estado y audita")
    void transicionPermitida(EstadoPartido actual, EstadoPartido nuevo) {
        conPartido(actual);

        servicio.transicionar(PARTIDO, hacia(nuevo));

        verify(autorizacion).exigir(PartidosService.CAMBIAR_ESTADO, ObjetivoAutorizacion.deLiga());
        verify(motivoOperacion).fijar(MOTIVO);
        boolean reprogramar = nuevo == EstadoPartido.REPROGRAMADO;
        if (reprogramar) {
            verify(partidos).reprogramar(PARTIDO, NUEVO_INICIO, CANCHA);
            verify(partidos, never()).cambiarEstado(any(), any());
        } else {
            verify(partidos).cambiarEstado(PARTIDO, nuevo);
            verify(partidos, never()).reprogramar(any(), any(), any());
            verifyNoInteractions(reprogramacion);
        }
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().modulo()).isEqualTo("M-08");
        assertThat(evento.getValue().accion()).isEqualTo("CAMBIAR_ESTADO_PARTIDO");
        assertThat(evento.getValue().motivo()).isEqualTo(MOTIVO);
        assertThat(evento.getValue().detalle()).containsEntry("estadoAnterior", actual.name())
                .containsEntry("estadoNuevo", nuevo.name());
    }

    // ── Transiciones prohibidas ───────────────────────────────────────────────────────────────

    static Stream<Arguments> transicionesProhibidas() {
        return Stream.of(
                Arguments.of(EstadoPartido.PROGRAMADO, EstadoPartido.SUSPENDIDO),
                Arguments.of(EstadoPartido.PROGRAMADO, EstadoPartido.EN_CURSO),
                Arguments.of(EstadoPartido.PROGRAMADO, EstadoPartido.FINALIZADO),
                Arguments.of(EstadoPartido.PROGRAMADO, EstadoPartido.PROGRAMADO),
                Arguments.of(EstadoPartido.EN_CURSO, EstadoPartido.REPROGRAMADO),
                Arguments.of(EstadoPartido.EN_CURSO, EstadoPartido.CANCELADO),
                Arguments.of(EstadoPartido.EN_CURSO, EstadoPartido.FINALIZADO),
                Arguments.of(EstadoPartido.SUSPENDIDO, EstadoPartido.SUSPENDIDO),
                Arguments.of(EstadoPartido.SUSPENDIDO, EstadoPartido.EN_CURSO),
                Arguments.of(EstadoPartido.REPROGRAMADO, EstadoPartido.REPROGRAMADO),
                Arguments.of(EstadoPartido.REPROGRAMADO, EstadoPartido.CANCELADO),
                Arguments.of(EstadoPartido.REPROGRAMADO, EstadoPartido.SUSPENDIDO),
                Arguments.of(EstadoPartido.FINALIZADO, EstadoPartido.CANCELADO),
                Arguments.of(EstadoPartido.FINALIZADO, EstadoPartido.REPROGRAMADO),
                Arguments.of(EstadoPartido.CANCELADO, EstadoPartido.REPROGRAMADO),
                Arguments.of(EstadoPartido.CANCELADO, EstadoPartido.SUSPENDIDO));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("transicionesProhibidas")
    @DisplayName("RF-046: cada transición prohibida responde 409 RF-046 sin escribir")
    void transicionProhibida(EstadoPartido actual, EstadoPartido nuevo) {
        conPartido(actual);

        assertThatThrownBy(() -> servicio.transicionar(PARTIDO, hacia(nuevo)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasFieldOrPropertyWithValue("codigo", "RF-046")
                .satisfies(error -> assertThat(((ReglaNegocioException) error).propiedades())
                        .containsEntry("estadoActual", actual)
                        .containsEntry("accionesPermitidas", AccionPartido.desde(actual)));
        verify(partidos, never()).cambiarEstado(any(), any());
        verify(partidos, never()).reprogramar(any(), any(), any());
        verifyNoInteractions(auditoria, motivoOperacion, reprogramacion);
    }

    // ── Validaciones de la petición ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("RN-021: el motivo con menos de 10 caracteres responde 400 RN-021 antes de leer el partido")
    void motivoCorto() {
        assertThatThrownBy(() -> servicio.transicionar(PARTIDO,
                new TransicionPartidoRequest(EstadoPartido.CANCELADO, "  lluvia  ", null, null)))
                .hasFieldOrPropertyWithValue("codigo", "RN-021");
        verifyNoInteractions(partidos);
    }

    @Test
    @DisplayName("RN-021: el motivo de más de 500 caracteres responde 400 RN-021")
    void motivoLargo() {
        assertThatThrownBy(() -> servicio.transicionar(PARTIDO,
                new TransicionPartidoRequest(EstadoPartido.CANCELADO, "x".repeat(501), null, null)))
                .hasFieldOrPropertyWithValue("codigo", "RN-021");
    }

    @Test
    @DisplayName("RF-043: sin estado nuevo responde 400 VALIDACION")
    void sinEstado() {
        assertThatThrownBy(() -> servicio.transicionar(PARTIDO, new TransicionPartidoRequest(null, MOTIVO, null, null)))
                .hasFieldOrPropertyWithValue("codigo", ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("DEC-160: reprogramar sin inicio responde 400 VALIDACION")
    void reprogramarSinInicio() {
        assertThatThrownBy(() -> servicio.transicionar(PARTIDO,
                new TransicionPartidoRequest(EstadoPartido.REPROGRAMADO, MOTIVO, null, null)))
                .hasFieldOrPropertyWithValue("codigo", ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("DEC-160: reprogramar a una fecha pasada responde 400 VALIDACION")
    void reprogramarAlPasado() {
        assertThatThrownBy(() -> servicio.transicionar(PARTIDO, new TransicionPartidoRequest(
                EstadoPartido.REPROGRAMADO, MOTIVO, OffsetDateTime.parse("2031-02-28T10:00:00-06:00"), null)))
                .hasFieldOrPropertyWithValue("codigo", ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("DEC-160: inicio o cancha fuera de una reprogramación responden 400 VALIDACION")
    void inicioSinReprogramar() {
        assertThatThrownBy(() -> servicio.transicionar(PARTIDO,
                new TransicionPartidoRequest(EstadoPartido.CANCELADO, MOTIVO, NUEVO_INICIO, null)))
                .hasFieldOrPropertyWithValue("codigo", ErroresTecnicos.VALIDACION);
        assertThatThrownBy(() -> servicio.transicionar(PARTIDO,
                new TransicionPartidoRequest(EstadoPartido.SUSPENDIDO, MOTIVO, null, OTRA_CANCHA)))
                .hasFieldOrPropertyWithValue("codigo", ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("RF-043: el partido que no es de la liga responde 404")
    void partidoInexistente() {
        given(partidos.bloquear(LIGA, PARTIDO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.transicionar(PARTIDO, hacia(EstadoPartido.CANCELADO)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("VAL-015: sin partidos.partido:CAMBIAR_ESTADO no lee ni escribe")
    void sinPermiso() {
        willThrow(ReglaNegocioException.prohibida(ErroresTecnicos.SIN_PERMISO, "Sin permiso"))
                .given(autorizacion).exigir(eq(PartidosService.CAMBIAR_ESTADO), any());

        assertThatThrownBy(() -> servicio.transicionar(PARTIDO, hacia(EstadoPartido.CANCELADO)))
                .hasFieldOrPropertyWithValue("codigo", ErroresTecnicos.SIN_PERMISO);
        verifyNoInteractions(partidos, auditoria);
    }

    @Test
    @DisplayName("DEC-162: una jornada sin publicar responde 409 RF-090")
    void jornadaSinPublicar() {
        conPartido(EstadoPartido.PROGRAMADO, EstadoJornada.REVISION);

        assertThatThrownBy(() -> servicio.transicionar(PARTIDO, hacia(EstadoPartido.CANCELADO)))
                .hasFieldOrPropertyWithValue("codigo", "RF-090");
        verify(partidos, never()).cambiarEstado(any(), any());
        verify(partidos, never()).reprogramar(any(), any(), any());
    }

    // ── Reprogramación ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-160: reprogramar a otra cancha valida conflictos con la cancha nueva y la audita")
    void reprogramarOtraCancha() {
        conPartido(EstadoPartido.SUSPENDIDO);

        servicio.transicionar(PARTIDO,
                new TransicionPartidoRequest(EstadoPartido.REPROGRAMADO, MOTIVO, NUEVO_INICIO, OTRA_CANCHA));

        verify(reprogramacion).validar(PARTIDO, NUEVO_INICIO, OTRA_CANCHA);
        verify(partidos).reprogramar(PARTIDO, NUEVO_INICIO, OTRA_CANCHA);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().detalle()).containsEntry("canchaAnteriorId", CANCHA.toString())
                .containsEntry("canchaNuevaId", OTRA_CANCHA.toString())
                .containsEntry("inicioNuevo", NUEVO_INICIO.toString());
    }

    @Test
    @DisplayName("VAL-004: el conflicto de la nueva fecha se propaga sin escribir ni auditar")
    void reprogramarConConflicto() {
        conPartido(EstadoPartido.PROGRAMADO);
        willThrow(ReglaNegocioException.conflicto("VAL-004", "La cancha ya está ocupada."))
                .given(reprogramacion).validar(PARTIDO, NUEVO_INICIO, CANCHA);

        assertThatThrownBy(() -> servicio.transicionar(PARTIDO, hacia(EstadoPartido.REPROGRAMADO)))
                .hasFieldOrPropertyWithValue("codigo", "VAL-004");
        verify(partidos, never()).cambiarEstado(any(), any());
        verify(partidos, never()).reprogramar(any(), any(), any());
        verify(motivoOperacion, never()).fijar(anyString());
        verifyNoInteractions(auditoria);
    }
}

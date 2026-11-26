package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
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
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionDto.ArbitroDesignadoDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionRepository.DesignacionVigente;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionRepository.PartidoDeJornada;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionRepository.PartidoDesignable;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionesJornadaDto.JornadaDto;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.EstadoJornada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link DesignacionService} (H-14.1, DEC-201 a DEC-205). Sin BD. */
class DesignacionServiceTest {

    private static final String EDITAR = "arbitraje.designacion:EDITAR";
    private static final String CONSULTAR = "arbitraje.designacion:CONSULTAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PARTIDO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ARBITRO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTRO_ARBITRO = UUID.fromString("12121212-1212-4121-8121-121212121212");
    private static final UUID DESIGNACION = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID ANTERIOR = UUID.fromString("78787878-7878-4787-8787-787878787878");
    private static final UUID JORNADA = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final Instant AHORA = Instant.parse("2030-08-02T12:00:00Z");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2030-08-10T10:00:00-06:00");
    private static final String MOTIVO = "  El árbitro central se lesionó  ";

    private final DesignacionRepository designaciones = mock(DesignacionRepository.class);
    private final ElegibilidadArbitral elegibilidad = mock(ElegibilidadArbitral.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final DesignacionService servicio = new DesignacionService(designaciones, elegibilidad, autorizacion,
            auditoria, new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
        given(designaciones.bloquearPartido(LIGA, PARTIDO))
                .willReturn(Optional.of(new PartidoDesignable(PARTIDO, EstadoPartido.PROGRAMADO)));
        given(designaciones.bloquearArbitro(LIGA, ARBITRO)).willReturn(true);
        given(designaciones.vigentesDelPartido(PARTIDO)).willReturn(List.of());
        given(designaciones.insertar(any(), any(), any(), any())).willReturn(DESIGNACION);
        given(designaciones.buscar(LIGA, DESIGNACION))
                .willReturn(Optional.of(designacion(DESIGNACION, FuncionArbitral.CENTRAL, true, null)));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Designar (DEC-201) ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-023: designa al central, guarda al perfil de la sesión y audita DESIGNAR_ARBITRO")
    void designarCentral() {
        DesignacionDto resultado = servicio.designar(PARTIDO,
                new DesignarArbitroRequest(ARBITRO, FuncionArbitral.CENTRAL, "se ignora sin central"));

        assertThat(resultado.id()).isEqualTo(DESIGNACION);
        assertThat(resultado.vigente()).isTrue();
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        verify(designaciones).insertar(PARTIDO, ARBITRO, FuncionArbitral.CENTRAL, PERFIL);
        verify(designaciones, never()).retirar(any(), any());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-05");
        assertThat(evento.accion()).isEqualTo("DESIGNAR_ARBITRO");
        assertThat(evento.entidad()).isEqualTo("designaciones");
        assertThat(evento.entidadId()).isEqualTo(DESIGNACION.toString());
        assertThat(evento.motivo()).isNull();
        assertThat(evento.detalle()).containsOnlyKeys("partidoId", "arbitroId", "funcion")
                .containsEntry("partidoId", PARTIDO.toString())
                .containsEntry("arbitroId", ARBITRO.toString())
                .containsEntry("funcion", "CENTRAL");
    }

    @Test
    @DisplayName("DEC-201: orden de errores: cuerpo 400 antes que el partido 404")
    void designarSinCuerpo() {
        assertThatThrownBy(() -> servicio.designar(PARTIDO, new DesignarArbitroRequest(null, FuncionArbitral.CENTRAL, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        assertThatThrownBy(() -> servicio.designar(PARTIDO, new DesignarArbitroRequest(ARBITRO, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verifyNoInteractions(designaciones, autorizacion, auditoria);
    }

    @Test
    @DisplayName("DEC-201: un partido de otra liga responde 404 antes de revisar el permiso")
    void designarPartidoInexistente() {
        given(designaciones.bloquearPartido(LIGA, PARTIDO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.designar(PARTIDO, peticion(FuncionArbitral.CENTRAL)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
        verify(designaciones, never()).bloquearArbitro(any(), any());
    }

    @Test
    @DisplayName("VAL-015: sin permiso responde 403 después del 404 y antes de buscar al árbitro")
    void designarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.designar(PARTIDO, peticion(FuncionArbitral.CENTRAL)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        InOrder orden = inOrder(designaciones, autorizacion);
        orden.verify(designaciones).bloquearPartido(LIGA, PARTIDO);
        orden.verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        verify(designaciones, never()).bloquearArbitro(any(), any());
    }

    @Test
    @DisplayName("DEC-201: un árbitro que no está en la liga responde 422 REFERENCIA_INVALIDA; bloquea partido y luego árbitro")
    void designarArbitroInexistente() {
        given(designaciones.bloquearArbitro(LIGA, ARBITRO)).willReturn(false);

        assertThatThrownBy(() -> servicio.designar(PARTIDO, peticion(FuncionArbitral.CENTRAL)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
        InOrder orden = inOrder(designaciones);
        orden.verify(designaciones).bloquearPartido(LIGA, PARTIDO);
        orden.verify(designaciones).bloquearArbitro(LIGA, ARBITRO);
        verify(designaciones, never()).insertar(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPartido.class, names = {"EN_CURSO", "FINALIZADO", "SUSPENDIDO", "CANCELADO"})
    @DisplayName("RF-023: un partido que no está programado ni reprogramado responde 409")
    void designarPartidoNoDesignable(EstadoPartido estado) {
        given(designaciones.bloquearPartido(LIGA, PARTIDO))
                .willReturn(Optional.of(new PartidoDesignable(PARTIDO, estado)));

        assertThatThrownBy(() -> servicio.designar(PARTIDO, peticion(FuncionArbitral.CENTRAL)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "RF-023").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains(DesignacionService.texto(estado));
                });
        verify(designaciones, never()).insertar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-023: un partido reprogramado admite designaciones")
    void designarReprogramado() {
        given(designaciones.bloquearPartido(LIGA, PARTIDO))
                .willReturn(Optional.of(new PartidoDesignable(PARTIDO, EstadoPartido.REPROGRAMADO)));

        servicio.designar(PARTIDO, peticion(FuncionArbitral.ASISTENTE));

        verify(designaciones).insertar(PARTIDO, ARBITRO, FuncionArbitral.ASISTENTE, PERFIL);
    }

    @Test
    @DisplayName("RF-023: el árbitro ya designado en el partido, con cualquier función, responde 409")
    void designarArbitroRepetido() {
        given(designaciones.vigentesDelPartido(PARTIDO))
                .willReturn(List.of(new DesignacionVigente(ANTERIOR, ARBITRO, FuncionArbitral.ASISTENTE)));

        assertThatThrownBy(() -> servicio.designar(PARTIDO, peticion(FuncionArbitral.CENTRAL)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "RF-023").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("como asistente");
                });
        verify(designaciones, never()).retirar(any(), any());
        verify(designaciones, never()).insertar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("DEC-201: con dos asistentes vigentes, el tercero responde 409 RF-023")
    void tercerAsistente() {
        given(designaciones.vigentesDelPartido(PARTIDO)).willReturn(List.of(
                new DesignacionVigente(UUID.randomUUID(), UUID.randomUUID(), FuncionArbitral.ASISTENTE),
                new DesignacionVigente(UUID.randomUUID(), UUID.randomUUID(), FuncionArbitral.ASISTENTE)));

        assertThatThrownBy(() -> servicio.designar(PARTIDO, peticion(FuncionArbitral.ASISTENTE)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-023"));
        verify(designaciones, never()).insertar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("DEC-201: con un asistente vigente, el segundo se designa")
    void segundoAsistente() {
        given(designaciones.vigentesDelPartido(PARTIDO)).willReturn(List.of(
                new DesignacionVigente(UUID.randomUUID(), OTRO_ARBITRO, FuncionArbitral.ASISTENTE),
                new DesignacionVigente(ANTERIOR, UUID.randomUUID(), FuncionArbitral.CENTRAL)));

        servicio.designar(PARTIDO, peticion(FuncionArbitral.ASISTENTE));

        verify(designaciones).insertar(PARTIDO, ARBITRO, FuncionArbitral.ASISTENTE, PERFIL);
        verify(designaciones, never()).retirar(any(), any());
        assertThat(eventoAuditado().accion()).isEqualTo("DESIGNAR_ARBITRO");
    }

    // ── Sustituir al central (DEC-202) ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-202: sustituir al central retira el anterior con el motivo recortado y luego inserta")
    void sustituirCentral() {
        given(designaciones.vigentesDelPartido(PARTIDO))
                .willReturn(List.of(new DesignacionVigente(ANTERIOR, OTRO_ARBITRO, FuncionArbitral.CENTRAL)));

        servicio.designar(PARTIDO, new DesignarArbitroRequest(ARBITRO, FuncionArbitral.CENTRAL, MOTIVO));

        InOrder orden = inOrder(designaciones);
        orden.verify(designaciones).retirar(ANTERIOR, MOTIVO.strip());
        orden.verify(designaciones).insertar(PARTIDO, ARBITRO, FuncionArbitral.CENTRAL, PERFIL);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("SUSTITUIR_ARBITRO");
        assertThat(evento.motivo()).isEqualTo(MOTIVO.strip());
        assertThat(evento.detalle())
                .containsEntry("designacionAnteriorId", ANTERIOR.toString())
                .containsEntry("motivo", MOTIVO.strip())
                .containsEntry("funcion", "CENTRAL");
    }

    @Test
    @DisplayName("RN-021: sustituir al central sin motivo, o con menos de 10 o más de 500, responde 400")
    void sustituirSinMotivo() {
        given(designaciones.vigentesDelPartido(PARTIDO))
                .willReturn(List.of(new DesignacionVigente(ANTERIOR, OTRO_ARBITRO, FuncionArbitral.CENTRAL)));

        for (String motivo : new String[] {null, "   ", "  corto  ", "x".repeat(501)}) {
            assertThatThrownBy(() -> servicio.designar(PARTIDO,
                    new DesignarArbitroRequest(ARBITRO, FuncionArbitral.CENTRAL, motivo)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        }
        verify(designaciones, never()).retirar(any(), any());
        verify(designaciones, never()).insertar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    // ── Retirar (DEC-203) ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-203: retira con motivo, bloquea el partido y audita RETIRAR_DESIGNACION")
    void retirar() {
        DesignacionDto vigente = designacion(DESIGNACION, FuncionArbitral.ASISTENTE, true, null);
        DesignacionDto retirada = designacion(DESIGNACION, FuncionArbitral.ASISTENTE, false, MOTIVO.strip());
        given(designaciones.buscar(LIGA, DESIGNACION)).willReturn(Optional.of(vigente), Optional.of(retirada));
        given(designaciones.bloquear(LIGA, DESIGNACION)).willReturn(Optional.of(vigente));

        DesignacionDto resultado = servicio.retirar(DESIGNACION, new RetirarDesignacionRequest(MOTIVO));

        assertThat(resultado.vigente()).isFalse();
        InOrder orden = inOrder(designaciones, autorizacion);
        orden.verify(designaciones).buscar(LIGA, DESIGNACION);
        orden.verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        orden.verify(designaciones).bloquearPartido(LIGA, PARTIDO);
        orden.verify(designaciones).bloquear(LIGA, DESIGNACION);
        orden.verify(designaciones).retirar(DESIGNACION, MOTIVO.strip());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("RETIRAR_DESIGNACION");
        assertThat(evento.entidadId()).isEqualTo(DESIGNACION.toString());
        assertThat(evento.detalle()).containsOnlyKeys("partidoId", "arbitroId", "funcion", "motivo")
                .containsEntry("arbitroId", ARBITRO.toString())
                .containsEntry("funcion", "ASISTENTE");
    }

    @Test
    @DisplayName("RN-021: retirar sin motivo suficiente responde 400 antes que el 404")
    void retirarSinMotivo() {
        assertThatThrownBy(() -> servicio.retirar(DESIGNACION, new RetirarDesignacionRequest("corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThatThrownBy(() -> servicio.retirar(DESIGNACION, null))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        verifyNoInteractions(designaciones, autorizacion);
    }

    @Test
    @DisplayName("DEC-203: una designación de otra liga responde 404 sin revisar el permiso")
    void retirarInexistente() {
        given(designaciones.buscar(LIGA, DESIGNACION)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.retirar(DESIGNACION, new RetirarDesignacionRequest(MOTIVO)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    @Test
    @DisplayName("RF-023: retirar una designación que ya no está vigente responde 409")
    void retirarNoVigente() {
        DesignacionDto retirada = designacion(DESIGNACION, FuncionArbitral.CENTRAL, false, "Retiro anterior");
        given(designaciones.buscar(LIGA, DESIGNACION)).willReturn(Optional.of(retirada));
        given(designaciones.bloquear(LIGA, DESIGNACION)).willReturn(Optional.of(retirada));

        assertThatThrownBy(() -> servicio.retirar(DESIGNACION, new RetirarDesignacionRequest(MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-023"));
        verify(designaciones, never()).retirar(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-023: retirar en un partido en curso responde 409")
    void retirarPartidoEnCurso() {
        DesignacionDto vigente = designacion(DESIGNACION, FuncionArbitral.CENTRAL, true, null);
        given(designaciones.buscar(LIGA, DESIGNACION)).willReturn(Optional.of(vigente));
        given(designaciones.bloquear(LIGA, DESIGNACION)).willReturn(Optional.of(vigente));
        given(designaciones.bloquearPartido(LIGA, PARTIDO))
                .willReturn(Optional.of(new PartidoDesignable(PARTIDO, EstadoPartido.EN_CURSO)));

        assertThatThrownBy(() -> servicio.retirar(DESIGNACION, new RetirarDesignacionRequest(MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-023"));
        verify(designaciones, never()).retirar(any(), any());
    }

    // ── Consultas ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("UI-025: la vista por jornada agrupa central y asistentes y calcula el avance")
    void listarPorJornada() {
        UUID otroPartido = UUID.randomUUID();
        given(designaciones.buscarJornada(LIGA, JORNADA)).willReturn(Optional.of(new JornadaDto(JORNADA, 3,
                EstadoJornada.BORRADOR, new ReferenciaDto(UUID.randomUUID(), "A"),
                new ReferenciaDto(UUID.randomUUID(), "Libre"))));
        given(designaciones.partidosDeJornada(JORNADA)).willReturn(List.of(
                partidoDeJornada(PARTIDO, EstadoPartido.PROGRAMADO),
                partidoDeJornada(otroPartido, EstadoPartido.SUSPENDIDO)));
        given(designaciones.vigentesDeJornada(JORNADA)).willReturn(List.of(
                designacion(UUID.randomUUID(), FuncionArbitral.CENTRAL, true, null),
                designacion(UUID.randomUUID(), FuncionArbitral.ASISTENTE, true, null),
                designacion(UUID.randomUUID(), FuncionArbitral.ASISTENTE, true, null)));

        DesignacionesJornadaDto vista = servicio.listarPorJornada(JORNADA);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        assertThat(vista.avance()).isEqualTo(new DesignacionesJornadaDto.AvanceDto(2, 1, 1));
        assertThat(vista.partidos()).hasSize(2);
        DesignacionesJornadaDto.PartidoDto primero = vista.partidos().get(0);
        assertThat(primero.designable()).isTrue();
        assertThat(primero.central()).isNotNull();
        assertThat(primero.central().partidoId()).as("sin campos del partido en la vista").isNull();
        assertThat(primero.central().vigente()).isNull();
        assertThat(primero.asistentes()).hasSize(2);
        DesignacionesJornadaDto.PartidoDto segundo = vista.partidos().get(1);
        assertThat(segundo.designable()).isFalse();
        assertThat(segundo.central()).isNull();
        assertThat(segundo.asistentes()).isEmpty();
    }

    @Test
    @DisplayName("UI-025: sin jornadaId responde 400 y una jornada de otra liga, 404")
    void listarPorJornadaInvalida() {
        assertThatThrownBy(() -> servicio.listarPorJornada(null))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        given(designaciones.buscarJornada(LIGA, JORNADA)).willReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.listarPorJornada(JORNADA)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("DEC-204: los candidatos exigen EDITAR después del 404 del partido")
    void candidatos() {
        given(designaciones.buscarPartido(LIGA, PARTIDO))
                .willReturn(Optional.of(new PartidoDesignable(PARTIDO, EstadoPartido.PROGRAMADO)));
        given(elegibilidad.candidatos(LIGA, PARTIDO)).willReturn(List.of());

        assertThat(servicio.candidatos(PARTIDO)).isEmpty();
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        given(designaciones.buscarPartido(LIGA, PARTIDO)).willReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.candidatos(PARTIDO)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("DEC-205: mis designaciones exigen el permiso con cualquier ámbito y validan el rango")
    void listarPropias() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(new AlcanceAutorizacion(false, java.util.Set.of(), true));
        FiltroMisDesignaciones filtro = new FiltroMisDesignaciones(INICIO, INICIO.plusDays(7), 0, 20);
        given(designaciones.misDesignaciones(LIGA, filtro)).willReturn(List.of());
        given(designaciones.contarMisDesignaciones(LIGA, filtro)).willReturn(0L);

        assertThat(servicio.listarPropias(filtro).totalElementos()).isZero();

        assertThatThrownBy(() -> servicio.listarPropias(new FiltroMisDesignaciones(INICIO, INICIO, 0, 20)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
    }

    @Test
    @DisplayName("VAL-015: sin el permiso de consultar designaciones, mis designaciones responde 403")
    void listarPropiasSinPermiso() {
        given(autorizacion.alcance(CONSULTAR)).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.listarPropias(new FiltroMisDesignaciones(null, null, 0, 20)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(designaciones);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private static DesignarArbitroRequest peticion(FuncionArbitral funcion) {
        return new DesignarArbitroRequest(ARBITRO, funcion, null);
    }

    private static DesignacionDto designacion(UUID id, FuncionArbitral funcion, boolean vigente, String motivo) {
        return new DesignacionDto(id, PARTIDO, new ArbitroDesignadoDto(ARBITRO, "Carlos Prado",
                new ReferenciaDto(UUID.randomUUID(), "Club Halcones")), funcion, vigente, false, INICIO, motivo);
    }

    private static PartidoDeJornada partidoDeJornada(UUID id, EstadoPartido estado) {
        return new PartidoDeJornada(id, INICIO, INICIO.plusHours(2), estado,
                new ReferenciaDto(UUID.randomUUID(), "Cancha 1"), new ReferenciaDto(UUID.randomUUID(), "Halcones"),
                new ReferenciaDto(UUID.randomUUID(), "Toros"));
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        return captor.getValue();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }
}

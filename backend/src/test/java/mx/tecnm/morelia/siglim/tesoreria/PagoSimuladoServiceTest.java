package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.tesoreria.PagoRepository.DatosIntentoPago;
import mx.tecnm.morelia.siglim.tesoreria.PagoRepository.DatosPagoSimulado;
import mx.tecnm.morelia.siglim.tesoreria.PagoRepository.IntentoConCobro;
import mx.tecnm.morelia.siglim.tesoreria.eventos.CobroPagado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link PagoSimuladoService}. Sin Spring, base de datos ni pasarela real. */
class PagoSimuladoServiceTest {

    private static final String PAGAR = "tesoreria.pago:PAGAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID COBRO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID INTENTO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID PAGO = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PERFIL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final Instant AHORA = Instant.parse("2030-03-01T12:00:00Z");
    private static final BigDecimal MONTO = new BigDecimal("1500.00");
    private static final String REFERENCIA = "SIM-20300301-ABC123";

    private final CobroRepository cobros = mock(CobroRepository.class);
    private final PagoRepository pagos = mock(PagoRepository.class);
    private final PasarelaDePago pasarela = mock(PasarelaDePago.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final PagoSimuladoService servicio = new PagoSimuladoService(cobros, pagos, pasarela, autorizacion,
            auditoria, eventos, Clock.fixed(AHORA, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA));

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

    private static CobroDto cobro(EstadoCobro estado) {
        return new CobroDto(COBRO, TipoCobro.MANUAL, "Cuota", MONTO, AHORA.atOffset(ZoneOffset.UTC).toLocalDate(),
                AHORA.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(15), estado,
                new CobroDto.ReferenciaDto(CLUB, "Halcones"), null,
                OffsetDateTime.parse("2030-03-01T06:00:00-06:00"));
    }

    private static IntentoPagoDto intento(String estado) {
        return new IntentoPagoDto(INTENTO, REFERENCIA, estado, OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
                "PENDIENTE".equals(estado) ? null : OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
    }

    private void cobroEn(EstadoCobro estado) {
        given(cobros.buscar(LIGA, COBRO)).willReturn(Optional.of(cobro(estado)));
        given(cobros.bloquear(LIGA, COBRO)).willReturn(Optional.of(cobro(estado)));
    }

    private void confirmacionEn(EstadoCobro estadoCobro, String estadoIntento) {
        given(pagos.buscarIntentoConCobro(LIGA, INTENTO))
                .willReturn(Optional.of(new IntentoConCobro(INTENTO, COBRO, CLUB)));
        given(cobros.bloquear(LIGA, COBRO)).willReturn(Optional.of(cobro(estadoCobro)));
        given(pagos.buscarIntentoParaActualizar(LIGA, INTENTO)).willReturn(Optional.of(intento(estadoIntento)));
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    // ── Inicio del intento (RF-138) ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-138: un cobro PENDIENTE inicia un intento PENDIENTE con referencia ficticia y lo audita")
    void iniciaIntento() {
        cobroEn(EstadoCobro.PENDIENTE);
        given(pagos.buscarIntentoPendiente(LIGA, COBRO)).willReturn(Optional.empty());
        given(pasarela.iniciar(COBRO)).willReturn(REFERENCIA);
        given(pagos.insertarIntento(any())).willReturn(true);

        IntentoPagoDto creado = servicio.iniciar(COBRO);

        verify(autorizacion).exigir(PAGAR, ObjetivoAutorizacion.deClubes(CLUB));
        ArgumentCaptor<DatosIntentoPago> datos = ArgumentCaptor.forClass(DatosIntentoPago.class);
        verify(pagos).insertarIntento(datos.capture());
        assertThat(datos.getValue().cobroId()).isEqualTo(COBRO);
        assertThat(datos.getValue().referencia()).isEqualTo(REFERENCIA);
        assertThat(datos.getValue().iniciadoPor()).isEqualTo(PERFIL);
        assertThat(datos.getValue().iniciadoEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));

        assertThat(creado.referencia()).isEqualTo(REFERENCIA);
        assertThat(creado.estado()).isEqualTo("PENDIENTE");
        assertThat(creado.resueltoEn()).isNull();

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("INICIAR_PAGO_SIMULADO");
        assertThat(evento.getValue().entidad()).isEqualTo("intentos_pago");
        assertThat(evento.getValue().entidadId()).isEqualTo(creado.id().toString());
        assertThat(evento.getValue().detalle())
                .containsEntry("cobroId", COBRO.toString())
                .containsEntry("referencia", REFERENCIA)
                .containsEntry("estadoAnterior", "PENDIENTE")
                .containsEntry("estado", "PENDIENTE");
    }

    @Test
    @DisplayName("RF-138: un doble clic devuelve el intento pendiente existente sin crear otro")
    void devuelveIntentoPendienteExistente() {
        cobroEn(EstadoCobro.PENDIENTE);
        given(pagos.buscarIntentoPendiente(LIGA, COBRO)).willReturn(Optional.of(intento("PENDIENTE")));

        IntentoPagoDto existente = servicio.iniciar(COBRO);

        assertThat(existente.id()).isEqualTo(INTENTO);
        verify(pagos, never()).insertarIntento(any());
        verifyNoInteractions(pasarela, auditoria);
    }

    @Test
    @DisplayName("ERS §4.3.5: un cobro RECHAZADO vuelve a PENDIENTE al iniciar un nuevo intento")
    void reintentaCobroRechazado() {
        cobroEn(EstadoCobro.RECHAZADO);
        given(pagos.buscarIntentoPendiente(LIGA, COBRO)).willReturn(Optional.empty());
        given(pasarela.iniciar(COBRO)).willReturn(REFERENCIA);
        given(pagos.insertarIntento(any())).willReturn(true);
        given(cobros.reactivar(LIGA, COBRO)).willReturn(true);

        IntentoPagoDto creado = servicio.iniciar(COBRO);

        verify(cobros).reactivar(LIGA, COBRO);
        verify(pagos).insertarIntento(any());
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().detalle()).containsEntry("estadoAnterior", "RECHAZADO");
        assertThat(creado.estado()).isEqualTo("PENDIENTE");
    }

    @ParameterizedTest(name = "desde {0}")
    @EnumSource(value = EstadoCobro.class, names = {"PAGADO", "VENCIDO"})
    @DisplayName("VAL-028: un cobro PAGADO o VENCIDO no inicia pago en línea y explica su estado")
    void noIniciaPagoDeCobroNoPagable(EstadoCobro estado) {
        cobroEn(estado);

        assertThatThrownBy(() -> servicio.iniciar(COBRO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(excepcion.codigo()).isEqualTo("VAL-028");
                    assertThat(excepcion.getMessage()).contains(estado.name());
                });
        verifyNoInteractions(pasarela, auditoria);
        verify(pagos, never()).insertarIntento(any());
    }

    @Test
    @DisplayName("VAL-024: un cobro inexistente o fuera de ámbito responde 404 sin crear nada")
    void cobroInexistente() {
        given(cobros.buscar(LIGA, COBRO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciar(COBRO)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(pasarela, auditoria);
        verify(pagos, never()).insertarIntento(any());
    }

    @Test
    @DisplayName("RF-138: si la referencia se repite, genera otra; tras cinco colisiones responde 409 CONCURRENCIA")
    void reintentaReferenciaUnica() {
        cobroEn(EstadoCobro.PENDIENTE);
        given(pagos.buscarIntentoPendiente(LIGA, COBRO)).willReturn(Optional.empty());
        given(pasarela.iniciar(COBRO)).willReturn("SIM-20300301-AAAAAA", "SIM-20300301-BBBBBB");
        given(pagos.insertarIntento(any())).willReturn(false, true);

        IntentoPagoDto creado = servicio.iniciar(COBRO);
        assertThat(creado.referencia()).isEqualTo("SIM-20300301-BBBBBB");

        given(pagos.insertarIntento(any())).willReturn(false);
        assertThatThrownBy(() -> servicio.iniciar(COBRO))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, ErroresTecnicos.CONCURRENCIA));
    }

    // ── Confirmación (RF-138, RNF-004) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("H-30 criterio 1 · RF-138: aprobar crea el pago EN_LINEA_SIMULADO, audita y publica CobroPagado")
    void apruebaPago() {
        confirmacionEn(EstadoCobro.PENDIENTE, "PENDIENTE");
        given(pagos.resolverIntento(eq(INTENTO), eq("APROBADO"), any())).willReturn(true);

        IntentoPagoDto resuelto = servicio.confirmar(INTENTO, ResultadoPagoSimulado.APROBADO);

        verify(autorizacion).exigir(PAGAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(pasarela).confirmar(REFERENCIA, ResultadoPagoSimulado.APROBADO);
        verify(pagos).resolverIntento(eq(INTENTO), eq("APROBADO"), any());
        ArgumentCaptor<DatosPagoSimulado> pago = ArgumentCaptor.forClass(DatosPagoSimulado.class);
        verify(pagos).insertarPagoSimulado(pago.capture());
        assertThat(pago.getValue().cobroId()).isEqualTo(COBRO);
        assertThat(pago.getValue().intentoId()).isEqualTo(INTENTO);
        assertThat(pago.getValue().monto()).isEqualByComparingTo(MONTO);
        assertThat(pago.getValue().referencia()).isEqualTo(REFERENCIA);
        assertThat(pago.getValue().registradoPor()).isEqualTo(PERFIL);
        assertThat(pago.getValue().pagadoEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));

        assertThat(resuelto.estado()).isEqualTo("APROBADO");
        assertThat(resuelto.referencia()).isEqualTo(REFERENCIA);
        assertThat(resuelto.resueltoEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));

        ArgumentCaptor<EventoAuditoria> auditado = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(auditado.capture());
        assertThat(auditado.getValue().accion()).isEqualTo("CONFIRMAR_PAGO_SIMULADO");
        assertThat(auditado.getValue().detalle())
                .containsEntry("cobroId", COBRO.toString())
                .containsEntry("referencia", REFERENCIA)
                .containsEntry("resultado", "APROBADO")
                .containsEntry("estadoCobro", "PAGADO")
                .containsEntry("monto", MONTO)
                .containsKey("pagoId");

        ArgumentCaptor<Object> publicado = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(publicado.capture());
        assertThat(publicado.getValue()).isInstanceOfSatisfying(CobroPagado.class, evento -> {
            assertThat(evento.ligaId()).isEqualTo(LIGA);
            assertThat(evento.cobroId()).isEqualTo(COBRO);
            assertThat(evento.clubId()).isEqualTo(CLUB);
            assertThat(evento.intentoId()).isEqualTo(INTENTO);
            assertThat(evento.monto()).isEqualByComparingTo(MONTO);
            assertThat(evento.medio()).isEqualTo(MedioPago.EN_LINEA_SIMULADO);
        });
    }

    @Test
    @DisplayName("H-30 criterio 2 · RF-138: rechazar deja el cobro RECHAZADO, sin pago ni evento")
    void rechazaPago() {
        confirmacionEn(EstadoCobro.PENDIENTE, "PENDIENTE");
        given(pagos.resolverIntento(eq(INTENTO), eq("RECHAZADO"), any())).willReturn(true);
        given(cobros.marcarRechazado(LIGA, COBRO)).willReturn(true);

        IntentoPagoDto resuelto = servicio.confirmar(INTENTO, ResultadoPagoSimulado.RECHAZADO);

        verify(pasarela).confirmar(REFERENCIA, ResultadoPagoSimulado.RECHAZADO);
        verify(pagos).resolverIntento(eq(INTENTO), eq("RECHAZADO"), any());
        verify(cobros).marcarRechazado(LIGA, COBRO);
        verify(pagos, never()).insertarPagoSimulado(any());
        verifyNoInteractions(eventos);
        assertThat(resuelto.estado()).isEqualTo("RECHAZADO");

        ArgumentCaptor<EventoAuditoria> auditado = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(auditado.capture());
        assertThat(auditado.getValue().detalle())
                .containsEntry("resultado", "RECHAZADO")
                .containsEntry("estadoCobro", "RECHAZADO")
                .doesNotContainKey("pagoId")
                .doesNotContainKey("monto");
    }

    @Test
    @DisplayName("H-30 criterio 3 · VAL-028: un cobro VENCIDO no se paga en línea")
    void cobroVencidoNoSePagaEnLinea() {
        confirmacionEn(EstadoCobro.VENCIDO, "PENDIENTE");

        assertThatThrownBy(() -> servicio.confirmar(INTENTO, ResultadoPagoSimulado.APROBADO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(excepcion.codigo()).isEqualTo("VAL-028");
                    assertThat(excepcion.getMessage()).contains("VENCIDO", "Tesorería");
                });
        sinConfirmacion();
    }

    @Test
    @DisplayName("VAL-028: un cobro ya PAGADO no admite otra confirmación")
    void cobroPagadoNoSePagaDosVeces() {
        confirmacionEn(EstadoCobro.PAGADO, "PENDIENTE");

        assertThatThrownBy(() -> servicio.confirmar(INTENTO, ResultadoPagoSimulado.APROBADO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "VAL-028"));
        sinConfirmacion();
    }

    @Test
    @DisplayName("VAL-028: un intento ya APROBADO o RECHAZADO no se confirma otra vez")
    void intentoYaResuelto() {
        confirmacionEn(EstadoCobro.PENDIENTE, "APROBADO");

        assertThatThrownBy(() -> servicio.confirmar(INTENTO, ResultadoPagoSimulado.APROBADO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "VAL-028"));
        sinConfirmacion();
    }

    @Test
    @DisplayName("RNF-004: si otra confirmación ganó la carrera, responde 409 CONCURRENCIA y no duplica el pago")
    void pierdeLaCarrera() {
        confirmacionEn(EstadoCobro.PENDIENTE, "PENDIENTE");
        given(pagos.resolverIntento(eq(INTENTO), eq("APROBADO"), any())).willReturn(false);

        assertThatThrownBy(() -> servicio.confirmar(INTENTO, ResultadoPagoSimulado.APROBADO))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, ErroresTecnicos.CONCURRENCIA));
        verify(pagos, never()).insertarPagoSimulado(any());
        verifyNoInteractions(auditoria, eventos);
    }

    @Test
    @DisplayName("RNF-004: si el cobro cambia al rechazar, responde 409 CONCURRENCIA y no deja auditoría")
    void noPuedeRechazarSiElCobroCambio() {
        confirmacionEn(EstadoCobro.PENDIENTE, "PENDIENTE");
        given(pagos.resolverIntento(eq(INTENTO), eq("RECHAZADO"), any())).willReturn(true);
        given(cobros.marcarRechazado(LIGA, COBRO)).willReturn(false);

        assertThatThrownBy(() -> servicio.confirmar(INTENTO, ResultadoPagoSimulado.RECHAZADO))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, ErroresTecnicos.CONCURRENCIA));
        verifyNoInteractions(auditoria, eventos);
    }

    @Test
    @DisplayName("VALIDACION: sin resultado responde 400 antes de tocar la base")
    void sinResultado() {
        assertThatThrownBy(() -> servicio.confirmar(INTENTO, null))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST,
                        ErroresTecnicos.VALIDACION));
        verifyNoInteractions(pagos, pasarela, auditoria, eventos);
    }

    @Test
    @DisplayName("VAL-024: un intento inexistente o fuera de ámbito responde 404")
    void intentoInexistente() {
        given(pagos.buscarIntentoConCobro(LIGA, INTENTO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.confirmar(INTENTO, ResultadoPagoSimulado.APROBADO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(pasarela, auditoria, eventos);
    }

    private void sinConfirmacion() {
        verifyNoInteractions(pasarela, auditoria, eventos);
        verify(pagos, never()).resolverIntento(any(), any(), any());
        verify(pagos, never()).insertarPagoSimulado(any());
    }
}

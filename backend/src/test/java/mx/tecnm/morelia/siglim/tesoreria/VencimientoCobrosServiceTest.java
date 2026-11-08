package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.EjecutorSistema;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.tesoreria.CobroRepository.CobroVencidoFila;
import mx.tecnm.morelia.siglim.tesoreria.eventos.CobroVencido;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Reglas de {@link VencimientoCobrosService}. Sin Spring ni base de datos; el reloj es fijo. */
class VencimientoCobrosServiceTest {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID EQUIPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    /** 1 de marzo de 2030 a las 23:30 en Ciudad de México: en UTC ya es 2 de marzo. */
    private static final Instant NOCHE_EN_MEXICO = Instant.parse("2030-03-02T05:30:00Z");
    private static final LocalDate HOY = LocalDate.parse("2030-03-01");
    private static final CobroVencidoFila AL_CLUB = new CobroVencidoFila(UUID.randomUUID(), LIGA, CLUB, null,
            TipoCobro.MANUAL, new BigDecimal("1500.00"), HOY.minusDays(1));
    private static final CobroVencidoFila AL_EQUIPO = new CobroVencidoFila(UUID.randomUUID(), LIGA, CLUB, EQUIPO,
            TipoCobro.ARBITRAJE, new BigDecimal("350.50"), HOY.minusDays(10));

    private final CobroRepository cobros = mock(CobroRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);

    @BeforeEach
    void transaccionSimulada() {
        given(transacciones.getTransaction(any())).willReturn(new SimpleTransactionStatus());
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    private VencimientoCobrosService servicio(boolean habilitada) {
        return new VencimientoCobrosService(cobros, auditoria, motivoOperacion, eventos, new EjecutorSistema(),
                transacciones, Clock.fixed(NOCHE_EN_MEXICO, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA),
                habilitada);
    }

    @Test
    @DisplayName("DEC-60: «hoy» es el día en America/Mexico_City aunque en UTC ya sea el día siguiente")
    void hoyEnLaZonaDeNegocio() {
        given(cobros.vencerPendientes(LIGA, HOY)).willReturn(List.of());

        assertThat(servicio(true).vencer()).isZero();

        verify(cobros).vencerPendientes(LIGA, HOY);
    }

    @Test
    @DisplayName("RF-141 · ADR-0009: audita y publica un evento por cada cobro vencido, con el motivo fijo antes del cambio")
    void auditaYPublicaPorCobro() {
        given(cobros.vencerPendientes(LIGA, HOY)).willReturn(List.of(AL_CLUB, AL_EQUIPO));

        assertThat(servicio(true).vencer()).isEqualTo(2);

        InOrder orden = inOrder(motivoOperacion, cobros, auditoria);
        orden.verify(motivoOperacion).fijar(VencimientoCobrosService.MOTIVO);
        orden.verify(cobros).vencerPendientes(LIGA, HOY);

        ArgumentCaptor<EventoAuditoria> auditados = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(2)).registrar(auditados.capture());
        EventoAuditoria primero = auditados.getAllValues().getFirst();
        assertThat(primero.ligaId()).isEqualTo(LIGA);
        assertThat(primero.modulo()).isEqualTo("M-13");
        assertThat(primero.accion()).isEqualTo("VENCER_COBRO");
        assertThat(primero.entidad()).isEqualTo("cobros");
        assertThat(primero.entidadId()).isEqualTo(AL_CLUB.id().toString());
        assertThat(primero.motivo()).isEqualTo("vencimiento automático por fecha límite");
        assertThat(primero.detalle()).containsExactly(
                entry("estadoAnterior", "PENDIENTE"),
                entry("estadoNuevo", "VENCIDO"),
                entry("fechaLimite", "2030-02-28"),
                entry("monto", new BigDecimal("1500.00")));
        assertThat(auditados.getAllValues().get(1).entidadId()).isEqualTo(AL_EQUIPO.id().toString());

        ArgumentCaptor<CobroVencido> publicados = ArgumentCaptor.forClass(CobroVencido.class);
        verify(eventos, times(2)).publishEvent(publicados.capture());
        assertThat(publicados.getAllValues()).containsExactly(
                new CobroVencido(LIGA, AL_CLUB.id(), CLUB, null, TipoCobro.MANUAL, new BigDecimal("1500.00"),
                        HOY.minusDays(1)),
                new CobroVencido(LIGA, AL_EQUIPO.id(), CLUB, EQUIPO, TipoCobro.ARBITRAJE, new BigDecimal("350.50"),
                        HOY.minusDays(10)));
    }

    @Test
    @DisplayName("ADR-0009 punto 4: el cambio se hace como el perfil Sistema y la sesión se retira al terminar")
    void ejecutaComoSistema() {
        AtomicReference<UUID> perfilDuranteElCambio = new AtomicReference<>();
        given(cobros.vencerPendientes(LIGA, HOY)).willAnswer(invocacion -> {
            perfilDuranteElCambio.set(UsuarioActual.idRequerido());
            return List.of(AL_CLUB);
        });

        servicio(true).vencer();

        assertThat(perfilDuranteElCambio.get()).isEqualTo(EjecutorSistema.PERFIL_SISTEMA);
        assertThat(UsuarioActual.perfilId()).isEmpty();
    }

    @Test
    @DisplayName("ADR-0009 §2: si no vence ningún cobro, no audita ni publica eventos")
    void sinCambiosNoAuditaNiPublica() {
        given(cobros.vencerPendientes(LIGA, HOY)).willReturn(List.of());

        servicio(true).vencer();

        verifyNoInteractions(auditoria, eventos);
    }

    @Test
    @DisplayName("RNF-015: si la auditoría falla, la transacción se revierte y vencer() propaga el error")
    void fallaRevierte() {
        given(cobros.vencerPendientes(LIGA, HOY)).willReturn(List.of(AL_CLUB));
        willThrow(new IllegalStateException("auditoría caída")).given(auditoria).registrar(any());

        assertThatThrownBy(() -> servicio(true).vencer()).hasMessage("auditoría caída");

        verify(transacciones).rollback(any());
        verify(transacciones, never()).commit(any());
        verify(eventos, never()).publishEvent(any());
        assertThat(UsuarioActual.perfilId()).isEmpty();
    }

    @Test
    @DisplayName("ADR-0009: un error en la ejecución programada no se propaga al planificador")
    void ejecucionProgramadaNoPropagaErrores() {
        given(cobros.vencerPendientes(LIGA, HOY)).willThrow(new IllegalStateException("base de datos caída"));
        VencimientoCobrosService tarea = servicio(true);

        tarea.ejecutarProgramada();
        tarea.ejecutarAlArrancar();

        verify(cobros, times(2)).vencerPendientes(LIGA, HOY);
        assertThat(UsuarioActual.perfilId()).isEmpty();
    }

    @Test
    @DisplayName("DEC-60: con habilitada=false no corre ni con el cron ni al arrancar")
    void deshabilitadaNoCorre() {
        VencimientoCobrosService tarea = servicio(false);

        tarea.ejecutarProgramada();
        tarea.ejecutarAlArrancar();

        verifyNoInteractions(cobros, auditoria, eventos, transacciones);
    }
}

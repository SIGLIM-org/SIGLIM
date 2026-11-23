package mx.tecnm.morelia.siglim.partidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
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
import mx.tecnm.morelia.siglim.partidos.PartidosRepository.PartidoIniciado;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Reglas de {@link InicioPartidosService} sin Spring ni base de datos, con el reloj fijo.
 *
 * <p>Refs: H-15.2, RF-127, RN-021, ADR-0009
 */
class InicioPartidosServiceTest {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final Instant AHORA = Instant.parse("2031-03-10T16:00:00Z");
    private static final PartidoIniciado PROGRAMADO = new PartidoIniciado(UUID.randomUUID(), EstadoPartido.PROGRAMADO,
            OffsetDateTime.parse("2031-03-10T10:00:00-06:00"));
    private static final PartidoIniciado REPROGRAMADO = new PartidoIniciado(UUID.randomUUID(),
            EstadoPartido.REPROGRAMADO, OffsetDateTime.parse("2031-03-10T09:00:00-06:00"));

    private final PartidosRepository partidos = mock(PartidosRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);

    @BeforeEach
    void transaccionSimulada() {
        given(transacciones.getTransaction(any())).willReturn(new SimpleTransactionStatus());
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    private InicioPartidosService servicio(boolean habilitada) {
        return new InicioPartidosService(partidos, auditoria, motivoOperacion, new EjecutorSistema(), transacciones,
                Clock.fixed(AHORA, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA), habilitada);
    }

    private static OffsetDateTime ahora() {
        return OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    }

    @Test
    @DisplayName("RF-127 · RN-021: fija el motivo antes del cambio y audita cada partido iniciado")
    void auditaCadaPartido() {
        given(partidos.iniciarLlegados(LIGA, ahora())).willReturn(List.of(PROGRAMADO, REPROGRAMADO));

        assertThat(servicio(true).iniciar()).isEqualTo(2);

        InOrder orden = inOrder(motivoOperacion, partidos, auditoria);
        orden.verify(motivoOperacion).fijar("inicio automático por fecha y hora");
        orden.verify(partidos).iniciarLlegados(LIGA, ahora());
        ArgumentCaptor<EventoAuditoria> auditados = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(2)).registrar(auditados.capture());
        EventoAuditoria segundo = auditados.getAllValues().get(1);
        assertThat(segundo.ligaId()).isEqualTo(LIGA);
        assertThat(segundo.modulo()).isEqualTo("M-08");
        assertThat(segundo.accion()).isEqualTo("INICIAR_PARTIDO");
        assertThat(segundo.entidad()).isEqualTo("partidos");
        assertThat(segundo.entidadId()).isEqualTo(REPROGRAMADO.id().toString());
        assertThat(segundo.motivo()).isEqualTo(InicioPartidosService.MOTIVO);
        assertThat(segundo.detalle()).containsExactly(entry("estadoAnterior", "REPROGRAMADO"),
                entry("estadoNuevo", "EN_CURSO"), entry("inicio", REPROGRAMADO.inicio().toString()));
    }

    @Test
    @DisplayName("ADR-0009: sin partidos por iniciar no audita nada")
    void sinPartidos() {
        given(partidos.iniciarLlegados(LIGA, ahora())).willReturn(List.of());

        assertThat(servicio(true).iniciar()).isZero();

        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("ADR-0009 §4: el cambio corre con la sesión del perfil Sistema y la retira al terminar")
    void comoSistema() {
        AtomicReference<UUID> perfil = new AtomicReference<>();
        given(partidos.iniciarLlegados(LIGA, ahora())).willAnswer(invocacion -> {
            perfil.set(UsuarioActual.idRequerido());
            return List.of(PROGRAMADO);
        });

        servicio(true).iniciar();

        assertThat(perfil.get()).isEqualTo(EjecutorSistema.PERFIL_SISTEMA);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("ADR-0009: deshabilitada, la ejecución programada no toca la base")
    void deshabilitada() {
        servicio(false).ejecutarProgramada();

        verifyNoInteractions(partidos, auditoria, motivoOperacion);
    }

    @Test
    @DisplayName("ADR-0009: un error no detiene el planificador; la llamada directa sí lo propaga")
    void errorNoDetieneElPlanificador() {
        given(partidos.iniciarLlegados(LIGA, ahora())).willThrow(new IllegalStateException("base caída"));

        servicio(true).ejecutarProgramada();
        assertThatThrownBy(() -> servicio(true).iniciar()).isInstanceOf(IllegalStateException.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.programacion.eventos.JornadaPublicada;
import mx.tecnm.morelia.siglim.tesoreria.CobroAutomaticoRepository.CobroAutomatico;
import mx.tecnm.morelia.siglim.tesoreria.CobroAutomaticoRepository.EquipoCobrable;
import mx.tecnm.morelia.siglim.tesoreria.CobroAutomaticoRepository.PartidoCobrable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Reglas de {@link CobroAutomaticoService} (B-30, RF-136). Sin Spring ni base de datos. */
class CobroAutomaticoServiceTest {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID JORNADA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PARTIDO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEMPORADA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID CATEGORIA = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PRESIDENTE = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final EquipoCobrable LOCAL = new EquipoCobrable(
            UUID.fromString("66666666-6666-4666-8666-666666666666"),
            UUID.fromString("77777777-7777-4777-8777-777777777777"), "Halcones");
    private static final EquipoCobrable VISITANTE = new EquipoCobrable(
            UUID.fromString("88888888-8888-4888-8888-888888888888"),
            UUID.fromString("99999999-9999-4999-8999-999999999999"), "Toros");
    /** 1 de marzo de 2031, mediodía en Morelia. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2031-03-01T18:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    private static final JornadaPublicada EVENTO = new JornadaPublicada(LIGA, JORNADA, PRESIDENTE);

    private final CobroAutomaticoRepository repositorio = mock(CobroAutomaticoRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final CobroAutomaticoService servicio = new CobroAutomaticoService(repositorio, auditoria, RELOJ, 7);

    @Test
    @DisplayName("RF-136: cada equipo recibe arbitraje y cancha con la tarifa, el periodo y su club")
    void cobrosDeAmbosEquipos() {
        dadoPartido(INICIO, null);
        dadasTarifas(Map.of(ConceptoTarifa.ARBITRAJE, new BigDecimal("350.00"),
                ConceptoTarifa.CANCHA, new BigDecimal("200.00")));

        assertThat(servicio.alPublicarJornada(EVENTO)).isEqualTo(4);

        List<CobroAutomatico> cobros = insertados(4);
        assertThat(cobros).extracting(CobroAutomatico::equipoId, CobroAutomatico::clubId, CobroAutomatico::tipo,
                        CobroAutomatico::monto)
                .containsExactly(
                        tuple(LOCAL.id(), LOCAL.clubId(), TipoCobro.ARBITRAJE, new BigDecimal("350.00")),
                        tuple(LOCAL.id(), LOCAL.clubId(), TipoCobro.CANCHA, new BigDecimal("200.00")),
                        tuple(VISITANTE.id(), VISITANTE.clubId(), TipoCobro.ARBITRAJE, new BigDecimal("350.00")),
                        tuple(VISITANTE.id(), VISITANTE.clubId(), TipoCobro.CANCHA, new BigDecimal("200.00")));
        assertThat(cobros).allSatisfy(cobro -> {
            assertThat(cobro.partidoId()).isEqualTo(PARTIDO);
            assertThat(cobro.periodoInicio()).isEqualTo(LocalDate.parse("2031-03-01"));
            assertThat(cobro.fechaLimite()).isEqualTo(LocalDate.parse("2031-03-17"));
        });
        assertThat(cobros.getFirst().concepto()).isEqualTo("Arbitraje · Jornada 3 · Halcones vs Toros");
        assertThat(cobros.get(3).concepto()).isEqualTo("Cancha · Jornada 3 · Toros vs Halcones");

        List<EventoAuditoria> eventos = auditados(5);
        assertThat(eventos).extracting(EventoAuditoria::accion).containsExactly(
                "GENERAR_COBRO", "GENERAR_COBRO", "GENERAR_COBRO", "GENERAR_COBRO", "GENERAR_COBROS_JORNADA");
        assertThat(eventos.getFirst().modulo()).isEqualTo("M-13");
        assertThat(eventos.getFirst().detalle()).doesNotContainKey("concepto")
                .containsEntry("jornadaId", JORNADA.toString()).containsEntry("estado", "PENDIENTE");
        assertThat(eventos.getLast().entidadId()).isEqualTo(JORNADA.toString());
        assertThat(eventos.getLast().detalle()).containsEntry("partidos", 1).containsEntry("cobrosGenerados", 4);
    }

    @Test
    @DisplayName("RF-136: sin cobro de cancha al equipo que juega en la cancha de su club")
    void sinCanchaPropia() {
        dadoPartido(INICIO, LOCAL.clubId());
        dadasTarifas(Map.of(ConceptoTarifa.ARBITRAJE, new BigDecimal("350.00"),
                ConceptoTarifa.CANCHA, new BigDecimal("200.00")));

        assertThat(servicio.alPublicarJornada(EVENTO)).isEqualTo(3);

        assertThat(insertados(3)).extracting(CobroAutomatico::equipoId, CobroAutomatico::tipo).containsExactly(
                tuple(LOCAL.id(), TipoCobro.ARBITRAJE),
                tuple(VISITANTE.id(), TipoCobro.ARBITRAJE),
                tuple(VISITANTE.id(), TipoCobro.CANCHA));
    }

    @Test
    @DisplayName("RF-136 [PROPUESTA]: sin tarifa o en cero se omite el cobro y la omisión queda auditada")
    void omisionesSinTarifa() {
        dadoPartido(INICIO, null);
        dadasTarifas(Map.of(ConceptoTarifa.ARBITRAJE, BigDecimal.ZERO));

        assertThat(servicio.alPublicarJornada(EVENTO)).isZero();

        verify(repositorio, times(0)).insertarSiNoExiste(any(), any(), any());
        EventoAuditoria resumen = auditados(1).getFirst();
        assertThat(resumen.accion()).isEqualTo("GENERAR_COBROS_JORNADA");
        assertThat(resumen.detalle()).containsEntry("cobrosGenerados", 0);
        List<?> omitidos = (List<?>) resumen.detalle().get("omitidos");
        assertThat(omitidos).hasSize(4);
        assertThat(omitidos.stream().map(omision -> (Object) ((Map<?, ?>) omision).get("causa")).toList())
                .containsExactly("TARIFA_EN_CERO", "SIN_TARIFA", "TARIFA_EN_CERO", "SIN_TARIFA");
    }

    @Test
    @DisplayName("RF-136: un cobro que ya existía no se duplica ni se vuelve a auditar")
    void idempotente() {
        dadoPartido(INICIO, null);
        dadasTarifas(Map.of(ConceptoTarifa.ARBITRAJE, new BigDecimal("350.00")));
        given(repositorio.insertarSiNoExiste(eq(LIGA), any(), eq(PRESIDENTE))).willReturn(Optional.empty());

        assertThat(servicio.alPublicarJornada(EVENTO)).isZero();

        assertThat(auditados(1)).extracting(EventoAuditoria::accion).containsExactly("GENERAR_COBROS_JORNADA");
    }

    @Test
    @DisplayName("RF-141 [PROPUESTA]: si el partido ya pasó, el plazo se cuenta desde hoy")
    void partidoPasado() {
        dadoPartido(OffsetDateTime.parse("2031-02-20T10:00:00-06:00"), null);
        dadasTarifas(Map.of(ConceptoTarifa.ARBITRAJE, new BigDecimal("350.00")));

        servicio.alPublicarJornada(EVENTO);

        assertThat(insertados(2)).extracting(CobroAutomatico::fechaLimite)
                .containsOnly(LocalDate.parse("2031-03-08"));
    }

    @Test
    @DisplayName("Configuración: un plazo negativo impide arrancar")
    void plazoNegativo() {
        assertThatThrownBy(() -> new CobroAutomaticoService(repositorio, auditoria, RELOJ, -1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dias-para-pagar");
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private void dadoPartido(OffsetDateTime inicio, UUID canchaClubId) {
        given(repositorio.partidosDeJornada(LIGA, JORNADA)).willReturn(List.of(new PartidoCobrable(
                PARTIDO, inicio, (short) 3, TEMPORADA, CATEGORIA, LOCAL, VISITANTE, canchaClubId)));
        given(repositorio.insertarSiNoExiste(eq(LIGA), any(), eq(PRESIDENTE)))
                .willAnswer(invocacion -> Optional.of(UUID.randomUUID()));
    }

    private void dadasTarifas(Map<ConceptoTarifa, BigDecimal> tarifas) {
        given(repositorio.tarifas(TEMPORADA, CATEGORIA)).willReturn(tarifas);
    }

    private List<CobroAutomatico> insertados(int cantidad) {
        ArgumentCaptor<CobroAutomatico> captor = ArgumentCaptor.forClass(CobroAutomatico.class);
        verify(repositorio, times(cantidad)).insertarSiNoExiste(eq(LIGA), captor.capture(), eq(PRESIDENTE));
        return captor.getAllValues();
    }

    private List<EventoAuditoria> auditados(int cantidad) {
        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(cantidad)).registrar(captor.capture());
        return captor.getAllValues();
    }
}

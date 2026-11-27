package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.DatosFecha;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.PartidoDeFecha;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.PartidosDelDia;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosJornada;
import mx.tecnm.morelia.siglim.programacion.VerificacionFechaDto.Aviso;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * Reglas del cambio de días de la fecha (H-32.2, DEC-193) y de la causa de «Sin árbitro» en la
 * verificación (H-34.2, DEC-220) en {@link FechaService}. Sin Spring.
 */
class FechaServiceTest {

    private static final String EDITAR = "programacion.jornada:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID FECHA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TEMPORADA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID JORNADA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final LocalDate SABADO = LocalDate.parse("2031-03-08");
    private static final LocalDate DOMINGO = SABADO.plusDays(1);
    private static final OffsetDateTime CREADO_EN = OffsetDateTime.parse("2031-03-01T06:00:00-06:00");
    private static final String MOTIVO = "La unidad deportiva cierra el sábado";

    private final FechaRepository fechas = mock(FechaRepository.class);
    private final JornadaRepository jornadas = mock(JornadaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final DesignadorArbitroCentral designador = mock(DesignadorArbitroCentral.class);
    private final FechaService servicio = new FechaService(fechas, jornadas, mock(ValidadorConflictos.class),
            mock(ValidadorLocalia.class), designador, autorizacion, auditoria, motivoOperacion,
            mock(ApplicationEventPublisher.class), new SiglimPropiedades(null, null, null, LIGA));

    private static DatosFecha fecha(List<LocalDate> dias) {
        return new DatosFecha(FECHA, TEMPORADA, 3, dias, null, null, null, 1, 1, 0, 2, 0, 1);
    }

    private static DatosJornada jornada(EstadoJornada estado) {
        return new DatosJornada(JORNADA, UUID.randomUUID(), null, 3, estado, null, null, null, null, null, null, null,
                CREADO_EN, CREADO_EN);
    }

    /** Fecha 3 (sábado y domingo) con una jornada en el estado dado. */
    private void existeFecha(EstadoJornada estado) {
        given(fechas.bloquear(LIGA, FECHA)).willReturn(true);
        given(jornadas.bloquearJornadasDeFecha(FECHA)).willReturn(List.of(jornada(estado)));
        given(fechas.buscar(LIGA, FECHA)).willReturn(Optional.of(fecha(List.of(SABADO, DOMINGO))));
        given(fechas.fechaQueUsaAlgunDia(any(), any(), any())).willReturn(Optional.empty());
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private void rechaza(List<LocalDate> dias, String motivo, HttpStatus estado, String codigo) {
        assertThatThrownBy(() -> servicio.actualizarDias(FECHA, new ActualizarFechaRequest(dias, motivo)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(estado, codigo));
        verify(fechas, never()).actualizarDias(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("DEC-193: agrega un día ordenado, fija el motivo y audita CAMBIAR_DIAS_FECHA con antes y después")
    void agregarDia() {
        existeFecha(EstadoJornada.BORRADOR);
        LocalDate viernes = SABADO.minusDays(1);

        FechaDto resultado = servicio.actualizarDias(FECHA,
                new ActualizarFechaRequest(List.of(DOMINGO, viernes, SABADO), MOTIVO));

        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        verify(motivoOperacion).fijar(MOTIVO);
        verify(fechas).actualizarDias(FECHA, List.of(viernes, SABADO, DOMINGO));
        assertThat(resultado.bloques()).as("con la forma del listado").isNull();
        assertThat(resultado.descansos()).isEqualTo(1);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("CAMBIAR_DIAS_FECHA");
        assertThat(evento.getValue().entidad()).isEqualTo("fechas_programacion");
        assertThat(evento.getValue().motivo()).isEqualTo(MOTIVO);
        assertThat(evento.getValue().detalle())
                .containsEntry("diasAnteriores", List.of("2031-03-08", "2031-03-09"))
                .containsEntry("dias", List.of("2031-03-07", "2031-03-08", "2031-03-09"));
    }

    @Test
    @DisplayName("DEC-193: los mismos días en otro orden no escriben ni auditan")
    void sinCambios() {
        existeFecha(EstadoJornada.BORRADOR);

        servicio.actualizarDias(FECHA, new ActualizarFechaRequest(List.of(DOMINGO, SABADO), MOTIVO));

        verify(fechas, never()).actualizarDias(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("DEC-193: sin días responde 400 VALIDACION")
    void sinDias() {
        rechaza(null, MOTIVO, HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION);
        verifyNoInteractions(fechas);
    }

    @Test
    @DisplayName("RN-021: sin motivo de al menos 10 caracteres responde 400 RN-021 antes de bloquear la fecha")
    void sinMotivo() {
        rechaza(List.of(SABADO), null, HttpStatus.BAD_REQUEST, "RN-021");
        rechaza(List.of(SABADO), "  breve  ", HttpStatus.BAD_REQUEST, "RN-021");
        verifyNoInteractions(fechas);
    }

    @Test
    @DisplayName("DEC-193: una fecha que no existe en la liga responde 404")
    void fechaInexistente() {
        given(fechas.bloquear(LIGA, FECHA)).willReturn(false);

        assertThatThrownBy(() -> servicio.actualizarDias(FECHA, new ActualizarFechaRequest(List.of(SABADO), MOTIVO)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("RF-003: los días de una fecha de una temporada cerrada no cambian")
    void temporadaCerrada() {
        existeFecha(EstadoJornada.BORRADOR);
        given(fechas.temporadaCerrada(TEMPORADA)).willReturn(true);

        rechaza(List.of(SABADO), MOTIVO, HttpStatus.CONFLICT, "RF-003");
    }

    @Test
    @DisplayName("RF-090: con una jornada en REVISION o PUBLICADO responde 409 RF-090")
    void jornadaFueraDeBorrador() {
        existeFecha(EstadoJornada.REVISION);
        rechaza(List.of(SABADO, DOMINGO.plusDays(1)), MOTIVO, HttpStatus.CONFLICT, "RF-090");

        existeFecha(EstadoJornada.PUBLICADO);
        rechaza(List.of(SABADO, DOMINGO.plusDays(1)), MOTIVO, HttpStatus.CONFLICT, "RF-090");
    }

    @Test
    @DisplayName("DEC-193: vacíos, más de 7, repetidos, nulos o fuera de 7 días consecutivos responden 422 VALIDACION")
    void diasInvalidos() {
        existeFecha(EstadoJornada.BORRADOR);

        rechaza(List.of(), MOTIVO, HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.VALIDACION);
        rechaza(SABADO.datesUntil(SABADO.plusDays(8)).toList(), MOTIVO, HttpStatus.UNPROCESSABLE_CONTENT,
                ErroresTecnicos.VALIDACION);
        rechaza(List.of(SABADO, SABADO), MOTIVO, HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.VALIDACION);
        rechaza(Arrays.asList(SABADO, null), MOTIVO, HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.VALIDACION);
        rechaza(List.of(SABADO, SABADO.plusDays(7)), MOTIVO, HttpStatus.UNPROCESSABLE_CONTENT,
                ErroresTecnicos.VALIDACION);
        assertThat(FechaService.diasValidos(List.of(SABADO.plusDays(6), SABADO)))
                .as("7 días consecutivos sí caben").containsExactly(SABADO, SABADO.plusDays(6));
    }

    @Test
    @DisplayName("DEC-193: un día que ya es de otra fecha de la temporada responde 422 y la nombra")
    void traslapeConOtraFecha() {
        existeFecha(EstadoJornada.BORRADOR);
        given(fechas.fechaQueUsaAlgunDia(TEMPORADA, FECHA, List.of(SABADO, DOMINGO, SABADO.plusDays(6))))
                .willReturn(Optional.of(4));

        assertThatThrownBy(() -> servicio.actualizarDias(FECHA,
                new ActualizarFechaRequest(List.of(SABADO, DOMINGO, SABADO.plusDays(6)), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(excepcion.getMessage()).contains("fecha 4");
                });
        verify(fechas, never()).actualizarDias(any(), any());
    }

    @Test
    @DisplayName("RF-090: quitar un día con partidos no cancelados responde 409 nombrando el día y cuántos")
    void quitarDiaConPartidos() {
        existeFecha(EstadoJornada.BORRADOR);
        given(fechas.partidosPorDia(FECHA)).willReturn(List.of(new PartidosDelDia(DOMINGO, 3)));

        assertThatThrownBy(() -> servicio.actualizarDias(FECHA, new ActualizarFechaRequest(List.of(SABADO), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(excepcion.codigo()).isEqualTo("RF-090");
                    assertThat(excepcion.getMessage()).contains("2031-03-09").contains("3 partidos");
                });
        verify(fechas, never()).actualizarDias(any(), any());
    }

    @Test
    @DisplayName("VAL-015: sin programacion.jornada:EDITAR no lee ni escribe")
    void sinPermiso() {
        willThrow(ReglaNegocioException.prohibida("VAL-015", "Sin permiso"))
                .given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        rechaza(List.of(SABADO), MOTIVO, HttpStatus.FORBIDDEN, "VAL-015");
        verifyNoInteractions(fechas, jornadas);
    }

    // ── Verificación: causa de «Sin árbitro» (H-34.2, DEC-220) ──────────────────────────────────

    @Test
    @DisplayName("RF-023 · DEC-220: la causa del puerto se agrega al aviso; sin causa en el mapa el aviso no cambia")
    void causaDeSinArbitro() {
        UUID conCausa = UUID.fromString("44444444-4444-4444-8444-444444444444");
        UUID sinCausa = UUID.fromString("55555555-5555-4555-8555-555555555555");
        UUID conCentral = UUID.fromString("66666666-6666-4666-8666-666666666666");
        UUID emparejamiento = UUID.fromString("77777777-7777-4777-8777-777777777777");
        existeFechaParaVerificar(partido(conCausa, emparejamiento, "Halcones", "Toros", null),
                partido(sinCausa, null, "Pumas", "Leones", null),
                partido(conCentral, null, "Lobos", "Osos", UUID.randomUUID()));
        String causa = "El único árbitro validado no puede pitarlo: 1 ya pita a esa hora (RF-039).";
        given(designador.explicarSinCentral(List.of(conCausa, sinCausa))).willReturn(Map.of(conCausa, causa));

        List<Aviso> advertencias = servicio.verificar(FECHA).advertencias();

        verify(designador).explicarSinCentral(List.of(conCausa, sinCausa));
        assertThat(advertencias).containsExactly(
                new Aviso(JORNADA, conCausa, emparejamiento, "RF-023", "Sin árbitro central: Halcones vs Toros. "
                        + causa + " La Comisión de Arbitraje lo designa en F-24."),
                new Aviso(JORNADA, sinCausa, null, "RF-023",
                        "Sin árbitro central: Pumas vs Leones. La Comisión de Arbitraje lo designa en F-24."));
    }

    @Test
    @DisplayName("DEC-220: sin partidos sin árbitro no se llama al puerto")
    void sinPartidosSinArbitro() {
        existeFechaParaVerificar(partido(UUID.randomUUID(), null, "Lobos", "Osos", UUID.randomUUID()));

        assertThat(servicio.verificar(FECHA).advertencias()).isEmpty();
        verifyNoInteractions(designador);
    }

    /** Fecha 3 sin jornadas propias que revisar, con los partidos dados y sin emparejamientos pendientes. */
    private void existeFechaParaVerificar(PartidoDeFecha... partidos) {
        given(fechas.buscar(LIGA, FECHA)).willReturn(Optional.of(fecha(List.of(SABADO, DOMINGO))));
        given(jornadas.listarJornadasDeFecha(FECHA)).willReturn(List.of());
        given(fechas.listarPartidos(FECHA)).willReturn(List.of(partidos));
        given(fechas.listarSinPartido(TEMPORADA, 3)).willReturn(List.of());
    }

    private static PartidoDeFecha partido(UUID id, UUID emparejamiento, String local, String visitante, UUID arbitro) {
        OffsetDateTime inicio = SABADO.atTime(10, 0).atOffset(CREADO_EN.getOffset());
        return new PartidoDeFecha(id, JORNADA, UUID.randomUUID(), emparejamiento, UUID.randomUUID(), local,
                UUID.randomUUID(), visitante, UUID.randomUUID(), "Cancha 1", inicio, inicio.plusHours(2),
                EstadoPartido.PROGRAMADO, null, true, false, arbitro, arbitro == null ? null : "Árbitro",
                arbitro == null ? null : true);
    }
}

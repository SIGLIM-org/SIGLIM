package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import mx.tecnm.morelia.siglim.tesoreria.TarifaRepository.TemporadaTarifas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** Reglas de {@link TarifaService}. Sin Spring ni base de datos. */
class TarifaServiceTest {

    private static final String EDITAR = "tesoreria.tarifa:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TEMPORADA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CATEGORIA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final BigDecimal MONTO = new BigDecimal("350.00");
    private static final TemporadaTarifas ABIERTA = new TemporadaTarifas(TEMPORADA, "Temporada 2026",
            EstadoTemporada.ACTIVA);
    private static final TemporadaTarifas CERRADA = new TemporadaTarifas(TEMPORADA, "Temporada 2025",
            EstadoTemporada.CERRADA);
    private static final TarifaDto TARIFA = new TarifaDto(ID, new TarifaDto.ReferenciaDto(TEMPORADA, "Temporada 2026"),
            new TarifaDto.ReferenciaDto(CATEGORIA, "Libre"), ConceptoTarifa.ARBITRAJE, MONTO, true);

    private final TarifaRepository tarifas = mock(TarifaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final TarifaService servicio = new TarifaService(tarifas, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

    private static CrearTarifaRequest peticion(BigDecimal monto) {
        return new CrearTarifaRequest(TEMPORADA, CATEGORIA, ConceptoTarifa.ARBITRAJE, monto);
    }

    private void altaValida() {
        given(tarifas.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(ABIERTA));
        given(tarifas.categoriaEnTemporada(TEMPORADA, CATEGORIA)).willReturn(true);
        given(tarifas.existe(TEMPORADA, CATEGORIA, ConceptoTarifa.ARBITRAJE)).willReturn(false);
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

    // ── Consulta ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-135: lista las tarifas de la temporada con el permiso de liga")
    void listar() {
        given(tarifas.buscarTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(ABIERTA));
        given(tarifas.listar(LIGA, TEMPORADA)).willReturn(List.of(TARIFA));

        assertThat(servicio.listar(TEMPORADA)).containsExactly(TARIFA);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
    }

    @Test
    @DisplayName("RF-135: listar sin temporada responde 400 y con una temporada inexistente 404")
    void listarSinTemporadaOInexistente() {
        assertThatThrownBy(() -> servicio.listar(null))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        given(tarifas.buscarTemporada(LIGA, TEMPORADA)).willReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.listar(TEMPORADA)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("VAL-015: sin el permiso no se consultan tarifas")
    void listarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.listar(TEMPORADA)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(tarifas);
    }

    // ── Alta ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-135, RF-093: registra la tarifa [DEMO] con monto BigDecimal y la audita")
    void crear() {
        altaValida();
        given(tarifas.insertar(TEMPORADA, CATEGORIA, ConceptoTarifa.ARBITRAJE, MONTO)).willReturn(ID);
        given(tarifas.buscar(LIGA, ID)).willReturn(Optional.of(TARIFA));

        TarifaDto creada = servicio.crear(peticion(MONTO));

        assertThat(creada).isEqualTo(TARIFA);
        assertThat(creada.esDemo()).isTrue();
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-13");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_TARIFA");
        assertThat(evento.entidad()).isEqualTo("tarifas");
        assertThat(evento.entidadId()).isEqualTo(ID.toString());
        assertThat(evento.detalle()).containsEntry("concepto", "ARBITRAJE").containsEntry("monto", MONTO)
                .containsEntry("esDemo", true);
    }

    @Test
    @DisplayName("RF-135: una tarifa de cero es válida")
    void crearEnCero() {
        altaValida();
        given(tarifas.insertar(TEMPORADA, CATEGORIA, ConceptoTarifa.ARBITRAJE, BigDecimal.ZERO)).willReturn(ID);
        given(tarifas.buscar(LIGA, ID)).willReturn(Optional.of(TARIFA));

        servicio.crear(peticion(BigDecimal.ZERO));

        verify(tarifas).insertar(TEMPORADA, CATEGORIA, ConceptoTarifa.ARBITRAJE, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("B-29 criterio 2 · RF-135: un monto negativo se rechaza con 400 y no se guarda")
    void crearMontoNegativo() {
        altaValida();

        assertThatThrownBy(() -> servicio.crear(peticion(new BigDecimal("-0.01"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(tarifas, never()).insertar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("B-29 criterio 1 · RF-135: la misma temporada, categoría y concepto responde 409 DUPLICADO")
    void crearDuplicada() {
        altaValida();
        given(tarifas.existe(TEMPORADA, CATEGORIA, ConceptoTarifa.ARBITRAJE)).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(peticion(MONTO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        verify(tarifas, never()).insertar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("B-29 criterio 3 · RF-003: en una temporada cerrada no se registran tarifas")
    void crearEnTemporadaCerrada() {
        given(tarifas.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(CERRADA));

        assertThatThrownBy(() -> servicio.crear(peticion(MONTO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(tarifas, never()).insertar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-135: temporada inexistente o categoría ajena a la temporada responden 422 REFERENCIA_INVALIDA")
    void crearReferenciasInvalidas() {
        given(tarifas.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.crear(peticion(MONTO))).isInstanceOfSatisfying(ReglaNegocioException.class,
                error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));

        given(tarifas.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(ABIERTA));
        given(tarifas.categoriaEnTemporada(TEMPORADA, CATEGORIA)).willReturn(false);
        assertThatThrownBy(() -> servicio.crear(peticion(MONTO))).isInstanceOfSatisfying(ReglaNegocioException.class,
                error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));
        verify(tarifas, never()).insertar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-015: sin el permiso no se registra la tarifa")
    void crearSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(peticion(MONTO))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(tarifas, auditoria);
    }

    // ── Edición ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-135, RF-093: cambia el monto y audita el anterior y el nuevo")
    void actualizar() {
        BigDecimal nuevo = new BigDecimal("400.00");
        TarifaDto actualizada = new TarifaDto(ID, TARIFA.temporada(), TARIFA.categoria(), ConceptoTarifa.ARBITRAJE,
                nuevo, true);
        given(tarifas.bloquear(LIGA, ID)).willReturn(Optional.of(TARIFA));
        given(tarifas.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(ABIERTA));
        given(tarifas.buscar(LIGA, ID)).willReturn(Optional.of(actualizada));

        assertThat(servicio.actualizar(ID, new ActualizarTarifaRequest(nuevo))).isEqualTo(actualizada);

        verify(tarifas).actualizarMonto(ID, nuevo);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("EDITAR_TARIFA");
        assertThat(evento.detalle()).containsEntry("montoAnterior", MONTO).containsEntry("monto", nuevo);
    }

    @Test
    @DisplayName("RF-135: el mismo monto no cambia nada ni se audita")
    void actualizarSinCambio() {
        given(tarifas.bloquear(LIGA, ID)).willReturn(Optional.of(TARIFA));
        given(tarifas.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(ABIERTA));

        assertThat(servicio.actualizar(ID, new ActualizarTarifaRequest(new BigDecimal("350.0")))).isEqualTo(TARIFA);

        verify(tarifas, never()).actualizarMonto(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("B-29 criterio 3 · RF-003: en una temporada cerrada la tarifa es de solo lectura")
    void actualizarEnTemporadaCerrada() {
        given(tarifas.bloquear(LIGA, ID)).willReturn(Optional.of(TARIFA));
        given(tarifas.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(CERRADA));

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarTarifaRequest(new BigDecimal("1.00"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(tarifas, never()).actualizarMonto(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("B-29 criterio 2 · RF-135: editar con un monto negativo responde 400")
    void actualizarMontoNegativo() {
        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarTarifaRequest(new BigDecimal("-5"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(tarifas, never()).actualizarMonto(any(), any());
    }

    @Test
    @DisplayName("RF-135: editar una tarifa inexistente responde 404")
    void actualizarInexistente() {
        given(tarifas.bloquear(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarTarifaRequest(MONTO)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}

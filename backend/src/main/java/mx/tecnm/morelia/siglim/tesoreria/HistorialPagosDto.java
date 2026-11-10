package mx.tecnm.morelia.siglim.tesoreria;

import java.util.List;

/**
 * Respuesta de {@code GET /api/v1/cobros/{id}/pagos}: los pagos del cobro (a lo más uno, por
 * {@code UNIQUE (cobro_id)}) y sus intentos de pago simulado, ambos en orden cronológico.
 *
 * <p>Refs: H-31.1, RF-138, RF-139, RF-140
 */
public record HistorialPagosDto(List<PagoDto> pagos, List<IntentoPagoDto> intentos) {
}

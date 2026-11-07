package mx.tecnm.morelia.siglim.tesoreria;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code POST /api/v1/intentos-pago/{id}/confirmacion} (RF-138, ADR-0012).
 *
 * <p>La interfaz solo envía el resultado que eligió la persona; la referencia, el monto y el cobro
 * los toma el backend del intento guardado. No se aceptan ni se guardan datos de tarjeta.
 *
 * <p>Refs: H-30.1, RF-138, ADR-0012
 *
 * @param resultado aprobar o rechazar la simulación; obligatorio
 */
public record ConfirmarPagoSimuladoRequest(
        @NotNull(message = "Indica si el pago se aprueba o se rechaza")
        ResultadoPagoSimulado resultado) {
}

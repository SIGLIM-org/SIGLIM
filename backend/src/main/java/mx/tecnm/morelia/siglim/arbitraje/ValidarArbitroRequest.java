package mx.tecnm.morelia.siglim.arbitraje;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/arbitros/{id}/validacion} (RF-113).
 *
 * <p>El rechazo exige observaciones de entre 10 y 500 caracteres; la validación puede llevarlas como
 * nota. El servicio valida la longitud mínima para responder con un mensaje claro.
 *
 * <p>Refs: H-11.1, RF-113, RF-021
 *
 * @param decision      validar o rechazar la propuesta
 * @param observaciones motivo del rechazo o nota de la validación
 */
public record ValidarArbitroRequest(
        @NotNull(message = "Indica si el árbitro se valida o se rechaza")
        DecisionArbitro decision,
        @Size(max = 500, message = "Las observaciones admiten hasta 500 caracteres")
        String observaciones) {
}

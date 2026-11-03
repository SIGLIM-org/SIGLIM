package mx.tecnm.morelia.siglim.temporadas;

/**
 * Cuerpo de {@code POST /api/v1/temporadas/{id}/cierre} (RF-003, RN-021).
 *
 * <p>El motivo es obligatorio y se valida en el servicio para responder con el código RN-021, como
 * en las demás transiciones de estado.
 */
public record CerrarTemporadaRequest(String motivo) {
}

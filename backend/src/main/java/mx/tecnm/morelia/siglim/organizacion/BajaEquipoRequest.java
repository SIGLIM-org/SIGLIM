package mx.tecnm.morelia.siglim.organizacion;

/**
 * Cuerpo de {@code POST /api/v1/equipos/{id}/baja} (RF-009, RN-021).
 *
 * <p>El motivo es obligatorio y se valida en el servicio para responder con el código RN-021, como
 * en las demás transiciones de estado.
 */
public record BajaEquipoRequest(String motivo) {
}

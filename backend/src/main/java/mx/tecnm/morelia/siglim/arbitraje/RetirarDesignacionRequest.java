package mx.tecnm.morelia.siglim.arbitraje;

/**
 * Cuerpo de {@code POST /api/v1/designaciones/{id}/retiro} (DEC-203). El motivo es obligatorio, de 10
 * a 500 caracteres tras recortarlo; lo valida el servicio para responder {@code 400 RN-021}.
 *
 * <p>Refs: H-14.1, RF-023, RN-021, DEC-203
 *
 * @param motivo por qué se retira la designación; queda en {@code motivo_cambio} y en la auditoría
 */
public record RetirarDesignacionRequest(String motivo) {
}

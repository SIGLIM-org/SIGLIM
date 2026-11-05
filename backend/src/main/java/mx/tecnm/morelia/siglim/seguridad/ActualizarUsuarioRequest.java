package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /api/v1/usuarios/{id}} para activar o desactivar una cuenta (RF-094;
 * convenciones de API §2.1).
 *
 * <p>El motivo es obligatorio al desactivar: el servicio lo exige y lo registra en la auditoría
 * (RF-093). Al activar se acepta sin motivo.
 *
 * <p>Refs: H-03.1, RF-094, RF-093
 *
 * @param activo estado final de la cuenta
 * @param motivo razón del cambio; obligatoria al desactivar
 */
public record ActualizarUsuarioRequest(
        @NotNull(message = "Indica si la cuenta queda activa")
        Boolean activo,
        @Size(max = 500, message = "El motivo admite hasta 500 caracteres")
        String motivo) {
}

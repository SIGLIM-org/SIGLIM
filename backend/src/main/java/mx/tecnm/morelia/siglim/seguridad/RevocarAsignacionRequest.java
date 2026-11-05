package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/asignaciones-rol/{id}/revocacion} (F-12, RF-094).
 *
 * <p>La asignación no se borra: queda inactiva con su fecha de revocación y el motivo va a la
 * auditoría (RF-093, RN-005).
 *
 * <p>Refs: H-03.1, RF-094, RF-093
 */
public record RevocarAsignacionRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres")
        String motivo) {
}

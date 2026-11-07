package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/interpretaciones-regla} (RF-007).
 *
 * <p>La interpretación siempre nace {@code PENDIENTE}: el cuerpo no admite estado ni resolución, y
 * tampoco quién la registra, que sale de la sesión. La temporada debe existir en la liga y no estar
 * cerrada, porque el histórico no se modifica (RF-003, RN-005).
 *
 * <p>Refs: B-03.1, RF-003, RF-007, RN-005
 *
 * @param temporadaId temporada a la que aplica la interpretación
 * @param descripcion texto de la interpretación administrativa [PROPUESTA]: el límite de 2000
 *                    caracteres, mientras la Liga no indique lo contrario
 */
public record CrearInterpretacionRequest(
        @NotNull(message = "La temporada es obligatoria")
        UUID temporadaId,
        @NotBlank(message = "La descripción de la interpretación es obligatoria")
        @Size(max = 2000, message = "La descripción de la interpretación admite hasta 2000 caracteres")
        String descripcion) {
}

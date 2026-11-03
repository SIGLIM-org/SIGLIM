package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/clubes} (RF-008). La liga sale de la configuración, no del cuerpo,
 * y el club nace {@code ACTIVO}.
 */
public record CrearClubRequest(
        @NotBlank(message = "El nombre del club es obligatorio")
        @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
        String nombre) {
}

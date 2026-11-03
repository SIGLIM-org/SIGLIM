package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/equipos} (RF-009). El equipo nace {@code ACTIVO} en el grupo
 * indicado; la temporada es la del grupo (RN-007).
 */
public record CrearEquipoRequest(
        @NotNull(message = "El club es obligatorio")
        UUID clubId,
        @NotNull(message = "El grupo es obligatorio")
        UUID grupoId,
        @NotBlank(message = "El nombre del equipo es obligatorio")
        @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
        String nombre) {
}

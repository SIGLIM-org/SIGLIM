package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/equipos/{id}}: un campo ausente conserva su valor (RF-009).
 *
 * <p><b>[PROPUESTA]</b> Solo se editan el nombre y el grupo. El club no cambia aquí (la venta del
 * equipo es B-05) y el estado se cambia con la acción de baja. El cambio de grupo solo procede con la
 * temporada en configuración y dentro de la misma temporada (RN-007).
 */
public record ActualizarEquipoRequest(
        @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
        String nombre,
        UUID grupoId) {
}

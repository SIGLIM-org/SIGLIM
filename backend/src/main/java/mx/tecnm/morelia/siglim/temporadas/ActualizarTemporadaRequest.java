package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Cuerpo de {@code PATCH /api/v1/temporadas/{id}}: un campo ausente conserva su valor (RF-001).
 *
 * <p>La temporada cerrada es de solo lectura: el servicio la rechaza antes de aplicar el cambio
 * (RF-003). El estado no se cambia aquí, sino con las acciones de activación y cierre.
 */
public record ActualizarTemporadaRequest(
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres")
        String nombre,
        LocalDate fechaInicio,
        LocalDate fechaFin) {
}

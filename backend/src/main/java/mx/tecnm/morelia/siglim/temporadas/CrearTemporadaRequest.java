package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/v1/temporadas} (RF-001).
 *
 * @param fechaInicio primer día de la temporada
 * @param fechaFin    último día; opcional mientras se configura
 */
public record CrearTemporadaRequest(
        @NotBlank(message = "El nombre de la temporada es obligatorio")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres")
        String nombre,
        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,
        LocalDate fechaFin) {
}

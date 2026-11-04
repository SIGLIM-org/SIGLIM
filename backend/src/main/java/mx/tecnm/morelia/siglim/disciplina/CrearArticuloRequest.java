package mx.tecnm.morelia.siglim.disciplina;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Cuerpo de {@code POST /api/v1/articulos} (RF-129). Los valores numéricos son opcionales y solo
 * se registran para el tipo de sanción correspondiente; {@code efecto}, {@code aplicacionAutomatica}
 * y {@code otorgaDefaultRestantes} nacen con su valor del diccionario si no se envían.
 */
public record CrearArticuloRequest(
        @NotBlank(message = "El número es obligatorio")
        @Size(max = 20, message = "El número admite hasta 20 caracteres")
        String numero,
        @NotBlank(message = "La descripción es obligatoria")
        String descripcion,
        @NotNull(message = "Elige el tipo de sanción")
        TipoSancion tipoSancion,
        @NotNull(message = "Elige a quién aplica la sanción")
        SujetoSancion sujeto,
        EfectoSancion efecto,
        Boolean aplicacionAutomatica,
        @Max(value = 32767, message = "Los partidos de suspensión admiten hasta 32767")
        Integer partidosSuspension,
        @Max(value = 32767, message = "Los puntos admiten hasta 32767")
        Integer puntos,
        @DecimalMin(value = "0.01", message = "El monto de la multa debe ser mayor a cero")
        @DecimalMax(value = "9999999999.99", message = "El monto de la multa admite hasta 9999999999.99")
        BigDecimal montoMulta,
        Boolean otorgaDefaultRestantes) {
}

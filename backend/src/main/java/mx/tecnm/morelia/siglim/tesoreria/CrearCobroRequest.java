package mx.tecnm.morelia.siglim.tesoreria;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/cobros}: crea un cobro {@code MANUAL} a un club o a un equipo
 * (RF-137). El cobro nace {@code PENDIENTE}.
 *
 * <p>{@code periodoInicio} es opcional; sin él se usa la fecha del día en la zona de negocio.
 * {@code fechaLimite} es el último día para pagar. Un equipo, si se indica, debe pertenecer al club.
 *
 * <p>Refs: H-29.1, RF-137
 *
 * @param monto importe en pesos mexicanos, mayor que cero y con hasta dos decimales
 */
public record CrearCobroRequest(
        @NotNull(message = "El club es obligatorio")
        UUID clubId,
        UUID equipoId,
        @NotBlank(message = "El concepto es obligatorio")
        @Size(max = 200, message = "El concepto admite hasta 200 caracteres")
        String concepto,
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "El monto admite hasta dos decimales")
        BigDecimal monto,
        LocalDate periodoInicio,
        @NotNull(message = "La fecha límite es obligatoria")
        LocalDate fechaLimite) {
}

package mx.tecnm.morelia.siglim.tesoreria;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Cuerpo de {@code PATCH /api/v1/tarifas/{id}}. Solo cambia el monto: la temporada, la categoría y el
 * concepto identifican la tarifa y no se editan (RF-135).
 *
 * <p>Refs: B-29.1, RF-135
 *
 * @param monto importe en pesos mexicanos, mayor o igual a cero, con hasta dos decimales
 */
public record ActualizarTarifaRequest(
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.00", message = "El monto no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El monto admite hasta dos decimales")
        BigDecimal monto) {
}

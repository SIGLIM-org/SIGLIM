package mx.tecnm.morelia.siglim.tesoreria;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/tarifas}: tarifa de un concepto para una categoría de la temporada
 * (RF-135). La combinación temporada, categoría y concepto es única.
 *
 * <p>El monto admite cero (columna {@code ck_tarifas_monto}: {@code monto >= 0}) y la misma escala que
 * {@code numeric(12,2)}: hasta diez dígitos enteros y dos decimales.
 *
 * <p>Refs: B-29.1, RF-135
 *
 * @param monto importe en pesos mexicanos, mayor o igual a cero; [DEMO] mientras siga PEN-042
 */
public record CrearTarifaRequest(
        @NotNull(message = "La temporada es obligatoria")
        UUID temporadaId,
        @NotNull(message = "La categoría es obligatoria")
        UUID categoriaId,
        @NotNull(message = "El concepto es obligatorio")
        ConceptoTarifa concepto,
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.00", message = "El monto no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El monto admite hasta dos decimales")
        BigDecimal monto) {
}

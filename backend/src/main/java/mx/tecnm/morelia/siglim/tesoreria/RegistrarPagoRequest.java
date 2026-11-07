package mx.tecnm.morelia.siglim.tesoreria;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Campos de texto de {@code POST /api/v1/cobros/{id}/pagos} ({@code multipart/form-data}); el
 * comprobante llega aparte en la parte {@code archivo} (RF-139).
 *
 * <p>El monto usa la misma regla que {@link CrearCobroRequest} ({@code numeric(12,2)}). Que sea igual
 * al del cobro, que el medio no sea {@code EN_LINEA_SIMULADO} y que la fecha no sea futura los valida
 * el servicio, porque dependen del cobro o del reloj.
 *
 * <p>Refs: H-31.1, RF-139
 *
 * @param medio      {@code EFECTIVO}, {@code TRANSFERENCIA} u {@code OTRO}
 * @param monto      importe pagado en pesos mexicanos; debe ser el total del cobro
 * @param fechaPago  día en que se recibió el pago; sin él, el momento del registro
 * @param referencia folio o referencia bancaria; opcional, hasta 60 caracteres
 */
public record RegistrarPagoRequest(
        @NotNull(message = "El medio de pago es obligatorio")
        MedioPago medio,
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "El monto admite hasta dos decimales")
        BigDecimal monto,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fechaPago,
        @Size(max = 60, message = "La referencia admite hasta 60 caracteres")
        String referencia) {
}

package mx.tecnm.morelia.siglim.tesoreria;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Intento de pago en línea simulado de un cobro (RF-138, ADR-0012), solo lectura.
 *
 * <p>Refs: H-30.1, H-31.1, RF-138, RF-140, ADR-0012
 *
 * @param referencia referencia ficticia {@code SIM-AAAAMMDD-XXXXXX}
 * @param estado     valor de {@code siglim.estado_intento_pago}: {@code PENDIENTE}, {@code APROBADO}
 *                   o {@code RECHAZADO}
 * @param resueltoEn se omite mientras el intento está pendiente
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentoPagoDto(UUID id, String referencia, String estado, OffsetDateTime iniciadoEn,
                             OffsetDateTime resueltoEn) {
}

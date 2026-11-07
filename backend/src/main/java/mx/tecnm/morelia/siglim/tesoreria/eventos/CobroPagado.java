package mx.tecnm.morelia.siglim.tesoreria.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.tesoreria.MedioPago;

/**
 * Evento de dominio que publica la confirmación de un pago simulado aprobado (ADR-0012).
 *
 * <p>Se publica dentro de la misma transacción que crea el pago (RNF-004): los oyentes síncronos,
 * como los avisos de {@code notificaciones} (ADR-0010), ven el cobro ya pagado y, si fallan, se
 * revierte la confirmación completa.
 *
 * <p>Refs: H-30.1, RF-138, RNF-004, ADR-0006, ADR-0012
 *
 * @param ligaId    liga del cobro
 * @param cobroId   cobro que quedó pagado
 * @param clubId    club que pagó, para dirigir los avisos
 * @param pagoId    pago creado con medio {@code EN_LINEA_SIMULADO}
 * @param intentoId intento que originó el pago
 * @param monto     importe pagado en pesos mexicanos
 * @param medio     siempre {@link MedioPago#EN_LINEA_SIMULADO} en esta entrega
 * @param pagadoEn  momento de la confirmación
 */
public record CobroPagado(UUID ligaId, UUID cobroId, UUID clubId, UUID pagoId, UUID intentoId,
                          BigDecimal monto, MedioPago medio, OffsetDateTime pagadoEn) {
}

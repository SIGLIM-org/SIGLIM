package mx.tecnm.morelia.siglim.tesoreria.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import mx.tecnm.morelia.siglim.tesoreria.TipoCobro;

/**
 * Evento de dominio que publica la tarea de vencimiento por cada cobro que pasó de
 * {@code PENDIENTE} a {@code VENCIDO} (RF-141, ADR-0009).
 *
 * <p>Se publica dentro de la misma transacción que vence el cobro: un oyente síncrono ve el cobro ya
 * vencido y, si falla, se revierte toda la ejecución (RNF-015). Aún no tiene oyentes: el aviso al club
 * es H-25 y la baja por falta de pago es B-22.
 *
 * <p>Refs: B-31.1, RF-141, ADR-0006, ADR-0009
 *
 * @param ligaId      liga del cobro
 * @param cobroId     cobro que quedó vencido
 * @param clubId      club deudor, para dirigir los avisos
 * @param equipoId    equipo del cobro; nulo en los cobros al club
 * @param tipo        origen del cobro
 * @param monto       importe adeudado en pesos mexicanos
 * @param fechaLimite último día en que se podía pagar sin vencer
 */
public record CobroVencido(UUID ligaId, UUID cobroId, UUID clubId, UUID equipoId, TipoCobro tipo,
                           BigDecimal monto, LocalDate fechaLimite) {
}

package mx.tecnm.morelia.siglim.tesoreria;

/**
 * Estado de un cobro (ERS §4.3.5): {@code PENDIENTE → PAGADO}, {@code PENDIENTE → RECHAZADO},
 * {@code RECHAZADO → PENDIENTE}, {@code PENDIENTE → VENCIDO} y {@code VENCIDO → PAGADO}.
 *
 * <p>Las transiciones las valida el servicio y, como última garantía, el trigger
 * {@code tg_cobros_estado} con {@code VAL-028}.
 *
 * <p>Refs: H-29.1, RF-137, RF-140, VAL-028
 */
public enum EstadoCobro {
    PENDIENTE,
    PAGADO,
    RECHAZADO,
    VENCIDO
}

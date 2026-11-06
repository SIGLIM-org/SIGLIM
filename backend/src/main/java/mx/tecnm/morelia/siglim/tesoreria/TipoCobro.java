package mx.tecnm.morelia.siglim.tesoreria;

/**
 * Origen de un cobro: arbitraje o cancha de un partido, multa de una sanción o manual.
 *
 * <p>H-29.1 solo crea cobros {@code MANUAL}; los automáticos son RF-136 y RF-133 (backlog).
 *
 * <p>Refs: H-29.1, RF-137
 */
public enum TipoCobro {
    ARBITRAJE,
    CANCHA,
    MULTA,
    MANUAL
}

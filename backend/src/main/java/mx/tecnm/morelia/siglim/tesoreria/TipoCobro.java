package mx.tecnm.morelia.siglim.tesoreria;

/**
 * Origen de un cobro: arbitraje o cancha de un partido, multa de una sanción o manual.
 *
 * <p>H-29.1 crea cobros {@code MANUAL}; B-30 genera {@code ARBITRAJE} y {@code CANCHA} al publicar la
 * jornada (RF-136). {@code MULTA} es RF-133 (backlog).
 *
 * <p>Refs: H-29.1, B-30, RF-136, RF-137
 */
public enum TipoCobro {
    ARBITRAJE,
    CANCHA,
    MULTA,
    MANUAL
}

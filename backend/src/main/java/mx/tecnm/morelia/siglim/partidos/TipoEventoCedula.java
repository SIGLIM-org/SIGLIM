package mx.tecnm.morelia.siglim.partidos;

/**
 * Tipos de evento de la cédula, los mismos del enum {@code siglim.tipo_evento_cedula} (RF-025, DEC-19).
 *
 * <p>Refs: H-16.2, RF-025
 */
public enum TipoEventoCedula {
    GOL,
    AUTOGOL,
    TARJETA_AMARILLA,
    TARJETA_ROJA,
    SUSTITUCION
}

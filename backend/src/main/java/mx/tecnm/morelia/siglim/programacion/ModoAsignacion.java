package mx.tecnm.morelia.siglim.programacion;

/**
 * Cómo se asigna una fecha (DEC-181).
 *
 * <p>Refs: H-33.2, DEC-181
 */
public enum ModoAsignacion {
    /** Solo los emparejamientos sin partido; lo ya asignado o ajustado a mano no se toca. */
    PENDIENTES,
    /** Cancela con motivo los partidos de la fecha y vuelve a asignar todo. */
    REGENERAR
}

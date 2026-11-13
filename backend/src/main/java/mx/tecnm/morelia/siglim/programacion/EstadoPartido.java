package mx.tecnm.morelia.siglim.programacion;

/**
 * Estado del partido, igual que el enum {@code siglim.estado_partido} (DEC-23, ERS §4.3.1).
 *
 * <p>La programación manual solo crea partidos {@link #PROGRAMADO}; los demás estados los aplican
 * las transiciones de M-08 y el trigger {@code tg_partidos_estado}.
 *
 * <p>Refs: H-12.1, RF-033, RF-043, DEC-23
 */
public enum EstadoPartido {

    PROGRAMADO,
    EN_CURSO,
    FINALIZADO,
    SUSPENDIDO,
    REPROGRAMADO,
    CANCELADO
}

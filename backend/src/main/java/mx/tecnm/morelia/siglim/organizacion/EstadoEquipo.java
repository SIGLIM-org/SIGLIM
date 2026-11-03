package mx.tecnm.morelia.siglim.organizacion;

/**
 * Estado de un equipo, igual que el enum {@code siglim.estado_equipo}.
 *
 * <p>La única transición es {@code ACTIVO → BAJA}, con motivo y fecha; la fila nunca se borra
 * (RN-005).
 *
 * <p>Refs: H-06.1, RF-009, RN-005
 */
public enum EstadoEquipo {
    ACTIVO,
    BAJA
}

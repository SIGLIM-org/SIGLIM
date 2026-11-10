package mx.tecnm.morelia.siglim.canchas;

/**
 * Enum {@code siglim.tipo_disponibilidad}. {@code DISPONIBLE} es informativo (RF-029);
 * {@code BLOQUEO} (RF-030) y {@code MANTENIMIENTO} restringen la cancha. Desde B-12.1 el
 * mantenimiento se registra como un bloqueo tipificado (RF-031).
 */
public enum TipoDisponibilidad {
    DISPONIBLE,
    BLOQUEO,
    MANTENIMIENTO
}

package mx.tecnm.morelia.siglim.registros;

/**
 * Estado de un registro de jugador, igual que el enum {@code siglim.estado_registro} (ERS §4.3).
 *
 * <p>Un registro «activo» para la unicidad por CURP es {@code PENDIENTE_VALIDACION}, {@code ACTIVO}
 * o {@code SUSPENDIDO}, como el índice {@code ux_registros_activo} (RN-037).
 *
 * <p>Refs: H-09.2, RF-017, RN-037
 */
public enum EstadoRegistro {
    PENDIENTE_VALIDACION,
    ACTIVO,
    SUSPENDIDO,
    BAJA,
    RECHAZADO;

    /** Ocupa el lugar de la persona en la temporada (VAL-017, {@code ux_registros_activo}). */
    boolean ocupaTemporada() {
        return this == PENDIENTE_VALIDACION || this == ACTIVO || this == SUSPENDIDO;
    }
}

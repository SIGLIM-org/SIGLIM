package mx.tecnm.morelia.siglim.registros;

/**
 * Dictamen de la liga sobre un registro {@code PENDIENTE_VALIDACION} (RF-109, DEC-97).
 *
 * <ul>
 *   <li>{@link #ACTIVO}: exige RF-013, VAL-001 y VAL-025 (DEC-99, DEC-100).</li>
 *   <li>{@link #RECHAZADO}: exige observaciones; es terminal y no cuenta para la unicidad (DEC-100).</li>
 * </ul>
 *
 * <p>Refs: H-10.2, RF-017, RF-109, DEC-97 a DEC-100
 */
public enum DecisionRegistro {
    ACTIVO,
    RECHAZADO;

    /** Estado del registro al que lleva el dictamen. */
    EstadoRegistro estado() {
        return EstadoRegistro.valueOf(name());
    }
}

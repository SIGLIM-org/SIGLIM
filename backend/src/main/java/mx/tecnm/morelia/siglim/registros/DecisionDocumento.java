package mx.tecnm.morelia.siglim.registros;

/**
 * Dictamen de la liga sobre un documento {@code PENDIENTE} (RF-109, DEC-96).
 *
 * <ul>
 *   <li>{@link #APROBADO}: el documento cuenta para {@code listoParaValidar}.</li>
 *   <li>{@link #RECHAZADO}: exige observaciones; la Directiva lo corrige con un reemplazo.</li>
 * </ul>
 *
 * <p>Refs: H-10.1, RF-109, DEC-96
 */
public enum DecisionDocumento {
    APROBADO,
    RECHAZADO;

    /** Estado de validación al que lleva el dictamen. */
    EstadoValidacion estado() {
        return EstadoValidacion.valueOf(name());
    }
}

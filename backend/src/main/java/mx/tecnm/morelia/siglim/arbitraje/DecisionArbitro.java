package mx.tecnm.morelia.siglim.arbitraje;

/**
 * Dictamen de la Comisión de Arbitraje sobre un árbitro {@code PROPUESTO} (RF-113).
 *
 * <ul>
 *   <li>{@link #VALIDADO}: el árbitro entra al catálogo que se puede designar.</li>
 *   <li>{@link #RECHAZADO}: se rechaza la propuesta; exige observaciones.</li>
 * </ul>
 *
 * <p>Refs: H-11.1, RF-113, RF-021
 */
public enum DecisionArbitro {

    VALIDADO,
    RECHAZADO;

    /** Estado al que lleva el dictamen. */
    public EstadoArbitro estado() {
        return EstadoArbitro.valueOf(name());
    }
}

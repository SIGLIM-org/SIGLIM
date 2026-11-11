package mx.tecnm.morelia.siglim.organizacion;

/**
 * Dictamen del Presidente sobre un tarjetón en {@code SOLICITADA} (RF-101, CU-018).
 *
 * <ul>
 *   <li>{@link #APROBADA}: afilia al club en la temporada.</li>
 *   <li>{@link #RECHAZADA}: rechaza el tarjetón; exige observaciones.</li>
 * </ul>
 *
 * <p>Refs: H-08.1, RF-101, CU-018, DEC-09
 */
public enum DecisionAfiliacion {

    APROBADA,
    RECHAZADA;

    /** Estado al que lleva el dictamen. */
    public EstadoAfiliacion estado() {
        return EstadoAfiliacion.valueOf(name());
    }
}

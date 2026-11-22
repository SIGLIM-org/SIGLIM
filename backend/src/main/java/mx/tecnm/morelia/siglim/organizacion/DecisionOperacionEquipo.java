package mx.tecnm.morelia.siglim.organizacion;

/**
 * Dictamen del Presidente sobre una operación de equipo en {@code SOLICITADA} (RF-103, RF-104,
 * RN-040).
 *
 * <ul>
 *   <li>{@link #AUTORIZADA}: la operación se aplicará al cerrar la temporada.</li>
 *   <li>{@link #RECHAZADA}: no surte efecto; exige observaciones.</li>
 * </ul>
 *
 * <p>Refs: B-05.1, RF-103, RF-104, RN-040
 */
public enum DecisionOperacionEquipo {

    AUTORIZADA,
    RECHAZADA;

    /** Estado al que lleva el dictamen. */
    public EstadoOperacionEquipo estado() {
        return EstadoOperacionEquipo.valueOf(name());
    }
}

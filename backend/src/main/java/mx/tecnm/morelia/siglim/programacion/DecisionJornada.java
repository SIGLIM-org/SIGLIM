package mx.tecnm.morelia.siglim.programacion;

/**
 * Dictamen del Presidente sobre una jornada en revisión (RF-121, DEC-21, ERS §4.3.6).
 *
 * <ul>
 *   <li>{@link #PUBLICADO}: aprueba la jornada y la publica en el portal.</li>
 *   <li>{@link #BORRADOR}: la devuelve al Programador con observaciones.</li>
 * </ul>
 *
 * <p>Refs: H-13.1, RF-121, CU-007
 */
public enum DecisionJornada {

    PUBLICADO,
    BORRADOR;

    /** Estado de publicación al que lleva el dictamen. */
    public EstadoJornada estado() {
        return EstadoJornada.valueOf(name());
    }
}

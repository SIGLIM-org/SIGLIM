package mx.tecnm.morelia.siglim.publicacion;

/**
 * Dictamen del Presidente sobre un comunicado en revisión (RF-090, ERS §4.3.6).
 *
 * <ul>
 *   <li>{@link #PUBLICADO}: aprueba el comunicado y lo publica en el portal.</li>
 *   <li>{@link #BORRADOR}: lo devuelve a la Secretaría con observaciones.</li>
 * </ul>
 *
 * <p>Refs: B-27.1, RF-090, CU-027
 */
public enum DecisionComunicado {

    PUBLICADO,
    BORRADOR;

    /** Estado de publicación al que lleva el dictamen. */
    public EstadoPublicacion estado() {
        return EstadoPublicacion.valueOf(name());
    }
}

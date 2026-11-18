package mx.tecnm.morelia.siglim.canchas;

/**
 * Dictamen del Presidente sobre un trámite pendiente (RF-116, RF-117). Cada formato acepta solo
 * sus dos decisiones: el Formato 1 {@link #APROBADA} o {@link #NO_APROBADA}; el Formato 2
 * {@link #ACEPTADA} o {@link #RECHAZADA}.
 *
 * <p>Refs: B-14, RF-116, RF-117, DEC-35
 */
public enum DecisionSolicitudCancha {

    APROBADA,
    NO_APROBADA,
    ACEPTADA,
    RECHAZADA;

    /** Estado al que lleva el dictamen. */
    public EstadoSolicitudCancha estado() {
        return EstadoSolicitudCancha.valueOf(name());
    }

    /** {@code true} si aprueba o acepta el trámite. */
    public boolean favorable() {
        return this == APROBADA || this == ACEPTADA;
    }

    /** {@code true} si la decisión pertenece al formato del tipo de trámite. */
    public boolean admitidaPara(TipoSolicitudCancha tipo) {
        return tipo.esOferta() ? (this == ACEPTADA || this == RECHAZADA)
                : (this == APROBADA || this == NO_APROBADA);
    }
}

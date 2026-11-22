package mx.tecnm.morelia.siglim.canchas;

/**
 * Tipo de trámite de cancha, igual que el enum {@code siglim.tipo_solicitud_cancha} (DEC-35, R8).
 *
 * <ul>
 *   <li>{@link #PRESTAMO} y {@link #COMODATO}: Formato 1, estados {@code PENDIENTE → APROBADA} o
 *       {@code NO_APROBADA} (RF-117).</li>
 *   <li>{@link #OFERTA}: Formato 2, estados {@code PENDIENTE_NEGOCIACION → ACEPTADA} o
 *       {@code RECHAZADA} (RF-116).</li>
 * </ul>
 *
 * <p>Refs: B-14, RF-116, RF-117, DEC-35
 */
public enum TipoSolicitudCancha {

    PRESTAMO,
    COMODATO,
    OFERTA;

    /** {@code true} para el Formato 2 (oferta); {@code false} para el Formato 1. */
    public boolean esOferta() {
        return this == OFERTA;
    }

    /** Número del formato del cliente (R8): 1 préstamo o comodato, 2 oferta. */
    public int formato() {
        return esOferta() ? 2 : 1;
    }

    /** Estado con el que nace el trámite. */
    public EstadoSolicitudCancha estadoInicial() {
        return esOferta() ? EstadoSolicitudCancha.PENDIENTE_NEGOCIACION : EstadoSolicitudCancha.PENDIENTE;
    }

    /** Requisito que rige el trámite; se usa como código de los errores de su máquina de estados. */
    public String requisito() {
        return esOferta() ? "RF-116" : "RF-117";
    }

    /** Texto para los mensajes a la persona, en minúsculas. */
    public String texto() {
        return switch (this) {
            case PRESTAMO -> "préstamo";
            case COMODATO -> "comodato";
            case OFERTA -> "oferta";
        };
    }
}

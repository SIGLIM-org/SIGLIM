package mx.tecnm.morelia.siglim.canchas;

/**
 * Estado de un trámite de cancha, igual que el enum {@code siglim.estado_solicitud_cancha}
 * (ERS §4.3, DEC-35).
 *
 * <p>Formato 1: {@code PENDIENTE → APROBADA | NO_APROBADA}. Formato 2:
 * {@code PENDIENTE_NEGOCIACION → ACEPTADA | RECHAZADA}. La restricción
 * {@code ck_solicitudes_cancha_estado} impide mezclar estados de un formato con el tipo del otro.
 *
 * <p>Refs: B-14, RF-116, RF-117
 */
public enum EstadoSolicitudCancha {

    PENDIENTE("pendiente"),
    APROBADA("aprobada"),
    NO_APROBADA("no aprobada"),
    PENDIENTE_NEGOCIACION("pendiente de negociación"),
    ACEPTADA("aceptada"),
    RECHAZADA("rechazada");

    private final String texto;

    EstadoSolicitudCancha(String texto) {
        this.texto = texto;
    }

    /** {@code true} mientras el trámite espera el dictamen del Presidente. */
    public boolean pendiente() {
        return this == PENDIENTE || this == PENDIENTE_NEGOCIACION;
    }

    /** Texto para los mensajes a la persona, en minúsculas. */
    public String texto() {
        return texto;
    }
}

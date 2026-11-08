package mx.tecnm.morelia.siglim.seguridad;

/**
 * Dictamen que el Presidente da a una solicitud de cambio de permisos incompatibles (RF-096).
 *
 * <p>No incluye {@code SOLICITADO}: una resolución siempre aplica o descarta el cambio.
 *
 * <p>Refs: H-20.1, RF-096, VAL-023
 */
public enum DecisionSolicitudPermisos {

    APROBADO,
    RECHAZADO;

    /** Estado que queda en la solicitud al aplicar este dictamen. */
    public EstadoSolicitudPermisos aEstado() {
        return this == APROBADO ? EstadoSolicitudPermisos.APROBADO : EstadoSolicitudPermisos.RECHAZADO;
    }
}

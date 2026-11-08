package mx.tecnm.morelia.siglim.seguridad;

/**
 * Estado de una solicitud de cambio de permisos con control dual (P-13, VAL-023).
 *
 * <p>Coincide con el enum {@code siglim.estado_solicitud_permisos}. Una solicitud nace
 * {@code SOLICITADO} y solo se resuelve una vez, como {@code APROBADO} o {@code RECHAZADO}.
 *
 * <p>Refs: H-20.1, RF-096, VAL-023
 */
public enum EstadoSolicitudPermisos {

    SOLICITADO,
    APROBADO,
    RECHAZADO;

    /** Verdadero cuando la solicitud ya no admite otra resolución. */
    public boolean resuelta() {
        return this != SOLICITADO;
    }
}

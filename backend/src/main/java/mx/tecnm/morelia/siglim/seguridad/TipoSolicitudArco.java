package mx.tecnm.morelia.siglim.seguridad;

/**
 * Derecho que ejerce el titular en una solicitud ARCO. Coincide con el enum
 * {@code siglim.tipo_solicitud_arco} de la base de datos.
 *
 * <p>Refs: B-01, RF-098, DEC-40
 */
public enum TipoSolicitudArco {
    ACCESO,
    RECTIFICACION,
    CANCELACION,
    OPOSICION
}

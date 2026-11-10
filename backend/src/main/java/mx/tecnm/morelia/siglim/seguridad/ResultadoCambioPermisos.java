package mx.tecnm.morelia.siglim.seguridad;

/**
 * Resultado de {@code PATCH /roles/{id}/permisos}: el cambio se aplicó o quedó pendiente del
 * Presidente (RF-095, RF-096, VAL-023).
 *
 * <p>El controlador responde {@code 200} con {@link #rol()} cuando se aplicó y {@code 202} con
 * {@link #solicitud()} cuando un par incompatible lo dejó pendiente (convenciones de API §2).
 *
 * <p>Refs: H-20.1, RF-095, RF-096, VAL-023
 */
record ResultadoCambioPermisos(RolDetalleDto rol, SolicitudCambioPermisosDto solicitud) {

    static ResultadoCambioPermisos aplicado(RolDetalleDto rol) {
        return new ResultadoCambioPermisos(rol, null);
    }

    static ResultadoCambioPermisos pendiente(SolicitudCambioPermisosDto solicitud) {
        return new ResultadoCambioPermisos(null, solicitud);
    }

    boolean pendiente() {
        return solicitud != null;
    }
}

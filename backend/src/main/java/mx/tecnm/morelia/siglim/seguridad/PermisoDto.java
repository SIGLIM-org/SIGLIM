package mx.tecnm.morelia.siglim.seguridad;

/**
 * Permiso del catálogo {@code modulo.recurso:ACCION} que la matriz de F-13 muestra agrupado por
 * módulo (RF-095, ADR-0007).
 *
 * <p>El catálogo de permisos se expone por su {@code codigo}, nunca por el identificador numérico
 * de la base (convenciones de API §1).
 *
 * <p>Refs: H-20.1, RF-095, ADR-0007
 */
public record PermisoDto(String codigo, String modulo, String descripcion) {
}

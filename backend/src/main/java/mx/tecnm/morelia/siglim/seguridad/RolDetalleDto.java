package mx.tecnm.morelia.siglim.seguridad;

import java.util.List;
import java.util.UUID;

/**
 * Detalle de un rol con los permisos que tiene asignados (RF-095, F-13).
 *
 * <p>{@code permisos} contiene solo los permisos efectivos del rol; el catálogo completo y los
 * pares incompatibles vienen de {@code GET /permisos}.
 *
 * <p>Refs: H-20.1, RF-095
 */
public record RolDetalleDto(
        UUID id,
        String codigo,
        String nombre,
        String descripcion,
        Ambito ambitoPredeterminado,
        boolean asignable,
        List<PermisoDto> permisos) {
}

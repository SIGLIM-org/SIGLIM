package mx.tecnm.morelia.siglim.seguridad;

import java.util.List;

/**
 * Catálogo completo de permisos y de pares incompatibles que necesita la matriz de F-13 (RF-095,
 * RF-096).
 *
 * <p>Es un catálogo pequeño y estable: se responde como arreglo sin paginación, igual que las
 * categorías y los tipos de incidencia (convenciones de API §4).
 *
 * <p>Refs: H-20.1, RF-095, RF-096
 */
public record CatalogoPermisosDto(List<PermisoDto> permisos, List<ParIncompatibleDto> paresIncompatibles) {
}

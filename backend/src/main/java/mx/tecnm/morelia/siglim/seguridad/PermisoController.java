package mx.tecnm.morelia.siglim.seguridad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de permisos y pares incompatibles para la matriz de F-13 (convenciones de API §6.1,
 * RF-095, RF-096).
 *
 * <p>Es un catálogo pequeño y estable: responde un arreglo sin paginación.
 *
 * <p>Refs: H-20.1, RF-095, RF-096
 */
@RestController
@RequestMapping("/api/v1/permisos")
class PermisoController {

    private final RolService servicio;

    PermisoController(RolService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    CatalogoPermisosDto listar() {
        return servicio.catalogoPermisos();
    }
}

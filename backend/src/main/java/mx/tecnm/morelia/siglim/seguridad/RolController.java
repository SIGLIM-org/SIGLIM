package mx.tecnm.morelia.siglim.seguridad;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de roles de la liga (convenciones de API §6.1, RF-094 y RF-095).
 *
 * <p>Lo usa la pantalla de usuarios para llenar el selector de rol de la invitación y de una nueva
 * asignación. La edición de permisos de un rol es H-20.
 *
 * <p>Refs: H-03.1, RF-094, RF-095
 */
@RestController
@RequestMapping("/api/v1/roles")
class RolController {

    private final RolService servicio;

    RolController(RolService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<RolDto> listar() {
        return servicio.listar();
    }
}

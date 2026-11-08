package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de roles y edición de su matriz de permisos (convenciones de API §6.1, RF-094 a RF-096).
 *
 * <p>Sin reglas de negocio: el detalle arma la pantalla F-13 y el {@code PATCH} responde {@code 200}
 * con el rol ya actualizado cuando el cambio se aplica, o {@code 202} con la solicitud cuando un par
 * incompatible lo deja pendiente del Presidente (VAL-023).
 *
 * <p>Refs: H-03.1, H-20.1, RF-094, RF-095, RF-096
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

    @GetMapping("/{id}")
    RolDetalleDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PatchMapping("/{id}/permisos")
    ResponseEntity<Object> actualizarPermisos(@PathVariable UUID id,
            @Valid @RequestBody ActualizarPermisosRolRequest peticion) {
        ResultadoCambioPermisos resultado = servicio.actualizarPermisos(id, peticion);
        return resultado.pendiente()
                ? ResponseEntity.accepted().body(resultado.solicitud())
                : ResponseEntity.ok(resultado.rol());
    }
}

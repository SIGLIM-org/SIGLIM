package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acciones sobre una asignación de rol ya creada (convenciones de API §2.1 y §6.1, RF-094).
 *
 * <p>Revocar no borra: la asignación queda inactiva con su motivo en la auditoría (RN-005,
 * RF-093).
 *
 * <p>Refs: H-03.1, RF-094
 */
@RestController
@RequestMapping("/api/v1/asignaciones-rol")
class AsignacionRolController {

    private final UsuarioService servicio;

    AsignacionRolController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/{id}/revocacion")
    ResponseEntity<UsuarioDto.AsignacionDto> revocar(@PathVariable UUID id,
            @Valid @RequestBody RevocarAsignacionRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.revocar(id, peticion));
    }
}

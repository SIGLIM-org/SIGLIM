package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.Valid;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control dual de permisos: solicitudes pendientes y su dictamen por el Presidente
 * (convenciones de API §6.1, RF-096, VAL-023).
 *
 * <p>Sin reglas de negocio. La resolución es un {@code POST} a un sub-recurso, no un {@code PATCH};
 * una solicitud resuelta no se reabre y el histórico no se borra (RF-003, RF-096). Las respuestas
 * nombran a quien solicita y a quien resuelve, así que llevan {@code Cache-Control: no-store}
 * (RNF-012).
 *
 * <p>Refs: H-20.1, RF-096, VAL-023
 */
@RestController
@RequestMapping("/api/v1/solicitudes-cambio-permisos")
class SolicitudCambioPermisosController {

    private final RolService servicio;

    SolicitudCambioPermisosController(RolService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    ResponseEntity<Pagina<SolicitudCambioPermisosDto>> listar(
            @RequestParam(required = false) EstadoSolicitudPermisos estado,
            @RequestParam(required = false) UUID rolId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.listarSolicitudes(estado, rolId, pagina, tamano));
    }

    @PostMapping("/{id}/resolucion")
    ResponseEntity<SolicitudCambioPermisosDto> resolver(@PathVariable UUID id,
            @Valid @RequestBody ResolverSolicitudCambioPermisosRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.resolverSolicitud(id, peticion));
    }
}

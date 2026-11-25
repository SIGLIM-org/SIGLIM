package mx.tecnm.morelia.siglim.notificaciones;

import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.notificaciones.NotificacionService.AvisosDelUsuario;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avisos dentro de la aplicación (convenciones de API §6.14). Sin reglas: el servicio autoriza y
 * filtra por el usuario de la sesión. El listado lleva {@value #CABECERA_NO_LEIDAS} con el conteo
 * para la campana y {@code Cache-Control: no-store} porque es información del usuario.
 *
 * <p>Refs: H-25.1, RF-142, UI-030
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
class NotificacionController {

    static final String CABECERA_NO_LEIDAS = "X-No-Leidas";

    private final NotificacionService servicio;

    NotificacionController(NotificacionService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    ResponseEntity<Pagina<NotificacionDto>> listar(@RequestParam(required = false) Boolean leida,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        AvisosDelUsuario avisos = servicio.listar(leida, pagina, tamano);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(CABECERA_NO_LEIDAS, Long.toString(avisos.noLeidas()))
                .body(avisos.pagina());
    }

    @PostMapping("/{id}/lectura")
    ResponseEntity<NotificacionDto> marcarLeida(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.marcarLeida(id));
    }

    @PostMapping("/lectura")
    LecturaMasivaDto marcarTodas() {
        return servicio.marcarTodas();
    }
}

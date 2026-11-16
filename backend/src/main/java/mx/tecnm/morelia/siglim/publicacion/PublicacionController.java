package mx.tecnm.morelia.siglim.publicacion;

import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bandeja de publicaciones pendientes del Presidente (convenciones de API §6.11). Sin reglas de
 * negocio: el servicio decide qué tipos ve la sesión.
 *
 * <p>Refs: H-13.1, RF-090, RF-121, UI-009
 */
@RestController
@RequestMapping("/api/v1/publicaciones")
class PublicacionController {

    private final PublicacionPendienteService servicio;

    PublicacionController(PublicacionPendienteService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/pendientes")
    Pagina<PublicacionPendienteDto> pendientes(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(pagina, tamano);
    }
}

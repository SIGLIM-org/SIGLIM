package mx.tecnm.morelia.siglim.tesoreria;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adeudos por club y equipo (convenciones de API §6.13). El filtro {@code clubId} es opcional y no
 * cambia el ámbito: un club ajeno simplemente no aparece.
 *
 * <p>Refs: H-29.1, RF-140
 */
@RestController
@RequestMapping("/api/v1/adeudos")
class AdeudoController {

    private final AdeudoService servicio;

    AdeudoController(AdeudoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    AdeudosDto consultar(@RequestParam(required = false) UUID clubId) {
        return servicio.consultar(clubId);
    }
}

package mx.tecnm.morelia.siglim.arbitraje;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuota de árbitros de un club (convenciones de API §6.5, RF-114, DEC-17). Es un subrecurso del
 * club y no duplica el detalle ni el listado de árbitros: solo calcula el indicador.
 *
 * <p>Refs: B-08.1, RF-114, RN-042, F-20
 */
@RestController
@RequestMapping("/api/v1/clubes")
class CuotaArbitrosController {

    private final ArbitroService servicio;

    CuotaArbitrosController(ArbitroService servicio) {
        this.servicio = servicio;
    }

    /**
     * Cuota del club en la temporada indicada o, si se omite {@code temporadaId}, en la temporada
     * activa de la liga.
     */
    @GetMapping("/{clubId}/cuota-arbitros")
    CuotaArbitrosDto consultar(@PathVariable UUID clubId,
            @RequestParam(required = false) UUID temporadaId) {
        return servicio.consultarCuota(clubId, temporadaId);
    }
}

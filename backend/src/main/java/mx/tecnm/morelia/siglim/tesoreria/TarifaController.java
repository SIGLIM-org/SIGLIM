package mx.tecnm.morelia.siglim.tesoreria;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarifas de arbitraje y de cancha (convenciones de API §6.13). Sin reglas de negocio: los valores
 * llegan al servicio, que valida y autoriza. El listado de una temporada no se pagina: tiene a lo
 * sumo dos tarifas por categoría.
 *
 * <p>Refs: B-29.1, RF-135
 */
@RestController
@RequestMapping("/api/v1/tarifas")
class TarifaController {

    private final TarifaService servicio;

    TarifaController(TarifaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<TarifaDto> listar(@RequestParam(required = false) UUID temporadaId) {
        return servicio.listar(temporadaId);
    }

    @PostMapping
    ResponseEntity<TarifaDto> crear(@Valid @RequestBody CrearTarifaRequest peticion) {
        TarifaDto tarifa = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/tarifas/" + tarifa.id())).body(tarifa);
    }

    @PatchMapping("/{id}")
    TarifaDto actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarTarifaRequest peticion) {
        return servicio.actualizar(id, peticion);
    }
}

package mx.tecnm.morelia.siglim.organizacion;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de una persona por CURP (convenciones de API §6.3). Sin reglas de negocio.
 *
 * <p>La usa F-15 para identificar a la persona de un cambio de directiva, porque el alta de cargos
 * recibe un {@code personaId} (RF-010, RF-102). La respuesta lleva {@code Cache-Control: no-store}
 * porque incluye datos personales (RNF-012).
 *
 * <p>Refs: H-22.2, RF-010, RF-011
 */
@RestController
@RequestMapping("/api/v1/personas")
class PersonaController {

    private final PersonaService servicio;

    PersonaController(PersonaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    ResponseEntity<PersonaDto> buscar(@RequestParam String curp) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.buscarPorCurp(curp));
    }
}

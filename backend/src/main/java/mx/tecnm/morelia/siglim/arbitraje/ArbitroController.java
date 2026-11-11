package mx.tecnm.morelia.siglim.arbitraje;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del catálogo de árbitros (convenciones de API §6.5). Sin reglas de negocio: el estado,
 * el perfil de quien propone o valida y la auditoría los pone el servicio.
 *
 * <p>No hay {@code DELETE}: el árbitro se conserva con su estado (RN-005). Las acciones de negocio
 * son {@code POST} a sub-recursos, no {@code PATCH} del campo {@code estado}.
 *
 * <p>Refs: H-11.1, RF-021, RF-112, RF-113, UI-007
 */
@RestController
@RequestMapping("/api/v1/arbitros")
class ArbitroController {

    private final ArbitroService servicio;

    ArbitroController(ArbitroService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<ArbitroDto> listar(@RequestParam(required = false) EstadoArbitro estado,
            @RequestParam(required = false) UUID clubId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(new FiltroArbitros(estado, clubId, pagina, tamano));
    }

    @PostMapping
    ResponseEntity<ArbitroDto> crear(@Valid @RequestBody CrearArbitroRequest peticion) {
        ArbitroDto arbitro = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/arbitros/" + arbitro.id())).body(arbitro);
    }

    @PostMapping("/{id}/validacion")
    ArbitroDto validar(@PathVariable UUID id, @Valid @RequestBody ValidarArbitroRequest peticion) {
        return servicio.validar(id, peticion);
    }
}

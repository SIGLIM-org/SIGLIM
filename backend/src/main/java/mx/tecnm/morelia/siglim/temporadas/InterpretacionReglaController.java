package mx.tecnm.morelia.siglim.temporadas;

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
 * Interpretaciones de regla y su resolución por el Presidente (convenciones de API §6.2). Sin
 * reglas de negocio: el alta no admite estado ni autoría, y la resolución es un {@code POST} a un
 * sub-recurso. No hay {@code PATCH} ni {@code DELETE}: una interpretación no se edita, se resuelve,
 * y el histórico no se borra (RF-003, RF-007).
 *
 * <p>Refs: B-03.1, RF-003, RF-007
 */
@RestController
@RequestMapping("/api/v1/interpretaciones-regla")
class InterpretacionReglaController {

    private final InterpretacionReglaService servicio;

    InterpretacionReglaController(InterpretacionReglaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<InterpretacionReglaDto> listar(@RequestParam(required = false) UUID temporadaId,
            @RequestParam(required = false) EstadoInterpretacion estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(temporadaId, estado, pagina, tamano);
    }

    @PostMapping
    ResponseEntity<InterpretacionReglaDto> registrar(@Valid @RequestBody CrearInterpretacionRequest peticion) {
        InterpretacionReglaDto interpretacion = servicio.registrar(peticion);
        return ResponseEntity.created(
                URI.create("/api/v1/interpretaciones-regla/" + interpretacion.id())).body(interpretacion);
    }

    @PostMapping("/{id}/resolucion")
    InterpretacionReglaDto resolver(@PathVariable UUID id,
            @Valid @RequestBody ResolverInterpretacionRequest peticion) {
        return servicio.resolver(id, peticion);
    }
}

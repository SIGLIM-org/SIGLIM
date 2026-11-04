package mx.tecnm.morelia.siglim.disciplina;

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
 * Artículos del reglamento (convenciones de API §6.9). Sin reglas de negocio.
 *
 * <p>Refs: H-26.1, RF-129, RF-060
 */
@RestController
@RequestMapping("/api/v1/articulos")
class ArticuloController {

    private final ArticulosService servicio;

    ArticuloController(ArticulosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<ArticuloDto> listar(@RequestParam(required = false) Boolean activo) {
        return servicio.listar(activo);
    }

    @PostMapping
    ResponseEntity<ArticuloDto> crear(@Valid @RequestBody CrearArticuloRequest peticion) {
        ArticuloDto articulo = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/articulos/" + articulo.id())).body(articulo);
    }

    @PatchMapping("/{id}")
    ArticuloDto actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarArticuloRequest peticion) {
        return servicio.actualizar(id, peticion);
    }
}

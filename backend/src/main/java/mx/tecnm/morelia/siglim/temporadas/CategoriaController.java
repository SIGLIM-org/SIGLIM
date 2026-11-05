package mx.tecnm.morelia.siglim.temporadas;

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
 * Catálogo de categorías con su orden y su categoría superior (convenciones de API §6.2). Sin
 * reglas de negocio. Es un catálogo pequeño: el listado no se pagina (§4). El orden se cambia con
 * la acción {@code POST /categorias/reordenamiento}, no con {@code PATCH}.
 *
 * <p>Refs: H-05.1, RF-004, RF-005, RF-099
 */
@RestController
@RequestMapping("/api/v1/categorias")
class CategoriaController {

    private final CategoriaService servicio;

    CategoriaController(CategoriaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<CategoriaDto> listar(@RequestParam(required = false) Boolean activa) {
        return servicio.listar(activa);
    }

    @GetMapping("/{id}")
    CategoriaDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping
    ResponseEntity<CategoriaDto> crear(@Valid @RequestBody CrearCategoriaRequest peticion) {
        CategoriaDto categoria = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/categorias/" + categoria.id())).body(categoria);
    }

    @PatchMapping("/{id}")
    CategoriaDto actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarCategoriaRequest peticion) {
        return servicio.actualizar(id, peticion);
    }

    @PostMapping("/reordenamiento")
    List<CategoriaDto> reordenar(@Valid @RequestBody ReordenarCategoriasRequest peticion) {
        return servicio.reordenar(peticion);
    }
}

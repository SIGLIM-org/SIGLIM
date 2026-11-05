package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Categorías que participan en una temporada, con sus grupos anidados (convenciones de API §6.2).
 * Sin reglas de negocio. Es un sub-recurso de la temporada porque no existe sin ella (§1); no hay
 * {@code DELETE} (D7).
 *
 * <p>Refs: H-05.1, RF-004, RF-005, RF-099
 */
@RestController
@RequestMapping("/api/v1/temporadas/{temporadaId}/categorias")
class CategoriaTemporadaController {

    private final GrupoService servicio;

    CategoriaTemporadaController(GrupoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<CategoriaTemporadaDto> listar(@PathVariable UUID temporadaId) {
        return servicio.listarCategorias(temporadaId);
    }

    @GetMapping("/{categoriaTemporadaId}")
    CategoriaTemporadaDto consultar(@PathVariable UUID temporadaId, @PathVariable UUID categoriaTemporadaId) {
        return servicio.consultarCategoria(temporadaId, categoriaTemporadaId);
    }

    @PostMapping
    ResponseEntity<CategoriaTemporadaDto> asignar(@PathVariable UUID temporadaId,
            @Valid @RequestBody AsignarCategoriaRequest peticion) {
        CategoriaTemporadaDto asignada = servicio.asignarCategoria(temporadaId, peticion);
        return ResponseEntity.created(URI.create("/api/v1/temporadas/" + temporadaId + "/categorias/" + asignada.id()))
                .body(asignada);
    }
}

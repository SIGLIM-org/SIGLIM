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
 * Grupos de las categorías de la temporada (convenciones de API §6.2). Sin reglas de negocio: el
 * filtro {@code categoriaTemporadaId} es obligatorio y lo valida el servicio, después del permiso,
 * para responder {@code 400 VALIDACION} con un mensaje claro. No hay {@code DELETE} (D7).
 *
 * <p>Refs: H-05.1, RF-004, RF-005, RF-099
 */
@RestController
@RequestMapping("/api/v1/grupos")
class GrupoController {

    private final GrupoService servicio;

    GrupoController(GrupoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<GrupoDto> listar(@RequestParam(required = false) UUID categoriaTemporadaId) {
        return servicio.listar(categoriaTemporadaId);
    }

    @GetMapping("/{id}")
    GrupoDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping
    ResponseEntity<GrupoDto> crear(@Valid @RequestBody CrearGrupoRequest peticion) {
        GrupoDto grupo = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/grupos/" + grupo.id())).body(grupo);
    }

    @PatchMapping("/{id}")
    GrupoDto actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarGrupoRequest peticion) {
        return servicio.actualizar(id, peticion);
    }
}

package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
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
 * Equipos y su baja (convenciones de API §6.3). Sin reglas de negocio: el servicio valida el motivo
 * de la baja para responder con {@code RN-021}.
 *
 * <p>Refs: H-06.1, RF-009
 */
@RestController
@RequestMapping("/api/v1/equipos")
class EquipoController {

    private final EquipoService servicio;

    EquipoController(EquipoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<EquipoDto> listar(@RequestParam(required = false) UUID temporadaId,
            @RequestParam(required = false) UUID grupoId,
            @RequestParam(required = false) UUID clubId,
            @RequestParam(required = false) EstadoEquipo estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(new FiltroEquipos(temporadaId, grupoId, clubId, estado, pagina, tamano));
    }

    @GetMapping("/{id}")
    EquipoDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping
    ResponseEntity<EquipoDto> crear(@Valid @RequestBody CrearEquipoRequest peticion) {
        EquipoDto equipo = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/equipos/" + equipo.id())).body(equipo);
    }

    @PatchMapping("/{id}")
    EquipoDto actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarEquipoRequest peticion) {
        return servicio.actualizar(id, peticion);
    }

    @PostMapping("/{id}/baja")
    EquipoDto darDeBaja(@PathVariable UUID id, @Valid @RequestBody BajaEquipoRequest peticion) {
        return servicio.darDeBaja(id, peticion);
    }
}

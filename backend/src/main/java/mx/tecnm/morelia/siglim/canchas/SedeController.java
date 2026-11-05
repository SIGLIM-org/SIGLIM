package mx.tecnm.morelia.siglim.canchas;

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
import org.springframework.web.bind.annotation.RestController;

/**
 * Sedes de la liga (convenciones de API §6.6). Sin reglas de negocio.
 *
 * <p>Refs: B-11.1, RF-028
 */
@RestController
@RequestMapping("/api/v1/sedes")
class SedeController {

    private final SedeService servicio;

    SedeController(SedeService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<SedeDto> listar() {
        return servicio.listar();
    }

    @PostMapping
    ResponseEntity<SedeDto> crear(@Valid @RequestBody CrearSedeRequest peticion) {
        SedeDto sede = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/sedes/" + sede.id())).body(sede);
    }

    @PatchMapping("/{id}")
    SedeDto actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarSedeRequest peticion) {
        return servicio.actualizar(id, peticion);
    }
}

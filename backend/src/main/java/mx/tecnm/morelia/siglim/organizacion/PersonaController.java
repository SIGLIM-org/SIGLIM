package mx.tecnm.morelia.siglim.organizacion;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
 * Personas y tutela (convenciones de API §6.3). Sin reglas de negocio: las validaciones y su orden
 * están en {@link PersonaService}.
 *
 * <p>Todas las respuestas llevan {@code Cache-Control: no-store} porque incluyen datos personales
 * (RNF-012). La tutela responde {@code 201} sin {@code Location}: todavía no existe un {@code GET}
 * del recurso.
 *
 * <p>Refs: H-22.2, H-09.1, RF-010, RF-011, RF-106
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

    @PostMapping
    ResponseEntity<PersonaDetalleDto> registrar(@RequestBody CrearPersonaRequest peticion) {
        PersonaDetalleDto persona = servicio.registrar(peticion);
        return ResponseEntity.created(URI.create("/api/v1/personas/" + persona.id()))
                .cacheControl(CacheControl.noStore())
                .body(persona);
    }

    @PatchMapping("/{id}")
    ResponseEntity<PersonaDetalleDto> actualizar(@PathVariable UUID id,
            @RequestBody ActualizarPersonaRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.actualizar(id, peticion));
    }

    @PostMapping("/{id}/tutelas")
    ResponseEntity<TutelaDto> registrarTutela(@PathVariable UUID id, @RequestBody RegistrarTutelaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(servicio.registrarTutela(id, peticion));
    }
}

package mx.tecnm.morelia.siglim.programacion;

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
 * Jornadas de la programación manual y alta de sus partidos (convenciones de API §6.7). Sin reglas
 * de negocio: el permiso, el estado {@code BORRADOR} de la jornada y los conflictos los valida el
 * servicio y, en última instancia, el trigger.
 *
 * <p>Refs: H-12.1, H-12.3, RF-033, RF-036, RF-039
 */
@RestController
@RequestMapping("/api/v1/jornadas")
class JornadaController {

    private final JornadaService servicio;

    JornadaController(JornadaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<JornadaDto> listar(@RequestParam(required = false) UUID grupoId,
            @RequestParam(required = false) EstadoJornada estadoPublicacion,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(grupoId, estadoPublicacion, pagina, tamano);
    }

    @GetMapping("/{id}")
    JornadaDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping
    ResponseEntity<JornadaDto> crear(@Valid @RequestBody CrearJornadaRequest peticion) {
        JornadaDto jornada = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/jornadas/" + jornada.id())).body(jornada);
    }

    @PostMapping("/{id}/partidos")
    ResponseEntity<PartidoDto> registrarPartido(@PathVariable UUID id,
            @Valid @RequestBody CrearPartidoRequest peticion) {
        PartidoDto partido = servicio.registrarPartido(id, peticion);
        return ResponseEntity.created(URI.create("/api/v1/partidos/" + partido.id())).body(partido);
    }

    /** Verifica todos los partidos sin guardar (H-12.3, RF-039, convenciones de API §6.7). */
    @PostMapping("/{id}/verificacion")
    VerificacionJornadaDto verificar(@PathVariable UUID id) {
        return servicio.verificar(id);
    }
}

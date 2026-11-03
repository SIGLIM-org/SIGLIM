package mx.tecnm.morelia.siglim.publicacion;

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
 * Comunicados y su flujo de publicación (convenciones de API §6.11). Sin reglas de negocio: el
 * envío no lleva cuerpo, y el servicio valida las observaciones de la devolución para responder con
 * {@code RF-090}.
 *
 * <p>Refs: B-27.1, RF-090, UI-031
 */
@RestController
@RequestMapping("/api/v1/comunicados")
class ComunicadoController {

    private final ComunicadoService servicio;

    ComunicadoController(ComunicadoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<ComunicadoDto> listar(@RequestParam(required = false) EstadoPublicacion estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(estado, pagina, tamano);
    }

    @GetMapping("/{id}")
    ComunicadoDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping
    ResponseEntity<ComunicadoDto> crear(@Valid @RequestBody CrearComunicadoRequest peticion) {
        ComunicadoDto comunicado = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/comunicados/" + comunicado.id())).body(comunicado);
    }

    @PatchMapping("/{id}")
    ComunicadoDto actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarComunicadoRequest peticion) {
        return servicio.actualizar(id, peticion);
    }

    @PostMapping("/{id}/envio")
    ComunicadoDto enviarARevision(@PathVariable UUID id) {
        return servicio.enviarARevision(id);
    }

    @PostMapping("/{id}/resolucion")
    ComunicadoDto resolver(@PathVariable UUID id,
            @Valid @RequestBody ResolverComunicadoRequest peticion) {
        return servicio.resolver(id, peticion);
    }
}

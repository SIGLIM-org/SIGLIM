package mx.tecnm.morelia.siglim.seguridad;

import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
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
 * Solicitudes ARCO: listado, registro y cambio de estado (convenciones de API §6.1). Sin reglas de
 * negocio: están en {@link SolicitudArcoService}.
 *
 * <p>Las respuestas llevan {@code Cache-Control: no-store} (datos personales, RNF-012). El registro
 * responde {@code 201} sin {@code Location}: no existe un {@code GET} del recurso individual.
 *
 * <p>Refs: B-01, RF-098
 */
@RestController
@RequestMapping("/api/v1/solicitudes-arco")
class SolicitudArcoController {

    private final SolicitudArcoService servicio;

    SolicitudArcoController(SolicitudArcoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    ResponseEntity<Pagina<SolicitudArcoDto>> listar(
            @RequestParam(required = false) EstadoSolicitudArco estado,
            @RequestParam(required = false) TipoSolicitudArco tipo,
            @RequestParam(required = false) UUID personaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.listar(new FiltroSolicitudesArco(estado, tipo, personaId, pagina, tamano)));
    }

    @PostMapping
    ResponseEntity<SolicitudArcoDto> registrar(@RequestBody RegistrarSolicitudArcoRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(servicio.registrar(peticion));
    }

    @PatchMapping("/{id}")
    ResponseEntity<SolicitudArcoDto> actualizar(@PathVariable UUID id,
            @RequestBody ActualizarSolicitudArcoRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.actualizar(id, peticion));
    }
}

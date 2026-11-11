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
 * Afiliación del club por temporada (convenciones de API §2.1 y §6.3). Sin reglas de negocio: el
 * folio, el estado y el perfil los pone el servicio; el rechazo valida sus observaciones allí para
 * responder con {@code RF-101}.
 *
 * <p>El tarjetón no se borra: los estados finales son {@code APROBADA} y {@code RECHAZADA}.
 *
 * <p>Refs: H-08.1, RF-101, CU-018, UI-023, DEC-09
 */
@RestController
@RequestMapping("/api/v1/afiliaciones")
class AfiliacionController {

    private final AfiliacionService servicio;

    AfiliacionController(AfiliacionService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<AfiliacionDto> listar(@RequestParam(required = false) UUID temporadaId,
            @RequestParam(required = false) EstadoAfiliacion estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(new FiltroAfiliaciones(temporadaId, estado, pagina, tamano));
    }

    @GetMapping("/{id}")
    AfiliacionDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping
    ResponseEntity<AfiliacionDto> crear(@Valid @RequestBody CrearAfiliacionRequest peticion) {
        AfiliacionDto afiliacion = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/afiliaciones/" + afiliacion.id()))
                .body(afiliacion);
    }

    @PatchMapping("/{id}")
    AfiliacionDto actualizar(@PathVariable UUID id,
            @Valid @RequestBody ActualizarAfiliacionRequest peticion) {
        return servicio.actualizar(id, peticion);
    }

    @PostMapping("/{id}/resolucion")
    AfiliacionDto resolver(@PathVariable UUID id, @Valid @RequestBody ResolverAfiliacionRequest peticion) {
        return servicio.resolver(id, peticion);
    }
}

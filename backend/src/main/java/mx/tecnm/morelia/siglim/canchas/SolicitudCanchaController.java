package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
 * Trámites de cancha: Formatos 1 y 2 y su carta con número de oficio (convenciones de API §6.6).
 * Sin reglas de negocio: el estado, el número de oficio, los perfiles y las fechas los pone el
 * servicio. Los trámites no se borran.
 *
 * <p>Refs: B-14, RF-116, RF-117, RF-118
 */
@RestController
@RequestMapping("/api/v1/solicitudes-cancha")
class SolicitudCanchaController {

    private final SolicitudCanchaService servicio;

    SolicitudCanchaController(SolicitudCanchaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<SolicitudCanchaDto> listar(@RequestParam(name = "tipo", required = false) List<TipoSolicitudCancha> tipos,
            @RequestParam(required = false) EstadoSolicitudCancha estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(new FiltroSolicitudesCancha(tipos, estado, pagina, tamano));
    }

    @GetMapping("/contexto")
    ContextoSolicitudesCanchaDto contexto() {
        return servicio.contexto();
    }

    @PostMapping
    ResponseEntity<SolicitudCanchaDto> crear(@Valid @RequestBody CrearSolicitudCanchaRequest peticion) {
        SolicitudCanchaDto solicitud = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/solicitudes-cancha/" + solicitud.id()))
                .body(solicitud);
    }

    @PostMapping("/{id}/resolucion")
    SolicitudCanchaDto resolver(@PathVariable UUID id,
            @Valid @RequestBody ResolverSolicitudCanchaRequest peticion) {
        return servicio.resolver(id, peticion);
    }

    @GetMapping("/{id}/oficio")
    CartaOficioDto oficio(@PathVariable UUID id) {
        return servicio.oficio(id);
    }
}

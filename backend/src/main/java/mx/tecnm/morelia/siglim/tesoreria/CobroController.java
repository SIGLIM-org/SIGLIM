package mx.tecnm.morelia.siglim.tesoreria;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cobros (convenciones de API §6.13). Sin reglas de negocio: los valores llegan al servicio, que
 * valida y autoriza. El listado se pagina; el alta siempre crea un cobro {@code MANUAL}.
 *
 * <p>Refs: H-29.1, RF-137, RF-140
 */
@RestController
@RequestMapping("/api/v1/cobros")
class CobroController {

    private final CobroService servicio;

    CobroController(CobroService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<CobroDto> listar(@RequestParam(required = false) UUID clubId,
            @RequestParam(required = false) UUID equipoId,
            @RequestParam(required = false) TipoCobro tipo,
            @RequestParam(name = "estado", required = false) List<EstadoCobro> estados,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "creadoEn,desc") String orden) {
        return servicio.listar(new FiltroCobros(clubId, equipoId, tipo, estados, desde, hasta, pagina, tamano, orden));
    }

    @PostMapping
    ResponseEntity<CobroDto> crear(@Valid @RequestBody CrearCobroRequest peticion) {
        CobroDto cobro = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/cobros/" + cobro.id())).body(cobro);
    }
}

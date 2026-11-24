package mx.tecnm.morelia.siglim.partidos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta y transiciones del partido (convenciones de API §6.8). El ajuste en borrador sigue en
 * {@code PATCH /partidos/{id}} de la programación (H-12.1). Las respuestas llevan
 * {@code Cache-Control: no-store} porque dependen del ámbito y de los permisos de la sesión.
 *
 * <p>Refs: H-15.1, RF-043, RF-045, RF-046
 */
@RestController
@RequestMapping("/api/v1/partidos")
class PartidosController {

    private final PartidosService servicio;

    PartidosController(PartidosService servicio) {
        this.servicio = servicio;
    }

    /** Partidos visibles para la sesión, por inicio, con sus {@code accionesPermitidas} (RF-043, RF-046). */
    @GetMapping
    ResponseEntity<Pagina<PartidoResumenDto>> listar(@RequestParam(required = false) UUID jornadaId,
            @RequestParam(required = false) EstadoPartido estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.listar(new FiltroPartidos(jornadaId, estado, desde, hasta, pagina, tamano)));
    }

    /** Detalle con árbitros, cédula, resultado vigente, historial y acciones permitidas. */
    @GetMapping("/{id}")
    ResponseEntity<PartidoDetalleDto> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.obtener(id));
    }

    /** Transiciones del partido con usuario, fecha y motivo (RF-045). */
    @GetMapping("/{id}/historial")
    ResponseEntity<List<CambioEstadoPartidoDto>> historial(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.historial(id));
    }

    /** Suspende, reprograma o cancela el partido; responde el detalle actualizado (RF-043, RN-021). */
    @PostMapping("/{id}/transiciones")
    ResponseEntity<PartidoDetalleDto> transicionar(@PathVariable UUID id,
            @RequestBody TransicionPartidoRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.transicionar(id, peticion));
    }
}

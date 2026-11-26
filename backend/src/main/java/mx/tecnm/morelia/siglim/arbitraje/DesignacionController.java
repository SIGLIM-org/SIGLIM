package mx.tecnm.morelia.siglim.arbitraje;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Designación de árbitros y «Mis designaciones» (convenciones de API §6.5). Sin reglas de negocio:
 * el permiso, el estado del partido, la sustitución y la auditoría los pone el servicio.
 *
 * <p>Designar responde {@code 201} <b>sin</b> {@code Location}, como la disponibilidad: no hay
 * {@code GET /designaciones/{id}}. Retirar es una acción de estado ({@code POST} a un sub-recurso) y
 * responde {@code 200} con la designación ya sin vigencia. Las consultas llevan
 * {@code Cache-Control: no-store} porque dependen de la sesión.
 *
 * <p>Refs: H-14.1, RF-023, RF-115, DEC-201 a DEC-205
 */
@RestController
class DesignacionController {

    private final DesignacionService servicio;

    DesignacionController(DesignacionService servicio) {
        this.servicio = servicio;
    }

    /** Partidos de la jornada con sus designaciones vigentes y el avance (UI-025). */
    @GetMapping("/api/v1/designaciones")
    ResponseEntity<DesignacionesJornadaDto> listarPorJornada(@RequestParam(required = false) UUID jornadaId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.listarPorJornada(jornadaId));
    }

    /** Árbitros validados con su elegibilidad, advertencias y carga para el partido (DEC-204). */
    @GetMapping("/api/v1/partidos/{id}/arbitros-candidatos")
    ResponseEntity<List<CandidatoArbitralDto>> candidatos(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.candidatos(id));
    }

    /** Designa un central o asistente; con central vigente lo sustituye con motivo (DEC-201, DEC-202). */
    @PostMapping("/api/v1/partidos/{id}/designaciones")
    ResponseEntity<DesignacionDto> designar(@PathVariable UUID id,
            @Valid @RequestBody DesignarArbitroRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.designar(id, peticion));
    }

    /** Retira la designación con motivo; responde {@code 200} con {@code vigente: false} (DEC-203). */
    @PostMapping("/api/v1/designaciones/{id}/retiro")
    DesignacionDto retirar(@PathVariable UUID id, @RequestBody RetirarDesignacionRequest peticion) {
        return servicio.retirar(id, peticion);
    }

    /** Designaciones vigentes del árbitro de la sesión en jornadas publicadas (DEC-205). */
    @GetMapping("/api/v1/mis-designaciones")
    ResponseEntity<Pagina<MiDesignacionDto>> listarPropias(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.listarPropias(new FiltroMisDesignaciones(desde, hasta, pagina, tamano)));
    }
}

package mx.tecnm.morelia.siglim.arbitraje;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disponibilidad arbitral por fecha y horario (convenciones de API §6.5). Sin reglas de negocio: el
 * estado del árbitro, el permiso y la auditoría los pone el servicio.
 *
 * <p>No hay {@code PUT} ni {@code PATCH}: cada periodo es un registro con identidad propia, se crea
 * con {@code POST} y se retira con {@code DELETE}. La disponibilidad no es historial deportivo, así
 * que el borrado es físico.
 *
 * <p>Refs: B-09.1, RF-022
 */
@RestController
@RequestMapping("/api/v1/arbitros/{id}/disponibilidad")
class DisponibilidadArbitralController {

    private final DisponibilidadArbitralService servicio;

    DisponibilidadArbitralController(DisponibilidadArbitralService servicio) {
        this.servicio = servicio;
    }

    /** Periodos del árbitro; {@code desde}/{@code hasta} son opcionales y filtran por traslape. */
    @GetMapping
    List<DisponibilidadArbitralDto> listar(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return servicio.listar(id, desde, hasta);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DisponibilidadArbitralDto registrar(@PathVariable UUID id,
            @Valid @RequestBody RegistrarDisponibilidadArbitralRequest peticion) {
        return servicio.registrar(id, peticion);
    }

    /** Retira el periodo; responde {@code 204} sin cuerpo. */
    @DeleteMapping("/{disponibilidadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eliminar(@PathVariable UUID id, @PathVariable UUID disponibilidadId) {
        servicio.eliminar(id, disponibilidadId);
    }
}

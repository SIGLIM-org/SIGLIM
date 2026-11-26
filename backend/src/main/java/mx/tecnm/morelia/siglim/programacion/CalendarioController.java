package mx.tecnm.morelia.siglim.programacion;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calendario de la temporada (convenciones de API §6.7): generarlo o regenerarlo y consultarlo. Es un
 * sub-recurso de la temporada porque no existe sin ella. Sin reglas de negocio: el permiso, los
 * grupos, el motivo y RN-020 los valida el servicio y, en última instancia, los triggers.
 *
 * <p>Refs: H-32.2, RF-034, RF-037, RN-020, DEC-189
 */
@RestController
@RequestMapping("/api/v1/temporadas/{temporadaId}/calendario")
class CalendarioController {

    private final CalendarioService servicio;

    CalendarioController(CalendarioService servicio) {
        this.servicio = servicio;
    }

    /** Genera o regenera el calendario; el cuerpo es opcional (H-32.2, DEC-189). */
    @PostMapping
    ResponseEntity<CalendarioGeneradoDto> generar(@PathVariable UUID temporadaId,
            @RequestBody(required = false) GenerarCalendarioRequest peticion) {
        CalendarioGeneradoDto generado = servicio.generar(temporadaId, peticion);
        return ResponseEntity.created(URI.create("/api/v1/temporadas/" + temporadaId + "/calendario")).body(generado);
    }

    @GetMapping
    CalendarioDto consultar(@PathVariable UUID temporadaId) {
        return servicio.consultar(temporadaId);
    }
}

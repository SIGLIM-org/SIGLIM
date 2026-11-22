package mx.tecnm.morelia.siglim.programacion;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ajuste manual de un partido programado (convenciones de API §6.7). El motivo del ajuste y el
 * estado {@code BORRADOR} de la jornada los valida el servicio (RF-040, RN-018).
 *
 * <p>Refs: H-12.1, RF-040, RN-018
 */
@RestController
@RequestMapping("/api/v1/partidos")
class PartidoController {

    private final JornadaService servicio;

    PartidoController(JornadaService servicio) {
        this.servicio = servicio;
    }

    @PatchMapping("/{id}")
    PartidoDto actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarPartidoRequest peticion) {
        return servicio.actualizarPartido(id, peticion);
    }
}

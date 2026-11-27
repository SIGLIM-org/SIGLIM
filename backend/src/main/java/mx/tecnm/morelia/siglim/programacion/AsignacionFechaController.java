package mx.tecnm.morelia.siglim.programacion;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asignación automática de cancha y horario a una fecha (convenciones de API §6.7). Sin reglas de
 * negocio: el permiso, el modo, el motivo y los estados los valida {@link AsignacionFechaService}. Va
 * aparte de {@link FechaController} para no mezclar sus dependencias.
 *
 * <p>Refs: H-33.2, RF-035, RF-041, RF-042, DEC-181, DEC-206
 */
@RestController
@RequestMapping("/api/v1/fechas")
class AsignacionFechaController {

    private final AsignacionFechaService servicio;

    AsignacionFechaController(AsignacionFechaService servicio) {
        this.servicio = servicio;
    }

    /** Asigna los pendientes o regenera la fecha completa (DEC-181). */
    @PostMapping("/{id}/asignacion")
    AsignacionFechaDto asignar(@PathVariable UUID id, @RequestBody(required = false) AsignarFechaRequest peticion) {
        return servicio.asignar(id, peticion);
    }
}

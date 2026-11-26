package mx.tecnm.morelia.siglim.programacion;

import jakarta.validation.Valid;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fechas de programación: vista por fecha (F-22, F-23), verificación, envío y dictamen de la fecha
 * completa (convenciones de API §6.7). Sin reglas de negocio: el permiso, los estados de las jornadas
 * y la separación de funciones los valida el servicio y, en última instancia, el trigger.
 *
 * <p>Refs: H-35.1, H-32.2, RF-033, RF-035, RF-039, RF-090, RF-121, DEC-175, DEC-193
 */
@RestController
@RequestMapping("/api/v1/fechas")
class FechaController {

    private final FechaService servicio;

    FechaController(FechaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<FechaDto> listar(@RequestParam(required = false) UUID temporadaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(temporadaId, pagina, tamano);
    }

    @GetMapping("/{id}")
    FechaDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    /** Cambia o agrega días de la fecha en borrador, con motivo (H-32.2, DEC-193). */
    @PatchMapping("/{id}")
    FechaDto actualizarDias(@PathVariable UUID id, @RequestBody ActualizarFechaRequest peticion) {
        return servicio.actualizarDias(id, peticion);
    }

    /** Verifica todas las jornadas de la fecha sin guardar (RF-039). */
    @PostMapping("/{id}/verificacion")
    VerificacionFechaDto verificar(@PathVariable UUID id) {
        return servicio.verificar(id);
    }

    /** Envía todas las jornadas de la fecha a aprobación del Presidente, sin cuerpo (RF-121). */
    @PostMapping("/{id}/envio")
    FechaDto enviarAAprobacion(@PathVariable UUID id) {
        return servicio.enviarAAprobacion(id);
    }

    /** Aprueba y publica, o devuelve con observaciones, la fecha completa (RF-121). */
    @PostMapping("/{id}/resolucion")
    FechaDto resolver(@PathVariable UUID id, @Valid @RequestBody ResolverFechaRequest peticion) {
        return servicio.resolver(id, peticion);
    }
}

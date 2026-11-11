package mx.tecnm.morelia.siglim.registros;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registros de jugador (convenciones de API §6.4). Sin reglas de negocio: las validaciones y su orden
 * están en {@link RegistroJugadorService}; por eso el cuerpo no lleva {@code @Valid} (el permiso se
 * comprueba antes que los datos, DEC-85).
 *
 * <p>El alta responde {@code 201} sin {@code Location} porque todavía no existe
 * {@code GET /registros-jugador/{id}}, y con {@code Cache-Control: no-store} porque incluye el nombre
 * de la persona (RNF-012).
 *
 * <p>Refs: H-09.2, RF-013, RF-107, DEC-85, DEC-90
 */
@RestController
@RequestMapping("/api/v1/registros-jugador")
class RegistroJugadorController {

    private final RegistroJugadorService servicio;

    RegistroJugadorController(RegistroJugadorService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    ResponseEntity<RegistroJugadorDto> registrar(@RequestBody CrearRegistroJugadorRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(servicio.registrar(peticion));
    }
}

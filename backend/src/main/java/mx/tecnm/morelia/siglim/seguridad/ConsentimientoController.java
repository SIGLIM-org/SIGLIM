package mx.tecnm.morelia.siglim.seguridad;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aviso de privacidad vigente y registro del consentimiento (convenciones de API §6.1). Sin reglas
 * de negocio: están en {@link ConsentimientoService}.
 *
 * <p>Las respuestas llevan {@code Cache-Control: no-store}. El consentimiento responde {@code 201}
 * sin {@code Location}: todavía no existe un {@code GET} del recurso.
 *
 * <p>Refs: H-09.1, RF-097, VAL-025
 */
@RestController
@RequestMapping("/api/v1")
class ConsentimientoController {

    private final ConsentimientoService servicio;

    ConsentimientoController(ConsentimientoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/avisos-privacidad/vigente")
    ResponseEntity<AvisoPrivacidadDto> avisoVigente(@RequestParam(required = false) String tipo) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.avisoVigente(tipo));
    }

    @PostMapping("/personas/{id}/consentimientos")
    ResponseEntity<ConsentimientoDto> registrar(@PathVariable UUID id,
            @RequestBody RegistrarConsentimientoRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(servicio.registrar(id, peticion));
    }
}

package mx.tecnm.morelia.siglim.registros;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta propia del jugador o de su tutor (convenciones de API §6.4, M-04.6). Sin reglas de
 * negocio: la autorización y su orden están en {@link MiInformacionService}. Un {@code personaId} mal
 * formado responde {@code 400 VALIDACION} antes del servicio, como en el resto de la API.
 *
 * <p>Las respuestas llevan {@code Cache-Control: no-store} porque incluyen nombres, la CURP enmascarada
 * y el estado de documentos y sanciones de una persona (RNF-012).
 *
 * <p>Refs: H-24.1, RF-144, RNF-012, UI-032, DEC-139, DEC-140
 */
@RestController
@RequestMapping("/api/v1/mi-informacion")
class MiInformacionController {

    private final MiInformacionService servicio;

    MiInformacionController(MiInformacionService servicio) {
        this.servicio = servicio;
    }

    /** La persona de la cuenta y los menores bajo su tutela vigente (DEC-139). */
    @GetMapping
    ResponseEntity<TitularesDto> titulares() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.titulares());
    }

    /** Registros, documentos, elegibilidad, partidos y sanciones de una persona de la que es titular (DEC-141). */
    @GetMapping("/{personaId}")
    ResponseEntity<MiInformacionDto> consultar(@PathVariable UUID personaId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.consultar(personaId));
    }
}

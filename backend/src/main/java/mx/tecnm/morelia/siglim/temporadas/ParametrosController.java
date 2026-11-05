package mx.tecnm.morelia.siglim.temporadas;

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
 * Parámetros deportivos versionados de la temporada (convenciones de API §6.2). Sin reglas de
 * negocio: el cuerpo lo valida el servicio después del permiso, para responder {@code 400} con un
 * mensaje claro. Las versiones no se editan ni se borran: no hay {@code PATCH} ni {@code DELETE}.
 *
 * <p>Refs: H-05.2, RF-006, RF-100
 */
@RestController
@RequestMapping("/api/v1/temporadas/{temporadaId}/parametros")
class ParametrosController {

    private final ParametrosService servicio;

    ParametrosController(ParametrosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    ParametrosTemporadaDto consultar(@PathVariable UUID temporadaId) {
        return servicio.consultar(temporadaId);
    }

    @GetMapping("/{version}")
    ParametrosDto consultarVersion(@PathVariable UUID temporadaId, @PathVariable int version) {
        return servicio.consultarVersion(temporadaId, version);
    }

    @PostMapping
    ResponseEntity<ParametrosDto> registrar(@PathVariable UUID temporadaId,
            @RequestBody RegistrarParametrosRequest peticion) {
        ParametrosDto creada = servicio.registrar(temporadaId, peticion);
        return ResponseEntity.created(URI.create("/api/v1/temporadas/" + temporadaId + "/parametros/"
                + creada.version())).body(creada);
    }
}

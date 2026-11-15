package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Venta de equipo y conservación de plaza (convenciones de API §2.1 y §6.3). Sin reglas de negocio:
 * el estado, los perfiles y las fechas los pone el servicio; el dictamen valida sus observaciones
 * allí para responder con {@code RN-021}.
 *
 * <p>{@code POST} responde {@code 201} sin {@code Location} porque todavía no existe un {@code GET}
 * del recurso (mismo criterio que los documentos de un registro, DEC-92). La operación no se borra:
 * sus estados son {@code SOLICITADA}, {@code AUTORIZADA}, {@code RECHAZADA} y {@code APLICADA}.
 *
 * <p>Refs: B-05.1, RF-103, RF-104, RN-040
 */
@RestController
@RequestMapping("/api/v1/operaciones-equipo")
class OperacionEquipoController {

    private final OperacionEquipoService servicio;

    OperacionEquipoController(OperacionEquipoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    ResponseEntity<OperacionEquipoDto> crear(
            @Valid @RequestBody RegistrarOperacionEquipoRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(peticion));
    }

    @PostMapping("/{id}/resolucion")
    OperacionEquipoDto resolver(@PathVariable UUID id,
            @Valid @RequestBody ResolverOperacionEquipoRequest peticion) {
        return servicio.resolver(id, peticion);
    }
}

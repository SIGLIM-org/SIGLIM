package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Venta de equipo y conservación de plaza (convenciones de API §2.1 y §6.3). Sin reglas de negocio:
 * el estado, los perfiles y las fechas los pone el servicio; el dictamen valida sus observaciones
 * allí para responder con {@code RN-021}.
 *
 * <p>{@code POST} responde {@code 201} con {@code Location}; {@code GET} tiene la bandeja, el
 * detalle y el catálogo de clubes destino de B-09 (B-05.3). La operación no se borra: sus estados
 * son {@code SOLICITADA}, {@code AUTORIZADA}, {@code RECHAZADA} y {@code APLICADA}.
 *
 * <p>Refs: B-05.1, B-05.3, RF-103, RF-104, RN-040
 */
@RestController
@RequestMapping("/api/v1/operaciones-equipo")
class OperacionEquipoController {

    private final OperacionEquipoService servicio;

    OperacionEquipoController(OperacionEquipoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<OperacionEquipoDto> listar(@RequestParam(required = false) UUID temporadaId,
            @RequestParam(required = false) EstadoOperacionEquipo estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(new FiltroOperacionesEquipo(temporadaId, estado, pagina, tamano));
    }

    @GetMapping("/contexto")
    ContextoOperacionesEquipoDto contexto() {
        return servicio.contexto();
    }

    @GetMapping("/{id}")
    OperacionEquipoDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping
    ResponseEntity<OperacionEquipoDto> crear(
            @Valid @RequestBody RegistrarOperacionEquipoRequest peticion) {
        OperacionEquipoDto operacion = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/operaciones-equipo/" + operacion.id()))
                .body(operacion);
    }

    @PostMapping("/{id}/resolucion")
    OperacionEquipoDto resolver(@PathVariable UUID id,
            @Valid @RequestBody ResolverOperacionEquipoRequest peticion) {
        return servicio.resolver(id, peticion);
    }
}

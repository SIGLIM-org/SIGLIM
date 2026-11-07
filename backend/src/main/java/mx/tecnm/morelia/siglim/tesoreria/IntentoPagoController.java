package mx.tecnm.morelia.siglim.tesoreria;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pago en línea simulado de un cobro (convenciones de API §6.13). Sin reglas de negocio: el servicio
 * valida el estado, autoriza por club, genera la referencia ficticia y confirma de forma atómica.
 *
 * <p>El inicio responde {@code 201} con la referencia y el estado {@code PENDIENTE}; la confirmación
 * responde el intento ya resuelto ({@code APROBADO} o {@code RECHAZADO}). No se reciben datos de
 * tarjeta.
 *
 * <p>Refs: H-30.1, RF-138, VAL-028, ADR-0012
 */
@RestController
class IntentoPagoController {

    private final PagoSimuladoService servicio;

    IntentoPagoController(PagoSimuladoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/api/v1/cobros/{cobroId}/intentos-pago")
    ResponseEntity<IntentoPagoDto> iniciar(@PathVariable UUID cobroId) {
        IntentoPagoDto intento = servicio.iniciar(cobroId);
        return ResponseEntity.created(URI.create("/api/v1/intentos-pago/" + intento.id())).body(intento);
    }

    @PostMapping("/api/v1/intentos-pago/{id}/confirmacion")
    IntentoPagoDto confirmar(@PathVariable UUID id, @Valid @RequestBody ConfirmarPagoSimuladoRequest peticion) {
        return servicio.confirmar(id, peticion.resultado());
    }
}

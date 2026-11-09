package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de cargos de directiva del club (RF-102). */
@RestController
@RequestMapping("/api/v1")
class CargoDirectivaController {

    private final CargoDirectivaService servicio;

    CargoDirectivaController(CargoDirectivaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/clubes/{clubId}/directiva")
    List<CargoDirectivaDto> consultar(@PathVariable UUID clubId) {
        return servicio.consultar(clubId);
    }

    @PostMapping("/clubes/{clubId}/directiva")
    ResponseEntity<CargoDirectivaDto> registrar(@PathVariable UUID clubId,
            @Valid @RequestBody RegistrarCargoDirectivaRequest peticion) {
        CargoDirectivaDto cargo = servicio.registrar(clubId, peticion);
        return ResponseEntity.created(URI.create("/api/v1/cargos-directiva/" + cargo.id())).body(cargo);
    }

    @PostMapping("/cargos-directiva/{id}/autorizacion")
    CargoDirectivaDto autorizar(@PathVariable UUID id) {
        return servicio.autorizar(id);
    }
}

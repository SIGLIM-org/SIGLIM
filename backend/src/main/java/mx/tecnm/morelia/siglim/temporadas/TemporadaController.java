package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporadas, sus acciones de activación y cierre y la referencia de su convocatoria (convenciones
 * de API §6.2). Sin reglas de negocio: el cuerpo de la activación es vacío, el servicio valida el
 * motivo del cierre para responder con {@code RN-021} y la convocatoria es un {@code PUT} de
 * reemplazo completo.
 *
 * <p>Refs: H-04.1, B-02.1, RF-001, RF-002, RF-003, RF-004, RF-005, RF-100, RN-058
 */
@RestController
@RequestMapping("/api/v1/temporadas")
class TemporadaController {

    private final TemporadaService servicio;
    private final ConvocatoriaService convocatorias;

    TemporadaController(TemporadaService servicio, ConvocatoriaService convocatorias) {
        this.servicio = servicio;
        this.convocatorias = convocatorias;
    }

    @GetMapping
    Pagina<TemporadaDto> listar(@RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(pagina, tamano);
    }

    @GetMapping("/{id}")
    TemporadaDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping
    ResponseEntity<TemporadaDto> crear(@Valid @RequestBody CrearTemporadaRequest peticion) {
        TemporadaDto temporada = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/temporadas/" + temporada.id())).body(temporada);
    }

    @PatchMapping("/{id}")
    TemporadaDto actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarTemporadaRequest peticion) {
        return servicio.actualizar(id, peticion);
    }

    @PostMapping("/{id}/activacion")
    TemporadaDto activar(@PathVariable UUID id) {
        return servicio.activar(id);
    }

    @PostMapping("/{id}/cierre")
    TemporadaDto cerrar(@PathVariable UUID id, @Valid @RequestBody CerrarTemporadaRequest peticion) {
        return servicio.cerrar(id, peticion);
    }

    @PutMapping("/{id}/convocatoria")
    ConvocatoriaDto asociarConvocatoria(@PathVariable UUID id,
            @Valid @RequestBody ReemplazarConvocatoriaRequest peticion) {
        return convocatorias.reemplazar(id, peticion);
    }
}

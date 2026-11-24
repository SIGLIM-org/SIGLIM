package mx.tecnm.morelia.siglim.partidos;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Captura de la cédula del partido: abrirla, la alineación, el marcador, los eventos, las incidencias,
 * las observaciones y el envío a validación (convenciones de API §6.8). Sin reglas de negocio: el
 * permiso, el estado y la elegibilidad los valida el servicio y, en última instancia, los triggers.
 *
 * <p>Refs: H-16.1, H-16.2, RF-025, RF-026, RF-047, RF-110, RF-122, RF-126, VAL-002, VAL-018, VAL-020
 */
@RestController
@RequestMapping("/api/v1")
class CedulaController {

    private final CedulaService servicio;

    CedulaController(CedulaService servicio) {
        this.servicio = servicio;
    }

    /** La cédula del partido; {@code 404} si todavía no se abre. Lleva nombres de jugadores. */
    @GetMapping("/partidos/{partidoId}/cedula")
    ResponseEntity<CedulaDto> consultar(@PathVariable UUID partidoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.consultar(partidoId));
    }

    /** Abre la cédula {@code EN_CAPTURA}, sin cuerpo (RF-025). */
    @PostMapping("/partidos/{partidoId}/cedula")
    ResponseEntity<CedulaDto> abrir(@PathVariable UUID partidoId) {
        CedulaDto cedula = servicio.abrir(partidoId);
        return ResponseEntity.created(URI.create("/api/v1/partidos/" + partidoId + "/cedula"))
                .cacheControl(CacheControl.noStore())
                .body(cedula);
    }

    /** Reemplaza la alineación de los dos equipos (RF-019, RF-020, RF-110). */
    @PutMapping("/cedulas/{id}/alineaciones")
    ResponseEntity<CedulaDto> guardarAlineaciones(@PathVariable UUID id,
            @RequestBody GuardarAlineacionesRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.guardarAlineaciones(id, peticion));
    }

    /** Guarda el marcador o la no presentación de un equipo (RF-047, RF-126). */
    @PutMapping("/cedulas/{id}/marcador")
    ResponseEntity<CedulaDto> guardarMarcador(@PathVariable UUID id, @RequestBody GuardarMarcadorRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.guardarMarcador(id, peticion));
    }

    /**
     * Registra un gol, autogol, tarjeta o sustitución (RF-025). Responde {@code 201} con la cédula y sin
     * {@code Location}: el evento no tiene un {@code GET} propio.
     */
    @PostMapping("/cedulas/{id}/eventos")
    ResponseEntity<CedulaDto> registrarEvento(@PathVariable UUID id, @RequestBody RegistrarEventoRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(servicio.registrarEvento(id, peticion));
    }

    /** Quita un evento mientras la cédula está en captura; responde {@code 204} sin cuerpo. */
    @DeleteMapping("/cedulas/{id}/eventos/{eventoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eliminarEvento(@PathVariable UUID id, @PathVariable UUID eventoId) {
        servicio.eliminarEvento(id, eventoId);
    }

    /** Registra una incidencia con su tipo e involucrados (RF-026); {@code 201} con la cédula. */
    @PostMapping("/cedulas/{id}/incidencias")
    ResponseEntity<CedulaDto> registrarIncidencia(@PathVariable UUID id,
            @RequestBody RegistrarIncidenciaRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(servicio.registrarIncidencia(id, peticion));
    }

    /** Reemplaza las observaciones del partido (RF-025). */
    @PutMapping("/cedulas/{id}/observaciones")
    ResponseEntity<CedulaDto> guardarObservaciones(@PathVariable UUID id,
            @RequestBody GuardarObservacionesRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.guardarObservaciones(id, peticion));
    }

    /** Envía la cédula a validación: {@code EN_CAPTURA → PENDIENTE_VALIDACION} (RF-122, VAL-020). */
    @PostMapping("/cedulas/{id}/envio")
    ResponseEntity<CedulaDto> enviar(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.enviar(id));
    }
}

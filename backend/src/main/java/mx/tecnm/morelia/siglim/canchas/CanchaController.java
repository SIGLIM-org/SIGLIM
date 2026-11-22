package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canchas, disponibilidad y ocupación (convenciones de API §6.6). Sin reglas de negocio: las fechas
 * {@code desde} y {@code hasta} son opcionales aquí para que el servicio explique la falta.
 *
 * <p>Refs: H-07.1, B-13.1, RF-027, RF-029, RF-030, RF-119
 */
@RestController
@RequestMapping("/api/v1/canchas")
class CanchaController {

    private final CanchaService servicio;

    CanchaController(CanchaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<CanchaDto> listar(@RequestParam(required = false) Boolean activa,
            @RequestParam(required = false) UbicacionCancha ubicacion,
            @RequestParam(required = false) PropietarioCancha propietario,
            @RequestParam(required = false) UUID sedeId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano,
            @RequestParam(defaultValue = "nombre,asc") String orden) {
        return servicio.listar(new FiltroCanchas(activa, ubicacion, propietario, sedeId, q, pagina, tamano, orden));
    }

    @GetMapping("/{id}")
    CanchaDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping
    ResponseEntity<CanchaDto> crear(@Valid @RequestBody CrearCanchaRequest peticion) {
        CanchaDto cancha = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/canchas/" + cancha.id())).body(cancha);
    }

    @PatchMapping("/{id}")
    CanchaDto actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarCanchaRequest peticion) {
        return servicio.actualizar(id, peticion);
    }

    @GetMapping("/{id}/disponibilidad")
    List<DisponibilidadDto> listarDisponibilidad(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return servicio.listarDisponibilidad(id, desde, hasta);
    }

    @PostMapping("/{id}/disponibilidad")
    @ResponseStatus(HttpStatus.CREATED)
    RegistroDisponibilidadDto registrarDisponibilidad(@PathVariable UUID id,
            @Valid @RequestBody RegistrarDisponibilidadRequest peticion) {
        return servicio.registrarDisponibilidad(id, peticion);
    }

    @GetMapping("/{id}/ocupacion")
    OcupacionDto consultarOcupacion(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return servicio.consultarOcupacion(id, desde, hasta);
    }

    /** Horarios por categoría de la cancha en una temporada (RF-119, convenciones de API §6.6.1). */
    @GetMapping("/{id}/horarios-club")
    List<HorarioCanchaClubDto> listarHorariosClub(@PathVariable UUID id, @RequestParam UUID temporadaId) {
        return servicio.listarHorariosClub(id, temporadaId);
    }

    /**
     * Reemplaza los horarios de la cancha en una temporada (RF-119). Sin {@code @Valid}: el servicio
     * valida el cuerpo después de la cancha y el permiso (§6.6.1).
     */
    @PutMapping("/{id}/horarios-club")
    List<HorarioCanchaClubDto> reemplazarHorariosClub(@PathVariable UUID id,
            @RequestBody ReemplazarHorariosClubRequest peticion) {
        return servicio.reemplazarHorariosClub(id, peticion);
    }
}

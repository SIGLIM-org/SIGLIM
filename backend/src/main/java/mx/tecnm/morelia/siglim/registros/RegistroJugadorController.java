package mx.tecnm.morelia.siglim.registros;

import java.net.URI;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registros de jugador (convenciones de API §6.4). Sin reglas de negocio: las validaciones y su orden
 * están en {@link RegistroJugadorService}; por eso el cuerpo no lleva {@code @Valid} (el permiso se
 * comprueba antes que los datos, DEC-85), y los filtros del listado llegan como texto y los valida
 * el servicio ({@link FiltroRegistros}).
 *
 * <p>El alta responde {@code 201} con {@code Location: /api/v1/registros-jugador/{id}} (DEC-109). Todas
 * las respuestas llevan {@code Cache-Control: no-store} porque incluyen el nombre de la persona y, en
 * el detalle, su CURP (RNF-012). La validación responde {@code 200} con el registro (DEC-102).
 *
 * <p>Refs: H-09.2, H-10.2, H-10.5, RF-013, RF-017, RF-107, RF-109, DEC-85, DEC-90, DEC-97, DEC-102, DEC-109,
 * DEC-110
 */
@RestController
@RequestMapping("/api/v1/registros-jugador")
class RegistroJugadorController {

    private static final String RUTA = "/api/v1/registros-jugador/";

    private final RegistroJugadorService servicio;

    RegistroJugadorController(RegistroJugadorService servicio) {
        this.servicio = servicio;
    }

    /** Registros del ámbito del usuario, paginados (H-10.5, RF-017, DEC-109). */
    @GetMapping
    ResponseEntity<Pagina<RegistroJugadorResumenDto>> listar(@RequestParam(required = false) String estado,
            @RequestParam(required = false) UUID temporadaId,
            @RequestParam(required = false) UUID clubId,
            @RequestParam(required = false) UUID equipoId,
            @RequestParam(required = false) String orden,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.listar(new FiltroRegistros(estado, temporadaId, clubId, equipoId, orden, pagina, tamano)));
    }

    /** Detalle con {@code esMenor}, tutores y la CURP según el permiso (H-10.5, DEC-109, DEC-110). */
    @GetMapping("/{id}")
    ResponseEntity<RegistroJugadorDetalleDto> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.consultar(id));
    }

    @PostMapping
    ResponseEntity<RegistroJugadorDto> registrar(@RequestBody CrearRegistroJugadorRequest peticion) {
        RegistroJugadorDto registro = servicio.registrar(peticion);
        return ResponseEntity.created(URI.create(RUTA + registro.id()))
                .cacheControl(CacheControl.noStore())
                .body(registro);
    }

    /** La liga activa o rechaza el registro: {@code 200} con el registro (H-10.2, RF-109, DEC-102). */
    @PostMapping("/{id}/validacion")
    ResponseEntity<RegistroJugadorDto> validar(@PathVariable UUID id, @RequestBody ValidarRegistroRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.validar(id, peticion));
    }
}

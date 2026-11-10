package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuentas internas: listado, detalle, invitación, activación/desactivación y asignaciones
 * (convenciones de API §6.1, RF-094).
 *
 * <p>Sin reglas de negocio. Las respuestas con datos personales llevan
 * {@code Cache-Control: no-store} (RNF-012, convenciones §5).
 *
 * <p>Refs: H-03.1, RF-094
 */
@RestController
@RequestMapping("/api/v1/usuarios")
class UsuarioController {

    private final UsuarioService servicio;

    UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    ResponseEntity<Pagina<UsuarioDto>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID rolId,
            @RequestParam(required = false) Ambito ambito,
            @RequestParam(required = false) UUID clubId,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.listar(new FiltroUsuarios(q, rolId, ambito, clubId, activo, pagina, tamano)));
    }

    @GetMapping("/{id}")
    ResponseEntity<UsuarioDto> consultar(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.consultar(id));
    }

    @PostMapping
    ResponseEntity<UsuarioDto> invitar(@Valid @RequestBody CrearUsuarioRequest peticion) {
        UsuarioDto usuario = servicio.invitar(peticion);
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + usuario.id()))
                .cacheControl(CacheControl.noStore())
                .body(usuario);
    }

    @PatchMapping("/{id}")
    ResponseEntity<UsuarioDto> actualizar(@PathVariable UUID id,
            @Valid @RequestBody ActualizarUsuarioRequest peticion) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.actualizar(id, peticion));
    }

    @PostMapping("/{id}/asignaciones")
    ResponseEntity<UsuarioDto.AsignacionDto> asignar(@PathVariable UUID id,
            @Valid @RequestBody AsignacionRequest peticion) {
        UsuarioDto.AsignacionDto asignacion = servicio.asignar(id, peticion);
        return ResponseEntity
                .created(URI.create("/api/v1/usuarios/" + id + "/asignaciones/" + asignacion.id()))
                .cacheControl(CacheControl.noStore())
                .body(asignacion);
    }
}

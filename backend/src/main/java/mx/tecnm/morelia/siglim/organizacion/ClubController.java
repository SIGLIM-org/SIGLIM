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
 * Clubes (convenciones de API §6.3). Sin reglas de negocio. No hay {@code PATCH}: el único dato
 * editable es el nombre y su cambio es la acción {@code POST /clubes/{id}/cambio-nombre} de B-04
 * (RN-041, DEC-49).
 *
 * <p>Refs: H-06.1, RF-008
 */
@RestController
@RequestMapping("/api/v1/clubes")
class ClubController {

    private final ClubService servicio;

    ClubController(ClubService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    Pagina<ClubDto> listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) EstadoClub estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return servicio.listar(new FiltroClubes(q, estado, pagina, tamano));
    }

    @GetMapping("/{id}")
    ClubDto consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping
    ResponseEntity<ClubDto> crear(@Valid @RequestBody CrearClubRequest peticion) {
        ClubDto club = servicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/clubes/" + club.id())).body(club);
    }
}

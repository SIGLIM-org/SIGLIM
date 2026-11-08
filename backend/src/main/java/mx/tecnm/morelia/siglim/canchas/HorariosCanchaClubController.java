package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * horarios-club (RF-119, BACKLOG-23).
 *
 * <ul>
 *   <li><b>GET</b> devuelve la lista del recurso {@code horariosCanchaClub} del detalle de cancha.</li>
 *   <li><b>PUT</b> reemplaza el conjunto completo por el cuerpo.</li>
 * </ul>
 *
 * <p>Refs: RF-119, DEC-33, BACKLOG-23, §6.6.1.</p>
 */
@RestController
@RequestMapping("/api/v1/canchas/{id}/horarios-club")
class HorariosCanchaClubController {

    private final HorariosCanchaClubService servicio;

    HorariosCanchaClubController(HorariosCanchaClubService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<HorariosCanchaClubDto> listar(@PathVariable UUID id) {
        return servicio.listar(id);
    }

    @PutMapping
    ResponseEntity<List<HorariosCanchaClubDto>> reemplazar(@PathVariable UUID id,
            @Valid @RequestBody RegistrarHorarioRequest request) {
        List<HorariosCanchaClubDto> resultado = servicio.reemplazar(id, request);
        return ResponseEntity.ok(resultado);
    }
}

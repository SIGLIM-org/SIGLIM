package mx.tecnm.morelia.siglim.canchas;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code PUT /api/v1/canchas/{id}/horarios-club}: el juego completo de horarios de la
 * cancha en una temporada (RF-119). Sin anotaciones de validación: el servicio valida los campos
 * <b>después</b> de la existencia de la cancha y del permiso (convenciones de API §6.6.1).
 *
 * @param temporadaId temporada cuyos horarios se reemplazan; los de otras temporadas no se tocan
 * @param horarios    lista completa; vacía borra los horarios de esa temporada
 */
public record ReemplazarHorariosClubRequest(UUID temporadaId, List<HorarioRequest> horarios) {

    /**
     * @param clubId opcional en una cancha de club (se toma el propietario); obligatorio en una de la
     *               liga, donde es el club que la tiene en comodato (DEC-33)
     */
    public record HorarioRequest(UUID categoriaId, Integer diaSemana, LocalTime horaInicio, LocalTime horaFin,
                                 UUID clubId) {
    }
}

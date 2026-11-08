package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Horario por categoría y comodato de una cancha (RF-119).
 *
 * <ul>
 *   <li><b>Código.</b> Se expone como lista simple desde {@code GET /canchas/{id}/horarios-club} y también
 *       se usa como respuesta del body del {@code PUT}.</li>
 *   <li><b>Orden.</b> Para el catálogo de lectura se ordena por {@code categoriaId} y {@code desde}.</li>
 *   <li><b>Comodato.</b> Cuando la cancha está en comodato se debe enviar {@code clubId}; omitirlo responde
 *       {@code 422 REFERENCIA_INVALIDA}.</li>
 * </ul>
 *
 * <p>Refs: RF-119, DEC-33, BACKLOG-23.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HorariosCanchaClubDto(
        UUID id,
        UUID canchaId,
        UUID clubId,
        UUID temporadaId,
        UUID categoriaId,
        int diaSemana,
        String horaInicio,
        String horaFin,
        boolean esComodato) {
}

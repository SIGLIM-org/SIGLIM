package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo del {@code PUT /canchas/{id}/horarios-club}.
 *
 * <ul>
 *   <li><b>Reemplazo.</b> Reemplaza el conjunto completo de horarios por categoría y comodato.</li>
 *   <li><b>Comodato.</b> Si la cancha ya tiene comodato, cada horario debe incluir {@code clubId}; un
 *       {@code clubId} ausente o que no pertenezca a la liga responde {@code 422 REFERENCIA_INVALIDA}.</li>
 *   <li><b>Traslape.</b> Dos horarios de la misma categoría no se traslapan; el traslape entra como regla de
 *       contenido antes de escribir.</li>
 * </ul>
 *
 * <p>Refs: RF-119, BACKLOG-23, §6.6.1.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistrarHorarioRequest(
        List<HorarioCanchaClubItem> horarios) {
    public record HorarioCanchaClubItem(
            UUID categoriaId,
            String desde,
            String hasta,
            UUID clubId,
            String observaciones) {
    }
}

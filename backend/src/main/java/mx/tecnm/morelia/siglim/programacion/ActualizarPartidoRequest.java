package mx.tecnm.morelia.siglim.programacion;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/partidos/{id}}: un campo ausente conserva su valor (RF-040).
 *
 * <p>Solo se ajusta un partido de una jornada en {@code BORRADOR}. Todo ajuste que cambie equipos,
 * cancha o inicio exige {@code motivo} (RN-018, RF-040): el servicio lo valida antes de escribir y
 * deja la traza en la auditoría. El estado no se cambia aquí, sino con las transiciones de M-08.
 *
 * <p>Refs: H-12.1, RF-040, RN-018
 *
 * @param equipoLocalId     equipo local nuevo, si se cambia
 * @param equipoVisitanteId equipo visitante nuevo, si se cambia
 * @param canchaId          cancha nueva, si se cambia
 * @param inicio            fecha y hora de inicio nueva, si se cambia
 * @param motivo            razón del ajuste; obligatoria cuando hay cambios (RN-018)
 */
public record ActualizarPartidoRequest(
        UUID equipoLocalId,
        UUID equipoVisitanteId,
        UUID canchaId,
        OffsetDateTime inicio,
        String motivo) {
}

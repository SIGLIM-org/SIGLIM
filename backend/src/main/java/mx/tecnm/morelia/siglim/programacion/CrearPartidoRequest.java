package mx.tecnm.morelia.siglim.programacion;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/jornadas/{id}/partidos}: registra un partido en la jornada
 * (RF-033, RF-036).
 *
 * <p>El estado inicial es {@code PROGRAMADO} y {@code finBloque} lo calcula la base con el bloque de
 * cancha de la categoría (DEC-25). Los conflictos duros los valida el servicio en H-12.2 y el trigger
 * {@code tg_partidos_programacion} es la última garantía.
 *
 * <p>Refs: H-12.1, B-15.1, RF-033, RF-036, RF-120, VAL-027, DEC-25
 *
 * @param equipoLocalId     equipo local, del grupo de la jornada
 * @param equipoVisitanteId equipo visitante, distinto del local y del grupo de la jornada
 * @param canchaId          cancha activa de la liga
 * @param inicio            fecha y hora de inicio
 * @param motivoAceptacion  motivo con el que se acepta una advertencia de localía (VAL-027,
 *                          convenciones de API §3.1); se guarda en {@code motivo_localia} y solo se
 *                          exige si el partido no respeta la localía (B-15.1, RF-120)
 */
public record CrearPartidoRequest(
        @NotNull(message = "El equipo local es obligatorio")
        UUID equipoLocalId,
        @NotNull(message = "El equipo visitante es obligatorio")
        UUID equipoVisitanteId,
        @NotNull(message = "La cancha es obligatoria")
        UUID canchaId,
        @NotNull(message = "La fecha y hora de inicio son obligatorias")
        OffsetDateTime inicio,
        String motivoAceptacion) {

    /** Partido sin motivo de localía: el caso común, cuando se respeta la localía. */
    public CrearPartidoRequest(UUID equipoLocalId, UUID equipoVisitanteId, UUID canchaId, OffsetDateTime inicio) {
        this(equipoLocalId, equipoVisitanteId, canchaId, inicio, null);
    }
}

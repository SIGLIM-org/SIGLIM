package mx.tecnm.morelia.siglim.programacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Resultado de verificar una fecha completa sin guardarla (H-35.1, RF-039, DEC-175).
 *
 * <p>{@code conflictos} reúne los bloqueos duros (VAL-003 a VAL-006) de los partidos vigentes de
 * todas las jornadas, con la misma regla del alta y el ajuste: con uno solo, la fecha no se envía.
 * {@code advertencias} no bloquean el envío: localía ({@code VAL-027}, B-15.1), partidos sin árbitro
 * central ({@code RF-023}, DEC-182) y emparejamientos sin asignar con su código (DEC-180).
 *
 * <p>Refs: H-35.1, RF-023, RF-039, RF-042, RF-120, VAL-003 a VAL-007, VAL-027, DEC-175, DEC-180, DEC-182
 */
public record VerificacionFechaDto(List<Aviso> conflictos, List<Aviso> advertencias) {

    public VerificacionFechaDto {
        conflictos = conflictos == null ? List.of() : List.copyOf(conflictos);
        advertencias = advertencias == null ? List.of() : List.copyOf(advertencias);
    }

    /**
     * Un conflicto o una advertencia de la fecha. Nombra la jornada y el partido o, si no hay
     * partido, el emparejamiento sin asignar.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Aviso(UUID jornadaId, UUID partidoId, UUID emparejamientoId, String codigo, String mensaje) {
    }
}

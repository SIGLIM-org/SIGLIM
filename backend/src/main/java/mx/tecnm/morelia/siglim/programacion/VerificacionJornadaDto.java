package mx.tecnm.morelia.siglim.programacion;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de verificar una jornada sin guardarla (H-12.3, RF-039; convenciones de API §6.7).
 *
 * <p>{@code conflictos} reúne los bloqueos duros VAL-003 a VAL-006 de todos los partidos y, si la
 * temporada no tiene parámetros, {@code RF-100}. {@code advertencias} reúne las de localía
 * ({@code VAL-027}, B-15.1): no bloquean el envío y se aceptan con motivo; si el partido ya tiene
 * motivo de localía, el mensaje lo dice. Cada aviso nombra el partido y explica el choque con la
 * cancha y el horario.
 *
 * <p>Refs: H-12.3, B-15.1, RF-039, RF-120, VAL-003, VAL-004, VAL-005, VAL-006, VAL-027
 */
public record VerificacionJornadaDto(List<Aviso> conflictos, List<Aviso> advertencias) {

    public VerificacionJornadaDto {
        conflictos = conflictos == null ? List.of() : List.copyOf(conflictos);
        advertencias = advertencias == null ? List.of() : List.copyOf(advertencias);
    }

    /** Un conflicto duro o una advertencia de un partido de la jornada. */
    public record Aviso(UUID partidoId, String codigo, String mensaje) {
    }
}

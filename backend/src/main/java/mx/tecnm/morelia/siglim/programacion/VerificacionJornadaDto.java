package mx.tecnm.morelia.siglim.programacion;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de verificar una jornada sin guardarla (H-12.3, RF-039; convenciones de API §6.7).
 *
 * <p>{@code conflictos} reúne los bloqueos duros VAL-003 a VAL-006 de todos los partidos y, si la
 * temporada no tiene parámetros, {@code RF-100}. {@code advertencias} queda vacío hasta que el
 * backlog B-15 implemente las reglas de localía (RF-120, VAL-027); el campo existe desde ahora para
 * no cambiar el contrato después. Cada aviso nombra el partido y explica el choque con la cancha y
 * el horario.
 *
 * <p>Refs: H-12.3, RF-039, RF-120, VAL-003, VAL-004, VAL-005, VAL-006
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

package mx.tecnm.morelia.siglim.organizacion;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tutela registrada entre un menor y su tutor (RF-106, DEC-82). Solo lleva identificadores y el
 * parentesco, sin datos de contacto.
 *
 * <p>Refs: H-09.1, RF-106, DEC-75
 */
public record TutelaDto(
        UUID id,
        UUID menorId,
        UUID tutorId,
        String parentesco,
        boolean vigente,
        OffsetDateTime creadoEn) {
}

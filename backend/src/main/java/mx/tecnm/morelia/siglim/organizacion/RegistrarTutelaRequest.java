package mx.tecnm.morelia.siglim.organizacion;

import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/personas/{id}/tutelas}: el tutor y su parentesco con el menor
 * (obligatorio, hasta 40 caracteres tras recortar espacios). Lo valida {@link PersonaService}.
 *
 * <p>Refs: H-09.1, RF-106, DEC-75, DEC-82
 */
public record RegistrarTutelaRequest(UUID tutorId, String parentesco) {
}

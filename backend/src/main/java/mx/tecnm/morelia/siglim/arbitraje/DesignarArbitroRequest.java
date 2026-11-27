package mx.tecnm.morelia.siglim.arbitraje;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/partidos/{id}/designaciones} (RF-023, DEC-201, DEC-202).
 *
 * <p>{@code motivo} solo se usa al sustituir al central vigente: entonces es obligatorio, de 10 a 500
 * caracteres tras recortarlo ({@code 400 RN-021}); el servicio valida la longitud para responder con
 * el código del ERS. Sin central vigente, o al designar un asistente, se ignora.
 *
 * <p>Refs: H-14.1, RF-023, RN-021, DEC-201, DEC-202
 *
 * @param arbitroId árbitro que se designa
 * @param funcion   central o asistente
 * @param motivo    motivo de la sustitución del central
 */
public record DesignarArbitroRequest(
        @NotNull(message = "Indica el árbitro que se designa")
        UUID arbitroId,
        @NotNull(message = "Indica si el árbitro es central o asistente")
        FuncionArbitral funcion,
        String motivo) {
}

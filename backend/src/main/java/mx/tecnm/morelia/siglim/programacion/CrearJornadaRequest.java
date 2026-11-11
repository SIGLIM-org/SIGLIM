package mx.tecnm.morelia.siglim.programacion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/jornadas} (RF-033).
 *
 * <p>La jornada nace en {@code BORRADOR}; los partidos se registran con una acción aparte para que el
 * Programador arme la programación antes de enviarla a aprobación (ERS §4.3.6).
 *
 * <p>Refs: H-12.1, RF-033
 *
 * @param grupoId grupo de la categoría en la temporada
 * @param faseId  fase final a la que pertenece; se omite en la fase regular
 * @param numero  número de la jornada dentro del grupo o de la fase, mayor que cero
 */
public record CrearJornadaRequest(
        @NotNull(message = "El grupo de la jornada es obligatorio")
        UUID grupoId,
        UUID faseId,
        @NotNull(message = "El número de la jornada es obligatorio")
        @Min(value = 1, message = "El número de la jornada debe ser mayor que cero")
        Integer numero) {
}

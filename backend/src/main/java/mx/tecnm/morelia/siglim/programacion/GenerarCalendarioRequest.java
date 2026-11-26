package mx.tecnm.morelia.siglim.programacion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo opcional de {@code POST /api/v1/temporadas/{id}/calendario} (H-32.2, DEC-189, DEC-193).
 *
 * <p>Refs: H-32.2, RF-034, RN-021, DEC-189, DEC-193
 *
 * @param fechaInicio desde cuándo se cuentan las fechas: la fecha 1 es el primer sábado en o después
 *                    de ella (DEC-186); por defecto, el inicio de la temporada. No se admite si alguna
 *                    fecha ya tiene jornadas (DEC-193)
 * @param grupoIds    grupos a generar; por defecto, todos los de la temporada
 * @param motivo      obligatorio, de al menos 10 caracteres, si algún grupo ya tiene calendario y se
 *                    reemplaza (RN-021, DEC-189)
 */
public record GenerarCalendarioRequest(LocalDate fechaInicio, List<UUID> grupoIds, String motivo) {
}

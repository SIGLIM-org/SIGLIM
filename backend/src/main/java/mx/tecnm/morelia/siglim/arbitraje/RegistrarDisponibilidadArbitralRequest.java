package mx.tecnm.morelia.siglim.arbitraje;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code POST /api/v1/arbitros/{id}/disponibilidad} (RF-022).
 *
 * <p>El periodo es obligatorio y debe tener inicio anterior al fin. {@code disponible} es opcional:
 * si se omite, el árbitro queda disponible en ese periodo (predeterminado de la tabla); con
 * {@code false} declara que no puede ser designado.
 *
 * <p>Refs: B-09.1, RF-022
 *
 * @param periodo    fecha y horario de la disponibilidad
 * @param disponible verdadero si está disponible; falso si no; {@code null} equivale a verdadero
 */
public record RegistrarDisponibilidadArbitralRequest(
        @NotNull(message = "El periodo es obligatorio")
        @Valid
        PeriodoArbitral periodo,
        Boolean disponible) {
}

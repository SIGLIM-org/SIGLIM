package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code POST /api/v1/canchas/{id}/disponibilidad}: disponibilidad (RF-029), bloqueo
 * (RF-030) o mantenimiento (RF-031). El motivo es obligatorio en bloqueo y mantenimiento (RN-021).
 */
public record RegistrarDisponibilidadRequest(
        @NotNull(message = "El tipo es obligatorio")
        TipoDisponibilidad tipo,
        @NotNull(message = "El periodo es obligatorio")
        @Valid
        Intervalo periodo,
        String motivo,
        Boolean confirmarPartidosAfectados) {
}

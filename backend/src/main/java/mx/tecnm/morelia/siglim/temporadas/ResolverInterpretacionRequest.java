package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code POST /api/v1/interpretaciones-regla/{id}/resolucion} (RF-007).
 *
 * <p>Solo el Presidente resuelve, con el permiso {@code temporadas.interpretacion:APROBAR}. La
 * resolución es definitiva: una interpretación ya resuelta no se vuelve a resolver. Cuando se
 * rechaza, las observaciones son obligatorias para que el registro explique el motivo.
 *
 * <p>Refs: B-03.1, RF-007
 *
 * @param estado        {@code AUTORIZADA} o {@code RECHAZADA}; {@code PENDIENTE} no es una resolución
 * @param observaciones motivo del rechazo; opcional al autorizar
 */
public record ResolverInterpretacionRequest(
        @NotNull(message = "Indica si la interpretación se autoriza o se rechaza")
        EstadoInterpretacion estado,
        String observaciones) {
}

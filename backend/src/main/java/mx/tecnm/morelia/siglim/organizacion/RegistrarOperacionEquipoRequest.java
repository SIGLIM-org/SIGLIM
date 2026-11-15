package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/operaciones-equipo} (RF-103, RF-104).
 *
 * <p>La venta exige {@code clubDestinoId} y que sea distinto del club actual del equipo; la
 * conservación de plaza no lleva club destino, porque es una operación independiente de la venta
 * (DEC-14). El servicio valida esas dos reglas para explicarlas con un mensaje claro.
 *
 * <p>Refs: B-05.1, RF-103, RF-104, RN-040
 *
 * @param tipo          venta o conservación de plaza
 * @param equipoId      equipo sobre el que se registra la operación
 * @param clubDestinoId club comprador; solo en la venta
 * @param observaciones nota de la solicitud, opcional
 */
public record RegistrarOperacionEquipoRequest(
        @NotNull(message = "Indica si la operación es una venta o una conservación de plaza")
        TipoOperacionEquipo tipo,
        @NotNull(message = "El equipo es obligatorio")
        UUID equipoId,
        UUID clubDestinoId,
        @Size(max = 500, message = "Las observaciones admiten hasta 500 caracteres")
        String observaciones) {
}

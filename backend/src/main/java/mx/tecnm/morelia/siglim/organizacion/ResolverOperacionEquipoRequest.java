package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/operaciones-equipo/{id}/resolucion} (RF-103, RF-104, RN-040).
 *
 * <p>El rechazo exige observaciones de entre 10 y 500 caracteres; la autorización puede llevarlas,
 * pero no son obligatorias. El servicio valida la longitud para responder con un mensaje claro.
 *
 * <p>Refs: B-05.1, RF-103, RF-104, RN-040
 *
 * @param decision      autorizar o rechazar la operación
 * @param observaciones motivo del rechazo o nota de la autorización
 */
public record ResolverOperacionEquipoRequest(
        @NotNull(message = "Indica si la operación se autoriza o se rechaza")
        DecisionOperacionEquipo decision,
        @Size(max = 500, message = "Las observaciones admiten hasta 500 caracteres")
        String observaciones) {
}

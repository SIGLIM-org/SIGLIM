package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/afiliaciones/{id}/resolucion} (RF-101, CU-018).
 *
 * <p>El rechazo exige observaciones de entre 10 y 500 caracteres; la aprobación puede llevarlas,
 * pero no son obligatorias. El servicio valida la longitud para responder con un mensaje claro.
 *
 * <p>Refs: H-08.1, RF-101, CU-018, DEC-09
 *
 * @param decision      aprobar o rechazar el tarjetón
 * @param observaciones motivo del rechazo o nota de la aprobación
 */
public record ResolverAfiliacionRequest(
        @NotNull(message = "Indica si la afiliación se aprueba o se rechaza")
        DecisionAfiliacion decision,
        @Size(max = 500, message = "Las observaciones admiten hasta 500 caracteres")
        String observaciones) {
}

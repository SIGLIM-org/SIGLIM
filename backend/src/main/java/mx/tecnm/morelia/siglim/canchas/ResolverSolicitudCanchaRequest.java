package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/solicitudes-cancha/{id}/resolucion} (RF-116, RF-117).
 *
 * <p>La decisión desfavorable ({@code NO_APROBADA} o {@code RECHAZADA}) exige observaciones de 10 a
 * 500 caracteres; la favorable las admite como condiciones. El servicio valida la longitud y que la
 * decisión sea del formato del trámite.
 *
 * <p>Refs: B-14, RF-116, RF-117, RN-021
 *
 * @param decision      aprobar o no aprobar (Formato 1); aceptar o rechazar (Formato 2)
 * @param observaciones motivo de la decisión desfavorable o condiciones de la favorable
 */
public record ResolverSolicitudCanchaRequest(
        @NotNull(message = "Indica la decisión sobre el trámite")
        DecisionSolicitudCancha decision,
        @Size(max = 500, message = "Las observaciones admiten hasta 500 caracteres")
        String observaciones) {
}

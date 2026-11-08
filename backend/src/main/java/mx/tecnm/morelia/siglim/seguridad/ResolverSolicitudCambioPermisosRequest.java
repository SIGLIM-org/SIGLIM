package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/solicitudes-cambio-permisos/{id}/resolucion} (RF-096).
 *
 * <p>El Presidente aprueba o rechaza el cambio pendiente. El motivo es obligatorio en los dos
 * dictámenes porque es el que exige la auditoría del control dual (DEC-04, RF-093, F-13) y se
 * guarda en {@code observaciones}; mínimo 10 caracteres, igual que el resto de operaciones con
 * motivo **[PROPUESTA]**.
 *
 * <p>Refs: H-20.1, RF-096, RF-093, DEC-04
 *
 * @param decision {@code APROBADO} aplica el cambio; {@code RECHAZADO} lo descarta
 * @param motivo   razón del dictamen, mínimo 10 caracteres
 */
public record ResolverSolicitudCambioPermisosRequest(
        @NotNull(message = "Indica si el cambio se aprueba o se rechaza")
        DecisionSolicitudPermisos decision,
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres")
        String motivo) {
}

package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Cuerpo de {@code PATCH /api/v1/roles/{id}/permisos} (RF-095, VAL-023).
 *
 * <p>La matriz de F-13 se guarda completa: {@code permisos} es el conjunto final que debe tener el
 * rol, no una lista de altas. El servicio calcula la diferencia contra lo que hay en la base para
 * dejar la solicitud de control dual y la auditoría. Los permisos se identifican por su código,
 * nunca por el identificador numérico de la base (convenciones de API §1).
 *
 * <p>El motivo es obligatorio y se audita (RF-093, DEC-04); mínimo 10 caracteres, igual que el
 * resto de operaciones con motivo **[PROPUESTA]**.
 *
 * <p>Refs: H-20.1, RF-095, VAL-023, DEC-04
 *
 * @param permisos conjunto final de códigos del rol; puede quedar vacío
 * @param motivo   razón del cambio, obligatoria
 */
public record ActualizarPermisosRolRequest(
        @NotNull(message = "Indica los permisos del rol")
        @Size(max = 500, message = "El rol admite hasta 500 permisos")
        List<@NotBlank(message = "El código del permiso no puede estar vacío") String> permisos,
        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres")
        String motivo) {
}

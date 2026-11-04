package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/canchas/{id}}: un campo ausente conserva su valor.
 *
 * <ul>
 *   <li>Si viene {@code propietario}, {@code clubId} y {@code propietarioExterno} se toman solo de
 *       la petición, para poder cambiar de propietario sin arrastrar los datos del anterior.</li>
 *   <li>{@code direccion} y {@code caracteristicas} en blanco se vacían.</li>
 *   <li>{@code motivo} es obligatorio si cambia {@code activa} (RN-021).</li>
 *   <li><b>Sede (RF-028, D2, [PROPUESTA]).</b> Sin {@code sedeId} ni {@code quitarSede}, la sede se
 *       conserva; con {@code sedeId}, se asigna esa sede; con {@code "quitarSede": true}, la cancha
 *       queda sin sede. Los dos juntos responden {@code 400 VALIDACION}. Una sede inexistente o de
 *       otra liga responde {@code 422 REFERENCIA_INVALIDA} (D4).</li>
 * </ul>
 */
public record ActualizarCanchaRequest(
        @Size(max = 120, message = "El nombre admite hasta 120 caracteres")
        String nombre,
        UbicacionCancha ubicacion,
        String direccion,
        PropietarioCancha propietario,
        UUID clubId,
        @Size(max = 150, message = "El propietario externo admite hasta 150 caracteres")
        String propietarioExterno,
        String caracteristicas,
        Boolean activa,
        String motivo,
        Boolean confirmarPartidosAfectados,
        UUID sedeId,
        Boolean quitarSede) {
}

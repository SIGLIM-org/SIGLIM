package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Cuerpo de {@code POST /api/v1/usuarios}: invita una cuenta y le asigna uno o más roles
 * (RF-094; convenciones de API §6.1).
 *
 * <p>El perfil de SIGLIM y la cuenta de Supabase Auth se crean con estos datos; el correo se
 * normaliza en el servicio. La liga sale de la configuración y nunca del cuerpo.
 *
 * <p>Refs: H-03.1, RF-094
 *
 * @param nombreMostrado nombre que verá la persona en la aplicación
 * @param correo         correo que recibirá la invitación
 * @param asignaciones   al menos un rol; si una asignación omite el ámbito, se usa el
 *                       predeterminado del rol
 */
public record CrearUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
        String nombreMostrado,
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 254, message = "El correo admite hasta 254 caracteres")
        String correo,
        @NotEmpty(message = "Asigna al menos un rol a la cuenta")
        @Valid
        List<AsignacionRequest> asignaciones) {
}

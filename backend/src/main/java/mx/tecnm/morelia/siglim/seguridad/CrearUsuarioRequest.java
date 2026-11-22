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
 * <p>Con {@code curp}, el perfil nace vinculado a la persona registrada con esa CURP
 * ({@code perfiles.persona_id}), para que {@code persona_usuario()} y {@code es_titular()} funcionen
 * en el ámbito {@code PROPIO} (DEC-155). La CURP se recorta y se pasa a mayúsculas en el servicio,
 * que valida su formato; sin ella, la invitación no cambia.
 *
 * <p>Refs: H-03.1, DEF-10.1, RF-094, DEC-155
 *
 * @param nombreMostrado nombre que verá la persona en la aplicación
 * @param correo         correo que recibirá la invitación
 * @param asignaciones   al menos un rol; si una asignación omite el ámbito, se usa el
 *                       predeterminado del rol
 * @param curp           opcional: CURP de la persona ya registrada a la que se vincula la cuenta
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
        List<AsignacionRequest> asignaciones,
        @Size(max = 18, message = "La CURP admite hasta 18 caracteres")
        String curp) {
}

package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Campos del Formato 2, carta de oferta de cancha deportiva (RF-116, R8). Se guardan en
 * {@code solicitudes_cancha.datos_formato}; los opcionales vacíos se omiten.
 *
 * <p>Contienen nombres y teléfonos de contacto: no van a la auditoría ni a la bitácora.
 *
 * <p>Refs: B-14, RF-116, RF-118, R8, DEC-35
 *
 * @param lugar               lugar de emisión de la carta
 * @param oferenteNombre      nombre completo de quien ofrece
 * @param oferenteCaracter    propietario, administrador o representante
 * @param institucion         institución del oferente, si aplica
 * @param nombreCancha        nombre de la cancha ofrecida
 * @param ubicacion           domicilio completo
 * @param tipoSuperficie      pasto natural, sintético, tierra u otra
 * @param disponibilidad      días y horarios disponibles
 * @param capacidadAproximada número aproximado de personas
 * @param serviciosDisponibles sanitarios, vestidores, iluminación, estacionamiento u otros
 * @param costoUso            importe por partido, hora o jornada, o «gratuito»
 * @param condicionesPago     forma y plazo de pago, si aplica
 * @param personaResponsable  persona responsable de la cancha
 * @param telefonoContacto    teléfono de contacto
 * @param correoContacto      correo de contacto, si aplica
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatosOferta(
        @NotBlank(message = "Indica el lugar de emisión de la carta")
        @Size(max = 120, message = "El lugar admite hasta 120 caracteres")
        String lugar,
        @NotBlank(message = "Indica el nombre de quien ofrece la cancha")
        @Size(max = 150, message = "El nombre del oferente admite hasta 150 caracteres")
        String oferenteNombre,
        @NotBlank(message = "Indica si es propietario, administrador o representante")
        @Size(max = 100, message = "El carácter del oferente admite hasta 100 caracteres")
        String oferenteCaracter,
        @Size(max = 150, message = "La institución admite hasta 150 caracteres")
        String institucion,
        @NotBlank(message = "Indica el nombre de la cancha")
        @Size(max = 120, message = "El nombre de la cancha admite hasta 120 caracteres")
        String nombreCancha,
        @NotBlank(message = "Indica la ubicación de la cancha")
        @Size(max = 300, message = "La ubicación admite hasta 300 caracteres")
        String ubicacion,
        @NotBlank(message = "Indica el tipo de superficie")
        @Size(max = 60, message = "El tipo de superficie admite hasta 60 caracteres")
        String tipoSuperficie,
        @NotBlank(message = "Indica los días y horarios disponibles")
        @Size(max = 300, message = "La disponibilidad admite hasta 300 caracteres")
        String disponibilidad,
        @PositiveOrZero(message = "La capacidad no puede ser negativa")
        Integer capacidadAproximada,
        @Size(max = 300, message = "Los servicios admiten hasta 300 caracteres")
        String serviciosDisponibles,
        @NotBlank(message = "Indica el costo por uso o si es gratuito")
        @Size(max = 150, message = "El costo por uso admite hasta 150 caracteres")
        String costoUso,
        @Size(max = 300, message = "Las condiciones de pago admiten hasta 300 caracteres")
        String condicionesPago,
        @NotBlank(message = "Indica la persona responsable")
        @Size(max = 150, message = "El responsable admite hasta 150 caracteres")
        String personaResponsable,
        @NotBlank(message = "Indica un teléfono de contacto")
        @Size(max = 20, message = "El teléfono admite hasta 20 caracteres")
        String telefonoContacto,
        @Email(message = "Escribe un correo electrónico válido")
        @Size(max = 150, message = "El correo admite hasta 150 caracteres")
        String correoContacto) {
}

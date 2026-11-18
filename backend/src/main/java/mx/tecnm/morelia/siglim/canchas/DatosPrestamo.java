package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Campos del Formato 1, carta de solicitud de préstamo o comodato de cancha (RF-117, R8). Se guardan
 * en {@code solicitudes_cancha.datos_formato}; los opcionales vacíos se omiten.
 *
 * <p>Contienen nombres y teléfonos de contacto: no van a la auditoría ni a la bitácora.
 *
 * <p>Refs: B-14, RF-117, RF-118, R8, DEC-35
 *
 * @param lugar                 lugar de emisión de la carta (p. ej., «Morelia, Michoacán»)
 * @param dirigidaA             la Liga o un dueño externo (DEC-35)
 * @param destinatarioNombre    propietario o responsable de la cancha a quien va la carta
 * @param destinatarioCargo     cargo o institución del destinatario
 * @param solicitanteNombre     nombre completo de quien suscribe
 * @param solicitanteCargo      cargo de quien suscribe dentro del club
 * @param instalacion           nombre o domicilio de la cancha solicitada
 * @param finalidad             partido oficial, jornada de liga, entrenamiento o evento
 * @param fechaUtilizacion      día de uso
 * @param horaInicio            hora de inicio
 * @param horaTermino           hora de término, posterior al inicio
 * @param actividad             descripción de la actividad deportiva
 * @param equiposParticipantes  equipos que participan, si aplica
 * @param asistentesAproximados número aproximado de asistentes
 * @param responsableActividad  persona responsable de la actividad
 * @param telefonoContacto      teléfono de contacto
 * @param correoContacto        correo de contacto, si aplica
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatosPrestamo(
        @NotBlank(message = "Indica el lugar de emisión de la carta")
        @Size(max = 120, message = "El lugar admite hasta 120 caracteres")
        String lugar,
        @NotNull(message = "Indica si la carta va dirigida a la Liga o a un dueño externo")
        DestinatarioPrestamo dirigidaA,
        @NotBlank(message = "Indica a quién va dirigida la carta")
        @Size(max = 150, message = "El destinatario admite hasta 150 caracteres")
        String destinatarioNombre,
        @NotBlank(message = "Indica el cargo o la institución del destinatario")
        @Size(max = 150, message = "El cargo del destinatario admite hasta 150 caracteres")
        String destinatarioCargo,
        @NotBlank(message = "Indica el nombre de quien solicita")
        @Size(max = 150, message = "El nombre de quien solicita admite hasta 150 caracteres")
        String solicitanteNombre,
        @NotBlank(message = "Indica el cargo de quien solicita")
        @Size(max = 100, message = "El cargo de quien solicita admite hasta 100 caracteres")
        String solicitanteCargo,
        @NotBlank(message = "Indica la instalación solicitada")
        @Size(max = 200, message = "La instalación admite hasta 200 caracteres")
        String instalacion,
        @NotBlank(message = "Indica la finalidad del préstamo")
        @Size(max = 200, message = "La finalidad admite hasta 200 caracteres")
        String finalidad,
        @NotNull(message = "Indica la fecha de utilización")
        LocalDate fechaUtilizacion,
        @NotNull(message = "Indica la hora de inicio")
        LocalTime horaInicio,
        @NotNull(message = "Indica la hora de término")
        LocalTime horaTermino,
        @NotBlank(message = "Describe la actividad deportiva")
        @Size(max = 500, message = "La actividad admite hasta 500 caracteres")
        String actividad,
        @Size(max = 300, message = "Los equipos participantes admiten hasta 300 caracteres")
        String equiposParticipantes,
        @PositiveOrZero(message = "Los asistentes no pueden ser negativos")
        Integer asistentesAproximados,
        @NotBlank(message = "Indica la persona responsable de la actividad")
        @Size(max = 150, message = "El responsable admite hasta 150 caracteres")
        String responsableActividad,
        @NotBlank(message = "Indica un teléfono de contacto")
        @Size(max = 20, message = "El teléfono admite hasta 20 caracteres")
        String telefonoContacto,
        @Email(message = "Escribe un correo electrónico válido")
        @Size(max = 150, message = "El correo admite hasta 150 caracteres")
        String correoContacto) {
}

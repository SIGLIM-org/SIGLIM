package mx.tecnm.morelia.siglim.compartido.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del alta de cuentas por invitación (ADR-0003 punto 5, RF-094).
 *
 * <p>La clave secreta del proyecto de Supabase solo existe en {@code backend/.env}: el backend la
 * usa para llamar a la API de administración de Supabase Auth, que envía la invitación por correo.
 * Nunca se registra en bitácoras ni se envía al frontend (AGENTS.md §9).
 *
 * <p>Queda separada de {@link SiglimPropiedades} para que las pruebas de controlador, que sustituyen
 * el servicio de usuarios, no necesiten un secreto real ni lo validen.
 *
 * <p>Refs: H-03.1, RF-094, ADR-0003, RNF-019, DEF-03
 *
 * @param claveSecreta    clave secreta del proyecto ({@code sb_secret_…} o el JWT de
 *                        {@code service_role}); se envía en las cabeceras de Supabase Auth
 * @param tiempoConexion  [PROPUESTA] límite para abrir la conexión con Supabase Auth
 * @param tiempoRespuesta [PROPUESTA] límite para recibir la respuesta de la invitación
 * @param origenFrontend  origen del frontend ({@code esquema://host[:puerto]}) al que vuelve el
 *                        enlace del correo de invitación; el backend le agrega
 *                        {@code /restablecer-contrasena} y lo envía como {@code redirect_to}. No
 *                        se reutiliza {@code SIGLIM_CORS_ORIGEN} porque es una lista y el enlace
 *                        necesita un solo destino, ni la cabecera {@code Origin} de la petición
 *                        porque los enlaces de correo no se arman con datos del cliente
 */
@Validated
@ConfigurationProperties("siglim.invitaciones")
public record PropiedadesInvitacion(
        @NotBlank(message = "Define SUPABASE_SECRET_KEY en backend/.env")
        String claveSecreta,
        @NotNull
        @DurationMin(seconds = 1) @DurationMax(seconds = 60)
        Duration tiempoConexion,
        @NotNull
        @DurationMin(seconds = 1) @DurationMax(seconds = 120)
        Duration tiempoRespuesta,
        @NotBlank(message = "Define SIGLIM_FRONTEND_ORIGEN en backend/.env")
        @Pattern(regexp = "^https?://[^/\\s*]+$",
                message = "SIGLIM_FRONTEND_ORIGEN es esquema://host[:puerto], sin ruta, diagonal final ni comodín")
        String origenFrontend) {
}

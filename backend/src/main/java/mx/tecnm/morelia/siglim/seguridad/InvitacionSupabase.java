package mx.tecnm.morelia.siglim.seguridad;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import mx.tecnm.morelia.siglim.compartido.config.PropiedadesInvitacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.compartido.error.ServicioNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Invita cuentas con la API de administración de Supabase Auth (ADR-0003 punto 5, RF-094).
 *
 * <p>Llama a {@code POST /auth/v1/invite} con la clave secreta del proyecto en las cabeceras
 * {@code apikey} y {@code Authorization}. La respuesta incluye el {@code id} de {@code auth.users},
 * que el servicio enlaza desde {@code perfiles.usuario_auth_id}.
 *
 * <p><b>Destino del enlace.</b> El correo de invitación vuelve a
 * {@code <origenFrontend>/restablecer-contrasena}, donde el invitado fija su contraseña. Supabase
 * lee ese destino del parámetro de consulta {@code redirect_to} (igual que {@code supabase-js}), no
 * del cuerpo. Si la URL no está en las <em>Redirect URLs</em> del proyecto (Authentication → URL
 * Configuration), Supabase usa la <em>Site URL</em> sin avisar; por eso la URL completa debe estar
 * registrada allí.
 *
 * <p><b>Errores.</b> Se clasifican por el {@code error_code} del cuerpo de Supabase, no por el
 * estado HTTP, porque Supabase usa 400 y 422 para causas muy distintas. Solo {@code email_exists} y
 * {@code user_already_exists} responden {@code 409 DUPLICADO}: el servicio ya revisó los perfiles,
 * así que es una cuenta de Auth sin perfil. Cualquier otro rechazo ({@code error_code} distinto,
 * {@code 409 conflict}, 429, 5xx o un cuerpo que no se puede leer) y la falla de red responden
 * {@code 503 SERVICIO_NO_DISPONIBLE} con un mensaje genérico.
 *
 * <p><b>Bitácora (RNF-011).</b> El cuerpo de la respuesta y la clave secreta nunca se registran:
 * pueden contener datos personales o credenciales (AGENTS.md §9). Por eso el
 * {@link ServicioNoDisponibleException} que nace de una respuesta HTTP <em>no</em> lleva la
 * {@link RestClientResponseException} como causa (su mensaje incluye el cuerpo y el manejador
 * global la registraría con su traza): solo lleva el estado y el {@code error_code}, y este último
 * únicamente si cumple {@code ^[a-z0-9_]{1,64}$} (si no, {@code desconocido}), para evitar la
 * inyección en la bitácora. La falla de red conserva su causa porque no trae cuerpo.
 *
 * <p>Refs: H-03.1, RF-094, ADR-0003, RNF-011, RNF-019, DEF-03
 */
final class InvitacionSupabase implements InvitadorAuth {

    private static final Logger log = LoggerFactory.getLogger(InvitacionSupabase.class);

    private static final String RUTA_INVITACION = "/auth/v1/invite";
    private static final String RUTA_USUARIO = "/auth/v1/admin/users/{id}";

    /**
     * Pantalla del frontend donde el invitado fija su contraseña. Debe coincidir con la ruta
     * {@code /restablecer-contrasena} declarada en {@code frontend/src/app/router.tsx}.
     */
    private static final String RUTA_RESTABLECER = "/restablecer-contrasena";

    /** {@code error_code} de Supabase Auth que significan «el correo ya tiene cuenta». */
    private static final Set<String> CODIGOS_DUPLICADO = Set.of("email_exists", "user_already_exists");

    /** Forma de un {@code error_code} que se puede escribir en la bitácora sin riesgo de inyección. */
    private static final Pattern CODIGO_SEGURO = Pattern.compile("^[a-z0-9_]{1,64}$");

    private final RestClient http;
    private final String destinoEnlace;

    /**
     * @param constructor constructor de {@code RestClient}
     * @param urlSupabase URL del proyecto, sin diagonal final
     * @param propiedades clave secreta, tiempos límite y origen del frontend al que vuelve el enlace
     */
    InvitacionSupabase(RestClient.Builder constructor, String urlSupabase, PropiedadesInvitacion propiedades) {
        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(propiedades.tiempoConexion())
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(propiedades.tiempoRespuesta());

        this.destinoEnlace = propiedades.origenFrontend() + RUTA_RESTABLECER;
        this.http = constructor
                .baseUrl(urlSupabase)
                .requestFactory(fabrica)
                // Supabase acepta la clave secreta en «apikey» y, para la API de administración de
                // Auth, también como portador. Se envían las dos para cubrir la clave nueva y la
                // heredada (service_role) sin elegir una en el código.
                .defaultHeader("apikey", propiedades.claveSecreta())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + propiedades.claveSecreta())
                .build();
    }

    @Override
    public UUID invitar(String correo) {
        try {
            RespuestaInvitacion respuesta = http.post()
                    // redirect_to va en el query string: Supabase no lo lee del cuerpo.
                    .uri(b -> b.path(RUTA_INVITACION).queryParam("redirect_to", destinoEnlace).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", correo))
                    .retrieve()
                    .body(RespuestaInvitacion.class);
            if (respuesta == null || respuesta.id() == null) {
                throw new ServicioNoDisponibleException(
                        "Supabase Auth no devolvió el identificador de la cuenta invitada", null);
            }
            return respuesta.id();
        } catch (RestClientResponseException error) {
            String codigo = codigoError(error);
            if (codigo != null && CODIGOS_DUPLICADO.contains(codigo)) {
                throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                        "Ya existe una cuenta de acceso con ese correo.");
            }
            String codigoSeguro = codigo != null && CODIGO_SEGURO.matcher(codigo).matches()
                    ? codigo : "desconocido";
            // Sin causa a propósito: el mensaje de la excepción HTTP incluye el cuerpo (RNF-011).
            throw new ServicioNoDisponibleException("Supabase Auth respondió " + error.getStatusCode().value()
                    + " (error_code=" + codigoSeguro + ") al invitar una cuenta", null);
        } catch (ResourceAccessException error) {
            throw new ServicioNoDisponibleException(
                    "No se pudo contactar a Supabase Auth para invitar una cuenta", error);
        }
    }

    /** {@code error_code} del cuerpo de Supabase, o {@code null} si no hay cuerpo o no se puede leer. */
    private static String codigoError(RestClientResponseException error) {
        try {
            ErrorAuth cuerpo = error.getResponseBodyAs(ErrorAuth.class);
            return cuerpo == null ? null : cuerpo.errorCode();
        } catch (RuntimeException ilegible) {
            return null;
        }
    }

    @Override
    public void eliminar(UUID usuarioAuthId) {
        try {
            http.delete()
                    .uri(RUTA_USUARIO, usuarioAuthId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException error) {
            // Compensación de mejor esfuerzo: la operación de negocio ya falló y su error es el que
            // debe llegar al usuario. No se registra el cuerpo (podría contener datos personales).
            log.warn("No se pudo eliminar la cuenta de Auth {} tras fallar la invitación: {}",
                    usuarioAuthId, error.getClass().getSimpleName());
        }
    }

    /** Respuesta mínima de {@code POST /auth/v1/invite}; el resto de campos no se usa. */
    private record RespuestaInvitacion(UUID id) {
    }

    /** Cuerpo de error de Supabase Auth; solo interesa {@code error_code}, el resto se ignora. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorAuth(@JsonProperty("error_code") String errorCode) {
    }
}

package mx.tecnm.morelia.siglim.seguridad;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;
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
 * que el servicio enlaza desde {@code perfiles.usuario_auth_id}. El enlace del correo usa la
 * <em>Site URL</em> configurada en Supabase Auth; el backend no la fija.
 *
 * <p><b>Errores.</b> Un correo ya registrado responde {@code 409 DUPLICADO}; cualquier falla de
 * red, credenciales o disponibilidad responde {@code 503 SERVICIO_NO_DISPONIBLE} con un mensaje
 * genérico. El cuerpo de la respuesta y la clave secreta nunca se registran: pueden contener datos
 * personales o credenciales (RNF-011, AGENTS.md §9).
 *
 * <p>Refs: H-03.1, RF-094, ADR-0003, RNF-011, RNF-019
 */
final class InvitacionSupabase implements InvitadorAuth {

    private static final Logger log = LoggerFactory.getLogger(InvitacionSupabase.class);

    private static final String RUTA_INVITACION = "/auth/v1/invite";
    private static final String RUTA_USUARIO = "/auth/v1/admin/users/{id}";

    private final RestClient http;

    /**
     * @param constructor constructor de {@code RestClient}; las pruebas lo sustituyen con
     *                    {@code MockRestServiceServer}
     * @param urlSupabase URL del proyecto, sin diagonal final
     */
    InvitacionSupabase(RestClient.Builder constructor, String urlSupabase, PropiedadesInvitacion propiedades) {
        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(propiedades.tiempoConexion())
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(propiedades.tiempoRespuesta());

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
                    .uri(RUTA_INVITACION)
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
            int estado = error.getStatusCode().value();
            if (estado == 400 || estado == 409 || estado == 422) {
                // Supabase usa 400/422 cuando el correo ya tiene cuenta; el servicio ya revisó los
                // perfiles, así que aquí solo puede quedar una cuenta de Auth sin perfil.
                throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                        "Ya existe una cuenta de acceso con ese correo.");
            }
            throw new ServicioNoDisponibleException(
                    "Supabase Auth respondió " + estado + " al invitar una cuenta", error);
        } catch (ResourceAccessException error) {
            throw new ServicioNoDisponibleException(
                    "No se pudo contactar a Supabase Auth para invitar una cuenta", error);
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
}

package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import mx.tecnm.morelia.siglim.compartido.config.PropiedadesInvitacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.compartido.error.ServicioNoDisponibleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Adaptador HTTP de invitaciones contra un servidor local: cabeceras, {@code redirect_to},
 * respuesta y clasificación de errores por {@code error_code}. No llama a Supabase ni usa la red
 * externa.
 */
class InvitacionSupabaseTest {

    private static final String CLAVE = "clave-secreta-de-prueba";
    private static final String ORIGEN_FRONTEND = "http://localhost:5173";
    private static final UUID AUTH = UUID.fromString("6f1d2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b");
    private static final String JSON = "application/json";

    private HttpServer servidor;
    private final AtomicReference<String> apikey = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> consulta = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/auth/v1/invite", intercambio -> {
            apikey.set(intercambio.getRequestHeaders().getFirst("apikey"));
            authorization.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            metodo.set(intercambio.getRequestMethod());
            consulta.set(intercambio.getRequestURI().getRawQuery());
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder(intercambio, 200, JSON, "{\"id\":\"" + AUTH + "\"}");
        });
        servidor.createContext("/auth/v1/admin/users/" + AUTH, intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            responder(intercambio, 204, JSON, "");
        });
        servidor.start();
    }

    @AfterEach
    void apagarServidor() {
        servidor.stop(0);
    }

    private static void responder(HttpExchange intercambio, int estado, String tipo, String cuerpo)
            throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", tipo);
        if (bytes.length == 0) {
            intercambio.sendResponseHeaders(estado, -1);
        } else {
            intercambio.sendResponseHeaders(estado, bytes.length);
            intercambio.getResponseBody().write(bytes);
        }
        intercambio.close();
    }

    /** Sustituye la respuesta de {@code /auth/v1/invite} por un error de Supabase Auth. */
    private void invitacionResponde(int estado, String tipo, String cuerpoRespuesta) {
        servidor.removeContext("/auth/v1/invite");
        servidor.createContext("/auth/v1/invite",
                intercambio -> responder(intercambio, estado, tipo, cuerpoRespuesta));
    }

    private InvitacionSupabase adaptador() {
        return new InvitacionSupabase(RestClient.builder(), url(),
                new PropiedadesInvitacion(CLAVE, Duration.ofSeconds(2), Duration.ofSeconds(5), ORIGEN_FRONTEND));
    }

    private String url() {
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    private void esperaDuplicado() {
        assertThatThrownBy(() -> adaptador().invitar("ana@ejemplo.mx"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado().value()).isEqualTo(409);
                    assertThat(error.codigo()).isEqualTo(ErroresTecnicos.DUPLICADO);
                    assertThat(error.getMessage()).isEqualTo("Ya existe una cuenta de acceso con ese correo.");
                });
    }

    private void esperaServicioNoDisponible() {
        assertThatThrownBy(() -> adaptador().invitar("ana@ejemplo.mx"))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    @DisplayName("H-03.1: invita con la clave secreta en «apikey» y «Authorization» y devuelve el id de Auth")
    void invitar() {
        UUID id = adaptador().invitar("ana@ejemplo.mx");

        assertThat(id).isEqualTo(AUTH);
        assertThat(metodo.get()).isEqualTo("POST");
        assertThat(apikey.get()).isEqualTo(CLAVE);
        assertThat(authorization.get()).isEqualTo("Bearer " + CLAVE);
    }

    @Test
    @DisplayName("DEF-03: la invitación envía redirect_to con /restablecer-contrasena del origen configurado")
    void invitarEnviaRedirectTo() {
        adaptador().invitar("ana@ejemplo.mx");

        assertThat(URLDecoder.decode(consulta.get(), StandardCharsets.UTF_8))
                .isEqualTo("redirect_to=http://localhost:5173/restablecer-contrasena");
        assertThat(cuerpo.get()).isEqualTo("{\"email\":\"ana@ejemplo.mx\"}");
    }

    @Test
    @DisplayName("RF-094: un correo ya registrado en Auth (email_exists) responde 409 DUPLICADO")
    void invitarCorreoExistente() {
        invitacionResponde(422, JSON,
                "{\"code\":422,\"error_code\":\"email_exists\",\"msg\":\"A user with this email address has already been registered\"}");

        esperaDuplicado();
    }

    @Test
    @DisplayName("DEF-03: user_already_exists también responde 409 DUPLICADO")
    void invitarUsuarioYaExiste() {
        invitacionResponde(422, JSON,
                "{\"code\":422,\"error_code\":\"user_already_exists\",\"msg\":\"User already registered\"}");

        esperaDuplicado();
    }

    @Test
    @DisplayName("DEF-03: email_address_not_authorized responde 503 y no DUPLICADO")
    void invitarCorreoNoAutorizado() {
        invitacionResponde(400, JSON,
                "{\"code\":400,\"error_code\":\"email_address_not_authorized\",\"msg\":\"Email address not authorized\"}");

        esperaServicioNoDisponible();
    }

    @Test
    @DisplayName("DEF-03: un 422 con otro error_code responde 503")
    void invitarValidacionFallida() {
        invitacionResponde(422, JSON,
                "{\"code\":422,\"error_code\":\"validation_failed\",\"msg\":\"Unable to validate email address\"}");

        esperaServicioNoDisponible();
    }

    @Test
    @DisplayName("DEF-03: un 409 conflict de Supabase responde 503")
    void invitarConflictoDeSupabase() {
        invitacionResponde(409, JSON, "{\"code\":409,\"error_code\":\"conflict\",\"msg\":\"Conflict\"}");

        esperaServicioNoDisponible();
    }

    @Test
    @DisplayName("DEF-03: un 4xx sin cuerpo JSON responde 503")
    void invitarSinCuerpoJson() {
        invitacionResponde(400, "text/plain", "Bad Request");
        esperaServicioNoDisponible();

        invitacionResponde(422, JSON, "");
        esperaServicioNoDisponible();
    }

    @Test
    @DisplayName("ADR-0003: una falla de Supabase Auth responde 503 SERVICIO_NO_DISPONIBLE")
    void invitarServicioCaido() {
        invitacionResponde(500, JSON, "{\"message\":\"boom\"}");

        esperaServicioNoDisponible();
    }

    @Test
    @DisplayName("DEF-03: el 503 no conserva el cuerpo de la respuesta de Supabase")
    void elErrorNoConservaElCuerpo() {
        invitacionResponde(400, JSON,
                "{\"code\":400,\"error_code\":\"email_address_not_authorized\",\"msg\":\"Email address ana@ejemplo.mx is not authorized\"}");

        assertThatThrownBy(() -> adaptador().invitar("ana@ejemplo.mx"))
                .isInstanceOfSatisfying(ServicioNoDisponibleException.class, error -> {
                    assertThat(error.getCause()).isNull();
                    assertThat(error.getMessage())
                            .doesNotContain("ana@ejemplo.mx")
                            .contains("error_code=email_address_not_authorized")
                            .contains("400");
                });
    }

    @Test
    @DisplayName("DEF-03: un error_code con caracteres no permitidos se registra como desconocido")
    void codigoInseguroSeRegistraComoDesconocido() {
        invitacionResponde(400, JSON, "{\"error_code\":\"falso\\n[ERROR] inyectado <script>\"}");

        assertThatThrownBy(() -> adaptador().invitar("ana@ejemplo.mx"))
                .isInstanceOfSatisfying(ServicioNoDisponibleException.class, error -> {
                    assertThat(error.getMessage()).contains("error_code=desconocido");
                    assertThat(error.getMessage()).doesNotContain("inyectado").doesNotContain("\n");
                });
    }

    @Test
    @DisplayName("ADR-0003: la compensación elimina la cuenta de Auth sin propagar el error")
    void eliminarCuenta() {
        adaptador().eliminar(AUTH);

        assertThat(metodo.get()).isEqualTo("DELETE");
    }
}

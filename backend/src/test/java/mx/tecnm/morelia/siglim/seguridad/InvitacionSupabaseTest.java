package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
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
 * Adaptador HTTP de invitaciones contra un servidor local: cabeceras, respuesta y traducción de
 * errores. No llama a Supabase ni usa la red externa.
 */
class InvitacionSupabaseTest {

    private static final String CLAVE = "clave-secreta-de-prueba";
    private static final UUID AUTH = UUID.fromString("6f1d2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b");

    private HttpServer servidor;
    private final AtomicReference<String> apikey = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> metodo = new AtomicReference<>();

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/auth/v1/invite", intercambio -> {
            apikey.set(intercambio.getRequestHeaders().getFirst("apikey"));
            authorization.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            metodo.set(intercambio.getRequestMethod());
            responder(intercambio, 200, "{\"id\":\"" + AUTH + "\"}");
        });
        servidor.createContext("/auth/v1/admin/users/" + AUTH, intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            responder(intercambio, 204, "");
        });
        servidor.start();
    }

    @AfterEach
    void apagarServidor() {
        servidor.stop(0);
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        if (bytes.length == 0) {
            intercambio.sendResponseHeaders(estado, -1);
        } else {
            intercambio.sendResponseHeaders(estado, bytes.length);
            intercambio.getResponseBody().write(bytes);
        }
        intercambio.close();
    }

    private InvitacionSupabase adaptador() {
        return new InvitacionSupabase(RestClient.builder(), url(),
                new PropiedadesInvitacion(CLAVE, Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }

    private String url() {
        return "http://localhost:" + servidor.getAddress().getPort();
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
    @DisplayName("RF-094: un correo ya registrado en Auth responde 409 DUPLICADO")
    void invitarCorreoExistente() throws IOException {
        servidor.removeContext("/auth/v1/invite");
        servidor.createContext("/auth/v1/invite",
                intercambio -> responder(intercambio, 422, "{\"error_code\":\"email_exists\"}"));

        assertThatThrownBy(() -> adaptador().invitar("ana@ejemplo.mx"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado().value()).isEqualTo(409);
                    assertThat(error.codigo()).isEqualTo(ErroresTecnicos.DUPLICADO);
                });
    }

    @Test
    @DisplayName("ADR-0003: una falla de Supabase Auth responde 503 SERVICIO_NO_DISPONIBLE")
    void invitarServicioCaido() throws IOException {
        servidor.removeContext("/auth/v1/invite");
        servidor.createContext("/auth/v1/invite",
                intercambio -> responder(intercambio, 500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> adaptador().invitar("ana@ejemplo.mx"))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    @DisplayName("ADR-0003: la compensación elimina la cuenta de Auth sin propagar el error")
    void eliminarCuenta() {
        adaptador().eliminar(AUTH);

        assertThat(metodo.get()).isEqualTo("DELETE");
    }
}

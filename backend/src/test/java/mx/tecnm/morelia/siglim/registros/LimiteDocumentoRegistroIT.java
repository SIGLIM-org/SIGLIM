package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Límite de tamaño de un documento de registro con el Tomcat real (H-10.1, DEC-91): igual que
 * {@code LimiteComprobanteIT}, un archivo mayor que {@code spring.servlet.multipart.max-file-size}
 * responde {@code 413 ARCHIVO_DEMASIADO_GRANDE} en RFC 9457 antes de llegar al controlador (y, por
 * tanto, antes del permiso y del 404). Usa las mismas propiedades para compartir su contexto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "SUPABASE_URL=https://proyecto-prueba.supabase.co",
        "SUPABASE_SECRET_KEY=clave-secreta-de-prueba",
        "SIGLIM_FRONTEND_ORIGEN=http://localhost:5173",
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=http://localhost:5173",
        "spring.datasource.hikari.maximum-pool-size=1"
})
class LimiteDocumentoRegistroIT extends BaseDatosPrueba {

    private static final String FRONTERA = "----frontera-siglim-prueba";

    @Value("${local.server.port}")
    private int puerto;

    @Value("${spring.servlet.multipart.max-file-size}")
    private String tamanoMaximo;

    @MockitoBean
    private JwtDecoder decodificador;

    @Test
    @DisplayName("RF-108 · DEC-91: un documento mayor que max-file-size responde 413 ARCHIVO_DEMASIADO_GRANDE en RFC 9457")
    void documentoDemasiadoGrande() throws IOException, InterruptedException {
        given(decodificador.decode(anyString())).willReturn(Jwt.withTokenValue("token-de-prueba")
                .header("alg", "ES256").subject(SUB_ADMIN.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build());
        assertThat(tamanoMaximo).isEqualTo("5MB");
        byte[] pdf = Arrays.copyOf("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII), 5 * 1024 * 1024 + 1024);

        HttpRequest peticion = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/v1/registros-jugador/" + UUID.randomUUID() + "/documentos"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-de-prueba")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + FRONTERA)
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpoComoNavegador(pdf)))
                .build();
        HttpResponse<String> respuesta;
        try (HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        assertThat(respuesta.statusCode()).isEqualTo(413);
        assertThat(respuesta.headers().firstValue(HttpHeaders.CONTENT_TYPE))
                .hasValue(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(respuesta.body()).contains("\"codigo\":\"ARCHIVO_DEMASIADO_GRANDE\"", "\"status\":413");
    }

    /** Campos de texto primero y el archivo al final, sin {@code Content-Length} por parte. */
    private static byte[] cuerpoComoNavegador(byte[] archivo) throws IOException {
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
        escribir(cuerpo, "--" + FRONTERA + "\r\nContent-Disposition: form-data; name=\"tipoDocumentoId\"\r\n\r\n5\r\n");
        escribir(cuerpo, "--" + FRONTERA + "\r\nContent-Disposition: form-data; name=\"archivo\"; "
                + "filename=\"identificacion.pdf\"\r\nContent-Type: application/pdf\r\n\r\n");
        cuerpo.write(archivo);
        escribir(cuerpo, "\r\n--" + FRONTERA + "--\r\n");
        return cuerpo.toByteArray();
    }

    private static void escribir(ByteArrayOutputStream cuerpo, String texto) {
        cuerpo.writeBytes(texto.getBytes(StandardCharsets.US_ASCII));
    }
}

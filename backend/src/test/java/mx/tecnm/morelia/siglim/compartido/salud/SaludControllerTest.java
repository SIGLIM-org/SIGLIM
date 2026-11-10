package mx.tecnm.morelia.siglim.compartido.salud;

import static org.assertj.core.api.Assertions.assertThat;

import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Prueba de controlador de {@code GET /api/v1/salud} con la cadena de seguridad real: es pública
 * (convenciones de API §5), así que responde sin token.
 */
@WebMvcTest(controllers = SaludController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class SaludControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("T-02: GET /api/v1/salud responde 200 con estado OK sin token")
    void respondeQueLaApiEstaEnEjecucion() {
        assertThat(mvc.get().uri("/api/v1/salud"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$.estado").isEqualTo("OK");
    }
}

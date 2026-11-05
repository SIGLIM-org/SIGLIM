package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Contrato HTTP del catálogo de roles (convenciones de API §6.1). */
@WebMvcTest(controllers = RolController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class RolControllerTest {

    private static final UUID ROL = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RolService servicio;

    @Test
    @DisplayName("RF-094: GET /roles devuelve el catálogo con el ámbito predeterminado")
    void listar() {
        given(servicio.listar()).willReturn(List.of(new RolDto(ROL, "DIRECTIVA_CLUB", "Directiva de club",
                "Tarjetón, equipos y jugadores de su club", Ambito.CLUB, true)));

        MvcTestResult resultado = mvc.get().uri("/api/v1/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SeguridadPrueba.tokenValido()).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].codigo").isEqualTo("DIRECTIVA_CLUB");
        assertThat(resultado).bodyJson().extractingPath("$[0].ambitoPredeterminado").isEqualTo("CLUB");
        assertThat(resultado).bodyJson().extractingPath("$[0].asignable").isEqualTo(true);
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/roles")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/roles")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar()).willThrow(error))
                .pruebas();
    }
}

package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Contrato HTTP de la consulta de personas por CURP (convenciones de API §6.3). */
@WebMvcTest(controllers = PersonaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class PersonaControllerTest {

    private static final UUID ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String CURP = "GODE840101HDFRNS09";
    private static final String RUTA = "/api/v1/personas?curp=" + CURP;
    private static final PersonaDto PERSONA = new PersonaDto(ID, CURP, "Ana", "López", null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PersonaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-010: GET /personas?curp= devuelve la identidad sin datos de contacto")
    void buscar() {
        given(servicio.buscarPorCurp(CURP)).willReturn(PERSONA);

        MvcTestResult resultado = mvc.get().uri(RUTA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$.curp").isEqualTo(CURP);
        assertThat(resultado).bodyJson().extractingPath("$.nombre").isEqualTo("Ana");
        assertThat(resultado).bodyJson().doesNotHavePath("$.apellidoMaterno");
        Mockito.verify(servicio).buscarPorCurp(CURP);
    }

    @Test
    @DisplayName("RF-010: una CURP mal formada responde 400 VALIDACION")
    void formatoInvalido() {
        given(servicio.buscarPorCurp("GODE"))
                .willThrow(ReglaNegocioException.invalida("VALIDACION",
                        "La CURP debe tener 18 caracteres y el formato oficial."));

        MvcTestResult resultado = mvc.get().uri("/api/v1/personas?curp=GODE")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("VAL-024: una persona inexistente responde 404 NO_ENCONTRADO")
    void noExiste() {
        given(servicio.buscarPorCurp(CURP)).willThrow(new RecursoNoEncontradoException("Persona"));

        MvcTestResult resultado = mvc.get().uri(RUTA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_ENCONTRADO);
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/personas")
    Stream<DynamicTest> seguridadBuscar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, RUTA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.buscarPorCurp(CURP)).willThrow(error))
                .pruebas();
    }
}

package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Contrato HTTP del aviso vigente y del consentimiento (convenciones de API §6.1; H-09.1). */
@WebMvcTest(controllers = ConsentimientoController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class ConsentimientoControllerTest {

    private static final UUID PERSONA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TUTOR = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID AVISO = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final String RUTA_AVISO = "/api/v1/avisos-privacidad/vigente";
    private static final String RUTA_CONSENTIMIENTO = "/api/v1/personas/{id}/consentimientos";
    private static final String CUERPO = """
            {"avisoId":"77777777-7777-4777-8777-777777777777","finalidades":["REGISTRO_DEPORTIVO"],
             "otorgadoPorId":"44444444-4444-4444-8444-444444444444"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ConsentimientoService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("DEC-80: GET /avisos-privacidad/vigente devuelve el aviso con esDemo y no-store")
    void avisoVigente() {
        given(servicio.avisoVigente(null)).willReturn(new AvisoPrivacidadDto(AVISO, TipoAvisoPrivacidad.SIMPLIFICADO,
                "v1", "Aviso de demostración", List.of("REGISTRO_DEPORTIVO", "DIFUSION"),
                LocalDate.parse("2026-11-11"), true));

        MvcTestResult resultado = mvc.get().uri(RUTA_AVISO)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.tipo").isEqualTo("SIMPLIFICADO");
        assertThat(resultado).bodyJson().extractingPath("$.vigenteDesde").isEqualTo("2026-11-11");
        assertThat(resultado).bodyJson().extractingPath("$.esDemo").isEqualTo(true);
    }

    @Test
    @DisplayName("DEC-80: el tipo viaja al servicio y un tipo inválido responde 400 VALIDACION")
    void avisoTipoInvalido() {
        given(servicio.avisoVigente("OTRO")).willThrow(ReglaNegocioException.invalida("VALIDACION",
                "El tipo de aviso debe ser INTEGRAL o SIMPLIFICADO."));

        MvcTestResult resultado = mvc.get().uri(RUTA_AVISO + "?tipo=OTRO")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VALIDACION");
    }

    @Test
    @DisplayName("DEC-81: POST /personas/{id}/consentimientos responde 201 sin Location")
    void registrar() {
        given(servicio.registrar(eq(PERSONA), any())).willReturn(new ConsentimientoDto(UUID.randomUUID(), PERSONA,
                TUTOR, AVISO, List.of("REGISTRO_DEPORTIVO"), OffsetDateTime.parse("2026-11-11T12:00:00-06:00")));

        MvcTestResult resultado = mvc.post().uri(RUTA_CONSENTIMIENTO, PERSONA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).doesNotContainHeader(HttpHeaders.LOCATION);
        assertThat(resultado).bodyJson().extractingPath("$.otorgadoPorId").isEqualTo(TUTOR.toString());
        Mockito.verify(servicio).registrar(PERSONA, new RegistrarConsentimientoRequest(AVISO,
                List.of("REGISTRO_DEPORTIVO"), TUTOR));
    }

    @Test
    @DisplayName("VAL-025: un menor sin tutor vigente responde 422 VAL-025")
    void menorSinTutor() {
        given(servicio.registrar(eq(PERSONA), any())).willThrow(ReglaNegocioException.noProcesable("VAL-025",
                "El consentimiento de un menor lo otorga su tutor."));

        MvcTestResult resultado = mvc.post().uri(RUTA_CONSENTIMIENTO, PERSONA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-025");
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/avisos-privacidad/vigente (solo sesión)")
    Stream<DynamicTest> seguridadAviso() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, RUTA_AVISO)
                .dobles(servicio)
                .soloSesion()
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/personas/{id}/consentimientos")
    Stream<DynamicTest> seguridadConsentimiento() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, RUTA_CONSENTIMIENTO, PERSONA)
                .cuerpoJson(CUERPO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrar(eq(PERSONA), any())).willThrow(error))
                .pruebas();
    }
}

package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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

/** Contrato HTTP del pago simulado (convenciones de API §6.13) con la cadena de seguridad real. */
@WebMvcTest(controllers = IntentoPagoController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class IntentoPagoControllerTest {

    private static final UUID COBRO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID INTENTO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String RUTA_INICIO = "/api/v1/cobros/{cobroId}/intentos-pago";
    private static final String BASE_INICIO = "/api/v1/cobros/" + COBRO + "/intentos-pago";
    private static final String RUTA_CONFIRMACION = "/api/v1/intentos-pago/{id}/confirmacion";
    private static final String BASE_CONFIRMACION = "/api/v1/intentos-pago/" + INTENTO + "/confirmacion";
    private static final String REFERENCIA = "SIM-20300301-ABC123";
    private static final IntentoPagoDto PENDIENTE = new IntentoPagoDto(INTENTO, REFERENCIA, "PENDIENTE",
            OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), null);
    private static final IntentoPagoDto APROBADO = new IntentoPagoDto(INTENTO, REFERENCIA, "APROBADO",
            OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), OffsetDateTime.parse("2030-03-01T06:05:00-06:00"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PagoSimuladoService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private static String cuerpo(MvcTestResult resultado) {
        try {
            return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 siempre está disponible", e);
        }
    }

    // ── Inicio (RF-138) ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-138: POST del cobro responde 201 con Location y la referencia ficticia PENDIENTE")
    void iniciar() {
        given(servicio.iniciar(COBRO)).willReturn(PENDIENTE);

        MvcTestResult resultado = mvc.post().uri(BASE_INICIO)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION,
                "/api/v1/intentos-pago/" + INTENTO);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(INTENTO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.referencia").isEqualTo(REFERENCIA);
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE");
        assertThat(cuerpo(resultado)).doesNotContain("resueltoEn");
    }

    @Test
    @DisplayName("VAL-028: el inicio de un cobro PAGADO o VENCIDO responde 409")
    void iniciarCobroNoPagable() {
        given(servicio.iniciar(COBRO)).willThrow(ReglaNegocioException.conflicto("VAL-028",
                "El cobro está VENCIDO; no se puede pagar en línea. Comunícate con Tesorería."));

        MvcTestResult resultado = mvc.post().uri(BASE_INICIO)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-028");
        assertThat(resultado).bodyJson().extractingPath("$.detail").asString().contains("VENCIDO");
    }

    @Test
    @DisplayName("VAL-024: el inicio de un cobro inexistente responde 404")
    void iniciarCobroInexistente() {
        given(servicio.iniciar(COBRO)).willThrow(new RecursoNoEncontradoException("Cobro " + COBRO));

        MvcTestResult resultado = mvc.post().uri(BASE_INICIO)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_ENCONTRADO);
    }

    // ── Confirmación (RF-138, RNF-004) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-138: confirmar APROBADO responde 200 con el intento resuelto y su fecha")
    void confirmar() {
        given(servicio.confirmar(INTENTO, ResultadoPagoSimulado.APROBADO)).willReturn(APROBADO);

        MvcTestResult resultado = mvc.post().uri(BASE_CONFIRMACION)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultado\":\"APROBADO\"}")
                .exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(INTENTO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("APROBADO");
        assertThat(resultado).bodyJson().extractingPath("$.resueltoEn").isNotNull();
        org.mockito.Mockito.verify(servicio).confirmar(INTENTO, ResultadoPagoSimulado.APROBADO);
    }

    @Test
    @DisplayName("RF-138: sin resultado o con un resultado desconocido responde 400 VALIDACION")
    void confirmarCamposInvalidos() {
        MvcTestResult sinResultado = mvc.post().uri(BASE_CONFIRMACION)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();
        assertThat(sinResultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(sinResultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);

        MvcTestResult desconocido = mvc.post().uri(BASE_CONFIRMACION)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"TAL_VEZ\"}").exchange();
        assertThat(desconocido).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(desconocido).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("VAL-028 y CONCURRENCIA: los errores del servicio llegan como 409 en RFC 9457")
    void confirmarErroresDeNegocio() {
        given(servicio.confirmar(INTENTO, ResultadoPagoSimulado.APROBADO))
                .willThrow(ReglaNegocioException.conflicto("VAL-028", "El cobro está PAGADO; no se puede pagar en línea."))
                .willThrow(ReglaNegocioException.conflicto(ErroresTecnicos.CONCURRENCIA,
                        "Otra confirmación de pago ganó la carrera."));

        MvcTestResult val028 = mvc.post().uri(BASE_CONFIRMACION)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"APROBADO\"}").exchange();
        assertThat(val028).hasStatus(HttpStatus.CONFLICT);
        assertThat(val028).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-028");

        MvcTestResult concurrencia = mvc.post().uri(BASE_CONFIRMACION)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"APROBADO\"}").exchange();
        assertThat(concurrencia).hasStatus(HttpStatus.CONFLICT);
        assertThat(concurrencia).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.CONCURRENCIA);
    }

    @Test
    @DisplayName("VAL-024: confirmar un intento inexistente responde 404")
    void confirmarIntentoInexistente() {
        given(servicio.confirmar(INTENTO, ResultadoPagoSimulado.RECHAZADO))
                .willThrow(new RecursoNoEncontradoException("Intento de pago " + INTENTO));

        MvcTestResult resultado = mvc.post().uri(BASE_CONFIRMACION)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"RECHAZADO\"}").exchange();

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_ENCONTRADO);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/cobros/{id}/intentos-pago")
    Stream<DynamicTest> seguridadIniciar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, RUTA_INICIO, COBRO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.iniciar(COBRO)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/intentos-pago/{id}/confirmacion")
    Stream<DynamicTest> seguridadConfirmar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, RUTA_CONFIRMACION, INTENTO)
                .cuerpoJson("{\"resultado\":\"APROBADO\"}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.confirmar(INTENTO, ResultadoPagoSimulado.APROBADO))
                        .willThrow(error))
                .pruebas();
    }
}

package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Contrato HTTP de la disponibilidad arbitral (convenciones de API §6.5) con la seguridad real. */
@WebMvcTest(controllers = DisponibilidadArbitralController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class DisponibilidadArbitralControllerTest {

    private static final UUID ARBITRO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DISPONIBILIDAD = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final String BASE = "/api/v1/arbitros/" + ARBITRO + "/disponibilidad";
    private static final OffsetDateTime DESDE = OffsetDateTime.parse("2030-08-10T09:00:00-06:00");
    private static final OffsetDateTime HASTA = OffsetDateTime.parse("2030-08-10T12:00:00-06:00");
    private static final String CUERPO = """
            {"periodo":{"desde":"2030-08-10T09:00:00-06:00","hasta":"2030-08-10T12:00:00-06:00"}}
            """;
    private static final DisponibilidadArbitralDto REGISTRO = new DisponibilidadArbitralDto(
            DISPONIBILIDAD, ARBITRO, new PeriodoArbitral(DESDE, HASTA), true, DESDE);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private DisponibilidadArbitralService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-022: GET devuelve los periodos del árbitro")
    void listar() {
        given(servicio.listar(ARBITRO, null, null)).willReturn(List.of(REGISTRO));

        MvcTestResult resultado = mvc.get().uri(BASE)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].id").isEqualTo(DISPONIBILIDAD.toString());
        assertThat(resultado).bodyJson().extractingPath("$[0].arbitroId").isEqualTo(ARBITRO.toString());
        assertThat(resultado).bodyJson().extractingPath("$[0].periodo.desde")
                .isEqualTo("2030-08-10T09:00:00-06:00");
        assertThat(resultado).bodyJson().extractingPath("$[0].periodo.hasta")
                .isEqualTo("2030-08-10T12:00:00-06:00");
        assertThat(resultado).bodyJson().extractingPath("$[0].disponible").isEqualTo(true);
        Mockito.verify(servicio).listar(ARBITRO, null, null);
    }

    @Test
    @DisplayName("RF-022: GET con desde y hasta los pasa al servicio")
    void listarPorRango() {
        given(servicio.listar(eq(ARBITRO), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .willReturn(List.of(REGISTRO));

        MvcTestResult resultado = mvc.get()
                .uri(BASE + "?desde=2030-08-10T08:00:00-06:00&hasta=2030-08-10T13:00:00-06:00")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        Mockito.verify(servicio).listar(ARBITRO,
                OffsetDateTime.parse("2030-08-10T08:00:00-06:00"),
                OffsetDateTime.parse("2030-08-10T13:00:00-06:00"));
    }

    @Test
    @DisplayName("RF-022: POST responde 201 con el periodo registrado")
    void registrar() {
        given(servicio.registrar(eq(ARBITRO), any(RegistrarDisponibilidadArbitralRequest.class)))
                .willReturn(REGISTRO);

        MvcTestResult resultado = mvc.post().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).bodyJson().extractingPath("$.disponible").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.periodo.hasta")
                .isEqualTo("2030-08-10T12:00:00-06:00");
    }

    @Test
    @DisplayName("RF-022: POST sin el fin del periodo responde 400 VALIDACION")
    void registrarSinHasta() {
        MvcTestResult resultado = mvc.post().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"periodo\":{\"desde\":\"2030-08-10T09:00:00-06:00\"}}").exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("periodo.hasta");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-022: POST sin periodo responde 400 VALIDACION")
    void registrarSinPeriodo() {
        MvcTestResult resultado = mvc.post().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("periodo");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-022: POST de un árbitro no validado responde 409 RF-022")
    void registrarNoValidado() {
        given(servicio.registrar(eq(ARBITRO), any(RegistrarDisponibilidadArbitralRequest.class)))
                .willThrow(ReglaNegocioException.conflicto("RF-022",
                        "Solo se registra disponibilidad de un árbitro validado; este está propuesto."));

        MvcTestResult resultado = mvc.post().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-022");
    }

    @Test
    @DisplayName("RF-022: DELETE del periodo responde 204 sin cuerpo")
    void eliminar() {
        MvcTestResult resultado = mvc.delete().uri(BASE + "/" + DISPONIBILIDAD)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(resultado.getResponse().getContentAsByteArray()).isEmpty();
        Mockito.verify(servicio).eliminar(ARBITRO, DISPONIBILIDAD);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/arbitros/{id}/disponibilidad")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/arbitros/{id}/disponibilidad", ARBITRO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(eq(ARBITRO), any(), any()))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/arbitros/{id}/disponibilidad")
    Stream<DynamicTest> seguridadRegistrar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/arbitros/{id}/disponibilidad", ARBITRO)
                .cuerpoJson(CUERPO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrar(eq(ARBITRO),
                        any(RegistrarDisponibilidadArbitralRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de DELETE /api/v1/arbitros/{id}/disponibilidad/{disponibilidadId}")
    Stream<DynamicTest> seguridadEliminar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.DELETE,
                        "/api/v1/arbitros/{id}/disponibilidad/{disponibilidadId}", ARBITRO, DISPONIBILIDAD)
                .dobles(servicio)
                .simularDenegacion(error -> willThrow(error).given(servicio)
                        .eliminar(ARBITRO, DISPONIBILIDAD))
                .pruebas();
    }
}

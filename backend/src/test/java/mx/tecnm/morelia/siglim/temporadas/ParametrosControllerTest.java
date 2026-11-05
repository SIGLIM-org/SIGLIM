package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.ArgumentCaptor;
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

/** Contrato HTTP de los parámetros de la temporada (convenciones de API §6.2) con la seguridad real. */
@WebMvcTest(controllers = ParametrosController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class ParametrosControllerTest {

    private static final UUID TEMPORADA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID VERSION = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CATEGORIA = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final String BASE = "/api/v1/temporadas/" + TEMPORADA + "/parametros";
    private static final String CUERPO = """
            {"puntosVictoria":3,"puntosEmpate":1,"puntosDerrota":0,
             "criteriosDesempate":["PUNTOS","DIFERENCIA_GOLES","SORTEO"],
             "golesDefaultGanador":3,"golesDefaultPerdedor":0,"plazoValidacionCedulaHoras":36,
             "duracionJuegoMin":90,"bloqueCanchaMin":120,"clasificadosLiguilla":8,
             "formatoLiguilla":"IDA_Y_VUELTA","empateLiguillaPorPenales":true,
             "equiposAscienden":2,"equiposDescienden":2,"permiteCambioClub":false,
             "categorias":[{"categoriaId":"%s","duracionJuegoMin":70,"bloqueCanchaMin":90}],
             "motivo":"Valores de demostración iniciales"}
            """.formatted(CATEGORIA);
    private static final ParametrosDto VERSION_1 = new ParametrosDto(VERSION, TEMPORADA, 1, 3, 1, 0,
            List.of(CriterioDesempate.PUNTOS, CriterioDesempate.DIFERENCIA_GOLES, CriterioDesempate.SORTEO), 3, 0, 36,
            90, 120, 8, FormatoLiguilla.IDA_Y_VUELTA, true, 2, 2, false,
            List.of(new ParametrosCategoriaDto(new ReferenciaDto(CATEGORIA, "Veteranos"), 70, 90)),
            true, ParametrosService.CAMPOS_DEMO, "Valores de demostración iniciales",
            new ReferenciaDto(PERFIL, "Administrador"), OffsetDateTime.parse("2030-03-01T06:00:00-06:00"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ParametrosService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult registrar(String cuerpo) {
        return mvc.post().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-006, RF-100: POST responde 201 con Location a la versión y el cuerpo completo")
    void registrarVersion() {
        given(servicio.registrar(eq(TEMPORADA), any(RegistrarParametrosRequest.class))).willReturn(VERSION_1);

        MvcTestResult resultado = registrar(CUERPO);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/1");
        assertThat(resultado).bodyJson().extractingPath("$.version").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("$.plazoValidacionCedulaHoras").isEqualTo(36);
        assertThat(resultado).bodyJson().extractingPath("$.categorias[0].categoria.nombre").isEqualTo("Veteranos");
        assertThat(resultado).bodyJson().extractingPath("$.camposDemo[0]").isEqualTo("puntosVictoria");
        assertThat(resultado).bodyJson().extractingPath("$.creadoPor.nombre").isEqualTo("Administrador");

        ArgumentCaptor<RegistrarParametrosRequest> peticion = ArgumentCaptor.forClass(RegistrarParametrosRequest.class);
        Mockito.verify(servicio).registrar(eq(TEMPORADA), peticion.capture());
        assertThat(peticion.getValue().esDemo()).as("omitido llega nulo; el servicio lo toma como true").isNull();
        assertThat(peticion.getValue().criteriosDesempate()).containsExactly(CriterioDesempate.PUNTOS,
                CriterioDesempate.DIFERENCIA_GOLES, CriterioDesempate.SORTEO);
        assertThat(peticion.getValue().categorias()).singleElement()
                .satisfies(fila -> assertThat(fila.categoriaId()).isEqualTo(CATEGORIA));
    }

    @Test
    @DisplayName("RF-050: un criterio de desempate desconocido, como el nombre del equipo, responde 400 VALIDACION")
    void criterioDesconocido() {
        MvcTestResult resultado = registrar(CUERPO.replace("\"DIFERENCIA_GOLES\"", "\"NOMBRE_EQUIPO\""));

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RN-021: el 400 RN-021 del servicio llega como problema RFC 9457")
    void motivoCorto() {
        given(servicio.registrar(eq(TEMPORADA), any(RegistrarParametrosRequest.class))).willThrow(
                ReglaNegocioException.invalida("RN-021", "Escribe un motivo de al menos 10 caracteres."));

        MvcTestResult resultado = registrar(CUERPO);

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-021");
    }

    @Test
    @DisplayName("RF-003: una temporada cerrada responde 409 RF-003")
    void temporadaCerrada() {
        given(servicio.registrar(eq(TEMPORADA), any(RegistrarParametrosRequest.class))).willThrow(
                ReglaNegocioException.conflicto("RF-003", "La temporada está cerrada."));

        MvcTestResult resultado = registrar(CUERPO);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-003");
    }

    @Test
    @DisplayName("D8: GET sin versiones responde vigente null, historial vacío y predeterminados")
    void consultarSinVersiones() {
        given(servicio.consultar(TEMPORADA)).willReturn(
                new ParametrosTemporadaDto(null, List.of(), ParametrosPredeterminadosDto.DE_LA_TABLA));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().hasPathSatisfying("$.vigente", valor -> assertThat(valor).isNull());
        assertThat(resultado).bodyJson().extractingPath("$.historial").asArray().isEmpty();
        assertThat(resultado).bodyJson().extractingPath("$.predeterminados.puntosVictoria").isEqualTo(3);
        assertThat(resultado).bodyJson().extractingPath("$.predeterminados.plazoValidacionCedulaHoras").isEqualTo(48);
        assertThat(resultado).bodyJson().extractingPath("$.predeterminados.esDemo").isEqualTo(true);
    }

    @Test
    @DisplayName("D8: GET con versiones omite predeterminados y trae el historial con camposCambiados")
    void consultarConVersiones() {
        given(servicio.consultar(TEMPORADA)).willReturn(new ParametrosTemporadaDto(VERSION_1,
                List.of(new VersionParametrosResumenDto(VERSION, 1, true, "Valores de demostración iniciales",
                        null, VERSION_1.creadoEn(), List.of("puntosVictoria"))), null));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.vigente.version").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("$.historial[0].camposCambiados[0]")
                .isEqualTo("puntosVictoria");
        assertThat(resultado).bodyJson().doesNotHavePath("$.historial[0].creadoPor");
        assertThat(resultado).bodyJson().doesNotHavePath("$.predeterminados");
    }

    @Test
    @DisplayName("RF-006: GET /{version} responde la versión pedida")
    void consultarVersion() {
        given(servicio.consultarVersion(TEMPORADA, 1)).willReturn(VERSION_1);

        MvcTestResult resultado = mvc.get().uri(BASE + "/1").header(HttpHeaders.AUTHORIZATION, autorizacion)
                .exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(VERSION.toString());
        assertThat(resultado).bodyJson().extractingPath("$.formatoLiguilla").isEqualTo("IDA_Y_VUELTA");
    }

    @Test
    @DisplayName("RF-006: las versiones no se editan ni se borran (405)")
    void sinEdicionNiBorrado() {
        assertThat(mvc.delete().uri(BASE + "/1").header(HttpHeaders.AUTHORIZATION, autorizacion).exchange())
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(mvc.patch().uri(BASE + "/1").header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange())
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/temporadas/{id}/parametros")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/temporadas/{id}/parametros", TEMPORADA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(TEMPORADA)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/temporadas/{id}/parametros/{version}")
    Stream<DynamicTest> seguridadConsultarVersion() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/temporadas/{id}/parametros/{version}", TEMPORADA, 1)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultarVersion(TEMPORADA, 1)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/temporadas/{id}/parametros")
    Stream<DynamicTest> seguridadRegistrar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/temporadas/{id}/parametros", TEMPORADA)
                .cuerpoJson(CUERPO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrar(eq(TEMPORADA),
                        any(RegistrarParametrosRequest.class))).willThrow(error))
                .pruebas();
    }
}

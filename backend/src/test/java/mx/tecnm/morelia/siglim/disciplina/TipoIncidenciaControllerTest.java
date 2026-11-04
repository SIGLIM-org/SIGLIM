package mx.tecnm.morelia.siglim.disciplina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
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

/** Contrato HTTP de tipos de incidencia (convenciones de API §6.9) con la seguridad real. */
@WebMvcTest(controllers = TipoIncidenciaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class TipoIncidenciaControllerTest {

    private static final long ID = 7L;
    private static final String BASE = "/api/v1/tipos-incidencia";
    private static final TipoIncidenciaDto TIPO =
            new TipoIncidenciaDto(ID, "EXPULSION", "Expulsión", true, true, true);
    private static final String CUERPO_CREAR = """
            {"codigo":"EXPULSION","nombre":"Expulsión","abreExpediente":true}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TiposIncidenciaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-128: GET lista el catálogo y acepta el filtro activo")
    void listar() {
        given(servicio.listar(true)).willReturn(List.of(TIPO));

        MvcTestResult resultado = mvc.get().uri(BASE + "?activo=true")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].codigo").isEqualTo("EXPULSION");
        assertThat(resultado).bodyJson().extractingPath("$[0].abreExpediente").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$[0].esDemo").isEqualTo(true);
    }

    @Test
    @DisplayName("RF-128: POST responde 201 con Location")
    void crear() {
        given(servicio.crear(any(CrearTipoIncidenciaRequest.class))).willReturn(TIPO);

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(7);
        assertThat(resultado).bodyJson().extractingPath("$.nombre").isEqualTo("Expulsión");
    }

    @Test
    @DisplayName("RF-128: POST sin código responde 400 VALIDACION y no llega al servicio")
    void crearSinCodigo() {
        MvcTestResult resultado = post(BASE, "{\"nombre\":\"Expulsión\",\"abreExpediente\":true}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("codigo");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-128: PATCH edita el tipo y devuelve el resultado")
    void actualizar() {
        given(servicio.actualizar(eq(ID), any(ActualizarTipoIncidenciaRequest.class))).willReturn(TIPO);

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}").exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("EXPULSION");
        ArgumentCaptor<ActualizarTipoIncidenciaRequest> peticion =
                ArgumentCaptor.forClass(ActualizarTipoIncidenciaRequest.class);
        Mockito.verify(servicio).actualizar(eq(ID), peticion.capture());
        assertThat(peticion.getValue().activo()).isFalse();
    }

    @Test
    @DisplayName("RF-128: un código repetido responde 409 DUPLICADO")
    void codigoDuplicado() {
        given(servicio.crear(any(CrearTipoIncidenciaRequest.class)))
                .willThrow(ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                        "Ya existe un tipo de incidencia con ese código."));

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.DUPLICADO);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/tipos-incidencia")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/tipos-incidencia")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearTipoIncidenciaRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/tipos-incidencia/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", ID)
                .cuerpoJson("{\"activo\":false}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID),
                        any(ActualizarTipoIncidenciaRequest.class))).willThrow(error))
                .pruebas();
    }
}

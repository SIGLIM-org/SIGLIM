package mx.tecnm.morelia.siglim.disciplina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

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

/** Contrato HTTP de artículos (convenciones de API §6.9) con la seguridad real. */
@WebMvcTest(controllers = ArticuloController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class ArticuloControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String BASE = "/api/v1/articulos";
    private static final ArticuloDto ARTICULO = new ArticuloDto(ID, "A-1", "Suspensión por expulsión",
            TipoSancion.SUSPENSION_PARTIDOS, SujetoSancion.JUGADOR, EfectoSancion.HACIA_ADELANTE, false,
            3, null, null, true, true, true);
    private static final String CUERPO_CREAR = """
            {"numero":"A-1","descripcion":"Suspensión por expulsión","tipoSancion":"SUSPENSION_PARTIDOS",
             "sujeto":"JUGADOR","partidosSuspension":3}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ArticulosService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-129: GET lista el catálogo con sanción, sujeto, efecto y aplicación automática")
    void listar() {
        given(servicio.listar(false)).willReturn(List.of(ARTICULO));

        MvcTestResult resultado = mvc.get().uri(BASE + "?activo=false")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].numero").isEqualTo("A-1");
        assertThat(resultado).bodyJson().extractingPath("$[0].tipoSancion").isEqualTo("SUSPENSION_PARTIDOS");
        assertThat(resultado).bodyJson().extractingPath("$[0].sujeto").isEqualTo("JUGADOR");
        assertThat(resultado).bodyJson().extractingPath("$[0].efecto").isEqualTo("HACIA_ADELANTE");
        assertThat(resultado).bodyJson().extractingPath("$[0].aplicacionAutomatica").isEqualTo(false);
    }

    @Test
    @DisplayName("RF-129: POST responde 201 con Location")
    void crear() {
        given(servicio.crear(any(CrearArticuloRequest.class))).willReturn(ARTICULO);

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$.partidosSuspension").isEqualTo(3);
    }

    @Test
    @DisplayName("RF-129: POST sin tipo de sanción responde 400 VALIDACION y no llega al servicio")
    void crearSinTipoSancion() {
        MvcTestResult resultado = post(BASE,
                "{\"numero\":\"A-1\",\"descripcion\":\"Suspensión\",\"sujeto\":\"JUGADOR\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("tipoSancion");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-129: POST con monto de multa no positivo responde 400 VALIDACION")
    void crearConMontoNoPositivo() {
        MvcTestResult resultado = post(BASE, """
                {"numero":"A-3","descripcion":"Multa","tipoSancion":"MULTA","sujeto":"CLUB","montoMulta":0}
                """);

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("montoMulta");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-129: PATCH edita el artículo y devuelve el resultado")
    void actualizar() {
        given(servicio.actualizar(eq(ID), any(ActualizarArticuloRequest.class))).willReturn(ARTICULO);

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}").exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.numero").isEqualTo("A-1");
        ArgumentCaptor<ActualizarArticuloRequest> peticion =
                ArgumentCaptor.forClass(ActualizarArticuloRequest.class);
        Mockito.verify(servicio).actualizar(eq(ID), peticion.capture());
        assertThat(peticion.getValue().activo()).isFalse();
    }

    @Test
    @DisplayName("RF-129: un número repetido responde 409 DUPLICADO")
    void numeroDuplicado() {
        given(servicio.crear(any(CrearArticuloRequest.class)))
                .willThrow(ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                        "Ya existe un artículo con ese número."));

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.DUPLICADO);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/articulos")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/articulos")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearArticuloRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/articulos/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", ID)
                .cuerpoJson("{\"activo\":false}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID),
                        any(ActualizarArticuloRequest.class))).willThrow(error))
                .pruebas();
    }
}

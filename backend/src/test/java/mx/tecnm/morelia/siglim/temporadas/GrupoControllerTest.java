package mx.tecnm.morelia.siglim.temporadas;

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
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;
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

/** Contrato HTTP de grupos (convenciones de API §6.2) con la cadena de seguridad real. */
@WebMvcTest(controllers = GrupoController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class GrupoControllerTest {

    private static final UUID GRUPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TEMPORADA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CATEGORIA = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID CATEGORIA_TEMPORADA = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final String BASE = "/api/v1/grupos";
    private static final String CUERPO_CREAR =
            "{\"categoriaTemporadaId\":\"" + CATEGORIA_TEMPORADA + "\",\"nombre\":\"A\"}";
    private static final GrupoDto DETALLE = new GrupoDto(GRUPO, "A", CATEGORIA_TEMPORADA, TEMPORADA,
            new ReferenciaDto(CATEGORIA, "Libre"), 3);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private GrupoService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult enviar(HttpMethod metodo, String ruta, String cuerpo) {
        return mvc.method(metodo).uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-005: GET lista los grupos de la categoría con la forma corta y sin paginar")
    void listar() {
        given(servicio.listar(CATEGORIA_TEMPORADA))
                .willReturn(List.of(new GrupoDto(GRUPO, "A", CATEGORIA_TEMPORADA, null, null, 3)));

        MvcTestResult resultado = mvc.get().uri(BASE + "?categoriaTemporadaId=" + CATEGORIA_TEMPORADA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].nombre").isEqualTo("A");
        assertThat(resultado).bodyJson().extractingPath("$[0].categoriaTemporadaId")
                .isEqualTo(CATEGORIA_TEMPORADA.toString());
        assertThat(resultado).bodyJson().extractingPath("$[0].equipos").isEqualTo(3);
        assertThat(resultado).bodyJson().doesNotHavePath("$[0].temporadaId");
        assertThat(resultado).bodyJson().doesNotHavePath("$[0].categoria");
    }

    @Test
    @DisplayName("RF-005: GET sin categoriaTemporadaId responde el 400 VALIDACION del servicio")
    void listarSinFiltro() {
        given(servicio.listar(null)).willThrow(ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                "Indica la categoría de la temporada con el filtro categoriaTemporadaId."));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("RF-005: GET /{id} devuelve la temporada y la categoría del grupo")
    void consultar() {
        given(servicio.consultar(GRUPO)).willReturn(DETALLE);

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + GRUPO)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.temporadaId").isEqualTo(TEMPORADA.toString());
        assertThat(resultado).bodyJson().extractingPath("$.categoria.nombre").isEqualTo("Libre");
        assertThat(resultado).bodyJson().extractingPath("$.equipos").isEqualTo(3);
    }

    @Test
    @DisplayName("RF-005: POST responde 201 con Location")
    void crear() {
        given(servicio.crear(any(CrearGrupoRequest.class))).willReturn(DETALLE);

        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + GRUPO);
        Mockito.verify(servicio).crear(new CrearGrupoRequest(CATEGORIA_TEMPORADA, "A"));
    }

    @Test
    @DisplayName("RF-005: POST con un nombre de más de 60 caracteres responde 400 VALIDACION")
    void crearConNombreLargo() {
        MvcTestResult resultado = enviar(HttpMethod.POST, BASE,
                "{\"categoriaTemporadaId\":\"" + CATEGORIA_TEMPORADA + "\",\"nombre\":\"" + "x".repeat(61) + "\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("nombre");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-005: POST sin categoriaTemporadaId responde 400 VALIDACION")
    void crearSinCategoria() {
        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, "{\"nombre\":\"A\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("categoriaTemporadaId");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-005: un grupo repetido responde 409 DUPLICADO")
    void crearDuplicado() {
        given(servicio.crear(any(CrearGrupoRequest.class))).willThrow(ReglaNegocioException.conflicto(
                ErroresTecnicos.DUPLICADO, "Ya existe un grupo llamado «A» en la categoría «Libre»."));

        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.DUPLICADO);
    }

    @Test
    @DisplayName("RF-005: PATCH renombra y responde 200")
    void actualizar() {
        given(servicio.actualizar(GRUPO, new ActualizarGrupoRequest("B"))).willReturn(DETALLE);

        MvcTestResult resultado = enviar(HttpMethod.PATCH, BASE + "/" + GRUPO, "{\"nombre\":\"B\"}");

        assertThat(resultado).hasStatusOk();
        Mockito.verify(servicio).actualizar(GRUPO, new ActualizarGrupoRequest("B"));
    }

    @Test
    @DisplayName("D7: DELETE de un grupo responde 405 METODO_NO_PERMITIDO")
    void sinBorrado() {
        MvcTestResult resultado = mvc.delete().uri(BASE + "/" + GRUPO)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/grupos")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "?categoriaTemporadaId={id}", CATEGORIA_TEMPORADA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(CATEGORIA_TEMPORADA)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/grupos/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", GRUPO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(GRUPO)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/grupos")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearGrupoRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/grupos/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", GRUPO)
                .cuerpoJson("{\"nombre\":\"B\"}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(GRUPO),
                        any(ActualizarGrupoRequest.class))).willThrow(error))
                .pruebas();
    }
}

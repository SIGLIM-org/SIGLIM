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
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
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

/** Contrato HTTP del catálogo de categorías (convenciones de API §6.2) con la cadena de seguridad real. */
@WebMvcTest(controllers = CategoriaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class CategoriaControllerTest {

    private static final UUID LIBRE = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID JUVENIL = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String BASE = "/api/v1/categorias";
    private static final String CUERPO_CREAR =
            "{\"nombre\":\"Juvenil\",\"tipo\":\"LIBRE\",\"categoriaSuperiorId\":\"" + LIBRE + "\"}";
    private static final String CUERPO_REORDENAR = "{\"ids\":[\"" + JUVENIL + "\",\"" + LIBRE + "\"]}";
    private static final CategoriaDto CATEGORIA_LIBRE = new CategoriaDto(LIBRE, "Libre", TipoCategoria.LIBRE, 1,
            null, true, OffsetDateTime.parse("2030-03-01T06:00:00-06:00"));
    private static final CategoriaDto CATEGORIA_JUVENIL = new CategoriaDto(JUVENIL, "Juvenil", TipoCategoria.LIBRE, 2,
            new ReferenciaDto(LIBRE, "Libre"), true, OffsetDateTime.parse("2030-03-01T06:00:00-06:00"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CategoriaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult enviar(HttpMethod metodo, String ruta, String cuerpo) {
        return mvc.method(metodo).uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-099: GET lista sin paginar, con la superior anidada y omitida si no tiene")
    void listar() {
        given(servicio.listar(true)).willReturn(List.of(CATEGORIA_LIBRE, CATEGORIA_JUVENIL));

        MvcTestResult resultado = mvc.get().uri(BASE + "?activa=true")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].nombre").isEqualTo("Libre");
        assertThat(resultado).bodyJson().extractingPath("$[0].orden").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("$[0].activa").isEqualTo(true);
        assertThat(resultado).bodyJson().doesNotHavePath("$[0].categoriaSuperior");
        assertThat(resultado).bodyJson().extractingPath("$[1].categoriaSuperior.id").isEqualTo(LIBRE.toString());
        assertThat(resultado).bodyJson().extractingPath("$[1].categoriaSuperior.nombre").isEqualTo("Libre");
        assertThat(resultado).bodyJson().extractingPath("$[1].tipo").isEqualTo("LIBRE");
        assertThat(resultado).bodyJson().hasPath("$[1].creadoEn");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido");
    }

    @Test
    @DisplayName("RF-099: GET de una categoría inexistente responde 404 NO_ENCONTRADO")
    void consultarInexistente() {
        given(servicio.consultar(LIBRE)).willThrow(new RecursoNoEncontradoException("Categoría " + LIBRE));

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + LIBRE)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_ENCONTRADO);
    }

    @Test
    @DisplayName("RF-099: POST responde 201 con Location y la categoría")
    void crear() {
        given(servicio.crear(any(CrearCategoriaRequest.class))).willReturn(CATEGORIA_JUVENIL);

        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + JUVENIL);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(JUVENIL.toString());
        ArgumentCaptor<CrearCategoriaRequest> peticion = ArgumentCaptor.forClass(CrearCategoriaRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().tipo()).isEqualTo(TipoCategoria.LIBRE);
        assertThat(peticion.getValue().categoriaSuperiorId()).isEqualTo(LIBRE);
    }

    @Test
    @DisplayName("RF-099: POST sin tipo responde 400 VALIDACION y no llega al servicio")
    void crearSinTipo() {
        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, "{\"nombre\":\"Juvenil\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("tipo");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-099: POST con un nombre de más de 80 caracteres responde 400 VALIDACION")
    void crearConNombreLargo() {
        MvcTestResult resultado = enviar(HttpMethod.POST, BASE,
                "{\"nombre\":\"" + "x".repeat(81) + "\",\"tipo\":\"LIBRE\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("nombre");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-099: POST con un tipo que no existe responde 400 VALIDACION")
    void crearConTipoInvalido() {
        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, "{\"nombre\":\"Juvenil\",\"tipo\":\"JUVENIL\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-099, D2: un ciclo en la jerarquía responde 409 RF-099")
    void actualizarConCiclo() {
        given(servicio.actualizar(eq(LIBRE), any(ActualizarCategoriaRequest.class))).willThrow(
                ReglaNegocioException.conflicto("RF-099", "La jerarquía formaría un ciclo: Libre → Juvenil → Libre."));

        MvcTestResult resultado = enviar(HttpMethod.PATCH, BASE + "/" + LIBRE,
                "{\"categoriaSuperiorId\":\"" + JUVENIL + "\"}");

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-099");
    }

    @Test
    @DisplayName("RF-099: PATCH pasa quitarSuperior y activa al servicio y responde 200")
    void actualizar() {
        given(servicio.actualizar(eq(JUVENIL), any(ActualizarCategoriaRequest.class))).willReturn(CATEGORIA_JUVENIL);

        MvcTestResult resultado = enviar(HttpMethod.PATCH, BASE + "/" + JUVENIL,
                "{\"quitarSuperior\":true,\"activa\":false}");

        assertThat(resultado).hasStatusOk();
        ArgumentCaptor<ActualizarCategoriaRequest> peticion = ArgumentCaptor.forClass(ActualizarCategoriaRequest.class);
        Mockito.verify(servicio).actualizar(eq(JUVENIL), peticion.capture());
        assertThat(peticion.getValue().quitarSuperior()).isTrue();
        assertThat(peticion.getValue().activa()).isFalse();
        assertThat(peticion.getValue().nombre()).isNull();
    }

    @Test
    @DisplayName("RF-099, D3: POST /reordenamiento responde 200 con la lista completa")
    void reordenar() {
        given(servicio.reordenar(new ReordenarCategoriasRequest(List.of(JUVENIL, LIBRE))))
                .willReturn(List.of(CATEGORIA_JUVENIL, CATEGORIA_LIBRE));

        MvcTestResult resultado = enviar(HttpMethod.POST, BASE + "/reordenamiento", CUERPO_REORDENAR);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].id").isEqualTo(JUVENIL.toString());
        assertThat(resultado).bodyJson().extractingPath("$[1].id").isEqualTo(LIBRE.toString());
    }

    @Test
    @DisplayName("RF-099, D3: POST /reordenamiento sin ids responde 400 VALIDACION")
    void reordenarSinIds() {
        MvcTestResult resultado = enviar(HttpMethod.POST, BASE + "/reordenamiento", "{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("ids");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-099, D3: la extensión faltantes del reordenamiento llega en el problema RFC 9457")
    void reordenarConFaltantes() {
        given(servicio.reordenar(any(ReordenarCategoriasRequest.class))).willThrow(ReglaNegocioException
                .invalida(ErroresTecnicos.VALIDACION, "Faltan categorías.")
                .con("faltantes", List.of(LIBRE)));

        MvcTestResult resultado = enviar(HttpMethod.POST, BASE + "/reordenamiento", "{\"ids\":[\"" + JUVENIL + "\"]}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.faltantes[0]").isEqualTo(LIBRE.toString());
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/categorias")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/categorias/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", LIBRE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(LIBRE)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/categorias")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearCategoriaRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/categorias/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", LIBRE)
                .cuerpoJson("{\"nombre\":\"Libre varonil\"}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(LIBRE),
                        any(ActualizarCategoriaRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/categorias/reordenamiento")
    Stream<DynamicTest> seguridadReordenar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/reordenamiento")
                .cuerpoJson(CUERPO_REORDENAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.reordenar(any(ReordenarCategoriasRequest.class)))
                        .willThrow(error))
                .pruebas();
    }
}

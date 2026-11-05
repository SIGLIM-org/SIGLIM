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
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.temporadas.CategoriaTemporadaDto.CategoriaResumenDto;
import mx.tecnm.morelia.siglim.temporadas.CategoriaTemporadaDto.GrupoResumenDto;
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

/** Contrato HTTP de las categorías de la temporada (convenciones de API §6.2) con la cadena de seguridad real. */
@WebMvcTest(controllers = CategoriaTemporadaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class CategoriaTemporadaControllerTest {

    private static final UUID TEMPORADA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CATEGORIA = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID SUPERIOR = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CATEGORIA_TEMPORADA = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String BASE = "/api/v1/temporadas/" + TEMPORADA + "/categorias";
    private static final String CUERPO_ASIGNAR = "{\"categoriaId\":\"" + CATEGORIA + "\"}";
    private static final CategoriaTemporadaDto ASIGNADA = new CategoriaTemporadaDto(CATEGORIA_TEMPORADA,
            new CategoriaResumenDto(CATEGORIA, "Juvenil", TipoCategoria.LIBRE, 2, new ReferenciaDto(SUPERIOR, "Libre")),
            List.of(new GrupoResumenDto(GRUPO, "A", 4)));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private GrupoService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String cuerpo) {
        return mvc.post().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-004, D8: GET lista las categorías con sus grupos anidados y su conteo de equipos")
    void listar() {
        given(servicio.listarCategorias(TEMPORADA)).willReturn(List.of(ASIGNADA));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].id").isEqualTo(CATEGORIA_TEMPORADA.toString());
        assertThat(resultado).bodyJson().extractingPath("$[0].categoria.id").isEqualTo(CATEGORIA.toString());
        assertThat(resultado).bodyJson().extractingPath("$[0].categoria.orden").isEqualTo(2);
        assertThat(resultado).bodyJson().extractingPath("$[0].categoria.categoriaSuperior.nombre").isEqualTo("Libre");
        assertThat(resultado).bodyJson().extractingPath("$[0].grupos[0].id").isEqualTo(GRUPO.toString());
        assertThat(resultado).bodyJson().extractingPath("$[0].grupos[0].nombre").isEqualTo("A");
        assertThat(resultado).bodyJson().extractingPath("$[0].grupos[0].equipos").isEqualTo(4);
    }

    @Test
    @DisplayName("RF-004: GET de una temporada inexistente responde 404")
    void listarDeTemporadaInexistente() {
        given(servicio.listarCategorias(TEMPORADA)).willThrow(new RecursoNoEncontradoException("Temporada " + TEMPORADA));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_ENCONTRADO);
    }

    @Test
    @DisplayName("RF-004: POST responde 201 con Location al detalle de la categoría de la temporada")
    void asignar() {
        given(servicio.asignarCategoria(eq(TEMPORADA), any(AsignarCategoriaRequest.class))).willReturn(ASIGNADA);

        MvcTestResult resultado = post(CUERPO_ASIGNAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED)
                .hasHeader(HttpHeaders.LOCATION, BASE + "/" + CATEGORIA_TEMPORADA);
        assertThat(resultado).bodyJson().extractingPath("$.categoria.nombre").isEqualTo("Juvenil");
        Mockito.verify(servicio).asignarCategoria(TEMPORADA, new AsignarCategoriaRequest(CATEGORIA));
    }

    @Test
    @DisplayName("RF-004: POST sin categoriaId responde 400 VALIDACION y no llega al servicio")
    void asignarSinCategoria() {
        MvcTestResult resultado = post("{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("categoriaId");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-004, D6: asignar con la temporada activa responde 409 RF-004")
    void asignarEnTemporadaActiva() {
        given(servicio.asignarCategoria(eq(TEMPORADA), any(AsignarCategoriaRequest.class))).willThrow(
                ReglaNegocioException.conflicto("RF-004", "La temporada «Temporada 2030» está activa."));

        MvcTestResult resultado = post(CUERPO_ASIGNAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-004");
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/temporadas/{id}/categorias")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listarCategorias(TEMPORADA)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/temporadas/{id}/categorias/{categoriaTemporadaId}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", CATEGORIA_TEMPORADA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultarCategoria(TEMPORADA, CATEGORIA_TEMPORADA))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/temporadas/{id}/categorias")
    Stream<DynamicTest> seguridadAsignar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_ASIGNAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.asignarCategoria(eq(TEMPORADA),
                        any(AsignarCategoriaRequest.class))).willThrow(error))
                .pruebas();
    }
}

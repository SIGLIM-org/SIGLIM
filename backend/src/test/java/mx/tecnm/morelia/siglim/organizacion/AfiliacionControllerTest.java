package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionDto.PerfilDto;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
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

/** Contrato HTTP del tarjetón de afiliación (convenciones de API §6.3) con la seguridad real. */
@WebMvcTest(controllers = AfiliacionController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class AfiliacionControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEMPORADA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CATEGORIA = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final String BASE = "/api/v1/afiliaciones";
    private static final String CUERPO_CREAR = "{\"clubId\":\"" + CLUB + "\",\"temporadaId\":\"" + TEMPORADA
            + "\",\"coloresUniformes\":\"Azul y blanco\",\"categoriaIds\":[\"" + CATEGORIA + "\"]}";
    private static final String CUERPO_ACTUALIZAR = "{\"coloresUniformes\":\"Azul y blanco\",\"categoriaIds\":[\""
            + CATEGORIA + "\"]}";
    private static final String CUERPO_APROBAR =
            "{\"decision\":\"APROBADA\",\"observaciones\":\"Todo en orden\"}";
    private static final String CUERPO_RECHAZAR =
            "{\"decision\":\"RECHAZADA\",\"observaciones\":\"Faltan las firmas de la directiva\"}";
    private static final AfiliacionDto AFILIACION = afiliacion(EstadoAfiliacion.SOLICITADA);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AfiliacionService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private static AfiliacionDto afiliacion(EstadoAfiliacion estado) {
        return new AfiliacionDto(ID, "AF-2030-0001", new ReferenciaDto(CLUB, "Club Halcones"),
                new TemporadaResumenDto(TEMPORADA, "Temporada 2030", EstadoTemporada.ACTIVA),
                "Azul y blanco", estado, List.of(new ReferenciaDto(CATEGORIA, "Libre")),
                new PerfilDto(UUID.randomUUID(), "Ana López"),
                OffsetDateTime.parse("2030-08-01T06:00:00-06:00"), null, null, null);
    }

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-101: GET lista el tarjetón con club, temporada y categorías")
    void listar() {
        FiltroAfiliaciones filtro = new FiltroAfiliaciones(TEMPORADA, EstadoAfiliacion.SOLICITADA, 0, 20);
        given(servicio.listar(filtro)).willReturn(Pagina.de(List.of(AFILIACION), 0, 20, 1));

        MvcTestResult resultado = mvc.get()
                .uri(BASE + "?temporadaId=" + TEMPORADA + "&estado=SOLICITADA")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].folio").isEqualTo("AF-2030-0001");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].club.nombre")
                .isEqualTo("Club Halcones");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].temporada.estado")
                .isEqualTo("ACTIVA");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].categorias[0].nombre")
                .isEqualTo("Libre");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estado").isEqualTo("SOLICITADA");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].resueltaPor");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(1);
        Mockito.verify(servicio).listar(filtro);
    }

    @Test
    @DisplayName("RF-101: un filtro con UUID mal formado responde 400 VALIDACION")
    void listarConFiltroInvalido() {
        MvcTestResult resultado = mvc.get().uri(BASE + "?temporadaId=no-es-uuid")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-101: GET /{id} devuelve el tarjetón y omite los campos de resolución")
    void consultar() {
        given(servicio.consultar(ID)).willReturn(AFILIACION);

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.folio").isEqualTo("AF-2030-0001");
        assertThat(resultado).bodyJson().extractingPath("$.coloresUniformes").isEqualTo("Azul y blanco");
        assertThat(resultado).bodyJson().doesNotHavePath("$.resueltaEn");
        assertThat(resultado).bodyJson().doesNotHavePath("$.observaciones");
    }

    @Test
    @DisplayName("RF-101: POST responde 201 con Location y el folio asignado")
    void crear() {
        given(servicio.crear(any(CrearAfiliacionRequest.class))).willReturn(AFILIACION);

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.folio").isEqualTo("AF-2030-0001");
        ArgumentCaptor<CrearAfiliacionRequest> peticion = ArgumentCaptor.forClass(CrearAfiliacionRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().clubId()).isEqualTo(CLUB);
        assertThat(peticion.getValue().temporadaId()).isEqualTo(TEMPORADA);
        assertThat(peticion.getValue().categoriaIds()).containsExactly(CATEGORIA);
    }

    @Test
    @DisplayName("RF-101: POST sin colores responde 400 VALIDACION y no llega al servicio")
    void crearSinColores() {
        MvcTestResult resultado = post(BASE, "{\"clubId\":\"" + CLUB + "\",\"temporadaId\":\"" + TEMPORADA
                + "\",\"categoriaIds\":[\"" + CATEGORIA + "\"]}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("coloresUniformes");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-101: POST sin categorías responde 400 VALIDACION")
    void crearSinCategorias() {
        MvcTestResult resultado = post(BASE, "{\"clubId\":\"" + CLUB + "\",\"temporadaId\":\"" + TEMPORADA
                + "\",\"coloresUniformes\":\"Azul y blanco\",\"categoriaIds\":[]}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("categoriaIds");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-101: PATCH pasa solo los campos indicados")
    void actualizar() {
        given(servicio.actualizar(eq(ID), any(ActualizarAfiliacionRequest.class))).willReturn(AFILIACION);

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_ACTUALIZAR).exchange();

        assertThat(resultado).hasStatusOk();
        ArgumentCaptor<ActualizarAfiliacionRequest> peticion =
                ArgumentCaptor.forClass(ActualizarAfiliacionRequest.class);
        Mockito.verify(servicio).actualizar(eq(ID), peticion.capture());
        assertThat(peticion.getValue().coloresUniformes()).isEqualTo("Azul y blanco");
        assertThat(peticion.getValue().categoriaIds()).containsExactly(CATEGORIA);
    }

    @Test
    @DisplayName("RF-101: la aprobación responde 200 y pasa la decisión al servicio")
    void resolverAprobar() {
        given(servicio.resolver(eq(ID), any(ResolverAfiliacionRequest.class)))
                .willReturn(afiliacion(EstadoAfiliacion.APROBADA));

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", CUERPO_APROBAR);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("APROBADA");
        ArgumentCaptor<ResolverAfiliacionRequest> peticion =
                ArgumentCaptor.forClass(ResolverAfiliacionRequest.class);
        Mockito.verify(servicio).resolver(eq(ID), peticion.capture());
        assertThat(peticion.getValue().decision()).isEqualTo(DecisionAfiliacion.APROBADA);
        assertThat(peticion.getValue().observaciones()).isEqualTo("Todo en orden");
    }

    @Test
    @DisplayName("RF-101: el rechazo sin observaciones suficientes conserva el 400 y el código")
    void resolverSinObservaciones() {
        given(servicio.resolver(eq(ID), any(ResolverAfiliacionRequest.class)))
                .willThrow(ReglaNegocioException.invalida("RF-101",
                        "Escribe observaciones de al menos 10 caracteres para rechazar la afiliación."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", "{\"decision\":\"RECHAZADA\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-101");
    }

    @Test
    @DisplayName("RF-101: resolver un tarjetón ya resuelto responde 409 RF-101")
    void resolverRepetida() {
        given(servicio.resolver(eq(ID), any(ResolverAfiliacionRequest.class)))
                .willThrow(ReglaNegocioException.conflicto("RF-101",
                        "Solo se resuelve un tarjetón en estado SOLICITADA; este ya está aprobada."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", CUERPO_RECHAZAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-101");
    }

    @Test
    @DisplayName("RN-005: no existe DELETE de afiliaciones (405)")
    void sinDelete() {
        MvcTestResult resultado = mvc.delete().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/afiliaciones")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroAfiliaciones.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/afiliaciones/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/afiliaciones")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearAfiliacionRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/afiliaciones/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", ID)
                .cuerpoJson(CUERPO_ACTUALIZAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID),
                        any(ActualizarAfiliacionRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/afiliaciones/{id}/resolucion")
    Stream<DynamicTest> seguridadResolver() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/resolucion", ID)
                .cuerpoJson(CUERPO_APROBAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.resolver(eq(ID),
                        any(ResolverAfiliacionRequest.class))).willThrow(error))
                .pruebas();
    }
}

package mx.tecnm.morelia.siglim.publicacion;

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

/** Contrato HTTP de comunicados (convenciones de API §6.11) con la cadena de seguridad real. */
@WebMvcTest(controllers = ComunicadoController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class ComunicadoControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final String BASE = "/api/v1/comunicados";
    private static final String CUERPO_CREAR = "{\"titulo\":\"Suspensión por lluvia\",\"cuerpo\":\"Se suspende.\"}";
    private static final String CUERPO_RESOLUCION = "{\"decision\":\"PUBLICADO\",\"observaciones\":\"Aprobado\"}";
    private static final ComunicadoDto BORRADOR = new ComunicadoDto(ID, "Suspensión por lluvia",
            "Se suspende la jornada.", EstadoPublicacion.BORRADOR, PERFIL, null, null, null, null,
            OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), OffsetDateTime.parse("2030-03-01T06:00:00-06:00"));
    private static final ComunicadoDto PUBLICADO = new ComunicadoDto(ID, "Suspensión por lluvia",
            "Se suspende la jornada.", EstadoPublicacion.PUBLICADO, PERFIL, UUID.randomUUID(),
            OffsetDateTime.parse("2030-03-02T06:00:00-06:00"), OffsetDateTime.parse("2030-03-02T06:00:00-06:00"),
            null, OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), OffsetDateTime.parse("2030-03-02T06:00:00-06:00"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ComunicadoService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        var peticion = mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return peticion.exchange();
    }

    @Test
    @DisplayName("RF-090: GET lista con la envoltura de paginación y el filtro por estado")
    void listar() {
        given(servicio.listar(EstadoPublicacion.BORRADOR, 1, 10))
                .willReturn(Pagina.de(List.of(BORRADOR), 1, 10, 11));

        MvcTestResult resultado = mvc.get().uri(BASE + "?estado=BORRADOR&pagina=1&tamano=10")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estadoPublicacion").isEqualTo("BORRADOR");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].redactadoPor")
                .isEqualTo(PERFIL.toString());
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
        Mockito.verify(servicio).listar(EstadoPublicacion.BORRADOR, 1, 10);
    }

    @Test
    @DisplayName("RF-090: GET del detalle responde el comunicado")
    void consultar() {
        given(servicio.consultar(ID)).willReturn(PUBLICADO);

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$.estadoPublicacion").isEqualTo("PUBLICADO");
        assertThat(resultado).bodyJson().extractingPath("$.publicadoEn").isEqualTo("2030-03-02T06:00:00-06:00");
    }

    @Test
    @DisplayName("RF-090: POST responde 201 con Location")
    void crear() {
        given(servicio.crear(any(CrearComunicadoRequest.class))).willReturn(BORRADOR);

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.estadoPublicacion").isEqualTo("BORRADOR");
        ArgumentCaptor<CrearComunicadoRequest> peticion = ArgumentCaptor.forClass(CrearComunicadoRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().titulo()).isEqualTo("Suspensión por lluvia");
    }

    @Test
    @DisplayName("RF-090: POST sin título responde 400 VALIDACION y no llega al servicio")
    void crearSinTitulo() {
        MvcTestResult resultado = post(BASE, "{\"cuerpo\":\"Se suspende.\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("titulo");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-090: PATCH cambia solo lo indicado")
    void actualizar() {
        given(servicio.actualizar(eq(ID), any(ActualizarComunicadoRequest.class))).willReturn(BORRADOR);

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Otro título\"}").exchange();

        assertThat(resultado).hasStatusOk();
        ArgumentCaptor<ActualizarComunicadoRequest> peticion =
                ArgumentCaptor.forClass(ActualizarComunicadoRequest.class);
        Mockito.verify(servicio).actualizar(eq(ID), peticion.capture());
        assertThat(peticion.getValue().titulo()).isEqualTo("Otro título");
        assertThat(peticion.getValue().cuerpo()).isNull();
    }

    @Test
    @DisplayName("RF-090: el envío a revisión es un POST sin cuerpo")
    void enviarARevision() {
        given(servicio.enviarARevision(ID)).willReturn(new ComunicadoDto(ID, "Suspensión por lluvia",
                "Se suspende la jornada.", EstadoPublicacion.REVISION, PERFIL, null, null, null, null,
                BORRADOR.creadoEn(), BORRADOR.actualizadoEn()));

        MvcTestResult resultado = post(BASE + "/" + ID + "/envio", null);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estadoPublicacion").isEqualTo("REVISION");
    }

    @Test
    @DisplayName("RF-090: la resolución pasa el dictamen al servicio")
    void resolver() {
        given(servicio.resolver(eq(ID), any(ResolverComunicadoRequest.class))).willReturn(PUBLICADO);

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", CUERPO_RESOLUCION);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estadoPublicacion").isEqualTo("PUBLICADO");
        ArgumentCaptor<ResolverComunicadoRequest> peticion =
                ArgumentCaptor.forClass(ResolverComunicadoRequest.class);
        Mockito.verify(servicio).resolver(eq(ID), peticion.capture());
        assertThat(peticion.getValue().decision()).isEqualTo(DecisionComunicado.PUBLICADO);
    }

    @Test
    @DisplayName("RF-090: el 400 por devolver sin observaciones conserva el código")
    void devolverSinObservaciones() {
        given(servicio.resolver(eq(ID), any(ResolverComunicadoRequest.class)))
                .willThrow(ReglaNegocioException.invalida("RF-090",
                        "Escribe observaciones de al menos 10 caracteres para devolver el comunicado."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion",
                "{\"decision\":\"BORRADOR\",\"observaciones\":\"Corto\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-090");
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/comunicados")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(null, 0, 20)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/comunicados/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/comunicados")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearComunicadoRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/comunicados/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", ID)
                .cuerpoJson("{\"titulo\":\"Otro título\"}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID),
                        any(ActualizarComunicadoRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/comunicados/{id}/envio")
    Stream<DynamicTest> seguridadEnviarARevision() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/envio", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.enviarARevision(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/comunicados/{id}/resolucion")
    Stream<DynamicTest> seguridadResolver() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/resolucion", ID)
                .cuerpoJson(CUERPO_RESOLUCION)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.resolver(eq(ID),
                        any(ResolverComunicadoRequest.class))).willThrow(error))
                .pruebas();
    }
}

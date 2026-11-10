package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.io.UnsupportedEncodingException;
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

/** Contrato HTTP de interpretaciones de regla (convenciones de API §6.2) con la cadena de
 * seguridad real: autenticación, permiso, ámbito y estado de la interpretación. */
@WebMvcTest(controllers = InterpretacionReglaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class InterpretacionReglaControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TEMPORADA = UUID.fromString("22222222-2222-4111-8111-111111111111");
    private static final String BASE = "/api/v1/interpretaciones-regla";
    private static final String CUERPO_REGISTRAR =
            "{\"temporadaId\":\"" + TEMPORADA + "\",\"descripcion\":\"Interpretaci\u00f3n de pruebas\"}";
    private static final String CUERPO_RESOLUCION = "{\"estado\":\"RECHAZADA\",\"observaciones\":\"Motivo del"
            + " rechazo con m\u00ednimo de diez caracteres.\"}";
    private static final InterpretacionReglaDto RESULTADO = new InterpretacionReglaDto(
            ID, TEMPORADA, "Interpretaci\u00f3n de pruebas", EstadoInterpretacion.PENDIENTE, null,
            OffsetDateTime.parse("2032-08-01T06:00:00-06:00"), null, null, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private InterpretacionReglaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        var peticion = mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return peticion.exchange();
    }

    private static String cuerpo(MvcTestResult resultado) {
        try {
            return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 siempre está disponible", e);
        }
    }

    @Test
    @DisplayName("RF-007: POST registra la interpretación y responde 201 con la ubicación")
    void registrar() {
        given(servicio.registrar(any(CrearInterpretacionRequest.class))).willReturn(RESULTADO);

        MvcTestResult resultado = post(BASE, CUERPO_REGISTRAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$.descripcion").isEqualTo("Interpretaci\u00f3n de"
                + " pruebas");
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE");
        assertThat(resultado).bodyJson().extractingPath("$.registradaEn").isNotNull();
        assertThat(cuerpo(resultado))
                .as("la autoría sale de la sesión, no del cuerpo")
                .doesNotContain("registradaPor", "resueltaPor", "observaciones");
        ArgumentCaptor<CrearInterpretacionRequest> peticion = ArgumentCaptor.forClass(CrearInterpretacionRequest.class);
        Mockito.verify(servicio).registrar(peticion.capture());
        assertThat(peticion.getValue().descripcion()).isEqualTo("Interpretaci\u00f3n de pruebas");
    }

    @Test
    @DisplayName("RF-007: el cuerpo vacío devuelve 400 VALIDACION y no llega al servicio")
    void registrarSinCuerpo() {
        MvcTestResult resultado = post(BASE, "{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[?(@.campo == 'temporadaId')].campo")
                .isEqualTo(List.of("temporadaId"));
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-007: la descripción vacía devuelve 400 VALIDACION")
    void registrarDescripcionVacia() {
        MvcTestResult resultado = post(BASE,
                "{\"temporadaId\":\"" + TEMPORADA + "\",\"descripcion\":\"   \"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("descripcion");
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-007: una descripción de más de 2000 caracteres devuelve 400 VALIDACION [PROPUESTA]")
    void registrarDescripcionMuyLarga() {
        String descripcion = "x".repeat(2001);
        MvcTestResult resultado = post(BASE,
                "{\"temporadaId\":\"" + TEMPORADA + "\",\"descripcion\":\"" + descripcion + "\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("descripcion");
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-007: GET lista con paginación y filtros")
    void listar() {
        given(servicio.listar(TEMPORADA, EstadoInterpretacion.PENDIENTE, 1, 10))
                .willReturn(Pagina.de(List.of(RESULTADO), 1, 10, 11));

        MvcTestResult resultado = mvc.get().uri(BASE + "?temporadaId=" + TEMPORADA
                + "&estado=PENDIENTE&pagina=1&tamano=10").header(HttpHeaders.AUTHORIZATION, autorizacion)
                .exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estado").isEqualTo("PENDIENTE");
        assertThat(resultado).bodyJson().extractingPath("$.pagina").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
        Mockito.verify(servicio).listar(TEMPORADA, EstadoInterpretacion.PENDIENTE, 1, 10);
    }

    @Test
    @DisplayName("RF-007: GET lista sin filtros y listas vacías")
    void listarSinFiltros() {
        given(servicio.listar(null, null, 0, 20)).willReturn(Pagina.de(List.of(), 0, 20, 0));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(0);
        assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(0);
        Mockito.verify(servicio).listar(null, null, 0, 20);
    }

    @Test
    @DisplayName("RF-007: GET con un estado que no existe devuelve 400 VALIDACION")
    void listarEstadoInvalido() {
        MvcTestResult resultado = mvc.get().uri(BASE + "?estado=CIRCULANTE")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-007: POST resolución autoriza")
    void resolverAutoriza() {
        InterpretacionReglaDto autorizada = new InterpretacionReglaDto(ID, TEMPORADA, "Interpretaci\u00f3n",
                EstadoInterpretacion.AUTORIZADA, null, OffsetDateTime.parse("2032-08-01T06:00:00-06:00"), null, null,
                null);
        given(servicio.resolver(eq(ID), any(ResolverInterpretacionRequest.class))).willReturn(autorizada);

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion",
                "{\"estado\":\"AUTORIZADA\"}");

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("AUTORIZADA");
        ArgumentCaptor<ResolverInterpretacionRequest> peticion = ArgumentCaptor
                .forClass(ResolverInterpretacionRequest.class);
        Mockito.verify(servicio).resolver(eq(ID), peticion.capture());
        assertThat(peticion.getValue().estado()).isEqualTo(EstadoInterpretacion.AUTORIZADA);
    }

    @Test
    @DisplayName("RF-007: POST resolución rechaza con observaciones")
    void resolverRechaza() {
        InterpretacionReglaDto rechazada = new InterpretacionReglaDto(ID, TEMPORADA, "Interpretaci\u00f3n",
                EstadoInterpretacion.RECHAZADA, null, OffsetDateTime.parse("2032-08-01T06:00:00-06:00"), null, null,
                "Motivo del rechazo con m\u00ednimo de diez caracteres.");
        given(servicio.resolver(eq(ID), any(ResolverInterpretacionRequest.class))).willReturn(rechazada);

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", CUERPO_RESOLUCION);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("RECHAZADA");
        assertThat(resultado).bodyJson().extractingPath("$.observaciones").isEqualTo("Motivo del rechazo con"
                + " m\u00ednimo de diez caracteres.");
    }

    @Test
    @DisplayName("RF-007: resolución sin estado devuelve 400 VALIDACION")
    void resolverSinEstado() {
        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion",
                "{\"observaciones\":\"x\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("estado");
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-007: resolución de una interpretación ya resuelta devuelve 409 RF-007")
    void resolverYaResuelta() {
        given(servicio.resolver(eq(ID), any(ResolverInterpretacionRequest.class)))
                .willThrow(ReglaNegocioException.conflicto("RF-007",
                        "La interpretaci\u00f3n ya est\u00e1 rechazada: una resoluci\u00f3n no se reabre."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", CUERPO_RESOLUCION);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-007");
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/interpretaciones-regla")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(null, null, 0, 20)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/interpretaciones-regla")
    Stream<DynamicTest> seguridadRegistrar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_REGISTRAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrar(any(CrearInterpretacionRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/interpretaciones-regla/{id}/resolucion")
    Stream<DynamicTest> seguridadResolver() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/resolucion", ID)
                .cuerpoJson(CUERPO_RESOLUCION)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.resolver(eq(ID), any(ResolverInterpretacionRequest.class)))
                        .willThrow(error))
                .pruebas();
    }
}

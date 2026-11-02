package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import mx.tecnm.morelia.siglim.temporadas.TemporadaDto.ActivacionDto;
import mx.tecnm.morelia.siglim.temporadas.TemporadaDto.RequisitoActivacionDto;
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

/** Contrato HTTP de temporadas (convenciones de API §6.2) con la cadena de seguridad real. */
@WebMvcTest(controllers = TemporadaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class TemporadaControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String BASE = "/api/v1/temporadas";
    private static final LocalDate INICIO = LocalDate.parse("2030-08-01");
    private static final LocalDate FIN = LocalDate.parse("2030-12-15");
    private static final String CUERPO_CREAR =
            "{\"nombre\":\"Temporada 2030\",\"fechaInicio\":\"2030-08-01\",\"fechaFin\":\"2030-12-15\"}";
    private static final String CUERPO_CIERRE = "{\"motivo\":\"Fin del calendario deportivo\"}";
    private static final TemporadaDto TEMPORADA = new TemporadaDto(ID, "Temporada 2030", INICIO, FIN,
            EstadoTemporada.CONFIGURACION, OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), 1, 1, 1,
            new ActivacionDto(true, List.of(
                    new RequisitoActivacionDto("RN-058", "No hay otra temporada activa.", true),
                    new RequisitoActivacionDto("RF-100", "Parámetros deportivos registrados (versión 1).", true),
                    new RequisitoActivacionDto("RF-004", "Al menos una categoría participa en la temporada.", true),
                    new RequisitoActivacionDto("RF-005", "Al menos un grupo está creado.", true))));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TemporadaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        var peticion = mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return peticion.exchange();
    }

    @Test
    @DisplayName("RF-001: GET lista con la envoltura de paginación y la lista de verificación")
    void listar() {
        given(servicio.listar(1, 10)).willReturn(Pagina.de(List.of(TEMPORADA), 1, 10, 11));

        MvcTestResult resultado = mvc.get().uri(BASE + "?pagina=1&tamano=10")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].nombre").isEqualTo("Temporada 2030");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].activacion.puedeActivarse").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].activacion.requisitos[2].codigo")
                .isEqualTo("RF-004");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
        assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
        Mockito.verify(servicio).listar(1, 10);
    }

    @Test
    @DisplayName("RF-001: POST responde 201 con Location")
    void crear() {
        given(servicio.crear(any(CrearTemporadaRequest.class))).willReturn(TEMPORADA);

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("CONFIGURACION");
        ArgumentCaptor<CrearTemporadaRequest> peticion = ArgumentCaptor.forClass(CrearTemporadaRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().fechaInicio()).isEqualTo(INICIO);
    }

    @Test
    @DisplayName("RF-001: POST sin fecha de inicio responde 400 VALIDACION y no llega al servicio")
    void crearSinFechaInicio() {
        MvcTestResult resultado = post(BASE, "{\"nombre\":\"Temporada 2030\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("fechaInicio");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-001: PATCH cambia solo lo indicado")
    void actualizar() {
        given(servicio.actualizar(eq(ID), any(ActualizarTemporadaRequest.class))).willReturn(TEMPORADA);

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Temporada 2031\"}").exchange();

        assertThat(resultado).hasStatusOk();
        ArgumentCaptor<ActualizarTemporadaRequest> peticion =
                ArgumentCaptor.forClass(ActualizarTemporadaRequest.class);
        Mockito.verify(servicio).actualizar(eq(ID), peticion.capture());
        assertThat(peticion.getValue().nombre()).isEqualTo("Temporada 2031");
        assertThat(peticion.getValue().fechaInicio()).isNull();
    }

    @Test
    @DisplayName("RF-001: la activación es un POST sin cuerpo y responde la temporada activa")
    void activar() {
        given(servicio.activar(ID)).willReturn(new TemporadaDto(ID, "Temporada 2030", INICIO, FIN,
                EstadoTemporada.ACTIVA, TEMPORADA.creadoEn(), 1, 1, 1, null));

        MvcTestResult resultado = post(BASE + "/" + ID + "/activacion", null);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVA");
        assertThat(resultado).bodyJson().doesNotHavePath("$.activacion");
    }

    @Test
    @DisplayName("RF-100: el 409 de activación lleva los requisitos que faltan")
    void activarSinRequisitos() {
        RequisitoActivacionDto faltante =
                new RequisitoActivacionDto("RF-100", "Faltan los parámetros deportivos de la temporada.", false);
        given(servicio.activar(ID)).willThrow(ReglaNegocioException
                .conflicto("RF-100", "La temporada necesita parámetros deportivos antes de activarse.")
                .con("requisitos", List.of(faltante)));

        MvcTestResult resultado = post(BASE + "/" + ID + "/activacion", null);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-100");
        assertThat(resultado).bodyJson().extractingPath("$.requisitos[0].codigo").isEqualTo("RF-100");
    }

    @Test
    @DisplayName("RF-003: el cierre pasa el motivo al servicio")
    void cerrar() {
        given(servicio.cerrar(eq(ID), any(CerrarTemporadaRequest.class))).willReturn(new TemporadaDto(ID,
                "Temporada 2030", INICIO, FIN, EstadoTemporada.CERRADA, TEMPORADA.creadoEn(), 1, 1, 1, null));

        MvcTestResult resultado = post(BASE + "/" + ID + "/cierre", CUERPO_CIERRE);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("CERRADA");
        ArgumentCaptor<CerrarTemporadaRequest> peticion = ArgumentCaptor.forClass(CerrarTemporadaRequest.class);
        Mockito.verify(servicio).cerrar(eq(ID), peticion.capture());
        assertThat(peticion.getValue().motivo()).isEqualTo("Fin del calendario deportivo");
    }

    @Test
    @DisplayName("RN-021: el 400 por falta de motivo conserva el código")
    void cerrarSinMotivo() {
        given(servicio.cerrar(eq(ID), any(CerrarTemporadaRequest.class)))
                .willThrow(ReglaNegocioException.invalida("RN-021", "Escribe un motivo de al menos 10 caracteres."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/cierre", "{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-021");
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/temporadas")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(0, 20)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/temporadas/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/temporadas")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearTemporadaRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/temporadas/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", ID)
                .cuerpoJson("{\"nombre\":\"Temporada 2031\"}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID),
                        any(ActualizarTemporadaRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/temporadas/{id}/activacion")
    Stream<DynamicTest> seguridadActivar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/activacion", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.activar(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/temporadas/{id}/cierre")
    Stream<DynamicTest> seguridadCerrar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/cierre", ID)
                .cuerpoJson(CUERPO_CIERRE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.cerrar(eq(ID),
                        any(CerrarTemporadaRequest.class))).willThrow(error))
                .pruebas();
    }
}

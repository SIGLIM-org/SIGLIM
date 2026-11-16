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
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.PerfilDto;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.TemporadaResumenDto;
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

/** Contrato HTTP de las operaciones de equipo (convenciones de API §6.3) con la seguridad real. */
@WebMvcTest(controllers = OperacionEquipoController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class OperacionEquipoControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID DESTINO = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID EQUIPO = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID TEMPORADA = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final String BASE = "/api/v1/operaciones-equipo";
    private static final String CUERPO_VENTA = "{\"tipo\":\"VENTA\",\"equipoId\":\"" + EQUIPO
            + "\",\"clubDestinoId\":\"" + DESTINO + "\",\"observaciones\":\"Acuerdo de asamblea\"}";
    private static final String CUERPO_CONSERVACION = "{\"tipo\":\"CONSERVACION_PLAZA\",\"equipoId\":\""
            + EQUIPO + "\"}";
    private static final String CUERPO_AUTORIZAR = "{\"decision\":\"AUTORIZADA\"}";
    private static final String CUERPO_RECHAZAR =
            "{\"decision\":\"RECHAZADA\",\"observaciones\":\"La asamblea no aprobó la venta\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private OperacionEquipoService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private static OperacionEquipoDto operacion(EstadoOperacionEquipo estado) {
        return new OperacionEquipoDto(ID, TipoOperacionEquipo.VENTA, estado,
                new ReferenciaDto(EQUIPO, "Halcones"),
                new ReferenciaDto(CLUB, "Club Halcones"),
                new ReferenciaDto(DESTINO, "Club Águilas"),
                new TemporadaResumenDto(TEMPORADA, "Temporada 2031", EstadoTemporada.ACTIVA),
                new PerfilDto(UUID.randomUUID(), "Ana López"),
                OffsetDateTime.parse("2031-08-01T06:00:00-06:00"), null, null, null, null);
    }

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-103: POST registra la venta y responde 201 con la operación SOLICITADA")
    void crear() {
        given(servicio.crear(any(RegistrarOperacionEquipoRequest.class)))
                .willReturn(operacion(EstadoOperacionEquipo.SOLICITADA));

        MvcTestResult resultado = post(BASE, CUERPO_VENTA);

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.tipo").isEqualTo("VENTA");
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("SOLICITADA");
        assertThat(resultado).bodyJson().extractingPath("$.clubOrigen.nombre").isEqualTo("Club Halcones");
        assertThat(resultado).bodyJson().extractingPath("$.clubDestino.nombre").isEqualTo("Club Águilas");
        assertThat(resultado).bodyJson().doesNotHavePath("$.autorizadaPor");
        ArgumentCaptor<RegistrarOperacionEquipoRequest> peticion =
                ArgumentCaptor.forClass(RegistrarOperacionEquipoRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().tipo()).isEqualTo(TipoOperacionEquipo.VENTA);
        assertThat(peticion.getValue().equipoId()).isEqualTo(EQUIPO);
        assertThat(peticion.getValue().clubDestinoId()).isEqualTo(DESTINO);
    }

    @Test
    @DisplayName("RF-104: la conservación de plaza llega al servicio sin club destino")
    void crearConservacion() {
        given(servicio.crear(any(RegistrarOperacionEquipoRequest.class)))
                .willReturn(operacion(EstadoOperacionEquipo.SOLICITADA));

        MvcTestResult resultado = post(BASE, CUERPO_CONSERVACION);

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        ArgumentCaptor<RegistrarOperacionEquipoRequest> peticion =
                ArgumentCaptor.forClass(RegistrarOperacionEquipoRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().tipo()).isEqualTo(TipoOperacionEquipo.CONSERVACION_PLAZA);
        assertThat(peticion.getValue().clubDestinoId()).isNull();
    }

    @Test
    @DisplayName("RF-103: POST sin tipo responde 400 VALIDACION y no llega al servicio")
    void crearSinTipo() {
        MvcTestResult resultado = post(BASE, "{\"equipoId\":\"" + EQUIPO + "\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("tipo");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RN-040: la autorización responde 200 y pasa la decisión al servicio")
    void resolverAutorizar() {
        given(servicio.resolver(eq(ID), any(ResolverOperacionEquipoRequest.class)))
                .willReturn(operacion(EstadoOperacionEquipo.AUTORIZADA));

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", CUERPO_AUTORIZAR);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("AUTORIZADA");
        ArgumentCaptor<ResolverOperacionEquipoRequest> peticion =
                ArgumentCaptor.forClass(ResolverOperacionEquipoRequest.class);
        Mockito.verify(servicio).resolver(eq(ID), peticion.capture());
        assertThat(peticion.getValue().decision()).isEqualTo(DecisionOperacionEquipo.AUTORIZADA);
    }

    @Test
    @DisplayName("RN-021: el rechazo sin observaciones suficientes conserva el 400 y el código")
    void resolverSinObservaciones() {
        given(servicio.resolver(eq(ID), any(ResolverOperacionEquipoRequest.class)))
                .willThrow(ReglaNegocioException.invalida("RN-021",
                        "Escribe observaciones de al menos 10 caracteres para rechazar la operación."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", "{\"decision\":\"RECHAZADA\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-021");
    }

    @Test
    @DisplayName("RN-040: resolver una operación ya resuelta responde 409 RF-103")
    void resolverRepetida() {
        given(servicio.resolver(eq(ID), any(ResolverOperacionEquipoRequest.class)))
                .willThrow(ReglaNegocioException.conflicto("RF-103",
                        "Solo se resuelve una operación en estado SOLICITADA; esta ya está autorizada."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", CUERPO_RECHAZAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-103");
    }

    @Test
    @DisplayName("B-05.3, RF-103: GET lista las operaciones con sus filtros y su estado")
    void listar() {
        given(servicio.listar(any(FiltroOperacionesEquipo.class)))
                .willReturn(Pagina.de(List.of(operacion(EstadoOperacionEquipo.SOLICITADA)), 0, 20, 1));

        MvcTestResult resultado = mvc.get().uri(BASE + "?estado=SOLICITADA&pagina=0&tamano=20")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estado").isEqualTo("SOLICITADA");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].equipo.nombre").isEqualTo("Halcones");
        ArgumentCaptor<FiltroOperacionesEquipo> filtro =
                ArgumentCaptor.forClass(FiltroOperacionesEquipo.class);
        Mockito.verify(servicio).listar(filtro.capture());
        assertThat(filtro.getValue().estado()).isEqualTo(EstadoOperacionEquipo.SOLICITADA);
    }

    @Test
    @DisplayName("B-05.3, VAL-024: GET del detalle responde 404 si el servicio no lo encuentra")
    void consultarFueraDeAmbito() {
        given(servicio.consultar(ID)).willThrow(new RecursoNoEncontradoException("Operación de equipo " + ID));

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("B-05.3: GET del contexto entrega los clubes destino")
    void contexto() {
        given(servicio.contexto()).willReturn(new ContextoOperacionesEquipoDto(
                List.of(new ReferenciaDto(CLUB, "Club Halcones"))));

        MvcTestResult resultado = mvc.get().uri(BASE + "/contexto")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.clubes[0].nombre").isEqualTo("Club Halcones");
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/operaciones-equipo")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_VENTA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(RegistrarOperacionEquipoRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/operaciones-equipo")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroOperacionesEquipo.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/operaciones-equipo/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/operaciones-equipo/contexto")
    Stream<DynamicTest> seguridadContexto() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/contexto")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.contexto()).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/operaciones-equipo/{id}/resolucion")
    Stream<DynamicTest> seguridadResolver() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/resolucion", ID)
                .cuerpoJson(CUERPO_AUTORIZAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.resolver(eq(ID),
                        any(ResolverOperacionEquipoRequest.class))).willThrow(error))
                .pruebas();
    }
}

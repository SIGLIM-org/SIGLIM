package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.PerfilDto;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.PersonaResumenDto;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;
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

/** Contrato HTTP del catálogo de árbitros (convenciones de API §6.5) con la seguridad real. */
@WebMvcTest(controllers = ArbitroController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class ArbitroControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PERSONA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final String BASE = "/api/v1/arbitros";
    private static final String CUERPO_CREAR =
            "{\"personaId\":\"" + PERSONA + "\",\"clubId\":\"" + CLUB + "\"}";
    private static final String CUERPO_VALIDAR =
            "{\"decision\":\"VALIDADO\",\"observaciones\":\"Todo en orden\"}";
    private static final String CUERPO_RECHAZAR = "{\"decision\":\"RECHAZADO\","
            + "\"observaciones\":\"No acreditó la constancia de la escuela de árbitros\"}";
    private static final ArbitroDto ARBITRO = arbitro(EstadoArbitro.PROPUESTO);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ArbitroService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private static ArbitroDto arbitro(EstadoArbitro estado) {
        boolean resuelto = estado == EstadoArbitro.VALIDADO || estado == EstadoArbitro.RECHAZADO;
        return new ArbitroDto(ID, new PersonaResumenDto(PERSONA, "Carlos Alberto Prado"),
                new ReferenciaDto(CLUB, "Club Halcones"), estado,
                new PerfilDto(UUID.randomUUID(), "Ana López"),
                OffsetDateTime.parse("2030-08-02T06:00:00-06:00"),
                resuelto ? new PerfilDto(UUID.randomUUID(), "Comisión de Arbitraje") : null,
                resuelto ? OffsetDateTime.parse("2030-08-02T12:00:00Z") : null,
                resuelto ? "Todo en orden" : null);
    }

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-021: GET lista los árbitros con persona, club y estado")
    void listar() {
        FiltroArbitros filtro = new FiltroArbitros(EstadoArbitro.PROPUESTO, CLUB, 0, 20);
        given(servicio.listar(filtro)).willReturn(Pagina.de(List.of(ARBITRO), 0, 20, 1));

        MvcTestResult resultado = mvc.get()
                .uri(BASE + "?estado=PROPUESTO&clubId=" + CLUB)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].persona.nombreCompleto")
                .isEqualTo("Carlos Alberto Prado");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].club.nombre")
                .isEqualTo("Club Halcones");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estado").isEqualTo("PROPUESTO");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].validadoPor");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(1);
        Mockito.verify(servicio).listar(filtro);
    }

    @Test
    @DisplayName("RF-021: un filtro con UUID mal formado responde 400 VALIDACION")
    void listarConUuidInvalido() {
        MvcTestResult resultado = mvc.get().uri(BASE + "?clubId=no-es-uuid")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-021: un estado desconocido responde 400 VALIDACION")
    void listarConEstadoInvalido() {
        MvcTestResult resultado = mvc.get().uri(BASE + "?estado=NO_EXISTE")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-112: POST responde 201 con Location y el árbitro PROPUESTO")
    void crear() {
        given(servicio.crear(any(CrearArbitroRequest.class))).willReturn(ARBITRO);

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("PROPUESTO");
        ArgumentCaptor<CrearArbitroRequest> peticion = ArgumentCaptor.forClass(CrearArbitroRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().personaId()).isEqualTo(PERSONA);
        assertThat(peticion.getValue().clubId()).isEqualTo(CLUB);
    }

    @Test
    @DisplayName("RF-112: POST sin persona responde 400 VALIDACION y no llega al servicio")
    void crearSinPersona() {
        MvcTestResult resultado = post(BASE, "{\"clubId\":\"" + CLUB + "\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("personaId");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-112: POST sin club responde 400 VALIDACION")
    void crearSinClub() {
        MvcTestResult resultado = post(BASE, "{\"personaId\":\"" + PERSONA + "\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("clubId");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-113: la validación responde 200 y pasa la decisión al servicio")
    void validar() {
        given(servicio.validar(eq(ID), any(ValidarArbitroRequest.class)))
                .willReturn(arbitro(EstadoArbitro.VALIDADO));

        MvcTestResult resultado = post(BASE + "/" + ID + "/validacion", CUERPO_VALIDAR);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("VALIDADO");
        ArgumentCaptor<ValidarArbitroRequest> peticion = ArgumentCaptor.forClass(ValidarArbitroRequest.class);
        Mockito.verify(servicio).validar(eq(ID), peticion.capture());
        assertThat(peticion.getValue().decision()).isEqualTo(DecisionArbitro.VALIDADO);
        assertThat(peticion.getValue().observaciones()).isEqualTo("Todo en orden");
    }

    @Test
    @DisplayName("RF-113: el rechazo con observaciones responde 200")
    void rechazar() {
        given(servicio.validar(eq(ID), any(ValidarArbitroRequest.class)))
                .willReturn(arbitro(EstadoArbitro.RECHAZADO));

        MvcTestResult resultado = post(BASE + "/" + ID + "/validacion", CUERPO_RECHAZAR);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("RECHAZADO");
    }

    @Test
    @DisplayName("RF-113: sin decisión responde 400 VALIDACION")
    void validarSinDecision() {
        MvcTestResult resultado = post(BASE + "/" + ID + "/validacion", "{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("decision");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-113: más de 500 caracteres en observaciones responde 400 VALIDACION")
    void validarObservacionesDemasiadoLargas() {
        String cuerpo = "{\"decision\":\"VALIDADO\",\"observaciones\":\"" + "a".repeat(501) + "\"}";

        MvcTestResult resultado = post(BASE + "/" + ID + "/validacion", cuerpo);

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("observaciones");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-113: validar un árbitro ya resuelto responde 409 RF-113")
    void validarRepetida() {
        given(servicio.validar(eq(ID), any(ValidarArbitroRequest.class)))
                .willThrow(ReglaNegocioException.conflicto("RF-113",
                        "Solo se valida un árbitro en estado PROPUESTO; este ya está validado."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/validacion", CUERPO_RECHAZAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-113");
    }

    @Test
    @DisplayName("RN-005: no existe DELETE del catálogo de árbitros (405)")
    void sinDelete() {
        MvcTestResult resultado = mvc.delete().uri(BASE)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/arbitros")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroArbitros.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/arbitros")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearArbitroRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/arbitros/{id}/validacion")
    Stream<DynamicTest> seguridadValidar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/validacion", ID)
                .cuerpoJson(CUERPO_VALIDAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.validar(eq(ID),
                        any(ValidarArbitroRequest.class))).willThrow(error))
                .pruebas();
    }
}

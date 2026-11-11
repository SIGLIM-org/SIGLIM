package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
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

/** Contrato HTTP de personas y tutela (convenciones de API §6.3; H-22.2, H-09.1). */
@WebMvcTest(controllers = PersonaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class PersonaControllerTest {

    private static final UUID ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String CURP = "GODE840101HDFRNS09";
    private static final String RUTA = "/api/v1/personas?curp=" + CURP;
    private static final PersonaDto PERSONA = new PersonaDto(ID, CURP, "Ana", "López", null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PersonaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-010: GET /personas?curp= devuelve la identidad sin datos de contacto")
    void buscar() {
        given(servicio.buscarPorCurp(CURP)).willReturn(PERSONA);

        MvcTestResult resultado = mvc.get().uri(RUTA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$.curp").isEqualTo(CURP);
        assertThat(resultado).bodyJson().extractingPath("$.nombre").isEqualTo("Ana");
        assertThat(resultado).bodyJson().doesNotHavePath("$.apellidoMaterno");
        Mockito.verify(servicio).buscarPorCurp(CURP);
    }

    @Test
    @DisplayName("RF-010: una CURP mal formada responde 400 VALIDACION")
    void formatoInvalido() {
        given(servicio.buscarPorCurp("GODE"))
                .willThrow(ReglaNegocioException.invalida("VALIDACION",
                        "La CURP debe tener 18 caracteres y el formato oficial."));

        MvcTestResult resultado = mvc.get().uri("/api/v1/personas?curp=GODE")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("VAL-024: una persona inexistente responde 404 NO_ENCONTRADO")
    void noExiste() {
        given(servicio.buscarPorCurp(CURP)).willThrow(new RecursoNoEncontradoException("Persona"));

        MvcTestResult resultado = mvc.get().uri(RUTA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_ENCONTRADO);
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/personas")
    Stream<DynamicTest> seguridadBuscar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, RUTA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.buscarPorCurp(CURP)).willThrow(error))
                .pruebas();
    }

    // ── H-09.1: alta, edición y tutela ──────────────────────────────────────────────────────────

    private static final String CUERPO_ALTA = """
            {"curp":"GODE840101HDFRNS09","nombre":"Ana","apellidoPaterno":"López","fechaNacimiento":"1984-01-01"}
            """;
    private static final String CUERPO_EDICION = """
            {"correo":"ana@correo.mx"}
            """;
    private static final UUID TUTOR = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final String CUERPO_TUTELA = """
            {"tutorId":"44444444-4444-4444-8444-444444444444","parentesco":"Madre"}
            """;
    private static final PersonaDetalleDto DETALLE = new PersonaDetalleDto(ID, CURP, "Ana", "López", null,
            LocalDate.parse("1984-01-01"), "ana@correo.mx", null);

    @Test
    @DisplayName("DEC-84: POST /personas responde 201 con Location, el detalle y no-store")
    void registrar() {
        given(servicio.registrar(any())).willReturn(DETALLE);

        MvcTestResult resultado = mvc.post().uri("/api/v1/personas")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_ALTA).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).hasHeader(HttpHeaders.LOCATION, "/api/v1/personas/" + ID);
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.fechaNacimiento").isEqualTo("1984-01-01");
        assertThat(resultado).bodyJson().extractingPath("$.correo").isEqualTo("ana@correo.mx");
        Mockito.verify(servicio).registrar(new CrearPersonaRequest(CURP, "Ana", "López", null,
                LocalDate.parse("1984-01-01"), null, null));
    }

    @Test
    @DisplayName("RF-010: una CURP existente responde 409 RF-010 con la extensión personaId")
    void registrarCurpExistente() {
        given(servicio.registrar(any())).willThrow(ReglaNegocioException.conflicto("RF-010",
                "Esta persona ya está registrada con esa CURP.").con("personaId", ID));

        MvcTestResult resultado = mvc.post().uri("/api/v1/personas")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_ALTA).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-010");
        assertThat(resultado).bodyJson().extractingPath("$.personaId").isEqualTo(ID.toString());
    }

    @Test
    @DisplayName("DEC-83: PATCH /personas/{id} responde 200 con el detalle y no-store")
    void actualizar() {
        given(servicio.actualizar(eq(ID), any())).willReturn(DETALLE);

        MvcTestResult resultado = mvc.patch().uri("/api/v1/personas/{id}", ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EDICION).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Mockito.verify(servicio).actualizar(ID, new ActualizarPersonaRequest(null, null, null, null, null,
                "ana@correo.mx", null));
    }

    @Test
    @DisplayName("DEC-82: POST /personas/{id}/tutelas responde 201 sin Location")
    void registrarTutela() {
        given(servicio.registrarTutela(eq(ID), any())).willReturn(new TutelaDto(UUID.randomUUID(), ID, TUTOR,
                "Madre", true, OffsetDateTime.parse("2026-11-11T12:00:00-06:00")));

        MvcTestResult resultado = mvc.post().uri("/api/v1/personas/{id}/tutelas", ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_TUTELA).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).doesNotContainHeader(HttpHeaders.LOCATION);
        assertThat(resultado).bodyJson().extractingPath("$.vigente").isEqualTo(true);
        Mockito.verify(servicio).registrarTutela(ID, new RegistrarTutelaRequest(TUTOR, "Madre"));
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/personas")
    Stream<DynamicTest> seguridadRegistrar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/personas")
                .cuerpoJson(CUERPO_ALTA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrar(any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/personas/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, "/api/v1/personas/{id}", ID)
                .cuerpoJson(CUERPO_EDICION)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID), any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/personas/{id}/tutelas")
    Stream<DynamicTest> seguridadTutela() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/personas/{id}/tutelas", ID)
                .cuerpoJson(CUERPO_TUTELA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrarTutela(eq(ID), any())).willThrow(error))
                .pruebas();
    }
}

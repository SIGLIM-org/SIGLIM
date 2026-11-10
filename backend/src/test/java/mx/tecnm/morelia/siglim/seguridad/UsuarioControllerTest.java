package mx.tecnm.morelia.siglim.seguridad;

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
import mx.tecnm.morelia.siglim.seguridad.UsuarioDto.AsignacionDto;
import mx.tecnm.morelia.siglim.seguridad.UsuarioDto.ClubDto;
import mx.tecnm.morelia.siglim.seguridad.UsuarioDto.RolDto;
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

/** Contrato HTTP de usuarios, invitación y asignaciones (convenciones de API §6.1). */
@WebMvcTest(controllers = {UsuarioController.class, AsignacionRolController.class}, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class UsuarioControllerTest {

    private static final UUID ID = UUID.fromString("0b7c3e1a-2d4f-4a6b-9c8d-7e6f5a4b3c2d");
    private static final UUID ROL = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CLUB = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ASIGNACION = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final String BASE = "/api/v1/usuarios";
    private static final String MOTIVO = "Cambio de funciones en la liga";
    private static final OffsetDateTime CREADO_EN = OffsetDateTime.parse("2030-03-01T06:00:00-06:00");

    private static final AsignacionDto ASIGNACION_DTO = new AsignacionDto(ASIGNACION,
            new RolDto(ROL, "DIRECTIVA_CLUB", "Directiva de club"), "CLUB",
            new ClubDto(CLUB, "Club Halcones"), true, CREADO_EN, null);
    private static final UsuarioDto USUARIO = new UsuarioDto(ID, "Ana López", "ana@ejemplo.mx", true,
            CREADO_EN, List.of(ASIGNACION_DTO));

    private static final String CUERPO_INVITAR = """
            {"nombreMostrado":"Ana López","correo":"ana@ejemplo.mx",
             "asignaciones":[{"rolId":"%s","ambito":"CLUB","clubId":"%s"}]}
            """.formatted(ROL, CLUB);
    private static final String CUERPO_ASIGNAR = """
            {"rolId":"%s","ambito":"CLUB","clubId":"%s"}
            """.formatted(ROL, CLUB);
    private static final String CUERPO_REVOCAR = "{\"motivo\":\"" + MOTIVO + "\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UsuarioService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String uri, String cuerpo) {
        return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    // ── Casos felices y validación ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-094: GET lista con la envoltura de paginación y pasa los filtros")
    void listar() {
        FiltroUsuarios filtro = new FiltroUsuarios("ana", ROL, Ambito.CLUB, CLUB, true, 1, 10);
        given(servicio.listar(filtro)).willReturn(Pagina.de(List.of(USUARIO), 1, 10, 11));

        MvcTestResult resultado = mvc.get()
                .uri(BASE + "?q=ana&rolId=" + ROL + "&ambito=CLUB&clubId=" + CLUB + "&activo=true&pagina=1&tamano=10")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].nombreMostrado").isEqualTo("Ana López");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].asignaciones[0].rol.codigo")
                .isEqualTo("DIRECTIVA_CLUB");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
        Mockito.verify(servicio).listar(filtro);
    }

    @Test
    @DisplayName("RF-094: GET por id responde el detalle")
    void consultar() {
        given(servicio.consultar(ID)).willReturn(USUARIO);

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.correo").isEqualTo("ana@ejemplo.mx");
    }

    @Test
    @DisplayName("H-03: POST invita y responde 201 con Location")
    void invitar() {
        given(servicio.invitar(any(CrearUsuarioRequest.class))).willReturn(USUARIO);

        MvcTestResult resultado = post(BASE, CUERPO_INVITAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.asignaciones[0].ambito").isEqualTo("CLUB");
        ArgumentCaptor<CrearUsuarioRequest> peticion = ArgumentCaptor.forClass(CrearUsuarioRequest.class);
        Mockito.verify(servicio).invitar(peticion.capture());
        assertThat(peticion.getValue().correo()).isEqualTo("ana@ejemplo.mx");
        assertThat(peticion.getValue().asignaciones()).singleElement()
                .satisfies(asignacion -> assertThat(asignacion.clubId()).isEqualTo(CLUB));
    }

    @Test
    @DisplayName("H-03 criterio 2: una asignación CLUB sin club responde 400 y no llega al servicio")
    void invitarClubSinClub() {
        MvcTestResult resultado = post(BASE, """
                {"nombreMostrado":"Ana","correo":"ana@ejemplo.mx",
                 "asignaciones":[{"rolId":"%s","ambito":"CLUB"}]}
                """.formatted(ROL));

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("H-03: invitar sin asignaciones responde 400")
    void invitarSinAsignaciones() {
        MvcTestResult resultado = post(BASE,
                "{\"nombreMostrado\":\"Ana\",\"correo\":\"ana@ejemplo.mx\",\"asignaciones\":[]}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-094: PATCH activa o desactiva la cuenta")
    void actualizar() {
        given(servicio.actualizar(eq(ID), any(ActualizarUsuarioRequest.class))).willReturn(USUARIO);

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":true}").exchange();

        assertThat(resultado).hasStatusOk();
        ArgumentCaptor<ActualizarUsuarioRequest> peticion = ArgumentCaptor.forClass(ActualizarUsuarioRequest.class);
        Mockito.verify(servicio).actualizar(eq(ID), peticion.capture());
        assertThat(peticion.getValue().activo()).isTrue();
    }

    @Test
    @DisplayName("RF-094: PATCH sin el estado final responde 400")
    void actualizarSinEstado() {
        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\":null}").exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-094: POST de una asignación responde 201 con Location")
    void asignar() {
        given(servicio.asignar(eq(ID), any(AsignacionRequest.class))).willReturn(ASIGNACION_DTO);

        MvcTestResult resultado = post(BASE + "/" + ID + "/asignaciones", CUERPO_ASIGNAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED)
                .hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID + "/asignaciones/" + ASIGNACION);
        assertThat(resultado).bodyJson().extractingPath("$.club.nombre").isEqualTo("Club Halcones");
    }

    @Test
    @DisplayName("RF-094: revocar una asignación con motivo responde 200")
    void revocar() {
        AsignacionDto revocada = new AsignacionDto(ASIGNACION, ASIGNACION_DTO.rol(), "CLUB",
                ASIGNACION_DTO.club(), false, CREADO_EN, CREADO_EN.plusDays(1));
        given(servicio.revocar(eq(ASIGNACION), any(RevocarAsignacionRequest.class))).willReturn(revocada);

        MvcTestResult resultado = post("/api/v1/asignaciones-rol/" + ASIGNACION + "/revocacion",
                CUERPO_REVOCAR);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.activa").isEqualTo(false);
    }

    @Test
    @DisplayName("RF-094: revocar sin el motivo mínimo responde 400")
    void revocarSinMotivo() {
        MvcTestResult resultado = post("/api/v1/asignaciones-rol/" + ASIGNACION + "/revocacion",
                "{\"motivo\":\"corto\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-094: un correo duplicado responde 409 DUPLICADO")
    void invitarDuplicado() {
        given(servicio.invitar(any(CrearUsuarioRequest.class)))
                .willThrow(ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                        "Ya existe una cuenta con el correo «ana@ejemplo.mx»."));

        MvcTestResult resultado = post(BASE, CUERPO_INVITAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.DUPLICADO);
    }

    @Test
    @DisplayName("H-03 criterio 4: VAL-022 responde 403 con su código en las tres rutas")
    void cuentaPropiaVal022() {
        given(servicio.asignar(eq(ID), any(AsignacionRequest.class)))
                .willThrow(ReglaNegocioException.prohibida("VAL-022",
                        "No puedes cambiar las asignaciones de rol de tu propia cuenta."));
        given(servicio.revocar(eq(ASIGNACION), any(RevocarAsignacionRequest.class)))
                .willThrow(ReglaNegocioException.prohibida("VAL-022",
                        "No puedes cambiar las asignaciones de rol de tu propia cuenta."));
        given(servicio.actualizar(eq(ID), any(ActualizarUsuarioRequest.class)))
                .willThrow(ReglaNegocioException.prohibida("VAL-022",
                        "No puedes activar ni desactivar tu propia cuenta."));

        List<MvcTestResult> resultados = List.of(
                post(BASE + "/" + ID + "/asignaciones", CUERPO_ASIGNAR),
                post("/api/v1/asignaciones-rol/" + ASIGNACION + "/revocacion", CUERPO_REVOCAR),
                mvc.patch().uri(BASE + "/" + ID)
                        .header(HttpHeaders.AUTHORIZATION, autorizacion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true}").exchange());

        for (MvcTestResult resultado : resultados) {
            assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN)
                    .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-022");
        }
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/usuarios")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroUsuarios.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/usuarios/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/usuarios")
    Stream<DynamicTest> seguridadInvitar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_INVITAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.invitar(any(CrearUsuarioRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/usuarios/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", ID)
                .cuerpoJson("{\"activo\":true}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID),
                        any(ActualizarUsuarioRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/usuarios/{id}/asignaciones")
    Stream<DynamicTest> seguridadAsignar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/asignaciones", ID)
                .cuerpoJson(CUERPO_ASIGNAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.asignar(eq(ID),
                        any(AsignacionRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/asignaciones-rol/{id}/revocacion")
    Stream<DynamicTest> seguridadRevocar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/asignaciones-rol/{id}/revocacion", ASIGNACION)
                .cuerpoJson(CUERPO_REVOCAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.revocar(eq(ASIGNACION),
                        any(RevocarAsignacionRequest.class))).willThrow(error))
                .pruebas();
    }
}

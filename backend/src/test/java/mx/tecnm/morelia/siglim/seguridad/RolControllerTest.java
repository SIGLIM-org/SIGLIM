package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.SolicitudCambioPermisosDto.PerfilSolicitudDto;
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

/** Contrato HTTP del catálogo de roles y de la matriz de permisos (convenciones de API §6.1). */
@WebMvcTest(controllers = {RolController.class, PermisoController.class}, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class RolControllerTest {

    private static final UUID ROL = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SOLICITUD = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID ADMINISTRADOR = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final String BASE = "/api/v1/roles";
    private static final String MOTIVO = "Ajuste de la matriz aprobado por la Liga";
    private static final OffsetDateTime FECHA = OffsetDateTime.parse("2031-02-01T10:00:00-06:00");
    private static final String CUERPO = """
            {"permisos":["seguridad.rol:CONSULTAR"],"motivo":"%s"}
            """.formatted(MOTIVO);

    private static final PermisoDto PERMISO =
            new PermisoDto("seguridad.rol:CONSULTAR", "M-01", "Consultar los roles");
    private static final RolDto ROL_DTO = new RolDto(ROL, "PROGRAMADOR", "Programador de partidos",
            "Programa las jornadas", Ambito.LIGA, true);
    private static final RolDetalleDto DETALLE = new RolDetalleDto(ROL, "PROGRAMADOR",
            "Programador de partidos", "Programa las jornadas", Ambito.LIGA, true, List.of(PERMISO));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RolService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    // ── Casos felices y reglas ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-094: GET /roles devuelve el catálogo con el ámbito predeterminado")
    void listar() {
        given(servicio.listar()).willReturn(List.of(ROL_DTO));

        MvcTestResult resultado = mvc.get().uri(BASE)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].codigo").isEqualTo("PROGRAMADOR");
        assertThat(resultado).bodyJson().extractingPath("$[0].ambitoPredeterminado").isEqualTo("LIGA");
        assertThat(resultado).bodyJson().extractingPath("$[0].asignable").isEqualTo(true);
    }

    @Test
    @DisplayName("RF-095: GET /roles/{id} devuelve el detalle con sus permisos")
    void consultar() {
        given(servicio.consultar(ROL)).willReturn(DETALLE);

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + ROL)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("PROGRAMADOR");
        assertThat(resultado).bodyJson().extractingPath("$.permisos[0].codigo")
                .isEqualTo("seguridad.rol:CONSULTAR");
        assertThat(resultado).bodyJson().extractingPath("$.permisos[0].modulo").isEqualTo("M-01");
    }

    @Test
    @DisplayName("RF-095: GET /permisos devuelve el catálogo y los pares incompatibles")
    void catalogoPermisos() {
        given(servicio.catalogoPermisos()).willReturn(new CatalogoPermisosDto(List.of(PERMISO),
                List.of(new ParIncompatibleDto("seguridad.rol:EDITAR",
                        "seguridad.permisos:APROBAR_INCOMPATIBLE", "RN-053"))));

        MvcTestResult resultado = mvc.get().uri("/api/v1/permisos")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.permisos[0].codigo")
                .isEqualTo("seguridad.rol:CONSULTAR");
        assertThat(resultado).bodyJson().extractingPath("$.paresIncompatibles[0].permisoA")
                .isEqualTo("seguridad.rol:EDITAR");
    }

    @Test
    @DisplayName("H-20 criterio 1: PATCH /roles/{id}/permisos aplica el cambio y responde 200")
    void actualizarPermisosAplicado() {
        given(servicio.actualizarPermisos(eq(ROL), any(ActualizarPermisosRolRequest.class)))
                .willReturn(ResultadoCambioPermisos.aplicado(DETALLE));

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ROL + "/permisos")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("PROGRAMADOR");
        assertThat(resultado).bodyJson().extractingPath("$.permisos[0].codigo")
                .isEqualTo("seguridad.rol:CONSULTAR");

        ArgumentCaptor<ActualizarPermisosRolRequest> peticion =
                ArgumentCaptor.forClass(ActualizarPermisosRolRequest.class);
        Mockito.verify(servicio).actualizarPermisos(eq(ROL), peticion.capture());
        assertThat(peticion.getValue().permisos()).containsExactly("seguridad.rol:CONSULTAR");
        assertThat(peticion.getValue().motivo()).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("H-20 criterio 2: un par incompatible responde 202 con la solicitud pendiente")
    void actualizarPermisosPendiente() {
        SolicitudCambioPermisosDto solicitud = new SolicitudCambioPermisosDto(SOLICITUD, ROL_DTO,
                List.of("seguridad.permisos:APROBAR_INCOMPATIBLE"), List.of("seguridad.rol:CONSULTAR"),
                MOTIVO, EstadoSolicitudPermisos.SOLICITADO,
                new PerfilSolicitudDto(ADMINISTRADOR, "Ana López"), FECHA, null, null, null);
        given(servicio.actualizarPermisos(eq(ROL), any(ActualizarPermisosRolRequest.class)))
                .willReturn(ResultadoCambioPermisos.pendiente(solicitud));

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ROL + "/permisos")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.ACCEPTED);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(SOLICITUD.toString());
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("SOLICITADO");
        assertThat(resultado).bodyJson().extractingPath("$.rol.codigo").isEqualTo("PROGRAMADOR");
        assertThat(resultado).bodyJson().extractingPath("$.solicitadoPor.nombreMostrado")
                .isEqualTo("Ana López");
    }

    @Test
    @DisplayName("H-20 criterio 4: el rol propio responde 403 VAL-022")
    void actualizarPermisosRolPropio() {
        given(servicio.actualizarPermisos(eq(ROL), any(ActualizarPermisosRolRequest.class)))
                .willThrow(ReglaNegocioException.prohibida("VAL-022",
                        "No puedes cambiar los permisos de tu propio rol."));

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ROL + "/permisos")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-022");
    }

    @Test
    @DisplayName("RN-021: un cambio sin motivo suficiente responde 400 y no llega al servicio")
    void actualizarPermisosSinMotivo() {
        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ROL + "/permisos")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permisos\":[],\"motivo\":\"corto\"}").exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/roles")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar()).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/roles/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", ROL)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(ROL)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/permisos")
    Stream<DynamicTest> seguridadCatalogoPermisos() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/permisos")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.catalogoPermisos()).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/roles/{id}/permisos")
    Stream<DynamicTest> seguridadActualizarPermisos() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}/permisos", ROL)
                .cuerpoJson(CUERPO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizarPermisos(eq(ROL),
                        any(ActualizarPermisosRolRequest.class))).willThrow(error))
                .pruebas();
    }
}

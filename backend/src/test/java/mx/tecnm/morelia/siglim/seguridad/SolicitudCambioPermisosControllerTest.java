package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
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

/** Contrato HTTP del control dual de permisos (convenciones de API §6.1, RF-096). */
@WebMvcTest(controllers = SolicitudCambioPermisosController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class SolicitudCambioPermisosControllerTest {

    private static final UUID SOLICITUD = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID ROL = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ADMINISTRADOR = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID PRESIDENTE = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final String BASE = "/api/v1/solicitudes-cambio-permisos";
    private static final String MOTIVO = "Ajuste de la matriz aprobado por la Liga";
    private static final OffsetDateTime FECHA = OffsetDateTime.parse("2031-02-01T10:00:00-06:00");

    private static final RolDto ROL_DTO = new RolDto(ROL, "PROGRAMADOR", "Programador de partidos",
            "Programa las jornadas", Ambito.LIGA, true);
    private static final SolicitudCambioPermisosDto PENDIENTE = new SolicitudCambioPermisosDto(
            SOLICITUD, ROL_DTO, List.of("seguridad.permisos:APROBAR_INCOMPATIBLE"),
            List.of("seguridad.rol:CONSULTAR"), MOTIVO, EstadoSolicitudPermisos.SOLICITADO,
            new PerfilSolicitudDto(ADMINISTRADOR, "Ana López"), FECHA, null, null, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RolService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-096: GET lista las solicitudes con la envoltura de paginación y sus filtros")
    void listar() {
        given(servicio.listarSolicitudes(EstadoSolicitudPermisos.SOLICITADO, ROL, 1, 10))
                .willReturn(Pagina.de(List.of(PENDIENTE), 1, 10, 11));

        MvcTestResult resultado = mvc.get()
                .uri(BASE + "?estado=SOLICITADO&rolId=" + ROL + "&pagina=1&tamano=10")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].id")
                .isEqualTo(SOLICITUD.toString());
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estado").isEqualTo("SOLICITADO");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].rol.codigo")
                .isEqualTo("PROGRAMADOR");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
    }

    @Test
    @DisplayName("H-20 criterio 3: el dictamen del Presidente responde 200 con la solicitud resuelta")
    void resolver() {
        SolicitudCambioPermisosDto aprobada = new SolicitudCambioPermisosDto(SOLICITUD, ROL_DTO,
                List.of("seguridad.permisos:APROBAR_INCOMPATIBLE"), List.of("seguridad.rol:CONSULTAR"),
                MOTIVO, EstadoSolicitudPermisos.APROBADO,
                new PerfilSolicitudDto(ADMINISTRADOR, "Ana López"), FECHA,
                new PerfilSolicitudDto(PRESIDENTE, "Luis Pérez"), FECHA.plusDays(1), MOTIVO);
        given(servicio.resolverSolicitud(eq(SOLICITUD), any(ResolverSolicitudCambioPermisosRequest.class)))
                .willReturn(aprobada);

        MvcTestResult resultado = mvc.post().uri(BASE + "/" + SOLICITUD + "/resolucion")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APROBADO\",\"motivo\":\"" + MOTIVO + "\"}").exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("APROBADO");
        assertThat(resultado).bodyJson().extractingPath("$.resueltoPor.nombreMostrado")
                .isEqualTo("Luis Pérez");

        ArgumentCaptor<ResolverSolicitudCambioPermisosRequest> peticion =
                ArgumentCaptor.forClass(ResolverSolicitudCambioPermisosRequest.class);
        Mockito.verify(servicio).resolverSolicitud(eq(SOLICITUD), peticion.capture());
        assertThat(peticion.getValue().decision()).isEqualTo(DecisionSolicitudPermisos.APROBADO);
        assertThat(peticion.getValue().motivo()).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("RN-021: un dictamen sin decisión o sin motivo suficiente responde 400")
    void resolverInvalido() {
        MvcTestResult sinDecision = mvc.post().uri(BASE + "/" + SOLICITUD + "/resolucion")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"" + MOTIVO + "\"}").exchange();
        MvcTestResult sinMotivo = mvc.post().uri(BASE + "/" + SOLICITUD + "/resolucion")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"RECHAZADO\",\"motivo\":\"corto\"}").exchange();

        assertThat(sinDecision).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(sinMotivo).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("DEC-04: quien solicita no aprueba; el 403 RF-096 se propaga")
    void quienSolicitaNoAprueba() {
        given(servicio.resolverSolicitud(eq(SOLICITUD), any(ResolverSolicitudCambioPermisosRequest.class)))
                .willThrow(ReglaNegocioException.prohibida("RF-096",
                        "Quien solicitó el cambio no puede aprobarlo."));

        MvcTestResult resultado = mvc.post().uri(BASE + "/" + SOLICITUD + "/resolucion")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APROBADO\",\"motivo\":\"" + MOTIVO + "\"}").exchange();

        assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-096");
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/solicitudes-cambio-permisos")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listarSolicitudes(any(), any(), anyInt(),
                        anyInt())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/solicitudes-cambio-permisos/{id}/resolucion")
    Stream<DynamicTest> seguridadResolver() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/resolucion", SOLICITUD)
                .cuerpoJson("{\"decision\":\"APROBADO\",\"motivo\":\"" + MOTIVO + "\"}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.resolverSolicitud(eq(SOLICITUD),
                        any(ResolverSolicitudCambioPermisosRequest.class))).willThrow(error))
                .pruebas();
    }
}

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
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
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

/** Contrato HTTP de las solicitudes ARCO (convenciones de API §6.1; B-01, RF-098). */
@WebMvcTest(controllers = SolicitudArcoController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class SolicitudArcoControllerTest {

    private static final UUID PERSONA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SOLICITUD = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final String RUTA = "/api/v1/solicitudes-arco";
    private static final String RUTA_SOLICITUD = RUTA + "/{id}";
    private static final String CUERPO_REGISTRO = """
            {"personaId":"22222222-2222-4222-8222-222222222222","tipo":"ACCESO","descripcion":"Ver mis datos"}
            """;
    private static final String CUERPO_CAMBIO = """
            {"estado":"ATENDIDA","respuesta":"Se entregó la copia de sus datos"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SolicitudArcoService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-098: GET /solicitudes-arco pasa los filtros al servicio y responde la página con no-store")
    void listar() {
        FiltroSolicitudesArco filtro = new FiltroSolicitudesArco(EstadoSolicitudArco.RECIBIDA,
                TipoSolicitudArco.ACCESO, PERSONA, 1, 10);
        given(servicio.listar(filtro)).willReturn(Pagina.de(List.of(solicitud(EstadoSolicitudArco.RECIBIDA)), 1, 10, 11));

        MvcTestResult resultado = mvc.get()
                .uri(RUTA + "?estado=RECIBIDA&tipo=ACCESO&personaId={p}&pagina=1&tamano=10", PERSONA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].tipo").isEqualTo("ACCESO");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].personaNombre").isEqualTo("Persona Prueba");
        assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
    }

    @Test
    @DisplayName("RF-098: un estado desconocido en el filtro responde 400")
    void listarEstadoDesconocido() {
        MvcTestResult resultado = mvc.get().uri(RUTA + "?estado=CERRADA")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-098: POST /solicitudes-arco responde 201 sin Location con la solicitud RECIBIDA")
    void registrar() {
        given(servicio.registrar(any())).willReturn(solicitud(EstadoSolicitudArco.RECIBIDA));

        MvcTestResult resultado = mvc.post().uri(RUTA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_REGISTRO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).doesNotContainHeader(HttpHeaders.LOCATION);
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("RECIBIDA");
        Mockito.verify(servicio).registrar(new RegistrarSolicitudArcoRequest(PERSONA, "ACCESO", "Ver mis datos"));
    }

    @Test
    @DisplayName("RF-098: PATCH /solicitudes-arco/{id} con una transición no permitida responde 422 RF-098")
    void transicionNoPermitida() {
        given(servicio.actualizar(eq(SOLICITUD), any())).willThrow(ReglaNegocioException.noProcesable("RF-098",
                "La solicitud ya está ATENDIDA y no admite más cambios de estado."));

        MvcTestResult resultado = mvc.patch().uri(RUTA_SOLICITUD, SOLICITUD)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_CAMBIO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-098");
        Mockito.verify(servicio).actualizar(SOLICITUD,
                new ActualizarSolicitudArcoRequest("ATENDIDA", "Se entregó la copia de sus datos"));
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/solicitudes-arco")
    Stream<DynamicTest> seguridadListado() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, RUTA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/solicitudes-arco")
    Stream<DynamicTest> seguridadRegistro() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, RUTA)
                .cuerpoJson(CUERPO_REGISTRO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrar(any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/solicitudes-arco/{id}")
    Stream<DynamicTest> seguridadCambioEstado() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, RUTA_SOLICITUD, SOLICITUD)
                .cuerpoJson(CUERPO_CAMBIO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(SOLICITUD), any())).willThrow(error))
                .pruebas();
    }

    private static SolicitudArcoDto solicitud(EstadoSolicitudArco estado) {
        return new SolicitudArcoDto(SOLICITUD, PERSONA, "Persona Prueba", TipoSolicitudArco.ACCESO, "Ver mis datos",
                estado, OffsetDateTime.parse("2026-11-16T10:00:00-06:00"), null, null, null, null);
    }
}

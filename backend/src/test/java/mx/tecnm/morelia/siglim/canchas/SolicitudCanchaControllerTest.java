package mx.tecnm.morelia.siglim.canchas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.canchas.SolicitudCanchaDto.PerfilDto;
import mx.tecnm.morelia.siglim.canchas.SolicitudCanchaDto.ReferenciaDto;
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

/** Contrato HTTP de los trámites de cancha (convenciones de API §6.6) con la seguridad real. */
@WebMvcTest(controllers = SolicitudCanchaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class SolicitudCanchaControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String BASE = "/api/v1/solicitudes-cancha";
    private static final String CUERPO_PRESTAMO = """
            {"tipo":"PRESTAMO","clubId":"%s","prestamo":{"lugar":"Morelia, Michoacán",
             "dirigidaA":"DUENO_EXTERNO","destinatarioNombre":"Ing. Laura Pérez",
             "destinatarioCargo":"Administración","solicitanteNombre":"Mario Ruiz",
             "solicitanteCargo":"Presidente","instalacion":"Cancha 2","finalidad":"Jornada de liga",
             "fechaUtilizacion":"2031-09-07","horaInicio":"10:00","horaTermino":"12:00",
             "actividad":"Partido oficial","responsableActividad":"Mario Ruiz",
             "telefonoContacto":"4431234567"}}
            """.formatted(CLUB);
    private static final String CUERPO_APROBAR = "{\"decision\":\"APROBADA\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SolicitudCanchaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private static SolicitudCanchaDto solicitud(EstadoSolicitudCancha estado) {
        return new SolicitudCanchaDto(ID, TipoSolicitudCancha.PRESTAMO, 1, estado, "OF-2031-0001",
                new ReferenciaDto(CLUB, "Halcones"), null, null, DatosSolicitudPrueba.prestamo(), null,
                new PerfilDto(UUID.randomUUID(), "Mario Ruiz"),
                OffsetDateTime.parse("2031-08-01T06:00:00-06:00"), null, null, null);
    }

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-117: POST captura el Formato 1 y responde 201 con el número de oficio")
    void crear() {
        given(servicio.crear(any(CrearSolicitudCanchaRequest.class)))
                .willReturn(solicitud(EstadoSolicitudCancha.PENDIENTE));

        MvcTestResult resultado = post(BASE, CUERPO_PRESTAMO);

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.numeroOficio").isEqualTo("OF-2031-0001");
        assertThat(resultado).bodyJson().extractingPath("$.formato").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("$.prestamo.horaInicio").isEqualTo("10:00:00");
        assertThat(resultado).bodyJson().doesNotHavePath("$.oferta");
        ArgumentCaptor<CrearSolicitudCanchaRequest> peticion = ArgumentCaptor.forClass(CrearSolicitudCanchaRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().prestamo().fechaUtilizacion()).isEqualTo(LocalDate.of(2031, 9, 7));
        assertThat(peticion.getValue().clubId()).isEqualTo(CLUB);
    }

    @Test
    @DisplayName("R8: un campo obligatorio del formato vacío responde 400 con el campo anidado")
    void crearSinCampoDelFormato() {
        MvcTestResult resultado = post(BASE, CUERPO_PRESTAMO.replace("\"solicitanteNombre\":\"Mario Ruiz\"",
                "\"solicitanteNombre\":\"\""));

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("prestamo.solicitanteNombre");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-117: el dictamen responde 200 y pasa la decisión al servicio")
    void resolver() {
        given(servicio.resolver(eq(ID), any(ResolverSolicitudCanchaRequest.class)))
                .willReturn(solicitud(EstadoSolicitudCancha.APROBADA));

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", CUERPO_APROBAR);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("APROBADA");
    }

    @Test
    @DisplayName("RF-117: dictaminar un trámite ya resuelto conserva el 409 y su código")
    void resolverRepetido() {
        given(servicio.resolver(eq(ID), any(ResolverSolicitudCanchaRequest.class)))
                .willThrow(ReglaNegocioException.conflicto("RF-117", "Este trámite ya se dictaminó: está aprobada."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/resolucion", CUERPO_APROBAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-117");
    }

    @Test
    @DisplayName("B-14: GET filtra por los tipos de la pestaña y el estado")
    void listar() {
        given(servicio.listar(any(FiltroSolicitudesCancha.class)))
                .willReturn(Pagina.de(List.of(solicitud(EstadoSolicitudCancha.PENDIENTE)), 0, 20, 1));

        MvcTestResult resultado = mvc.get().uri(BASE + "?tipo=PRESTAMO&tipo=COMODATO&estado=PENDIENTE")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].club.nombre").isEqualTo("Halcones");
        ArgumentCaptor<FiltroSolicitudesCancha> filtro = ArgumentCaptor.forClass(FiltroSolicitudesCancha.class);
        Mockito.verify(servicio).listar(filtro.capture());
        assertThat(filtro.getValue().tipos())
                .containsExactly(TipoSolicitudCancha.PRESTAMO, TipoSolicitudCancha.COMODATO);
        assertThat(filtro.getValue().estado()).isEqualTo(EstadoSolicitudCancha.PENDIENTE);
    }

    @Test
    @DisplayName("RF-118: GET del oficio entrega los datos de la carta")
    void oficio() {
        given(servicio.oficio(ID)).willReturn(new CartaOficioDto("OF-2031-0001", TipoSolicitudCancha.PRESTAMO, 1,
                "Solicitud de préstamo de cancha deportiva.", "Liga", LocalDate.of(2031, 8, 1), "Halcones",
                DatosSolicitudPrueba.prestamo(), null, EstadoSolicitudCancha.PENDIENTE, null, null, null));

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + ID + "/oficio")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.numeroOficio").isEqualTo("OF-2031-0001");
        assertThat(resultado).bodyJson().extractingPath("$.fechaEmision").isEqualTo("2031-08-01");
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/solicitudes-cancha")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_PRESTAMO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearSolicitudCanchaRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/solicitudes-cancha")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroSolicitudesCancha.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/solicitudes-cancha/contexto")
    Stream<DynamicTest> seguridadContexto() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/contexto")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.contexto()).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/solicitudes-cancha/{id}/resolucion")
    Stream<DynamicTest> seguridadResolver() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/resolucion", ID)
                .cuerpoJson(CUERPO_APROBAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.resolver(eq(ID),
                        any(ResolverSolicitudCanchaRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/solicitudes-cancha/{id}/oficio")
    Stream<DynamicTest> seguridadOficio() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}/oficio", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.oficio(ID)).willThrow(error))
                .pruebas();
    }
}

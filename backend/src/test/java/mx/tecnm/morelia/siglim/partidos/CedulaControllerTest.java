package mx.tecnm.morelia.siglim.partidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.EventoDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.IncidenciaDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.InvolucradoDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.JugadorAlineadoDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.TipoIncidenciaDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.MarcadorDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.PartidoDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
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

/** Contrato HTTP de la captura de la cédula (convenciones de API §6.8, H-16.1) con la seguridad real. */
@WebMvcTest(controllers = CedulaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class CedulaControllerTest {

    private static final UUID PARTIDO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CEDULA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID LOCAL = UUID.fromString("44444444-4444-4444-8444-000000000001");
    private static final UUID VISITANTE = UUID.fromString("44444444-4444-4444-8444-000000000002");
    private static final UUID REGISTRO = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final String CUERPO_ALINEACION = """
            { "jugadores": [ { "registroJugadorId": "%s", "equipoId": "%s", "numeroPlayera": 10, "titular": true } ] }
            """.formatted(REGISTRO, LOCAL);
    private static final String CUERPO_MARCADOR = """
            { "golesLocal": 2, "golesVisitante": 1 }
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CedulaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-025: GET /partidos/{id}/cedula responde la cédula con no-store, sin CURP")
    void consultar() throws Exception {
        given(servicio.consultar(PARTIDO)).willReturn(cedula());

        MvcTestResult resultado = mvc.get().uri("/api/v1/partidos/" + PARTIDO + "/cedula")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("EN_CAPTURA");
        assertThat(resultado).bodyJson().extractingPath("$.partido.estado").isEqualTo("EN_CURSO");
        assertThat(resultado).bodyJson().extractingPath("$.partido.inicio").isEqualTo("2031-03-10T10:00:00-06:00");
        assertThat(resultado).bodyJson().extractingPath("$.marcador.porDefault").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.marcador.valorDemo").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.alineaciones[0].categoriaSuperior").isEqualTo(true);
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("curp");
    }

    @Test
    @DisplayName("RF-025: POST /partidos/{id}/cedula abre la cédula con 201 y Location, sin cuerpo")
    void abrir() {
        given(servicio.abrir(PARTIDO)).willReturn(cedula());

        MvcTestResult resultado = mvc.post().uri("/api/v1/partidos/" + PARTIDO + "/cedula")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).hasHeader(HttpHeaders.LOCATION, "/api/v1/partidos/" + PARTIDO + "/cedula");
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(CEDULA.toString());
    }

    @Test
    @DisplayName("RF-019: PUT /cedulas/{id}/alineaciones pasa la lista al servicio y responde la cédula")
    void guardarAlineaciones() {
        ArgumentCaptor<GuardarAlineacionesRequest> peticion = ArgumentCaptor.forClass(GuardarAlineacionesRequest.class);
        given(servicio.guardarAlineaciones(eq(CEDULA), peticion.capture())).willReturn(cedula());

        MvcTestResult resultado = mvc.put().uri("/api/v1/cedulas/" + CEDULA + "/alineaciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_ALINEACION).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(peticion.getValue().jugadores()).singleElement().satisfies(jugador -> {
            assertThat(jugador.registroJugadorId()).isEqualTo(REGISTRO);
            assertThat(jugador.equipoId()).isEqualTo(LOCAL);
            assertThat(jugador.numeroPlayera()).isEqualTo(10);
            assertThat(jugador.titular()).isTrue();
        });
    }

    @Test
    @DisplayName("VAL-002: un jugador no elegible responde 422 con la lista errores por jugador")
    void alineacionNoElegible() {
        given(servicio.guardarAlineaciones(eq(CEDULA), any())).willThrow(ReglaNegocioException
                .noProcesable("VAL-002", "Un jugador no puede alinearse: «Juan» tiene una sanción vigente.")
                .con("errores", List.of(Map.of("campo", "jugadores[0]", "indice", 0, "codigo", "RF-020",
                        "registroJugadorId", REGISTRO.toString(), "mensaje", "«Juan» tiene una sanción vigente."))));

        MvcTestResult resultado = mvc.put().uri("/api/v1/cedulas/" + CEDULA + "/alineaciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_ALINEACION).exchange();

        assertThat(resultado).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-002");
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].codigo").isEqualTo("RF-020");
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].indice").isEqualTo(0);
    }

    @Test
    @DisplayName("RF-047: PUT /cedulas/{id}/marcador pasa el marcador al servicio")
    void guardarMarcador() {
        given(servicio.guardarMarcador(eq(CEDULA), any())).willReturn(cedula());

        MvcTestResult resultado = mvc.put().uri("/api/v1/cedulas/" + CEDULA + "/marcador")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_MARCADOR).exchange();

        assertThat(resultado).hasStatusOk();
        Mockito.verify(servicio).guardarMarcador(CEDULA, new GuardarMarcadorRequest(2, 1, null, null, null));
    }

    @Test
    @DisplayName("VALIDACION: un identificador mal formado o un JSON inválido responde 400 sin llamar al servicio")
    void peticionesMalFormadas() {
        MvcTestResult uuid = mvc.put().uri("/api/v1/cedulas/no-es-uuid/marcador")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_MARCADOR).exchange();
        MvcTestResult json = mvc.put().uri("/api/v1/cedulas/" + CEDULA + "/marcador")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"golesLocal\": \"dos\" }").exchange();

        assertThat(uuid).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(uuid).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(json).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RN-005: la cédula no se borra (DELETE responde 405)")
    void sinDelete() {
        MvcTestResult resultado = mvc.delete().uri("/api/v1/partidos/" + PARTIDO + "/cedula")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/partidos/{partidoId}/cedula")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/partidos/{partidoId}/cedula", PARTIDO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(PARTIDO)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/partidos/{partidoId}/cedula")
    Stream<DynamicTest> seguridadAbrir() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/partidos/{partidoId}/cedula", PARTIDO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.abrir(PARTIDO)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PUT /api/v1/cedulas/{id}/alineaciones")
    Stream<DynamicTest> seguridadAlineaciones() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PUT, "/api/v1/cedulas/{id}/alineaciones", CEDULA)
                .cuerpoJson(CUERPO_ALINEACION)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.guardarAlineaciones(eq(CEDULA), any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PUT /api/v1/cedulas/{id}/marcador")
    Stream<DynamicTest> seguridadMarcador() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PUT, "/api/v1/cedulas/{id}/marcador", CEDULA)
                .cuerpoJson(CUERPO_MARCADOR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.guardarMarcador(eq(CEDULA), any())).willThrow(error))
                .pruebas();
    }

    // ── H-16.2: eventos, incidencias, observaciones y envío ─────────────────────────────────────

    private static final UUID FILA = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID EVENTO = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final String CUERPO_EVENTO = """
            { "tipo": "GOL", "minuto": 34, "alineacionId": "%s" }
            """.formatted(FILA);
    private static final String CUERPO_INCIDENCIA = """
            { "tipoIncidenciaId": 2, "minuto": 70, "descripcion": "Agresión al rival",
              "involucrados": [ { "equipoId": "%s" } ] }
            """.formatted(VISITANTE);
    private static final String CUERPO_OBSERVACIONES = """
            { "observaciones": "Lluvia en el segundo tiempo" }
            """;

    @Test
    @DisplayName("RF-025: POST /cedulas/{id}/eventos responde 201 con la cédula y pasa el evento al servicio")
    void registrarEvento() throws Exception {
        given(servicio.registrarEvento(eq(CEDULA), any())).willReturn(cedula());

        MvcTestResult resultado = mvc.post().uri("/api/v1/cedulas/" + CEDULA + "/eventos")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EVENTO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.eventos[0].tipo").isEqualTo("GOL");
        assertThat(resultado).bodyJson().extractingPath("$.eventos[0].jugador.nombre").isEqualTo("Juan Prueba");
        ArgumentCaptor<RegistrarEventoRequest> peticion = ArgumentCaptor.forClass(RegistrarEventoRequest.class);
        Mockito.verify(servicio).registrarEvento(eq(CEDULA), peticion.capture());
        assertThat(peticion.getValue()).isEqualTo(new RegistrarEventoRequest(TipoEventoCedula.GOL, 34, FILA, null, null));
    }

    @Test
    @DisplayName("RF-025: un tipo de evento que no existe responde 400 sin llegar al servicio")
    void eventoConTipoInvalido() {
        MvcTestResult resultado = mvc.post().uri("/api/v1/cedulas/" + CEDULA + "/eventos")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"tipo\": \"PENAL\" }").exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-025: DELETE /cedulas/{id}/eventos/{eventoId} responde 204 sin cuerpo")
    void eliminarEvento() {
        MvcTestResult resultado = mvc.delete().uri("/api/v1/cedulas/" + CEDULA + "/eventos/" + EVENTO)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.NO_CONTENT);
        Mockito.verify(servicio).eliminarEvento(CEDULA, EVENTO);
    }

    @Test
    @DisplayName("RF-026: POST /cedulas/{id}/incidencias responde 201 con la incidencia y sus involucrados")
    void registrarIncidencia() throws Exception {
        given(servicio.registrarIncidencia(eq(CEDULA), any())).willReturn(cedula());

        MvcTestResult resultado = mvc.post().uri("/api/v1/cedulas/" + CEDULA + "/incidencias")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_INCIDENCIA).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).bodyJson().extractingPath("$.incidencias[0].tipo.abreExpediente").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.incidencias[0].involucrados[0].nombre").isEqualTo("Toros");
        ArgumentCaptor<RegistrarIncidenciaRequest> peticion = ArgumentCaptor.forClass(RegistrarIncidenciaRequest.class);
        Mockito.verify(servicio).registrarIncidencia(eq(CEDULA), peticion.capture());
        assertThat(peticion.getValue().involucrados())
                .containsExactly(new RegistrarIncidenciaRequest.InvolucradoRequest(null, VISITANTE));
    }

    @Test
    @DisplayName("RF-025: PUT /cedulas/{id}/observaciones responde 200 con la cédula")
    void guardarObservaciones() {
        given(servicio.guardarObservaciones(eq(CEDULA), any())).willReturn(cedula());

        MvcTestResult resultado = mvc.put().uri("/api/v1/cedulas/" + CEDULA + "/observaciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_OBSERVACIONES).exchange();

        assertThat(resultado).hasStatusOk();
        Mockito.verify(servicio).guardarObservaciones(CEDULA, new GuardarObservacionesRequest("Lluvia en el segundo tiempo"));
    }

    @Test
    @DisplayName("RF-122: POST /cedulas/{id}/envio responde 200 con la cédula en validación y su plazo")
    void enviar() {
        given(servicio.enviar(CEDULA)).willReturn(cedula());

        MvcTestResult resultado = mvc.post().uri("/api/v1/cedulas/" + CEDULA + "/envio")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.plazoVenceEn").isEqualTo("2031-03-12T10:00:00-06:00");
    }

    @Test
    @DisplayName("VAL-020: el 422 del envío incompleto conserva la extensión faltantes")
    void enviarIncompleta() {
        given(servicio.enviar(CEDULA)).willThrow(ReglaNegocioException.noProcesable("VAL-020",
                        "La cédula está incompleta y no se puede enviar: Captura el marcador.")
                .con("faltantes", List.of(Map.of("campo", "marcador", "mensaje", "Captura el marcador."))));

        MvcTestResult resultado = mvc.post().uri("/api/v1/cedulas/" + CEDULA + "/envio")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-020");
        assertThat(resultado).bodyJson().extractingPath("$.faltantes[0].campo").isEqualTo("marcador");
    }

    @Test
    @DisplayName("RF-122: el 409 de la cédula fuera de captura llega con su código")
    void fueraDeCaptura() {
        given(servicio.registrarEvento(eq(CEDULA), any())).willThrow(ReglaNegocioException.conflicto("RF-122",
                "Los eventos solo se modifican mientras la cédula está en captura; ahora está pendiente validacion."));

        MvcTestResult resultado = mvc.post().uri("/api/v1/cedulas/" + CEDULA + "/eventos")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_EVENTO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-122");
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/cedulas/{id}/eventos")
    Stream<DynamicTest> seguridadEventos() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/cedulas/{id}/eventos", CEDULA)
                .cuerpoJson(CUERPO_EVENTO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrarEvento(eq(CEDULA), any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de DELETE /api/v1/cedulas/{id}/eventos/{eventoId}")
    Stream<DynamicTest> seguridadEliminarEvento() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.DELETE, "/api/v1/cedulas/{id}/eventos/{eventoId}", CEDULA, EVENTO)
                .dobles(servicio)
                .simularDenegacion(error -> Mockito.doThrow(error).when(servicio).eliminarEvento(CEDULA, EVENTO))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/cedulas/{id}/incidencias")
    Stream<DynamicTest> seguridadIncidencias() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/cedulas/{id}/incidencias", CEDULA)
                .cuerpoJson(CUERPO_INCIDENCIA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrarIncidencia(eq(CEDULA), any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PUT /api/v1/cedulas/{id}/observaciones")
    Stream<DynamicTest> seguridadObservaciones() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PUT, "/api/v1/cedulas/{id}/observaciones", CEDULA)
                .cuerpoJson(CUERPO_OBSERVACIONES)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.guardarObservaciones(eq(CEDULA), any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/cedulas/{id}/envio")
    Stream<DynamicTest> seguridadEnvio() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/cedulas/{id}/envio", CEDULA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.enviar(CEDULA)).willThrow(error))
                .pruebas();
    }

    private static CedulaDto cedula() {
        OffsetDateTime inicio = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
        UUID fila = UUID.randomUUID();
        return new CedulaDto(CEDULA,
                new PartidoDto(PARTIDO, new ReferenciaDto(LOCAL, "Halcones"), new ReferenciaDto(VISITANTE, "Toros"),
                        inicio, EstadoPartido.EN_CURSO),
                EstadoCedula.EN_CAPTURA, new ReferenciaDto(UUID.randomUUID(), "Capturista"),
                new MarcadorDto(0, 3, null, null, new ReferenciaDto(LOCAL, "Halcones"), true, true),
                List.of(new JugadorAlineadoDto(fila, REGISTRO, "Juan Prueba", LOCAL, 10, true, true)),
                List.of(new EventoDto(EVENTO, TipoEventoCedula.GOL, 34, LOCAL, new ReferenciaDto(fila, "Juan Prueba"),
                        null, null)),
                List.of(new IncidenciaDto(UUID.randomUUID(), new TipoIncidenciaDto(2L, "Expulsión", true), 70,
                        "Agresión al rival", List.of(new InvolucradoDto(null, VISITANTE, "Toros")))),
                null, null, inicio.plusDays(2), inicio, inicio);
    }
}

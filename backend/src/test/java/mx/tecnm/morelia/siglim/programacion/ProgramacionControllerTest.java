package mx.tecnm.morelia.siglim.programacion;

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
import mx.tecnm.morelia.siglim.programacion.PartidoDto.ReferenciaDto;
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

/**
 * Contrato HTTP de la programación manual (convenciones de API §6.7) con la cadena de seguridad real.
 *
 * <p>Refs: H-12.1, RF-033, RF-036, RF-040
 */
@WebMvcTest(controllers = {JornadaController.class, PartidoController.class}, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class ProgramacionControllerTest {

    private static final UUID JORNADA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PARTIDO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID LOCAL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID VISITANTE = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CANCHA = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    private static final String BASE = "/api/v1/jornadas";
    private static final String CUERPO_JORNADA = "{\"grupoId\":\"" + GRUPO + "\",\"numero\":3}";
    private static final String CUERPO_PARTIDO = "{\"equipoLocalId\":\"" + LOCAL
            + "\",\"equipoVisitanteId\":\"" + VISITANTE + "\",\"canchaId\":\"" + CANCHA
            + "\",\"inicio\":\"2031-03-10T10:00:00-06:00\"}";
    private static final String CUERPO_AJUSTE = "{\"inicio\":\"2031-03-11T12:00:00-06:00\","
            + "\"motivo\":\"Cambio de horario solicitado por la Liga\"}";

    private static final PartidoDto PARTIDO_DTO = new PartidoDto(PARTIDO, JORNADA,
            new ReferenciaDto(LOCAL, "Halcones"), new ReferenciaDto(VISITANTE, "Toros"),
            new ReferenciaDto(CANCHA, "Morelos 2"), INICIO, INICIO.plusHours(2),
            EstadoPartido.PROGRAMADO, null, INICIO.minusDays(1), INICIO.minusDays(1));
    private static final JornadaDto JORNADA_DTO = new JornadaDto(JORNADA, GRUPO, null, 3,
            EstadoJornada.BORRADOR, List.of(PARTIDO_DTO), INICIO.minusDays(1), INICIO.minusDays(1));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private JornadaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        var peticion = mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return peticion.exchange();
    }

    // ── Caso feliz de cada endpoint ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-033: GET /jornadas lista con la envoltura de paginación y los filtros")
    void listar() {
        given(servicio.listar(GRUPO, EstadoJornada.BORRADOR, 1, 10))
                .willReturn(Pagina.de(List.of(JORNADA_DTO), 1, 10, 11));

        MvcTestResult resultado = mvc.get()
                .uri(BASE + "?grupoId=" + GRUPO + "&estadoPublicacion=BORRADOR&pagina=1&tamano=10")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].numero").isEqualTo(3);
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estadoPublicacion")
                .isEqualTo("BORRADOR");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
        Mockito.verify(servicio).listar(GRUPO, EstadoJornada.BORRADOR, 1, 10);
    }

    @Test
    @DisplayName("RF-033: GET /jornadas/{id} devuelve la jornada con sus partidos")
    void consultar() {
        given(servicio.consultar(JORNADA)).willReturn(JORNADA_DTO);

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + JORNADA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(JORNADA.toString());
        assertThat(resultado).bodyJson().extractingPath("$.partidos[0].id").isEqualTo(PARTIDO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.partidos[0].estado").isEqualTo("PROGRAMADO");
        assertThat(resultado).bodyJson().extractingPath("$.partidos[0].cancha.nombre").isEqualTo("Morelos 2");
    }

    @Test
    @DisplayName("RF-033: POST /jornadas responde 201 con Location y estado BORRADOR")
    void crear() {
        given(servicio.crear(any(CrearJornadaRequest.class))).willReturn(JORNADA_DTO);

        MvcTestResult resultado = post(BASE, CUERPO_JORNADA);

        assertThat(resultado).hasStatus(HttpStatus.CREATED)
                .hasHeader(HttpHeaders.LOCATION, BASE + "/" + JORNADA);
        assertThat(resultado).bodyJson().extractingPath("$.estadoPublicacion").isEqualTo("BORRADOR");
        ArgumentCaptor<CrearJornadaRequest> peticion = ArgumentCaptor.forClass(CrearJornadaRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().grupoId()).isEqualTo(GRUPO);
        assertThat(peticion.getValue().numero()).isEqualTo(3);
        assertThat(peticion.getValue().faseId()).isNull();
    }

    @Test
    @DisplayName("RF-033: POST sin grupo o con número cero responde 400 VALIDACION")
    void crearInvalida() {
        MvcTestResult sinGrupo = post(BASE, "{\"numero\":1}");
        MvcTestResult numeroCero = post(BASE, "{\"grupoId\":\"" + GRUPO + "\",\"numero\":0}");

        assertThat(sinGrupo).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(sinGrupo).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(sinGrupo).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("grupoId");
        assertThat(numeroCero).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(numeroCero).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("numero");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-036: POST /jornadas/{id}/partidos responde 201 con Location del partido")
    void registrarPartido() {
        given(servicio.registrarPartido(eq(JORNADA), any(CrearPartidoRequest.class))).willReturn(PARTIDO_DTO);

        MvcTestResult resultado = post(BASE + "/" + JORNADA + "/partidos", CUERPO_PARTIDO);

        assertThat(resultado).hasStatus(HttpStatus.CREATED)
                .hasHeader(HttpHeaders.LOCATION, "/api/v1/partidos/" + PARTIDO);
        assertThat(resultado).bodyJson().extractingPath("$.equipoLocal.id").isEqualTo(LOCAL.toString());
        assertThat(resultado).bodyJson().extractingPath("$.finBloque").isEqualTo("2031-03-10T12:00:00-06:00");
        ArgumentCaptor<CrearPartidoRequest> peticion = ArgumentCaptor.forClass(CrearPartidoRequest.class);
        Mockito.verify(servicio).registrarPartido(eq(JORNADA), peticion.capture());
        assertThat(peticion.getValue().canchaId()).isEqualTo(CANCHA);
    }

    @Test
    @DisplayName("RF-036: POST sin cancha responde 400 VALIDACION y no llega al servicio")
    void registrarPartidoInvalido() {
        MvcTestResult resultado = post(BASE + "/" + JORNADA + "/partidos",
                "{\"equipoLocalId\":\"" + LOCAL + "\",\"equipoVisitanteId\":\"" + VISITANTE
                        + "\",\"inicio\":\"2031-03-10T10:00:00-06:00\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("canchaId");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-040: PATCH /partidos/{id} pasa el motivo y responde el partido ajustado")
    void actualizarPartido() {
        given(servicio.actualizarPartido(eq(PARTIDO), any(ActualizarPartidoRequest.class)))
                .willReturn(PARTIDO_DTO);

        MvcTestResult resultado = mvc.patch().uri("/api/v1/partidos/" + PARTIDO)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_AJUSTE).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(PARTIDO.toString());
        ArgumentCaptor<ActualizarPartidoRequest> peticion =
                ArgumentCaptor.forClass(ActualizarPartidoRequest.class);
        Mockito.verify(servicio).actualizarPartido(eq(PARTIDO), peticion.capture());
        assertThat(peticion.getValue().motivo()).isEqualTo("Cambio de horario solicitado por la Liga");
        assertThat(peticion.getValue().equipoLocalId()).isNull();
    }

    @Test
    @DisplayName("RN-021: el 400 por ajustar sin motivo conserva el código")
    void ajustarSinMotivo() {
        given(servicio.actualizarPartido(eq(PARTIDO), any(ActualizarPartidoRequest.class)))
                .willThrow(ReglaNegocioException.invalida("RN-021",
                        "Escribe un motivo de al menos 10 caracteres para ajustar el partido."));

        MvcTestResult resultado = mvc.patch().uri("/api/v1/partidos/" + PARTIDO)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"inicio\":\"2031-03-11T12:00:00-06:00\"}").exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-021");
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/jornadas")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "?grupoId=" + GRUPO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(GRUPO, null, 0, 20)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/jornadas/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", JORNADA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(JORNADA)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/jornadas")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_JORNADA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearJornadaRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/jornadas/{id}/partidos")
    Stream<DynamicTest> seguridadRegistrarPartido() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/partidos", JORNADA)
                .cuerpoJson(CUERPO_PARTIDO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrarPartido(eq(JORNADA),
                        any(CrearPartidoRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/partidos/{id}")
    Stream<DynamicTest> seguridadActualizarPartido() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, "/api/v1/partidos/{id}", PARTIDO)
                .cuerpoJson(CUERPO_AJUSTE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizarPartido(eq(PARTIDO),
                        any(ActualizarPartidoRequest.class))).willThrow(error))
                .pruebas();
    }
}

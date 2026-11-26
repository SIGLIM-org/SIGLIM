package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.arbitraje.CandidatoArbitralDto.CausaDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionDto.ArbitroDesignadoDto;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.EstadoJornada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
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

/** Contrato HTTP de las designaciones (convenciones de API §6.5, H-14.1) con la seguridad real. */
@WebMvcTest(controllers = DesignacionController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class DesignacionControllerTest {

    private static final UUID PARTIDO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ARBITRO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID DESIGNACION = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID JORNADA = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2030-08-10T10:00:00-06:00");
    private static final String CUERPO = """
            {"arbitroId":"11111111-1111-4111-8111-111111111111","funcion":"CENTRAL"}
            """;
    private static final String RETIRO = """
            {"motivo":"El árbitro avisó que no puede asistir"}
            """;
    private static final ArbitroDesignadoDto ARBITRO_DTO =
            new ArbitroDesignadoDto(ARBITRO, null, new ReferenciaDto(CLUB, "Club Halcones"));
    private static final DesignacionDto DESIGNADA = new DesignacionDto(DESIGNACION, PARTIDO, ARBITRO_DTO,
            FuncionArbitral.CENTRAL, true, false, INICIO, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private DesignacionService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    // ── GET /designaciones ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("UI-025: GET /designaciones devuelve la jornada, el avance y los partidos con central null")
    void listarPorJornada() {
        ReferenciaDto ref = new ReferenciaDto(UUID.randomUUID(), "X");
        DesignacionesJornadaDto vista = new DesignacionesJornadaDto(
                new DesignacionesJornadaDto.JornadaDto(JORNADA, 2, EstadoJornada.PUBLICADO, ref, ref),
                new DesignacionesJornadaDto.AvanceDto(1, 0, 0),
                List.of(new DesignacionesJornadaDto.PartidoDto(PARTIDO, INICIO, INICIO.plusHours(2),
                        EstadoPartido.PROGRAMADO, ref, ref, ref, true, null,
                        List.of(DESIGNADA.enJornada()))));
        given(servicio.listarPorJornada(JORNADA)).willReturn(vista);

        MvcTestResult resultado = mvc.get().uri("/api/v1/designaciones?jornadaId=" + JORNADA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk().hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.jornada.estadoPublicacion").isEqualTo("PUBLICADO");
        assertThat(resultado).bodyJson().extractingPath("$.avance.partidos").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("$.partidos[0].designable").isEqualTo(true);
        assertThat(resultado).bodyJson().hasPathSatisfying("$.partidos[0].central",
                valor -> assertThat(valor).isNull());
        assertThat(resultado).bodyJson().extractingPath("$.partidos[0].asistentes[0].arbitro.club.nombre")
                .isEqualTo("Club Halcones");
        assertThat(resultado).bodyJson().hasPathSatisfying("$.partidos[0].asistentes[0].arbitro.nombre",
                valor -> assertThat(valor).isNull());
        assertThat(resultado).bodyJson().doesNotHavePath("$.partidos[0].asistentes[0].partidoId");
        assertThat(resultado).bodyJson().doesNotHavePath("$.partidos[0].asistentes[0].vigente");
    }

    @Test
    @DisplayName("UI-025: GET /designaciones sin jornadaId llega al servicio, que responde 400")
    void listarSinJornada() {
        given(servicio.listarPorJornada(null)).willThrow(ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                "Indica la jornada de la que quieres ver las designaciones."));

        MvcTestResult resultado = mvc.get().uri("/api/v1/designaciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
    }

    // ── GET /partidos/{id}/arbitros-candidatos ──────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-204: GET candidatos devuelve elegibilidad, causas, advertencias y carga")
    void candidatos() {
        given(servicio.candidatos(PARTIDO)).willReturn(List.of(new CandidatoArbitralDto(ARBITRO_DTO, false,
                List.of(new CausaDto("VAL-019", "Pertenece a Club Halcones, que juega este partido.")),
                List.of(new CausaDto("RF-022", "Declaró que no está disponible en ese horario.")), 3)));

        MvcTestResult resultado = mvc.get().uri("/api/v1/partidos/" + PARTIDO + "/arbitros-candidatos")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].arbitro.id").isEqualTo(ARBITRO.toString());
        assertThat(resultado).bodyJson().extractingPath("$[0].elegible").isEqualTo(false);
        assertThat(resultado).bodyJson().extractingPath("$[0].causas[0].codigo").isEqualTo("VAL-019");
        assertThat(resultado).bodyJson().extractingPath("$[0].advertencias[0].codigo").isEqualTo("RF-022");
        assertThat(resultado).bodyJson().extractingPath("$[0].carga").isEqualTo(3);
    }

    // ── POST /partidos/{id}/designaciones ───────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-023: POST designa y responde 201 sin Location con la designación y partidoId")
    void designar() {
        given(servicio.designar(eq(PARTIDO), any(DesignarArbitroRequest.class))).willReturn(DESIGNADA);

        MvcTestResult resultado = mvc.post().uri("/api/v1/partidos/" + PARTIDO + "/designaciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).doesNotContainHeader(HttpHeaders.LOCATION);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(DESIGNACION.toString());
        assertThat(resultado).bodyJson().extractingPath("$.partidoId").isEqualTo(PARTIDO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.funcion").isEqualTo("CENTRAL");
        assertThat(resultado).bodyJson().extractingPath("$.vigente").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.automatica").isEqualTo(false);
        assertThat(resultado).bodyJson().extractingPath("$.designadoEn").isEqualTo("2030-08-10T10:00:00-06:00");
        Mockito.verify(servicio).designar(PARTIDO, new DesignarArbitroRequest(ARBITRO, FuncionArbitral.CENTRAL, null));
    }

    @Test
    @DisplayName("RF-023: POST sin arbitroId o sin función responde 400 VALIDACION sin tocar el servicio")
    void designarSinCampos() {
        MvcTestResult sinArbitro = mvc.post().uri("/api/v1/partidos/" + PARTIDO + "/designaciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"funcion\":\"CENTRAL\"}").exchange();
        MvcTestResult sinFuncion = mvc.post().uri("/api/v1/partidos/" + PARTIDO + "/designaciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"arbitroId\":\"" + ARBITRO + "\"}").exchange();

        assertThat(sinArbitro).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(sinArbitro).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(sinArbitro).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("arbitroId");
        assertThat(sinFuncion).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(sinFuncion).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("funcion");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-023: POST con una función que no existe responde 400 VALIDACION")
    void designarFuncionInvalida() {
        MvcTestResult resultado = mvc.post().uri("/api/v1/partidos/" + PARTIDO + "/designaciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"arbitroId\":\"" + ARBITRO + "\",\"funcion\":\"CUARTO\"}").exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("VAL-019: POST con un árbitro del club de un equipo responde 422")
    void designarMismoClub() {
        given(servicio.designar(eq(PARTIDO), any(DesignarArbitroRequest.class))).willThrow(
                ReglaNegocioException.noProcesable("VAL-019", "El árbitro pertenece al club de uno de los equipos."));

        MvcTestResult resultado = mvc.post().uri("/api/v1/partidos/" + PARTIDO + "/designaciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-019");
    }

    @Test
    @DisplayName("RN-021: POST que sustituye al central sin motivo responde 400 RN-021")
    void designarSustitucionSinMotivo() {
        given(servicio.designar(eq(PARTIDO), any(DesignarArbitroRequest.class))).willThrow(
                ReglaNegocioException.invalida("RN-021", "Escribe un motivo de 10 a 500 caracteres."));

        MvcTestResult resultado = mvc.post().uri("/api/v1/partidos/" + PARTIDO + "/designaciones")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-021");
    }

    // ── POST /designaciones/{id}/retiro ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-203: POST retiro responde 200 con vigente false y el motivo")
    void retirar() {
        String motivo = "El árbitro avisó que no puede asistir";
        given(servicio.retirar(DESIGNACION, new RetirarDesignacionRequest(motivo))).willReturn(new DesignacionDto(
                DESIGNACION, PARTIDO, ARBITRO_DTO, FuncionArbitral.CENTRAL, false, false, INICIO, motivo));

        MvcTestResult resultado = mvc.post().uri("/api/v1/designaciones/" + DESIGNACION + "/retiro")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(RETIRO).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.vigente").isEqualTo(false);
        assertThat(resultado).bodyJson().extractingPath("$.motivoCambio").isEqualTo(motivo);
    }

    @Test
    @DisplayName("RF-023: POST retiro de una designación ya retirada responde 409 RF-023")
    void retirarNoVigente() {
        given(servicio.retirar(eq(DESIGNACION), any(RetirarDesignacionRequest.class)))
                .willThrow(ReglaNegocioException.conflicto("RF-023", "La designación ya no está vigente."));

        MvcTestResult resultado = mvc.post().uri("/api/v1/designaciones/" + DESIGNACION + "/retiro")
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(RETIRO).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-023");
    }

    // ── GET /mis-designaciones ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-205: GET mis-designaciones pasa filtros y responde la envoltura de paginación")
    void listarPropias() {
        ReferenciaDto ref = new ReferenciaDto(UUID.randomUUID(), "X");
        MiDesignacionDto elemento = new MiDesignacionDto(DESIGNACION, FuncionArbitral.CENTRAL,
                new MiDesignacionDto.PartidoDto(PARTIDO, INICIO, INICIO.plusHours(2), EstadoPartido.PROGRAMADO,
                        ref, ref, ref, ref, ref, new MiDesignacionDto.JornadaDto(JORNADA, 2)), null);
        FiltroMisDesignaciones filtro = new FiltroMisDesignaciones(INICIO, INICIO.plusDays(7), 1, 5);
        given(servicio.listarPropias(filtro)).willReturn(Pagina.de(List.of(elemento), 1, 5, 6));

        MvcTestResult resultado = mvc.get()
                .uri("/api/v1/mis-designaciones?desde=2030-08-10T10:00:00-06:00&hasta=2030-08-17T10:00:00-06:00"
                        + "&pagina=1&tamano=5")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].designacionId").isEqualTo(DESIGNACION.toString());
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].partido.jornada.numero").isEqualTo(2);
        assertThat(resultado).bodyJson().hasPathSatisfying("$.contenido[0].cedula", valor -> assertThat(valor).isNull());
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(6);
        assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/designaciones")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/designaciones?jornadaId=" + JORNADA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listarPorJornada(JORNADA)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/partidos/{id}/arbitros-candidatos")
    Stream<DynamicTest> seguridadCandidatos() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/partidos/{id}/arbitros-candidatos", PARTIDO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.candidatos(PARTIDO)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/partidos/{id}/designaciones")
    Stream<DynamicTest> seguridadDesignar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/partidos/{id}/designaciones", PARTIDO)
                .cuerpoJson(CUERPO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.designar(eq(PARTIDO), any(DesignarArbitroRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/designaciones/{id}/retiro")
    Stream<DynamicTest> seguridadRetirar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/designaciones/{id}/retiro", DESIGNACION)
                .cuerpoJson(RETIRO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.retirar(eq(DESIGNACION), any(RetirarDesignacionRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/mis-designaciones")
    Stream<DynamicTest> seguridadMisDesignaciones() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/mis-designaciones")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listarPropias(any(FiltroMisDesignaciones.class)))
                        .willThrow(error))
                .pruebas();
    }
}

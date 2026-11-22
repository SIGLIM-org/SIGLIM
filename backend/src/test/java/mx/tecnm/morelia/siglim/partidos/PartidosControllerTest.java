package mx.tecnm.morelia.siglim.partidos;

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
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.JornadaDto;
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.programacion.EstadoJornada;
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

/**
 * Contrato HTTP de la consulta y las transiciones del partido (convenciones de API §6.8) con la cadena
 * de seguridad real.
 *
 * <p>Refs: H-15.1, RF-043, RF-045, RF-046, RN-021
 */
@WebMvcTest(controllers = PartidosController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class PartidosControllerTest {

    private static final UUID PARTIDO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID JORNADA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CANCHA = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    private static final String BASE = "/api/v1/partidos";
    private static final String CUERPO_CANCELAR = "{\"estadoNuevo\":\"CANCELADO\","
            + "\"motivo\":\"Lluvia intensa en la sede\"}";
    private static final String CUERPO_REPROGRAMAR = "{\"estadoNuevo\":\"REPROGRAMADO\","
            + "\"motivo\":\"Cancha inundada por la lluvia\",\"inicio\":\"2031-03-17T10:00:00-06:00\","
            + "\"canchaId\":\"" + CANCHA + "\"}";

    private static final ReferenciaDto LOCAL = new ReferenciaDto(UUID.randomUUID(), "Halcones");
    private static final ReferenciaDto VISITANTE = new ReferenciaDto(UUID.randomUUID(), "Toros");
    private static final ReferenciaDto CANCHA_REF = new ReferenciaDto(CANCHA, "Morelos 2");
    private static final List<AccionPartido> ACCIONES = List.of(AccionPartido.REPROGRAMAR, AccionPartido.CANCELAR);

    private static final PartidoResumenDto RESUMEN = new PartidoResumenDto(PARTIDO, JORNADA, 3, LOCAL, VISITANTE,
            CANCHA_REF, INICIO, INICIO.plusHours(2), EstadoPartido.PROGRAMADO, ACCIONES);

    private static PartidoDetalleDto detalle(EstadoPartido estado, List<AccionPartido> acciones) {
        return new PartidoDetalleDto(PARTIDO, new JornadaDto(JORNADA, 3, EstadoJornada.PUBLICADO),
                new ReferenciaDto(UUID.randomUUID(), "Temporada 2031"), new ReferenciaDto(UUID.randomUUID(), "Libre"),
                new ReferenciaDto(UUID.randomUUID(), "Grupo A"), LOCAL, VISITANTE, CANCHA_REF, INICIO,
                INICIO.plusHours(2), estado, List.of(), null, null,
                List.of(new CambioEstadoPartidoDto(null, EstadoPartido.PROGRAMADO, "Programación del partido",
                        new ReferenciaDto(UUID.randomUUID(), "Programador"), INICIO.minusDays(3))),
                acciones);
    }

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PartidosService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-043: GET /partidos pasa los filtros y devuelve la página con accionesPermitidas")
    void listar() {
        given(servicio.listar(any(FiltroPartidos.class))).willReturn(Pagina.de(List.of(RESUMEN), 0, 10, 1));

        MvcTestResult resultado = mvc.get()
                .uri(BASE + "?jornadaId=" + JORNADA + "&estado=PROGRAMADO&desde=2031-03-01T00:00:00-06:00"
                        + "&hasta=2031-04-01T00:00:00-06:00&pagina=0&tamano=10")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk().hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].jornadaNumero").isEqualTo(3);
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].accionesPermitidas")
                .asArray().containsExactly("REPROGRAMAR", "CANCELAR");
        ArgumentCaptor<FiltroPartidos> filtro = ArgumentCaptor.forClass(FiltroPartidos.class);
        Mockito.verify(servicio).listar(filtro.capture());
        assertThat(filtro.getValue().jornadaId()).isEqualTo(JORNADA);
        assertThat(filtro.getValue().estado()).isEqualTo(EstadoPartido.PROGRAMADO);
        assertThat(filtro.getValue().desde()).isEqualTo(OffsetDateTime.parse("2031-03-01T00:00:00-06:00"));
        assertThat(filtro.getValue().tamano()).isEqualTo(10);
    }

    @Test
    @DisplayName("RF-043: GET /partidos sin filtros usa la primera página de 20")
    void listarPorDefecto() {
        given(servicio.listar(any(FiltroPartidos.class))).willReturn(Pagina.de(List.of(), 0, 20, 0));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        Mockito.verify(servicio).listar(new FiltroPartidos(null, null, null, null, 0, 20));
    }

    @Test
    @DisplayName("RF-043: un estado que no existe en el filtro responde 400")
    void listarEstadoInvalido() {
        MvcTestResult resultado = mvc.get().uri(BASE + "?estado=JUGANDO")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-046: GET /partidos/{id} devuelve el detalle con historial y accionesPermitidas")
    void obtener() {
        given(servicio.obtener(PARTIDO)).willReturn(detalle(EstadoPartido.PROGRAMADO, ACCIONES));

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + PARTIDO)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk().hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.jornada.estadoPublicacion").isEqualTo("PUBLICADO");
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("PROGRAMADO");
        assertThat(resultado).bodyJson().extractingPath("$.historial[0].motivo").isEqualTo("Programación del partido");
        assertThat(resultado).bodyJson().extractingPath("$.accionesPermitidas").asArray()
                .containsExactly("REPROGRAMAR", "CANCELAR");
    }

    @Test
    @DisplayName("RF-045: GET /partidos/{id}/historial devuelve las transiciones")
    void historial() {
        given(servicio.historial(PARTIDO)).willReturn(detalle(EstadoPartido.PROGRAMADO, List.of()).historial());

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + PARTIDO + "/historial")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk().hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$[0].estadoNuevo").isEqualTo("PROGRAMADO");
        assertThat(resultado).bodyJson().extractingPath("$[0].cambiadoPor.nombre").isEqualTo("Programador");
    }

    @Test
    @DisplayName("RF-043: POST /partidos/{id}/transiciones reprograma y devuelve el detalle")
    void reprogramar() {
        given(servicio.transicionar(eq(PARTIDO), any(TransicionPartidoRequest.class)))
                .willReturn(detalle(EstadoPartido.REPROGRAMADO, List.of()));

        MvcTestResult resultado = post(BASE + "/" + PARTIDO + "/transiciones", CUERPO_REPROGRAMAR);

        assertThat(resultado).hasStatusOk().hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("REPROGRAMADO");
        ArgumentCaptor<TransicionPartidoRequest> peticion = ArgumentCaptor.forClass(TransicionPartidoRequest.class);
        Mockito.verify(servicio).transicionar(eq(PARTIDO), peticion.capture());
        assertThat(peticion.getValue().estadoNuevo()).isEqualTo(EstadoPartido.REPROGRAMADO);
        assertThat(peticion.getValue().motivo()).isEqualTo("Cancha inundada por la lluvia");
        assertThat(peticion.getValue().inicio().toInstant())
                .isEqualTo(OffsetDateTime.parse("2031-03-17T10:00:00-06:00").toInstant());
        assertThat(peticion.getValue().canchaId()).isEqualTo(CANCHA);
    }

    @Test
    @DisplayName("RF-046: la transición no permitida responde 409 con estadoActual y accionesPermitidas")
    void transicionNoPermitida() {
        given(servicio.transicionar(eq(PARTIDO), any(TransicionPartidoRequest.class))).willThrow(
                ReglaNegocioException.conflicto("RF-046", "Un partido CANCELADO no puede pasar a CANCELADO.")
                        .con("estadoActual", EstadoPartido.CANCELADO)
                        .con("accionesPermitidas", List.of()));

        MvcTestResult resultado = post(BASE + "/" + PARTIDO + "/transiciones", CUERPO_CANCELAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-046");
        assertThat(resultado).bodyJson().extractingPath("$.estadoActual").isEqualTo("CANCELADO");
        assertThat(resultado).bodyJson().extractingPath("$.accionesPermitidas").asArray().isEmpty();
    }

    @Test
    @DisplayName("RN-021: el motivo corto responde 400 RN-021")
    void motivoCorto() {
        given(servicio.transicionar(eq(PARTIDO), any(TransicionPartidoRequest.class)))
                .willThrow(ReglaNegocioException.invalida("RN-021", "Explica el motivo del cambio."));

        MvcTestResult resultado = post(BASE + "/" + PARTIDO + "/transiciones",
                "{\"estadoNuevo\":\"CANCELADO\",\"motivo\":\"lluvia\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-021");
    }

    @Test
    @DisplayName("VAL-004: el conflicto de cancha al reprogramar conserva el código 409")
    void conflictoAlReprogramar() {
        given(servicio.transicionar(eq(PARTIDO), any(TransicionPartidoRequest.class)))
                .willThrow(ReglaNegocioException.conflicto("VAL-004", "La cancha Morelos 2 ya está ocupada."));

        MvcTestResult resultado = post(BASE + "/" + PARTIDO + "/transiciones", CUERPO_REPROGRAMAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-004");
    }

    @Test
    @DisplayName("RF-043: GET /partidos/{id} con un id que no es UUID responde 400")
    void idInvalido() {
        MvcTestResult resultado = mvc.get().uri(BASE + "/no-es-uuid")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/partidos")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroPartidos.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/partidos/{id}")
    Stream<DynamicTest> seguridadObtener() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", PARTIDO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.obtener(PARTIDO)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/partidos/{id}/historial")
    Stream<DynamicTest> seguridadHistorial() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}/historial", PARTIDO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.historial(PARTIDO)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/partidos/{id}/transiciones")
    Stream<DynamicTest> seguridadTransicionar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/transiciones", PARTIDO)
                .cuerpoJson(CUERPO_CANCELAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.transicionar(eq(PARTIDO),
                        any(TransicionPartidoRequest.class))).willThrow(error))
                .pruebas();
    }
}

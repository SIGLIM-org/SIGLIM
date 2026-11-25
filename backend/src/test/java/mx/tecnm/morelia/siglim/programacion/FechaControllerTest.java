package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.FechaDto.Bloque;
import mx.tecnm.morelia.siglim.programacion.FechaDto.Descanso;
import mx.tecnm.morelia.siglim.programacion.FechaDto.FilaPartido;
import mx.tecnm.morelia.siglim.programacion.FechaDto.SinAsignar;
import mx.tecnm.morelia.siglim.programacion.PartidoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.programacion.VerificacionFechaDto.Aviso;
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
 * Contrato HTTP de la fecha de programación (H-35.1, convenciones de API §6.7) con la cadena de
 * seguridad real: caso feliz, 400, 401, 403, 404 y 409 de cada endpoint.
 *
 * <p>Refs: H-35.1, RF-033, RF-039, RF-090, RF-121, DEC-175, DEC-180
 */
@WebMvcTest(controllers = FechaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class FechaControllerTest {

    private static final UUID FECHA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TEMPORADA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID JORNADA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID PARTIDO = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID EMPAREJAMIENTO = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID PENDIENTE = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID PROGRAMADOR = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-09T08:00:00-06:00");
    private static final String BASE = "/api/v1/fechas";
    private static final String CUERPO_DEVOLUCION = "{\"decision\":\"BORRADOR\","
            + "\"observaciones\":\"Mover Pumas vs Leones a la cancha 3\"}";

    private static final FilaPartido FILA = new FilaPartido(PARTIDO, JORNADA, EMPAREJAMIENTO,
            new ReferenciaDto(UUID.randomUUID(), "Halcones"), new ReferenciaDto(UUID.randomUUID(), "Toros"),
            new ReferenciaDto(UUID.randomUUID(), "Cancha 2"), INICIO, INICIO.plusHours(2),
            EstadoPartido.PROGRAMADO, null, null, true, true, null);
    private static final Bloque BLOQUE = new Bloque(new ReferenciaDto(UUID.randomUUID(), "Primera"),
            new ReferenciaDto(UUID.randomUUID(), "Grupo 1"), List.of(7), JORNADA, EstadoJornada.BORRADOR,
            List.of(FILA), List.of(new Descanso(UUID.randomUUID(), new ReferenciaDto(UUID.randomUUID(), "Águilas"))),
            List.of(new SinAsignar(PENDIENTE, new ReferenciaDto(UUID.randomUUID(), "Lobos"),
                    new ReferenciaDto(UUID.randomUUID(), "Osos"), "VAL-007", "Sin horario libre el domingo")));
    private static final FechaDto DETALLE = new FechaDto(FECHA, TEMPORADA, 3,
            List.of(LocalDate.parse("2031-03-08"), LocalDate.parse("2031-03-09")), EstadoJornada.BORRADOR,
            1, 1, 1, null, null, null, null, null, null, null, List.of(BLOQUE));
    private static final FechaDto RESUMEN = new FechaDto(FECHA, TEMPORADA, 3, DETALLE.dias(),
            EstadoJornada.BORRADOR, 1, 1, 1, null, null, null, null, null, null, null, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private FechaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        var peticion = mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return peticion.exchange();
    }

    // ── Caso feliz ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-175: GET /fechas lista con paginación, estado derivado y sin bloques")
    void listar() {
        given(servicio.listar(TEMPORADA, 0, 20)).willReturn(Pagina.de(List.of(RESUMEN), 0, 20, 1));

        MvcTestResult resultado = mvc.get().uri(BASE + "?temporadaId=" + TEMPORADA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].numero").isEqualTo(3);
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estadoPublicacion").isEqualTo("BORRADOR");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].dias[0]").isEqualTo("2031-03-08");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].sinAsignar").isEqualTo(1);
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].bloques");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(1);
    }

    @Test
    @DisplayName("UI-009: GET /fechas/{id} devuelve los bloques con partidos, descansos y sin asignar")
    void consultar() {
        given(servicio.consultar(FECHA)).willReturn(DETALLE);

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + FECHA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.bloques[0].categoria.nombre").isEqualTo("Primera");
        assertThat(resultado).bodyJson().extractingPath("$.bloques[0].grupo.nombre").isEqualTo("Grupo 1");
        assertThat(resultado).bodyJson().extractingPath("$.bloques[0].partidos[0].cancha.nombre")
                .isEqualTo("Cancha 2");
        assertThat(resultado).bodyJson().extractingPath("$.bloques[0].partidos[0].ajustado").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.bloques[0].partidos[0].emparejamientoId")
                .isEqualTo(EMPAREJAMIENTO.toString());
        assertThat(resultado).bodyJson().doesNotHavePath("$.bloques[0].partidos[0].arbitro");
        assertThat(resultado).bodyJson().extractingPath("$.bloques[0].descansos[0].equipo.nombre")
                .isEqualTo("Águilas");
        assertThat(resultado).bodyJson().extractingPath("$.bloques[0].sinAsignar[0].codigo").isEqualTo("VAL-007");
        assertThat(resultado).bodyJson().extractingPath("$.bloques[0].sinAsignar[0].emparejamientoId")
                .isEqualTo(PENDIENTE.toString());
    }

    @Test
    @DisplayName("RF-039: POST /fechas/{id}/verificacion devuelve conflictos y advertencias")
    void verificar() {
        given(servicio.verificar(FECHA)).willReturn(new VerificacionFechaDto(
                List.of(new Aviso(JORNADA, PARTIDO, null, "VAL-004", "La cancha está ocupada.")),
                List.of(new Aviso(JORNADA, null, PENDIENTE, "VAL-007", "Sin asignar: Lobos vs Osos"))));

        MvcTestResult resultado = post(BASE + "/" + FECHA + "/verificacion", null);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.conflictos[0].jornadaId").isEqualTo(JORNADA.toString());
        assertThat(resultado).bodyJson().extractingPath("$.conflictos[0].codigo").isEqualTo("VAL-004");
        assertThat(resultado).bodyJson().doesNotHavePath("$.conflictos[0].emparejamientoId");
        assertThat(resultado).bodyJson().extractingPath("$.advertencias[0].emparejamientoId")
                .isEqualTo(PENDIENTE.toString());
    }

    @Test
    @DisplayName("RF-121: POST /fechas/{id}/envio, sin cuerpo, devuelve la fecha en REVISION")
    void enviar() {
        FechaDto enviada = new FechaDto(FECHA, TEMPORADA, 3, DETALLE.dias(), EstadoJornada.REVISION,
                1, 1, 0, null, PROGRAMADOR, INICIO.minusDays(2), null, null, null, null, List.of());
        given(servicio.enviarAAprobacion(FECHA)).willReturn(enviada);

        MvcTestResult resultado = post(BASE + "/" + FECHA + "/envio", null);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estadoPublicacion").isEqualTo("REVISION");
        assertThat(resultado).bodyJson().extractingPath("$.enviadaPor").isEqualTo(PROGRAMADOR.toString());
        assertThat(resultado).bodyJson().doesNotHavePath("$.aprobadaPor");
    }

    @Test
    @DisplayName("RF-121: POST /fechas/{id}/resolucion pasa la decisión y las observaciones al servicio")
    void resolver() {
        given(servicio.resolver(eq(FECHA), any(ResolverFechaRequest.class))).willReturn(DETALLE);

        MvcTestResult resultado = post(BASE + "/" + FECHA + "/resolucion", CUERPO_DEVOLUCION);

        assertThat(resultado).hasStatusOk();
        ArgumentCaptor<ResolverFechaRequest> peticion = ArgumentCaptor.forClass(ResolverFechaRequest.class);
        Mockito.verify(servicio).resolver(eq(FECHA), peticion.capture());
        assertThat(peticion.getValue().decision()).isEqualTo(DecisionJornada.BORRADOR);
        assertThat(peticion.getValue().observaciones()).isEqualTo("Mover Pumas vs Leones a la cancha 3");
    }

    // ── Errores de regla ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-121: la resolución sin decisión responde 400 sin llegar al servicio")
    void resolverSinDecision() {
        MvcTestResult resultado = post(BASE + "/" + FECHA + "/resolucion", "{\"observaciones\":\"x\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-039: el 409 del envío con conflictos conserva el código y la lista")
    void enviarConConflictos() {
        given(servicio.enviarAAprobacion(FECHA)).willThrow(ReglaNegocioException.conflicto("RF-039",
                        "La fecha tiene 1 conflicto sin resolver: la cancha está ocupada.")
                .con("conflictos", List.of(new Aviso(JORNADA, PARTIDO, null, "VAL-004", "La cancha está ocupada."))));

        MvcTestResult resultado = post(BASE + "/" + FECHA + "/envio", null);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-039");
        assertThat(resultado).bodyJson().extractingPath("$.conflictos[0].jornadaId").isEqualTo(JORNADA.toString());
    }

    @Test
    @DisplayName("RF-090: el 409 de una fecha que ya no está en revisión conserva el código")
    void resolverFueraDeRevision() {
        given(servicio.resolver(eq(FECHA), any(ResolverFechaRequest.class))).willThrow(
                ReglaNegocioException.conflicto("RF-090", "Solo una fecha en revisión se aprueba o se devuelve."));

        MvcTestResult resultado = post(BASE + "/" + FECHA + "/resolucion", "{\"decision\":\"PUBLICADO\"}");

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-090");
    }

    @Test
    @DisplayName("RF-121: el 403 de la separación de funciones conserva el código")
    void resolverSeparacionDeFunciones() {
        given(servicio.resolver(eq(FECHA), any(ResolverFechaRequest.class))).willThrow(
                ReglaNegocioException.prohibida("RF-121", "Quien programó o envió la fecha no puede dictaminarla."));

        MvcTestResult resultado = post(BASE + "/" + FECHA + "/resolucion", CUERPO_DEVOLUCION);

        assertThat(resultado).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-121");
    }

    @Test
    @DisplayName("DEC-175: una fecha inexistente responde 404 NO_ENCONTRADO")
    void consultarInexistente() {
        given(servicio.consultar(FECHA)).willThrow(new RecursoNoEncontradoException("Fecha " + FECHA));

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + FECHA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("NO_ENCONTRADO");
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/fechas")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "?temporadaId=" + TEMPORADA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(TEMPORADA, 0, 20)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/fechas/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", FECHA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(FECHA)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/fechas/{id}/verificacion")
    Stream<DynamicTest> seguridadVerificar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/verificacion", FECHA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.verificar(FECHA)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/fechas/{id}/envio")
    Stream<DynamicTest> seguridadEnviar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/envio", FECHA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.enviarAAprobacion(FECHA)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/fechas/{id}/resolucion")
    Stream<DynamicTest> seguridadResolver() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/resolucion", FECHA)
                .cuerpoJson(CUERPO_DEVOLUCION)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.resolver(eq(FECHA), any(ResolverFechaRequest.class)))
                        .willThrow(error))
                .pruebas();
    }
}

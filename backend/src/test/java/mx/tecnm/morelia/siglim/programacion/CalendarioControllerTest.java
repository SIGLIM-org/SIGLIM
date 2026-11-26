package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.CalendarioGeneradoDto.Advertencia;
import mx.tecnm.morelia.siglim.programacion.CalendarioGeneradoDto.GrupoGenerado;
import mx.tecnm.morelia.siglim.programacion.CalendarioGeneradoDto.GrupoOmitido;
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
 * Contrato HTTP del calendario de la temporada (H-32.2, convenciones de API §6.7) con la cadena de
 * seguridad real: 201 con {@code Location}, cuerpo opcional, 400, 404, 409 y 422, y 401/403/404 de
 * cada endpoint.
 *
 * <p>Refs: H-32.2, RF-034, RF-037, RN-020, RN-021, DEC-187, DEC-189
 */
@WebMvcTest(controllers = CalendarioController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class CalendarioControllerTest {

    private static final UUID TEMPORADA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID GRUPO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID GRUPO_CHICO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String BASE = "/api/v1/temporadas/" + TEMPORADA + "/calendario";
    private static final String CUERPO = "{\"fechaInicio\":\"2031-03-05\",\"grupoIds\":[\"" + GRUPO + "\"],"
            + "\"motivo\":\"Se agregó un equipo al grupo\"}";
    private static final CalendarioGeneradoDto GENERADO = new CalendarioGeneradoDto(3, 2, 10, 10,
            List.of(new GrupoGenerado(GRUPO, "Grupo 1", 5, 10, true)),
            List.of(new GrupoOmitido(GRUPO_CHICO, "Grupo 2", 1, "Tiene 1 equipo activo")),
            List.of(new Advertencia(CalendarioService.FECHAS_DESPUES_DEL_FIN, null, "Las fechas terminan después")));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CalendarioService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String cuerpo) {
        var peticion = mvc.post().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return peticion.exchange();
    }

    // ── Caso feliz ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-034: POST responde 201 con Location al calendario y el resumen de la generación")
    void generar() {
        given(servicio.generar(eq(TEMPORADA), any(GenerarCalendarioRequest.class))).willReturn(GENERADO);

        MvcTestResult resultado = post(CUERPO);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE);
        assertThat(resultado).bodyJson().extractingPath("$.versionParametros").isEqualTo(3);
        assertThat(resultado).bodyJson().extractingPath("$.totalFechas").isEqualTo(10);
        assertThat(resultado).bodyJson().extractingPath("$.grupos[0].reemplazado").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.gruposOmitidos[0].equiposActivos").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("$.advertencias[0].tipo").isEqualTo("FECHAS_DESPUES_DEL_FIN");
        assertThat(resultado).bodyJson().doesNotHavePath("$.advertencias[0].grupoId");
        ArgumentCaptor<GenerarCalendarioRequest> peticion = ArgumentCaptor.forClass(GenerarCalendarioRequest.class);
        Mockito.verify(servicio).generar(eq(TEMPORADA), peticion.capture());
        assertThat(peticion.getValue().fechaInicio()).isEqualTo(LocalDate.parse("2031-03-05"));
        assertThat(peticion.getValue().grupoIds()).containsExactly(GRUPO);
        assertThat(peticion.getValue().motivo()).isEqualTo("Se agregó un equipo al grupo");
    }

    @Test
    @DisplayName("RF-034: POST sin cuerpo llega al servicio con la petición nula")
    void generarSinCuerpo() {
        given(servicio.generar(eq(TEMPORADA), isNull())).willReturn(GENERADO);

        assertThat(post(null)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("RF-034, RF-037: GET devuelve los grupos con sus fechas, partidos y descanso")
    void consultar() {
        CalendarioDto.EquipoDto local = new CalendarioDto.EquipoDto(UUID.randomUUID(), "Halcones", "ACTIVO");
        CalendarioDto.EquipoDto visitante = new CalendarioDto.EquipoDto(UUID.randomUUID(), "Toros", "ACTIVO");
        CalendarioDto.EquipoDto descansa = new CalendarioDto.EquipoDto(UUID.randomUUID(), "Águilas", "BAJA");
        given(servicio.consultar(TEMPORADA)).willReturn(new CalendarioDto(TEMPORADA, List.of(
                new CalendarioDto.GrupoDto(GRUPO, "Grupo 1", new ReferenciaDto(UUID.randomUUID(), "Libre"), true,
                        List.of(new CalendarioDto.FechaDto(1, 1, List.of(new CalendarioDto.PartidoDto(
                                UUID.randomUUID(), local, visitante, null)), descansa))),
                new CalendarioDto.GrupoDto(GRUPO_CHICO, "Grupo 2", new ReferenciaDto(UUID.randomUUID(), "Libre"),
                        false, List.of()))));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.grupos[0].generado").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.grupos[0].fechas[0].vuelta").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("$.grupos[0].fechas[0].partidos[0].local.nombre")
                .isEqualTo("Halcones");
        assertThat(resultado).bodyJson().doesNotHavePath("$.grupos[0].fechas[0].partidos[0].partidoId");
        assertThat(resultado).bodyJson().extractingPath("$.grupos[0].fechas[0].descansa.estado").isEqualTo("BAJA");
        assertThat(resultado).bodyJson().extractingPath("$.grupos[1].generado").isEqualTo(false);
    }

    // ── Errores de regla ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RN-020: el 409 de un grupo con partidos conserva el código y la lista de grupos")
    void regenerarConPartidos() {
        given(servicio.generar(eq(TEMPORADA), any(GenerarCalendarioRequest.class))).willThrow(
                ReglaNegocioException.conflicto("RN-020", "El calendario de «Libre – Grupo 1» ya tiene partidos.")
                        .con("grupos", List.of(Map.of("grupoId", GRUPO.toString(), "nombre", "Grupo 1"))));

        MvcTestResult resultado = post(CUERPO);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-020");
        assertThat(resultado).bodyJson().extractingPath("$.grupos[0].grupoId").isEqualTo(GRUPO.toString());
    }

    @Test
    @DisplayName("RN-021: el 400 del reemplazo sin motivo conserva el código")
    void reemplazoSinMotivo() {
        given(servicio.generar(eq(TEMPORADA), any(GenerarCalendarioRequest.class))).willThrow(
                ReglaNegocioException.invalida("RN-021", "Escribe un motivo de al menos 10 caracteres."));

        MvcTestResult resultado = post("{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-021");
    }

    @Test
    @DisplayName("DEC-187: el 422 de una temporada sin grupos generables conserva el código")
    void ningunGrupoGenerable() {
        given(servicio.generar(eq(TEMPORADA), any(GenerarCalendarioRequest.class))).willThrow(
                ReglaNegocioException.noProcesable(ErroresTecnicos.VALIDACION, "Ningún grupo tiene 2 equipos activos."));

        MvcTestResult resultado = post("{}");

        assertThat(resultado).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("RF-034: una fechaInicio con formato inválido responde 400 sin llegar al servicio")
    void fechaInicioMalFormada() {
        MvcTestResult resultado = post("{\"fechaInicio\":\"05/03/2031\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-034: GET de una temporada inexistente responde 404 NO_ENCONTRADO")
    void consultarInexistente() {
        given(servicio.consultar(TEMPORADA)).willThrow(new RecursoNoEncontradoException("Temporada " + TEMPORADA));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_ENCONTRADO);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/temporadas/{id}/calendario")
    Stream<DynamicTest> seguridadGenerar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.generar(eq(TEMPORADA), any(GenerarCalendarioRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/temporadas/{id}/calendario")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(TEMPORADA)).willThrow(error))
                .pruebas();
    }
}

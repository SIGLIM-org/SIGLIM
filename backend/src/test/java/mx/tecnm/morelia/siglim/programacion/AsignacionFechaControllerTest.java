package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
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

/**
 * Contrato HTTP de {@code POST /api/v1/fechas/{id}/asignacion} (H-33.2, convenciones de API §6.7) con
 * la cadena de seguridad real: 200, 400, 401, 403, 404, 409 y 422.
 *
 * <p>Refs: H-33.2, H-34.2, RF-023, RF-041, RF-042, RN-021, DEC-181, DEC-206, DEC-208, DEC-219
 */
@WebMvcTest(controllers = AsignacionFechaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class AsignacionFechaControllerTest {

    private static final UUID FECHA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TEMPORADA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PENDIENTE = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID SIN_ARBITRO = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final String RUTA = "/api/v1/fechas/" + FECHA + "/asignacion";
    private static final String CUERPO_PENDIENTES = "{\"modo\":\"PENDIENTES\"}";
    private static final String CUERPO_REGENERAR = "{\"modo\":\"REGENERAR\",\"motivo\":\"Se cerró la cancha 2 por lluvia\"}";
    private static final LocalDate DOMINGO = LocalDate.parse("2031-03-09");

    private static final FechaDto FECHA_DTO = new FechaDto(FECHA, TEMPORADA, 3,
            List.of(DOMINGO.minusDays(1), DOMINGO), EstadoJornada.BORRADOR, 1, 4, 1, 0, null, null, null, null, null,
            null, null, List.of());
    private static final AsignacionFechaDto RESPUESTA = new AsignacionFechaDto(FECHA_DTO,
            new AsignacionFechaDto.Totales(4, 1, 0, 3, 1),
            List.of(new AsignacionFechaDto.NoAsignado(PENDIENTE, "VAL-007",
                    "Sin horario libre el domingo para Libre (bloque de 120 min): 4 franjas libres, 5 partidos")),
            List.of(new AsignacionFechaDto.SinArbitro(SIN_ARBITRO, "RF-023",
                    "El único árbitro validado no puede pitarlo: 1 ya pita a esa hora (RF-039).")),
            List.of(), List.of(new AsignacionFechaDto.ResumenDia(DOMINGO, 4, 120, 5)));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AsignacionFechaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String cuerpo) {
        return mvc.post().uri(RUTA).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    private void falla(ReglaNegocioException error) {
        given(servicio.asignar(eq(FECHA), any(AsignarFechaRequest.class))).willThrow(error);
    }

    @Test
    @DisplayName("RF-042 · DEC-219: 200 con la fecha, los totales, los no asignados, los sin árbitro y el resumen por día")
    void asignar() {
        given(servicio.asignar(eq(FECHA), any(AsignarFechaRequest.class))).willReturn(RESPUESTA);

        MvcTestResult resultado = post(CUERPO_REGENERAR);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.fecha.numero").isEqualTo(3);
        assertThat(resultado).bodyJson().extractingPath("$.totales.asignados").isEqualTo(4);
        assertThat(resultado).bodyJson().extractingPath("$.totales.cancelados").isEqualTo(0);
        assertThat(resultado).bodyJson().extractingPath("$.totales.centrales").isEqualTo(3);
        assertThat(resultado).bodyJson().extractingPath("$.totales.sinArbitro").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("$.noAsignados[0].codigo").isEqualTo("VAL-007");
        assertThat(resultado).bodyJson().extractingPath("$.sinArbitro[0].partidoId").isEqualTo(SIN_ARBITRO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.sinArbitro[0].codigo").isEqualTo("RF-023");
        assertThat(resultado).bodyJson().extractingPath("$.sinArbitro[0].causa")
                .isEqualTo("El único árbitro validado no puede pitarlo: 1 ya pita a esa hora (RF-039).");
        assertThat(resultado).bodyJson().extractingPath("$.resumen[0].dia").isEqualTo("2031-03-09");
        assertThat(resultado).bodyJson().extractingPath("$.resumen[0].franjasLibres").isEqualTo(4);
        assertThat(resultado).bodyJson().extractingPath("$.resumen[0].bloqueMinutos").isEqualTo(120);
        assertThat(resultado).bodyJson().extractingPath("$.resumen[0].partidosRequeridos").isEqualTo(5);
        ArgumentCaptor<AsignarFechaRequest> peticion = ArgumentCaptor.forClass(AsignarFechaRequest.class);
        Mockito.verify(servicio).asignar(eq(FECHA), peticion.capture());
        assertThat(peticion.getValue().modo()).isEqualTo(ModoAsignacion.REGENERAR);
        assertThat(peticion.getValue().motivo()).isEqualTo("Se cerró la cancha 2 por lluvia");
    }

    @Test
    @DisplayName("DEC-206: un modo que no existe responde 400 sin llegar al servicio")
    void modoInvalido() {
        MvcTestResult resultado = post("{\"modo\":\"TODOS\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RN-021: regenerar sin motivo conserva el 400 y el código del servicio")
    void regenerarSinMotivo() {
        falla(ReglaNegocioException.invalida("RN-021", "Escribe un motivo de al menos 10 caracteres."));

        MvcTestResult resultado = post("{\"modo\":\"REGENERAR\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-021");
    }

    @Test
    @DisplayName("RF-090: una jornada en revisión responde 409 con su código")
    void jornadaEnRevision() {
        falla(ReglaNegocioException.conflicto("RF-090", "La jornada 3 está en revisión."));

        MvcTestResult resultado = post(CUERPO_PENDIENTES);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-090");
    }

    @Test
    @DisplayName("RF-006: sin parámetros vigentes responde 409 con su código")
    void sinParametros() {
        falla(ReglaNegocioException.conflicto("RF-006", "La temporada no tiene parámetros deportivos."));

        MvcTestResult resultado = post(CUERPO_PENDIENTES);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-006");
    }

    @Test
    @DisplayName("DEC-206: una fecha sin calendario responde 422 VALIDACION")
    void sinCalendario() {
        falla(ReglaNegocioException.noProcesable(ErroresTecnicos.VALIDACION, "La fecha 3 no tiene emparejamientos."));

        MvcTestResult resultado = post(CUERPO_PENDIENTES);

        assertThat(resultado).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("DEC-206: una fecha inexistente responde 404 NO_ENCONTRADO")
    void fechaInexistente() {
        given(servicio.asignar(eq(FECHA), any(AsignarFechaRequest.class)))
                .willThrow(new RecursoNoEncontradoException("Fecha " + FECHA));

        assertThat(post(CUERPO_PENDIENTES)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/fechas/{id}/asignacion")
    Stream<DynamicTest> seguridadAsignar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/fechas/{id}/asignacion", FECHA)
                .cuerpoJson(CUERPO_PENDIENTES)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.asignar(eq(FECHA), any(AsignarFechaRequest.class)))
                        .willThrow(error))
                .pruebas();
    }
}

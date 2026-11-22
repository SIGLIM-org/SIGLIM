package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
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

/** Contrato HTTP de tarifas (convenciones de API §6.13) con la cadena de seguridad real. */
@WebMvcTest(controllers = TarifaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class TarifaControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TEMPORADA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CATEGORIA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String BASE = "/api/v1/tarifas";
    private static final BigDecimal MONTO = new BigDecimal("350.00");
    private static final TarifaDto TARIFA = new TarifaDto(ID, new TarifaDto.ReferenciaDto(TEMPORADA, "Temporada 2026"),
            new TarifaDto.ReferenciaDto(CATEGORIA, "Libre"), ConceptoTarifa.CANCHA, MONTO, true);
    private static final String CUERPO_CREAR = """
            {"temporadaId":"%s","categoriaId":"%s","concepto":"CANCHA","monto":350.00}
            """.formatted(TEMPORADA, CATEGORIA);
    private static final String CUERPO_EDITAR = "{\"monto\":350.00}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TarifaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult enviar(HttpMethod metodo, String ruta, String cuerpo) {
        return mvc.method(metodo).uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    private static String cuerpo(MvcTestResult resultado) {
        try {
            return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 siempre está disponible", e);
        }
    }

    @Test
    @DisplayName("RF-135: GET lista las tarifas de la temporada con esDemo y el monto con dos decimales")
    void listar() {
        given(servicio.listar(TEMPORADA)).willReturn(List.of(TARIFA));

        MvcTestResult resultado = mvc.get().uri(BASE + "?temporadaId=" + TEMPORADA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.OK);
        assertThat(resultado).bodyJson().extractingPath("$[0].concepto").isEqualTo("CANCHA");
        assertThat(resultado).bodyJson().extractingPath("$[0].categoria.nombre").isEqualTo("Libre");
        assertThat(resultado).bodyJson().extractingPath("$[0].esDemo").isEqualTo(true);
        assertThat(cuerpo(resultado)).contains("\"monto\":350.00");
    }

    @Test
    @DisplayName("RF-135: POST responde 201 con Location")
    void crear() {
        given(servicio.crear(any(CrearTarifaRequest.class))).willReturn(TARIFA);

        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        ArgumentCaptor<CrearTarifaRequest> peticion = ArgumentCaptor.forClass(CrearTarifaRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().concepto()).isEqualTo(ConceptoTarifa.CANCHA);
        assertThat(peticion.getValue().monto()).isEqualByComparingTo(MONTO);
    }

    @Test
    @DisplayName("B-29 criterio 2 · RF-135: POST con monto negativo responde 400 VALIDACION y no llega al servicio")
    void crearMontoNegativo() {
        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, """
                {"temporadaId":"%s","categoriaId":"%s","concepto":"ARBITRAJE","monto":-1}
                """.formatted(TEMPORADA, CATEGORIA));

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("monto");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-135: POST con más de dos decimales o sin concepto responde 400 VALIDACION")
    void crearInvalido() {
        assertThat(enviar(HttpMethod.POST, BASE, """
                {"temporadaId":"%s","categoriaId":"%s","concepto":"ARBITRAJE","monto":1.005}
                """.formatted(TEMPORADA, CATEGORIA))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(enviar(HttpMethod.POST, BASE, """
                {"temporadaId":"%s","categoriaId":"%s","monto":1}
                """.formatted(TEMPORADA, CATEGORIA))).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-135: PATCH responde 200 con la tarifa actualizada")
    void actualizar() {
        given(servicio.actualizar(eq(ID), any(ActualizarTarifaRequest.class))).willReturn(TARIFA);

        MvcTestResult resultado = enviar(HttpMethod.PATCH, BASE + "/" + ID, CUERPO_EDITAR);

        assertThat(resultado).hasStatus(HttpStatus.OK);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
    }

    @Test
    @DisplayName("B-29 criterio 2 · RF-135: PATCH con monto negativo responde 400 VALIDACION")
    void actualizarMontoNegativo() {
        assertThat(enviar(HttpMethod.PATCH, BASE + "/" + ID, "{\"monto\":-0.01}")).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("B-29.2 · RF-135: GET /contexto devuelve las temporadas con su estado y categorías")
    void contexto() {
        given(servicio.contexto()).willReturn(new ContextoTarifasDto(List.of(new ContextoTarifasDto.TemporadaDto(
                TEMPORADA, "Temporada 2026", EstadoTemporada.ACTIVA, List.of(TARIFA.categoria())))));

        MvcTestResult resultado = mvc.get().uri(BASE + "/contexto")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.OK);
        assertThat(resultado).bodyJson().extractingPath("$.temporadas[0].estado").isEqualTo("ACTIVA");
        assertThat(resultado).bodyJson().extractingPath("$.temporadas[0].categorias[0].nombre").isEqualTo("Libre");
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/tarifas/contexto")
    Stream<DynamicTest> seguridadContexto() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/contexto")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.contexto()).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/tarifas")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "?temporadaId=" + TEMPORADA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/tarifas")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearTarifaRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/tarifas/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", ID)
                .cuerpoJson(CUERPO_EDITAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID), any(ActualizarTarifaRequest.class)))
                        .willThrow(error))
                .pruebas();
    }
}

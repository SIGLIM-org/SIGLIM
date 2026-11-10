package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Contrato HTTP de los pagos de un cobro (convenciones de API §6.13) con la cadena de seguridad real. */
@WebMvcTest(controllers = PagoController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class PagoControllerTest {

    private static final UUID COBRO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PAGO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID DOCUMENTO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String RUTA = "/api/v1/cobros/{cobroId}/pagos";
    private static final String BASE = "/api/v1/cobros/" + COBRO + "/pagos";
    private static final BigDecimal MONTO = new BigDecimal("1500.00");
    private static final byte[] PDF = "%PDF-1.4 comprobante".getBytes(StandardCharsets.US_ASCII);
    private static final PagoDto PAGO_DTO = new PagoDto(PAGO, COBRO, MedioPago.TRANSFERENCIA, MONTO, "SPEI-123",
            OffsetDateTime.parse("2030-03-01T00:00:00-06:00"),
            new CobroDto.ReferenciaDto(SeguridadPrueba.PERFIL_ID, "Tesorero"),
            new PagoDto.ComprobanteDto(DOCUMENTO, "application/pdf", PDF.length));
    private static final Map<String, String> CAMPOS = Map.of("medio", "TRANSFERENCIA", "monto", "1500.00",
            "fechaPago", "2030-03-01", "referencia", "SPEI-123");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PagoService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private static MockMultipartFile archivo() {
        return new MockMultipartFile("archivo", "comprobante.pdf", "application/pdf", PDF);
    }

    private MvcTestResult registrar(MockMultipartFile archivo, Map<String, String> campos) {
        var peticion = mvc.post().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).multipart();
        if (archivo != null) {
            peticion.file(archivo);
        }
        campos.forEach(peticion::param);
        return peticion.exchange();
    }

    private static String cuerpo(MvcTestResult resultado) {
        try {
            return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 siempre está disponible", e);
        }
    }

    // ── Registro (RF-139) ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-139: POST multipart responde 201 con Location, los metadatos del comprobante y sin datos de S3")
    void registrar() {
        given(servicio.registrar(eq(COBRO), any(RegistrarPagoRequest.class), any(byte[].class))).willReturn(PAGO_DTO);

        MvcTestResult resultado = registrar(archivo(), CAMPOS);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(PAGO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.medio").isEqualTo("TRANSFERENCIA");
        assertThat(resultado).bodyJson().extractingPath("$.registradoPor.nombre").isEqualTo("Tesorero");
        assertThat(resultado).bodyJson().extractingPath("$.comprobante.documentoId").isEqualTo(DOCUMENTO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.comprobante.tipoMime").isEqualTo("application/pdf");
        assertThat(cuerpo(resultado)).contains("\"monto\":1500.00")
                .doesNotContain("s3Bucket", "s3Clave", "\"url\"", "amazonaws");

        ArgumentCaptor<RegistrarPagoRequest> peticion = ArgumentCaptor.forClass(RegistrarPagoRequest.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(servicio).registrar(eq(COBRO), peticion.capture(), bytes.capture());
        assertThat(peticion.getValue()).isEqualTo(new RegistrarPagoRequest(MedioPago.TRANSFERENCIA, MONTO,
                LocalDate.parse("2030-03-01"), "SPEI-123"));
        assertThat(bytes.getValue()).isEqualTo(PDF);
    }

    @Test
    @DisplayName("RF-139: sin la parte archivo responde 400 VALIDACION y no llega al servicio")
    void registrarSinArchivo() {
        MvcTestResult resultado = registrar(null, CAMPOS);

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-139: sin medio ni monto, o con monto de tres decimales, responde 400 VALIDACION por campo")
    void registrarCamposInvalidos() {
        MvcTestResult sinMedio = registrar(archivo(), Map.of("monto", "1500.00"));
        assertThat(sinMedio).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(sinMedio).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("medio");

        MvcTestResult decimales = registrar(archivo(), Map.of("medio", "EFECTIVO", "monto", "1500.001"));
        assertThat(decimales).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(decimales).bodyJson().extractingPath("$.errores[0].mensaje")
                .isEqualTo("El monto admite hasta dos decimales");

        MvcTestResult medioDesconocido = registrar(archivo(), Map.of("medio", "TARJETA", "monto", "1500.00"));
        assertThat(medioDesconocido).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(medioDesconocido).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("medio");

        MvcTestResult referenciaLarga = registrar(archivo(),
                Map.of("medio", "EFECTIVO", "monto", "1500.00", "referencia", "x".repeat(61)));
        assertThat(referenciaLarga).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-139: un cuerpo JSON responde 415 FORMATO_NO_SOPORTADO")
    void registrarConJson() {
        MvcTestResult resultado = mvc.post().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"medio\":\"EFECTIVO\",\"monto\":1500.00}")
                .exchange();

        assertThat(resultado).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.FORMATO_NO_SOPORTADO);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("VAL-028 y RF-139: los errores del servicio llegan como 409 y 422 en RFC 9457")
    void registrarErroresDeNegocio() {
        given(servicio.registrar(eq(COBRO), any(RegistrarPagoRequest.class), any(byte[].class)))
                .willThrow(ReglaNegocioException.conflicto("VAL-028", "El cobro está PAGADO; no admite otro pago."))
                .willThrow(ReglaNegocioException.noProcesable("RF-139",
                        "El pago debe cubrir el monto total del cobro (1500.00 MXN)."));

        MvcTestResult conflicto = registrar(archivo(), CAMPOS);
        assertThat(conflicto).hasStatus(HttpStatus.CONFLICT);
        assertThat(conflicto).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-028");
        assertThat(conflicto).bodyJson().extractingPath("$.detail")
                .isEqualTo("El cobro está PAGADO; no admite otro pago.");

        MvcTestResult monto = registrar(archivo(), CAMPOS);
        assertThat(monto).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(monto).bodyJson().extractingPath("$.codigo").isEqualTo("RF-139");
    }

    @Test
    @DisplayName("VAL-024: un cobro inexistente responde 404 NO_ENCONTRADO")
    void registrarCobroInexistente() {
        given(servicio.registrar(eq(COBRO), any(RegistrarPagoRequest.class), any(byte[].class)))
                .willThrow(new RecursoNoEncontradoException("Cobro " + COBRO));

        MvcTestResult resultado = registrar(archivo(), CAMPOS);

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_ENCONTRADO);
    }

    // ── Historial (RF-140) ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-140: GET responde pagos e intentos sin datos de S3")
    void historial() {
        IntentoPagoDto intento = new IntentoPagoDto(UUID.randomUUID(), "SIM-20300301-ABC123", "RECHAZADO",
                OffsetDateTime.parse("2030-03-01T10:00:00-06:00"), OffsetDateTime.parse("2030-03-01T10:05:00-06:00"));
        given(servicio.historial(COBRO)).willReturn(new HistorialPagosDto(List.of(PAGO_DTO), List.of(intento)));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.pagos[0].id").isEqualTo(PAGO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.intentos[0].referencia").isEqualTo("SIM-20300301-ABC123");
        assertThat(resultado).bodyJson().extractingPath("$.intentos[0].estado").isEqualTo("RECHAZADO");
        assertThat(cuerpo(resultado)).doesNotContain("s3Bucket", "s3Clave", "\"url\"", "amazonaws");
    }

    @Test
    @DisplayName("RF-140: el nombre de quien registró se omite si no es visible")
    void historialSinNombreDelPerfil() {
        PagoDto sinNombre = new PagoDto(PAGO, COBRO, MedioPago.EFECTIVO, MONTO, null, null,
                new CobroDto.ReferenciaDto(SeguridadPrueba.PERFIL_ID, null), null);
        given(servicio.historial(COBRO)).willReturn(new HistorialPagosDto(List.of(sinNombre), List.of()));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(cuerpo(resultado)).doesNotContain("nombre", "referencia", "comprobante");
        assertThat(resultado).bodyJson().extractingPath("$.pagos[0].registradoPor.id")
                .isEqualTo(SeguridadPrueba.PERFIL_ID.toString());
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/cobros/{id}/pagos")
    Stream<DynamicTest> seguridadRegistrar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, RUTA, COBRO)
                .cuerpoMultipart(archivo(), CAMPOS)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrar(eq(COBRO), any(RegistrarPagoRequest.class),
                        any(byte[].class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/cobros/{id}/pagos")
    Stream<DynamicTest> seguridadHistorial() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, RUTA, COBRO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.historial(COBRO)).willThrow(error))
                .pruebas();
    }
}

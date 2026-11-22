package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.EnlaceTemporal;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ArchivoRechazadoException;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RequisitosRegistroDto.CategoriaDto;
import mx.tecnm.morelia.siglim.registros.RequisitosRegistroDto.RequisitoDto;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import mx.tecnm.morelia.siglim.temporadas.TipoCategoria;
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
import org.springframework.util.unit.DataSize;

/** Contrato HTTP de los documentos de un registro (convenciones de API §6.4 y §7; H-10.1). */
@WebMvcTest(controllers = DocumentoRegistroController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class DocumentoRegistroControllerTest {

    private static final UUID REGISTRO = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID DOCUMENTO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final String CARGA = "/api/v1/registros-jugador/" + REGISTRO + "/documentos";
    private static final String REEMPLAZO = "/api/v1/documentos/" + DOCUMENTO + "/reemplazo";
    private static final String REQUISITOS = "/api/v1/registros-jugador/" + REGISTRO + "/requisitos";
    private static final String ENLACE = "/api/v1/documentos/" + DOCUMENTO + "/enlace";
    private static final String VALIDACION = "/api/v1/documentos/" + DOCUMENTO + "/validacion";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F'};
    private static final TipoDocumentoDto FOTOGRAFIA = new TipoDocumentoDto(5, "FOTOGRAFIA", "Fotografía");
    private static final DocumentoDto PENDIENTE = new DocumentoDto(DOCUMENTO, REGISTRO, FOTOGRAFIA, "image/jpeg",
            183204, EstadoValidacion.PENDIENTE, null, null, null, null,
            OffsetDateTime.parse("2026-11-12T12:00:00-06:00"));
    private static final DocumentoDto RECHAZADO = new DocumentoDto(DOCUMENTO, REGISTRO, FOTOGRAFIA, "image/jpeg",
            183204, EstadoValidacion.RECHAZADO, "La fotografía está borrosa",
            new ReferenciaDto(SeguridadPrueba.PERFIL_ID, "Secretario"),
            OffsetDateTime.parse("2026-11-12T13:00:00-06:00"), null, OffsetDateTime.parse("2026-11-12T12:00:00-06:00"));
    private static final String CUERPO_VALIDACION = "{\"decision\":\"APROBADO\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private DocumentoRegistroService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private static MockMultipartFile archivo() {
        return new MockMultipartFile("archivo", "foto-de-ana.png", "image/png", JPEG);
    }

    // ── Carga ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-108: POST multipart responde 201 sin Location, con no-store y sin datos de S3")
    void cargar() {
        given(servicio.cargar(eq(REGISTRO), eq(5L), any(byte[].class))).willReturn(PENDIENTE);

        MvcTestResult resultado = cargar(archivo(), Map.of("tipoDocumentoId", "5"));

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).doesNotContainHeader(HttpHeaders.LOCATION);
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(DOCUMENTO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.registroId").isEqualTo(REGISTRO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.tipoDocumento.codigo").isEqualTo("FOTOGRAFIA");
        assertThat(resultado).bodyJson().extractingPath("$.tipoMime").isEqualTo("image/jpeg");
        assertThat(resultado).bodyJson().extractingPath("$.tamanoBytes").isEqualTo(183204);
        assertThat(resultado).bodyJson().extractingPath("$.estadoValidacion").isEqualTo("PENDIENTE");
        assertThat(resultado).bodyJson().extractingPath("$.cargadoEn").isEqualTo("2026-11-12T12:00:00-06:00");
        sinDatosDeS3(resultado);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        Mockito.verify(servicio).cargar(eq(REGISTRO), eq(5L), bytes.capture());
        assertThat(bytes.getValue()).isEqualTo(JPEG);
    }

    @Test
    @DisplayName("DEC-91: sin la parte archivo o sin tipoDocumentoId llega al servicio con null y responde 400 VALIDACION")
    void cargarSinDatos() {
        given(servicio.cargar(eq(REGISTRO), any(), any())).willThrow(ReglaNegocioException.invalida(
                ErroresTecnicos.VALIDACION, "Indica el tipo de documento y el archivo."));

        MvcTestResult sinArchivo = cargar(null, Map.of("tipoDocumentoId", "5"));
        MvcTestResult sinTipo = cargar(archivo(), Map.of());

        assertThat(sinArchivo).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(sinArchivo).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(sinTipo).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verify(servicio).cargar(eq(REGISTRO), eq(5L), isNull());
        Mockito.verify(servicio).cargar(eq(REGISTRO), isNull(), any(byte[].class));
    }

    @Test
    @DisplayName("DEC-91: un tipoDocumentoId no numérico responde 400 VALIDACION en RFC 9457 sin llegar al servicio")
    void cargarTipoNoNumerico() {
        MvcTestResult resultado = cargar(archivo(), Map.of("tipoDocumentoId", "foto"));

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-108: 413 ARCHIVO_DEMASIADO_GRANDE y 415 TIPO_ARCHIVO_NO_PERMITIDO en RFC 9457")
    void cargarArchivoRechazado() {
        given(servicio.cargar(eq(REGISTRO), eq(5L), any(byte[].class)))
                .willThrow(ArchivoRechazadoException.demasiadoGrande(DataSize.ofMegabytes(5)))
                .willThrow(ArchivoRechazadoException.tipoNoPermitido());

        MvcTestResult grande = cargar(archivo(), Map.of("tipoDocumentoId", "5"));
        MvcTestResult tipo = cargar(archivo(), Map.of("tipoDocumentoId", "5"));

        assertThat(grande).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(grande).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(grande).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.ARCHIVO_DEMASIADO_GRANDE);
        assertThat(tipo).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(tipo).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.TIPO_ARCHIVO_NO_PERMITIDO);
    }

    @Test
    @DisplayName("RF-108: un cuerpo JSON responde 415 FORMATO_NO_SOPORTADO sin llegar al servicio")
    void cargarConJson() {
        MvcTestResult resultado = mvc.post().uri(CARGA).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipoDocumentoId\":5}").exchange();

        assertThat(resultado).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.FORMATO_NO_SOPORTADO);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-108 · DEC-92: 422 RF-108 y 409 DUPLICADO del servicio llegan en RFC 9457")
    void cargarErroresDeNegocio() {
        given(servicio.cargar(eq(REGISTRO), eq(5L), any(byte[].class)))
                .willThrow(ReglaNegocioException.noProcesable("RF-108", "El registro no exige «Credencial INE»."))
                .willThrow(ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                        "Este registro ya tiene los 2 documentos de «Fotografía». Para cambiar uno, reemplázalo."));

        MvcTestResult noExigido = cargar(archivo(), Map.of("tipoDocumentoId", "5"));
        MvcTestResult cupo = cargar(archivo(), Map.of("tipoDocumentoId", "5"));

        assertThat(noExigido).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(noExigido).bodyJson().extractingPath("$.codigo").isEqualTo("RF-108");
        assertThat(cupo).hasStatus(HttpStatus.CONFLICT);
        assertThat(cupo).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.DUPLICADO);
    }

    // ── Reemplazo ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-93: POST /documentos/{id}/reemplazo responde 201 sin Location, con no-store y sin datos de S3")
    void reemplazar() {
        UUID nuevo = UUID.randomUUID();
        DocumentoDto reemplazo = new DocumentoDto(nuevo, REGISTRO, FOTOGRAFIA, "image/png", 2048,
                EstadoValidacion.PENDIENTE, null, null, null, DOCUMENTO, OffsetDateTime.parse("2026-11-12T14:00:00-06:00"));
        given(servicio.reemplazar(eq(DOCUMENTO), any(byte[].class))).willReturn(reemplazo);

        MvcTestResult resultado = mvc.post().uri(REEMPLAZO).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .multipart().file(archivo()).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).doesNotContainHeader(HttpHeaders.LOCATION);
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(nuevo.toString());
        assertThat(resultado).bodyJson().extractingPath("$.reemplazaA").isEqualTo(DOCUMENTO.toString());
        sinDatosDeS3(resultado);
    }

    @Test
    @DisplayName("RF-109: reemplazar un documento aprobado responde 409 RF-109")
    void reemplazarAprobado() {
        given(servicio.reemplazar(eq(DOCUMENTO), any(byte[].class)))
                .willThrow(ReglaNegocioException.conflicto("RF-109", "Este documento ya está aprobado y no se reemplaza."));

        MvcTestResult resultado = mvc.post().uri(REEMPLAZO).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .multipart().file(archivo()).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-109");
    }

    // ── Requisitos ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-108 · DEC-96: GET requisitos responde 200 con no-store, esDemo y los documentos vigentes")
    void requisitos() {
        RequisitoDto fotografia = new RequisitoDto(FOTOGRAFIA, 2, false, true, 0, 0, 2, List.of(RECHAZADO));
        given(servicio.requisitos(REGISTRO)).willReturn(new RequisitosRegistroDto(REGISTRO,
                EstadoRegistro.PENDIENTE_VALIDACION, new CategoriaDto(UUID.randomUUID(), "Infantil", TipoCategoria.INFANTIL),
                true, List.of(fotografia), false, false));

        MvcTestResult resultado = mvc.get().uri(REQUISITOS).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.estadoRegistro").isEqualTo("PENDIENTE_VALIDACION");
        assertThat(resultado).bodyJson().extractingPath("$.categoria.tipo").isEqualTo("INFANTIL");
        assertThat(resultado).bodyJson().extractingPath("$.esMenor").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.requisitos[0].esDemo").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.requisitos[0].faltan").isEqualTo(2);
        assertThat(resultado).bodyJson().extractingPath("$.requisitos[0].documentos[0].observaciones")
                .isEqualTo("La fotografía está borrosa");
        assertThat(resultado).bodyJson().extractingPath("$.requisitos[0].documentos[0].validadoPor.nombre")
                .isEqualTo("Secretario");
        assertThat(resultado).bodyJson().extractingPath("$.completo").isEqualTo(false);
        assertThat(resultado).bodyJson().extractingPath("$.listoParaValidar").isEqualTo(false);
        sinDatosDeS3(resultado);
    }

    // ── Enlace ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RNF-018 · DEC-95: GET enlace responde 200 { url, expiraEn } con Cache-Control no-store")
    void enlace() {
        given(servicio.enlace(DOCUMENTO)).willReturn(new EnlaceTemporal(
                "https://siglim-documentos-prueba.s3.us-east-2.amazonaws.com/liga/x.jpg?X-Amz-Expires=60",
                OffsetDateTime.parse("2026-11-12T12:01:00-06:00")));

        MvcTestResult resultado = mvc.get().uri(ENLACE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.url").asString().contains("X-Amz-Expires=60");
        assertThat(resultado).bodyJson().extractingPath("$.expiraEn").isEqualTo("2026-11-12T12:01:00-06:00");
        assertThat(cuerpo(resultado)).doesNotContain("s3Bucket", "s3Clave", "sha256");
    }

    // ── Validación ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-109: POST validacion responde 200 con el documento, no-store y sin datos de S3")
    void validar() {
        given(servicio.validar(eq(DOCUMENTO), any())).willReturn(RECHAZADO);

        MvcTestResult resultado = validar("{\"decision\":\"RECHAZADO\",\"observaciones\":\"La fotografía está borrosa\"}");

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.estadoValidacion").isEqualTo("RECHAZADO");
        assertThat(resultado).bodyJson().extractingPath("$.validadoPor.id").isEqualTo(SeguridadPrueba.PERFIL_ID.toString());
        sinDatosDeS3(resultado);
        Mockito.verify(servicio).validar(DOCUMENTO,
                new ValidarDocumentoRequest(DecisionDocumento.RECHAZADO, "La fotografía está borrosa"));
    }

    @Test
    @DisplayName("DEC-96: una decision fuera del enum responde 400 VALIDACION; sin decision la valida el servicio")
    void validarDecisionInvalida() {
        MvcTestResult otra = validar("{\"decision\":\"QUIZA\"}");
        assertThat(otra).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(otra).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);

        given(servicio.validar(eq(DOCUMENTO), any())).willThrow(ReglaNegocioException.invalida("RF-109",
                "Escribe observaciones de al menos 10 caracteres para rechazar el documento."));
        MvcTestResult sinObservaciones = validar("{\"decision\":\"RECHAZADO\"}");
        assertThat(sinObservaciones).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(sinObservaciones).bodyJson().extractingPath("$.codigo").isEqualTo("RF-109");
    }

    @Test
    @DisplayName("VAL-022 · RF-109: 403 VAL-022 y 409 RF-109 del servicio llegan en RFC 9457")
    void validarErroresDeNegocio() {
        given(servicio.validar(eq(DOCUMENTO), any()))
                .willThrow(ReglaNegocioException.prohibida("VAL-022", "No puedes validar un documento que cargaste tú."))
                .willThrow(ReglaNegocioException.conflicto("RF-109", "Este documento ya está aprobado."));

        MvcTestResult propio = validar(CUERPO_VALIDACION);
        MvcTestResult resuelto = validar(CUERPO_VALIDACION);

        assertThat(propio).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(propio).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-022");
        assertThat(resuelto).hasStatus(HttpStatus.CONFLICT);
        assertThat(resuelto).bodyJson().extractingPath("$.codigo").isEqualTo("RF-109");
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/registros-jugador/{id}/documentos")
    Stream<DynamicTest> seguridadCargar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/registros-jugador/{id}/documentos", REGISTRO)
                .cuerpoMultipart(archivo(), Map.of("tipoDocumentoId", "5"))
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.cargar(eq(REGISTRO), eq(5L), any(byte[].class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/documentos/{id}/reemplazo")
    Stream<DynamicTest> seguridadReemplazar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/documentos/{id}/reemplazo", DOCUMENTO)
                .cuerpoMultipart(archivo(), Map.of())
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.reemplazar(eq(DOCUMENTO), any(byte[].class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/registros-jugador/{id}/requisitos")
    Stream<DynamicTest> seguridadRequisitos() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/registros-jugador/{id}/requisitos", REGISTRO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.requisitos(REGISTRO)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/documentos/{id}/enlace")
    Stream<DynamicTest> seguridadEnlace() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/documentos/{id}/enlace", DOCUMENTO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.enlace(DOCUMENTO)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/documentos/{id}/validacion")
    Stream<DynamicTest> seguridadValidar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/documentos/{id}/validacion", DOCUMENTO)
                .cuerpoJson(CUERPO_VALIDACION)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.validar(eq(DOCUMENTO), any())).willThrow(error))
                .pruebas();
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────────────────────────

    private MvcTestResult cargar(MockMultipartFile archivo, Map<String, String> campos) {
        var peticion = mvc.post().uri(CARGA).header(HttpHeaders.AUTHORIZATION, autorizacion).multipart();
        if (archivo != null) {
            peticion.file(archivo);
        }
        campos.forEach(peticion::param);
        return peticion.exchange();
    }

    private MvcTestResult validar(String cuerpo) {
        return mvc.post().uri(VALIDACION).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    private static void sinDatosDeS3(MvcTestResult resultado) {
        assertThat(cuerpo(resultado)).doesNotContain("s3Bucket", "s3Clave", "\"url\"", "sha256", "amazonaws",
                "foto-de-ana");
    }

    private static String cuerpo(MvcTestResult resultado) {
        try {
            return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 siempre está disponible", e);
        }
    }
}

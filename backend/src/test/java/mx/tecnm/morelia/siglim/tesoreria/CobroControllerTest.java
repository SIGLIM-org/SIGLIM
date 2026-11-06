package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
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

/** Contrato HTTP de cobros (convenciones de API §6.13) con la cadena de seguridad real. */
@WebMvcTest(controllers = CobroController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class CobroControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID EQUIPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String BASE = "/api/v1/cobros";
    private static final LocalDate INICIO = LocalDate.parse("2030-03-01");
    private static final LocalDate LIMITE = LocalDate.parse("2030-03-15");
    private static final BigDecimal MONTO = new BigDecimal("1500.00");
    private static final CobroDto COBRO = new CobroDto(ID, TipoCobro.MANUAL, "Cuota de inscripción", MONTO, INICIO,
            LIMITE, EstadoCobro.PENDIENTE, new CobroDto.ReferenciaDto(CLUB, "Halcones"),
            new CobroDto.ReferenciaDto(EQUIPO, "Halcones A"), OffsetDateTime.parse("2030-03-01T06:00:00-06:00"));
    private static final String CUERPO_CREAR = """
            {"clubId":"%s","equipoId":"%s","concepto":"Cuota de inscripción","monto":1500.00,
             "fechaLimite":"2030-03-15"}
            """.formatted(CLUB, EQUIPO);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CobroService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    private static String cuerpo(MvcTestResult resultado) {
        try {
            return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 siempre está disponible", e);
        }
    }

    // ── Alta (RF-137) ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-137: POST responde 201 con Location y el monto con dos decimales")
    void crear() {
        given(servicio.crear(any(CrearCobroRequest.class))).willReturn(COBRO);

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE");
        assertThat(cuerpo(resultado)).contains("\"monto\":1500.00");

        ArgumentCaptor<CrearCobroRequest> peticion = ArgumentCaptor.forClass(CrearCobroRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().clubId()).isEqualTo(CLUB);
        assertThat(peticion.getValue().equipoId()).isEqualTo(EQUIPO);
        assertThat(peticion.getValue().monto()).isEqualByComparingTo(MONTO);
        assertThat(peticion.getValue().fechaLimite()).isEqualTo(LIMITE);
    }

    @Test
    @DisplayName("RF-137: POST sin club responde 400 VALIDACION y no llega al servicio")
    void crearSinClub() {
        MvcTestResult resultado = post(BASE,
                "{\"concepto\":\"Cuota\",\"monto\":1500.00,\"fechaLimite\":\"2030-03-15\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("clubId");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-137: POST sin fecha límite responde 400 VALIDACION")
    void crearSinFechaLimite() {
        MvcTestResult resultado = post(BASE,
                "{\"clubId\":\"" + CLUB + "\",\"concepto\":\"Cuota\",\"monto\":1500.00}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("fechaLimite");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-137: POST con monto no positivo o con tres decimales responde 400 VALIDACION")
    void crearMontoInvalido() {
        String negativo = """
                {"clubId":"%s","concepto":"Cuota","monto":-1.00,"fechaLimite":"2030-03-15"}
                """.formatted(CLUB);
        String tresDecimales = """
                {"clubId":"%s","concepto":"Cuota","monto":10.123,"fechaLimite":"2030-03-15"}
                """.formatted(CLUB);

        assertThat(post(BASE, negativo)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post(BASE, tresDecimales)).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── Listado (RF-140) ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-140: GET lista con la envoltura de paginación y pasa filtros, fechas y orden")
    void listar() {
        given(servicio.listar(any(FiltroCobros.class))).willReturn(Pagina.de(List.of(COBRO), 1, 10, 11));

        MvcTestResult resultado = mvc.get().uri(BASE + "?clubId=" + CLUB + "&equipoId=" + EQUIPO
                        + "&tipo=MANUAL&estado=PENDIENTE,VENCIDO&desde=2030-03-01&hasta=2030-03-31"
                        + "&pagina=1&tamano=10&orden=fechaLimite,asc")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].club.nombre").isEqualTo("Halcones");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].equipo.nombre").isEqualTo("Halcones A");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
        assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
        assertThat(cuerpo(resultado)).contains("\"monto\":1500.00");

        ArgumentCaptor<FiltroCobros> filtro = ArgumentCaptor.forClass(FiltroCobros.class);
        Mockito.verify(servicio).listar(filtro.capture());
        assertThat(filtro.getValue()).isEqualTo(new FiltroCobros(CLUB, EQUIPO, TipoCobro.MANUAL,
                List.of(EstadoCobro.PENDIENTE, EstadoCobro.VENCIDO), INICIO, LocalDate.parse("2030-03-31"), 1, 10,
                "fechaLimite,asc"));
    }

    @Test
    @DisplayName("RF-140: GET sin filtros usa página 0, tamaño 20 y orden por creación descendente")
    void listarPredeterminado() {
        given(servicio.listar(any(FiltroCobros.class))).willReturn(Pagina.de(List.of(), 0, 20, 0));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        ArgumentCaptor<FiltroCobros> filtro = ArgumentCaptor.forClass(FiltroCobros.class);
        Mockito.verify(servicio).listar(filtro.capture());
        assertThat(filtro.getValue()).isEqualTo(new FiltroCobros(null, null, null, List.of(), null, null, 0, 20,
                "creadoEn,desc"));
    }

    @Test
    @DisplayName("RF-140: un estado desconocido responde 400 VALIDACION")
    void listarEstadoInvalido() {
        MvcTestResult resultado = mvc.get().uri(BASE + "?estado=NO_EXISTE")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/cobros")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroCobros.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/cobros")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearCobroRequest.class))).willThrow(error))
                .pruebas();
    }
}

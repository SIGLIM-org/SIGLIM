package mx.tecnm.morelia.siglim.organizacion;

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

/** Contrato HTTP de los endpoints de directiva con la cadena de seguridad real. */
@WebMvcTest(controllers = CargoDirectivaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class CargoDirectivaControllerTest {

    private static final UUID CLUB = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PERSONA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String CONSULTAR = "/api/v1/clubes/" + CLUB + "/directiva";
    private static final String REGISTRAR = "{\"personaId\":\"" + PERSONA
            + "\",\"cargo\":\"PRESIDENTE\",\"desde\":\"2030-01-01\"}";
    private static final CargoDirectivaDto PENDIENTE = new CargoDirectivaDto(ID, CLUB, PERSONA, "Ana López",
            CargoDirectiva.PRESIDENTE, LocalDate.parse("2030-01-01"), null,
            EstadoCargoDirectiva.PENDIENTE, null);
    private static final CargoDirectivaDto AUTORIZADO = new CargoDirectivaDto(ID, CLUB, PERSONA, "Ana López",
            CargoDirectiva.PRESIDENTE, LocalDate.parse("2030-01-01"), null,
            EstadoCargoDirectiva.AUTORIZADO, SeguridadPrueba.PERFIL_ID);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CargoDirectivaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-102: GET directiva devuelve los cargos del club")
    void consultar() {
        given(servicio.consultar(CLUB)).willReturn(List.of(PENDIENTE));

        MvcTestResult resultado = mvc.get().uri(CONSULTAR)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].estado").isEqualTo("PENDIENTE");
        assertThat(resultado).bodyJson().extractingPath("$[0].desde").isEqualTo("2030-01-01");
        Mockito.verify(servicio).consultar(CLUB);
    }

    @Test
    @DisplayName("RF-102: POST registra un cambio pendiente con Location")
    void registrar() {
        given(servicio.registrar(eq(CLUB), any(RegistrarCargoDirectivaRequest.class))).willReturn(PENDIENTE);

        MvcTestResult resultado = post(CONSULTAR, REGISTRAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED)
                .hasHeader(HttpHeaders.LOCATION, "/api/v1/cargos-directiva/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE");
        ArgumentCaptor<RegistrarCargoDirectivaRequest> peticion =
                ArgumentCaptor.forClass(RegistrarCargoDirectivaRequest.class);
        Mockito.verify(servicio).registrar(eq(CLUB), peticion.capture());
        assertThat(peticion.getValue()).isEqualTo(new RegistrarCargoDirectivaRequest(PERSONA,
                CargoDirectiva.PRESIDENTE, LocalDate.parse("2030-01-01")));
    }

    @Test
    @DisplayName("RF-102: POST exige persona, cargo y fecha de inicio")
    void registrarIncompleto() {
        MvcTestResult resultado = post(CONSULTAR, "{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-102: POST autorización devuelve el cargo autorizado")
    void autorizar() {
        given(servicio.autorizar(ID)).willReturn(AUTORIZADO);

        MvcTestResult resultado = mvc.post().uri("/api/v1/cargos-directiva/" + ID + "/autorizacion")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("AUTORIZADO");
        assertThat(resultado).bodyJson().extractingPath("$.autorizadoPor")
                .isEqualTo(SeguridadPrueba.PERFIL_ID.toString());
        Mockito.verify(servicio).autorizar(ID);
    }

    @Test
    @DisplayName("RF-102: POST autorización informa el cambio no vigente")
    void autorizarNoPendiente() {
        given(servicio.autorizar(ID)).willThrow(ReglaNegocioException.conflicto("VAL-003", "El cargo ya se autorizó."));

        MvcTestResult resultado = mvc.post().uri("/api/v1/cargos-directiva/" + ID + "/autorizacion")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-003");
    }

    // RNF-003: cada ruta verifica sesión, permiso y ámbito mediante el arnés común.
    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/clubes/{clubId}/directiva")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/clubes/{id}/directiva", CLUB)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(CLUB)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/clubes/{clubId}/directiva")
    Stream<DynamicTest> seguridadRegistrar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/clubes/{id}/directiva", CLUB)
                .cuerpoJson(REGISTRAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrar(eq(CLUB), any(RegistrarCargoDirectivaRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/cargos-directiva/{id}/autorizacion")
    Stream<DynamicTest> seguridadAutorizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, "/api/v1/cargos-directiva/{id}/autorizacion", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.autorizar(ID)).willThrow(error))
                .pruebas();
    }
}

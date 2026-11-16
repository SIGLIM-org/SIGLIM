package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.arbitraje.CuotaArbitrosDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.arbitraje.CuotaArbitrosDto.ResumenCuotaDto;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Contrato HTTP de la cuota de árbitros del club (convenciones de API §6.5) con la seguridad real. */
@WebMvcTest(controllers = CuotaArbitrosController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class CuotaArbitrosControllerTest {

    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEMPORADA = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final String BASE = "/api/v1/clubes/" + CLUB + "/cuota-arbitros";
    private static final CuotaArbitrosDto CUOTA = new CuotaArbitrosDto(
            new ReferenciaDto(CLUB, "Club Halcones"),
            new ReferenciaDto(TEMPORADA, "Apertura 2030"),
            5, 4, new ResumenCuotaDto(2, 1, false));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ArbitroService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-114: GET calcula la cuota del club en la temporada indicada")
    void consultar() {
        given(servicio.consultarCuota(CLUB, TEMPORADA)).willReturn(CUOTA);

        MvcTestResult resultado = mvc.get()
                .uri(BASE + "?temporadaId=" + TEMPORADA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.club.id").isEqualTo(CLUB.toString());
        assertThat(resultado).bodyJson().extractingPath("$.club.nombre").isEqualTo("Club Halcones");
        assertThat(resultado).bodyJson().extractingPath("$.temporada.id").isEqualTo(TEMPORADA.toString());
        assertThat(resultado).bodyJson().extractingPath("$.temporada.nombre").isEqualTo("Apertura 2030");
        assertThat(resultado).bodyJson().extractingPath("$.equiposActivos").isEqualTo(5);
        assertThat(resultado).bodyJson().extractingPath("$.equiposPorArbitro").isEqualTo(4);
        assertThat(resultado).bodyJson().extractingPath("$.cuotaArbitros.requeridos").isEqualTo(2);
        assertThat(resultado).bodyJson().extractingPath("$.cuotaArbitros.validados").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("$.cuotaArbitros.cumple").isEqualTo(false);
        Mockito.verify(servicio).consultarCuota(CLUB, TEMPORADA);
    }

    @Test
    @DisplayName("RF-114: sin temporadaId la cuota usa la temporada activa de la liga")
    void consultarSinTemporada() {
        given(servicio.consultarCuota(eq(CLUB), isNull())).willReturn(CUOTA);

        MvcTestResult resultado = mvc.get().uri(BASE)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.temporada.id").isEqualTo(TEMPORADA.toString());
        Mockito.verify(servicio).consultarCuota(CLUB, null);
    }

    @Test
    @DisplayName("RF-114: un club sin temporada activa responde 409 RF-114")
    void consultarSinTemporadaActiva() {
        given(servicio.consultarCuota(eq(CLUB), any())).willThrow(ReglaNegocioException.conflicto(
                "RF-114", "La liga no tiene una temporada activa: indica la temporada de la cuota."));

        MvcTestResult resultado = mvc.get().uri(BASE)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-114");
    }

    @Test
    @DisplayName("RF-114: un clubId con UUID mal formado responde 400 VALIDACION")
    void consultarConClubInvalido() {
        MvcTestResult resultado = mvc.get().uri("/api/v1/clubes/no-es-uuid/cuota-arbitros")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-114: un temporadaId con UUID mal formado responde 400 VALIDACION")
    void consultarConTemporadaInvalida() {
        MvcTestResult resultado = mvc.get().uri(BASE + "?temporadaId=no-es-uuid")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RN-005: no existe DELETE de la cuota (405)")
    void sinDelete() {
        MvcTestResult resultado = mvc.delete().uri(BASE)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 del endpoint (convenciones de API §10) ───────────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/clubes/{clubId}/cuota-arbitros")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/clubes/{clubId}/cuota-arbitros", CLUB)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultarCuota(eq(CLUB), any()))
                        .willThrow(error))
                .pruebas();
    }
}

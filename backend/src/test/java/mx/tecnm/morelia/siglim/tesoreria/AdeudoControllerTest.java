package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Contrato HTTP de adeudos (convenciones de API §6.13) con la cadena de seguridad real. */
@WebMvcTest(controllers = AdeudoController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class AdeudoControllerTest {

    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID EQUIPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String BASE = "/api/v1/adeudos";
    private static final AdeudosDto ADEUDOS = new AdeudosDto(
            new BigDecimal("825.50"), new BigDecimal("300.00"), new BigDecimal("525.50"), 5,
            List.of(new AdeudosDto.ClubAdeudoDto(
                    new AdeudosDto.ReferenciaDto(CLUB, "Halcones"),
                    new BigDecimal("825.50"), new BigDecimal("300.00"), new BigDecimal("525.50"), 5,
                    List.of(new AdeudosDto.EquipoAdeudoDto(
                            new AdeudosDto.ReferenciaDto(EQUIPO, "Halcones A"),
                            new BigDecimal("250.00"), new BigDecimal("200.00"), new BigDecimal("50.00"), 2)))));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AdeudoService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private static String cuerpo(MvcTestResult resultado) {
        try {
            return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 siempre está disponible", e);
        }
    }

    @Test
    @DisplayName("RF-140: GET devuelve totales y desglose por club y equipo con dos decimales")
    void consultar() {
        given(servicio.consultar(CLUB)).willReturn(ADEUDOS);

        MvcTestResult resultado = mvc.get().uri(BASE + "?clubId=" + CLUB)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.clubes[0].club.nombre").isEqualTo("Halcones");
        assertThat(resultado).bodyJson().extractingPath("$.clubes[0].equipos[0].equipo.nombre")
                .isEqualTo("Halcones A");
        assertThat(resultado).bodyJson().extractingPath("$.cobros").isEqualTo(5);
        assertThat(cuerpo(resultado))
                .contains("\"total\":825.50")
                .contains("\"pendiente\":300.00")
                .contains("\"vencido\":525.50");
        Mockito.verify(servicio).consultar(CLUB);
    }

    @Test
    @DisplayName("RF-140: GET sin club consulta todos los adeudos del ámbito")
    void consultarSinFiltro() {
        given(servicio.consultar(any())).willReturn(new AdeudosDto(BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), 0, List.of()));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.clubes").isEqualTo(List.of());
        Mockito.verify(servicio).consultar(null);
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/adeudos")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(any())).willThrow(error))
                .pruebas();
    }
}

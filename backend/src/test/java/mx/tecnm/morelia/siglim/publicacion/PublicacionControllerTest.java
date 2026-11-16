package mx.tecnm.morelia.siglim.publicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.publicacion.PublicacionPendienteDto.Perfil;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Contrato HTTP de la bandeja de pendientes (convenciones de API §6.11) con la cadena de seguridad real.
 *
 * <p>Refs: H-13.1, RF-090, RF-121
 */
@WebMvcTest(controllers = PublicacionController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class PublicacionControllerTest {

    private static final String RUTA = "/api/v1/publicaciones/pendientes";
    private static final UUID JORNADA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID COMUNICADO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PERFIL = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final OffsetDateTime ENVIO = OffsetDateTime.parse("2031-03-01T10:00:00-06:00");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PublicacionPendienteService servicio;

    @Test
    @DisplayName("RF-090, RF-121: GET /publicaciones/pendientes pagina jornadas y comunicados en revisión")
    void pendientes() {
        given(servicio.listar(0, 20)).willReturn(Pagina.de(List.of(
                new PublicacionPendienteDto(TipoPublicacion.JORNADA, JORNADA, "Jornada 3",
                        "Temporada 2031 · Libre · A", 4L, new Perfil(PERFIL, "Programador"), ENVIO),
                new PublicacionPendienteDto(TipoPublicacion.COMUNICADO, COMUNICADO, "Aviso", null, null,
                        new Perfil(PERFIL, "Secretaría"), ENVIO.plusHours(1))), 0, 20, 2));

        MvcTestResult resultado = mvc.get().uri(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SeguridadPrueba.tokenValido()).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].tipo").isEqualTo("JORNADA");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].partidos").isEqualTo(4);
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].enviadoPor.nombre").isEqualTo("Programador");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[1].tipo").isEqualTo("COMUNICADO");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[1].partidos");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(2);
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/publicaciones/pendientes")
    Stream<DynamicTest> seguridad() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, RUTA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(0, 20)).willThrow(error))
                .pruebas();
    }
}

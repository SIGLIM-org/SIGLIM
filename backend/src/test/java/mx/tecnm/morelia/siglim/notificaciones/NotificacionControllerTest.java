package mx.tecnm.morelia.siglim.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.notificaciones.NotificacionService.AvisosDelUsuario;
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

/** Contrato HTTP de los avisos (convenciones de API §6.14) con la cadena de seguridad real. */
@WebMvcTest(controllers = NotificacionController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class NotificacionControllerTest {

    private static final String BASE = "/api/v1/notificaciones";
    private static final UUID ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PARTIDO = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final OffsetDateTime CREADA = OffsetDateTime.parse("2026-11-25T10:00:00-06:00");
    private static final NotificacionDto AVISO = new NotificacionDto(ID, "CEDULA_PENDIENTE",
            "Cédula pendiente de validación", "La cédula de Halcones vs Toros (jornada 3) espera tu validación.",
            "/app/partidos/" + PARTIDO, CREADA, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private NotificacionService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-142 · UI-030: GET devuelve la página de avisos y el conteo en X-No-Leidas, sin caché")
    void listar() {
        given(servicio.listar(false, 0, 20))
                .willReturn(new AvisosDelUsuario(Pagina.de(List.of(AVISO), 0, 20, 1), 7));

        MvcTestResult resultado = mvc.get().uri(BASE + "?leida=false")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk()
                .hasHeader("X-No-Leidas", "7")
                .hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].evento").isEqualTo("CEDULA_PENDIENTE");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].enlace").isEqualTo("/app/partidos/" + PARTIDO);
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].leidaEn").isNull();
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(1);
    }

    @Test
    @DisplayName("RF-142: GET sin filtro pide todos los avisos con la página predeterminada")
    void listarSinFiltro() {
        given(servicio.listar(null, 0, 20)).willReturn(new AvisosDelUsuario(Pagina.de(List.of(), 0, 20, 0), 0));

        assertThat(mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange())
                .hasStatusOk().hasHeader("X-No-Leidas", "0");
        Mockito.verify(servicio).listar(null, 0, 20);
    }

    @Test
    @DisplayName("H-25 criterio 2: POST /{id}/lectura devuelve el aviso leído")
    void marcarLeida() {
        OffsetDateTime leida = CREADA.plusMinutes(5);
        given(servicio.marcarLeida(ID)).willReturn(new NotificacionDto(ID, AVISO.evento(), AVISO.titulo(),
                AVISO.cuerpo(), AVISO.enlace(), CREADA, leida));

        MvcTestResult resultado = mvc.post().uri(BASE + "/{id}/lectura", ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.leidaEn").isNotNull();
    }

    @Test
    @DisplayName("RF-142: POST /lectura responde cuántos avisos marcó")
    void marcarTodas() {
        given(servicio.marcarTodas()).willReturn(new LecturaMasivaDto(3));

        MvcTestResult resultado = mvc.post().uri(BASE + "/lectura")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.marcadas").isEqualTo(3);
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/notificaciones")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(), anyInt(), anyInt())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/notificaciones/{id}/lectura")
    Stream<DynamicTest> seguridadMarcarLeida() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/lectura", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.marcarLeida(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/notificaciones/lectura")
    Stream<DynamicTest> seguridadMarcarTodas() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/lectura")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.marcarTodas()).willThrow(error))
                .pruebas();
    }
}

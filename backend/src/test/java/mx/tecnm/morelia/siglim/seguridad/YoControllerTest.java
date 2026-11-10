package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.seguridad.YoDto.AsignacionDto;
import mx.tecnm.morelia.siglim.seguridad.YoDto.ClubDto;
import mx.tecnm.morelia.siglim.seguridad.YoDto.PerfilDto;
import mx.tecnm.morelia.siglim.seguridad.YoDto.PermisoDto;
import mx.tecnm.morelia.siglim.seguridad.YoDto.RolDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Prueba de controlador de {@code GET /api/v1/yo} con la cadena de seguridad real. */
@WebMvcTest(controllers = YoController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class YoControllerTest {

    private static final UUID ASIGNACION_LIGA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ASIGNACION_CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CLUB = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private YoService servicio;

    @Test
    @DisplayName("RF-094: con token válido responde 200 con perfil, asignaciones y permisos, sin caché")
    void respondeElContrato() {
        given(servicio.consultar()).willReturn(new YoDto(
                new PerfilDto(SeguridadPrueba.PERFIL_ID, "Ana López", null),
                List.of(
                        new AsignacionDto(ASIGNACION_LIGA,
                                new RolDto("PROGRAMADOR", "Programador de partidos"), "LIGA", null),
                        new AsignacionDto(ASIGNACION_CLUB,
                                new RolDto("DIRECTIVA_CLUB", "Directiva de club"), "CLUB",
                                new ClubDto(CLUB, "Club Halcones"))),
                List.of(
                        new PermisoDto("partidos.partido:CONSULTAR", "M-08", "LIGA", null),
                        new PermisoDto("registros.registro:EDITAR", "M-04", "CLUB", CLUB))));

        MvcTestResult resultado = mvc.get().uri("/api/v1/yo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SeguridadPrueba.tokenValido())
                .exchange();

        assertThat(resultado)
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.perfil.id").isEqualTo(SeguridadPrueba.PERFIL_ID.toString());
        json.extractingPath("$.perfil.nombreMostrado").isEqualTo("Ana López");
        json.extractingPath("$.perfil.correo").isNull();
        json.extractingPath("$.asignaciones[0].id").isEqualTo(ASIGNACION_LIGA.toString());
        json.extractingPath("$.asignaciones[0].rol.codigo").isEqualTo("PROGRAMADOR");
        json.extractingPath("$.asignaciones[0].rol.nombre").isEqualTo("Programador de partidos");
        json.extractingPath("$.asignaciones[0].ambito").isEqualTo("LIGA");
        json.extractingPath("$.asignaciones[0].club").isNull();
        json.extractingPath("$.asignaciones[1].ambito").isEqualTo("CLUB");
        json.extractingPath("$.asignaciones[1].club.id").isEqualTo(CLUB.toString());
        json.extractingPath("$.asignaciones[1].club.nombre").isEqualTo("Club Halcones");
        json.extractingPath("$.permisos[0].codigo").isEqualTo("partidos.partido:CONSULTAR");
        json.extractingPath("$.permisos[0].modulo").isEqualTo("M-08");
        json.extractingPath("$.permisos[0].ambito").isEqualTo("LIGA");
        json.extractingPath("$.permisos[0].clubId").isNull();
        json.extractingPath("$.permisos[1].clubId").isEqualTo(CLUB.toString());
    }

    @Test
    @DisplayName("H-01.1: sin token responde 401 NO_AUTENTICADO en formato RFC 9457")
    void sinTokenResponde401() {
        MvcTestResult resultado = mvc.get().uri("/api/v1/yo").exchange();

        assertThat(resultado)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("NO_AUTENTICADO");
        assertThat(resultado).bodyJson().extractingPath("$.status").isEqualTo(401);
        assertThat(resultado).bodyJson().extractingPath("$.instance").isEqualTo("/api/v1/yo");
    }
}

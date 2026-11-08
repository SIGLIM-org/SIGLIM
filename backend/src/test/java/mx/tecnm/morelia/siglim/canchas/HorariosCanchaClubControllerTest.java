package mx.tecnm.morelia.siglim.canchas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Contrato HTTP de horarios de cancha de club (B-13.1, RF-119). */
@WebMvcTest(controllers = HorariosCanchaClubController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class HorariosCanchaClubControllerTest {

    private static final UUID CANCHA_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEMPORADA_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID CATEGORIA_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID HORARIO_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final String BASE = "/api/v1/canchas/" + CANCHA_ID + "/horarios-club";
    private static final String CUERPO = """
            {"horarios":[{"temporadaId":"33333333-3333-4333-8333-333333333333",
            "categoriaId":"44444444-4444-4444-8444-444444444444","diaSemana":1,
            "horaInicio":"18:00:00","horaFin":"20:00:00","esComodato":false}]}
            """;
    private static final HorariosCanchaClubDto HORARIO = new HorariosCanchaClubDto(
            HORARIO_ID, CANCHA_ID, CLUB_ID, TEMPORADA_ID, CATEGORIA_ID, 1,
            LocalTime.of(18, 0), LocalTime.of(20, 0), false);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private HorariosCanchaClubService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-119: GET devuelve los horarios de la cancha")
    void consultar() {
        given(servicio.listar(CANCHA_ID)).willReturn(List.of(HORARIO));

        MvcTestResult resultado = mvc.get().uri(BASE)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].categoriaId").isEqualTo(CATEGORIA_ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$[0].horaInicio").isEqualTo("18:00:00");
    }

    @Test
    @DisplayName("RF-119: PUT reemplaza el conjunto y devuelve 200")
    void reemplazar() {
        given(servicio.reemplazar(eq(CANCHA_ID), any(RegistrarHorarioRequest.class)))
                .willReturn(List.of(HORARIO));

        MvcTestResult resultado = put(CUERPO);

        assertThat(resultado).hasStatus(HttpStatus.OK);
        assertThat(resultado).bodyJson().extractingPath("$[0].esComodato").isEqualTo(false);
        Mockito.verify(servicio).reemplazar(eq(CANCHA_ID), any(RegistrarHorarioRequest.class));
    }

    @Test
    @DisplayName("RF-119: PUT sin la lista responde 400 VALIDACION")
    void putSinLista() {
        MvcTestResult resultado = put("{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/canchas/{id}/horarios-club")
    Stream<DynamicTest> seguridadGet() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, "/api/v1/canchas/{id}/horarios-club", CANCHA_ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(CANCHA_ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PUT /api/v1/canchas/{id}/horarios-club")
    Stream<DynamicTest> seguridadPut() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PUT, "/api/v1/canchas/{id}/horarios-club", CANCHA_ID)
                .cuerpoJson(CUERPO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.reemplazar(eq(CANCHA_ID),
                        any(RegistrarHorarioRequest.class))).willThrow(error))
                .pruebas();
    }

    private MvcTestResult put(String cuerpo) {
        return mvc.put().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }
}

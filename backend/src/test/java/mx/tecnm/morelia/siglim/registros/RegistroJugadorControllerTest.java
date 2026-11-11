package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.PersonaResumenDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
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

/** Contrato HTTP del alta de un registro de jugador (convenciones de API §6.4; H-09.2). */
@WebMvcTest(controllers = RegistroJugadorController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class RegistroJugadorControllerTest {

    private static final String RUTA = "/api/v1/registros-jugador";
    private static final UUID PERSONA = UUID.fromString("21000000-0000-4000-8000-000000000001");
    private static final UUID EQUIPO = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID REGISTRO = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final String CUERPO = """
            {"personaId":"21000000-0000-4000-8000-000000000001","equipoId":"60000000-0000-4000-8000-000000000001"}
            """;
    private static final RegistroJugadorDto CREADO = new RegistroJugadorDto(REGISTRO,
            new PersonaResumenDto(PERSONA, "Ana López"),
            new ReferenciaDto(EQUIPO, "Halcones"),
            new ReferenciaDto(UUID.randomUUID(), "Club Halcones"),
            new ReferenciaDto(UUID.randomUUID(), "Libre"),
            new TemporadaResumenDto(UUID.randomUUID(), "Temporada 2026", EstadoTemporada.ACTIVA),
            EstadoRegistro.PENDIENTE_VALIDACION, LocalDate.parse("2026-11-11"),
            OffsetDateTime.parse("2026-11-11T12:00:00-06:00"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private RegistroJugadorService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-013 · DEC-90: POST /registros-jugador responde 201 sin Location, con no-store y sin CURP")
    void registrar() {
        given(servicio.registrar(any())).willReturn(CREADO);

        MvcTestResult resultado = enviar(CUERPO);

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).doesNotContainHeader(HttpHeaders.LOCATION);
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(REGISTRO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.persona.nombreCompleto").isEqualTo("Ana López");
        assertThat(resultado).bodyJson().extractingPath("$.equipo.nombre").isEqualTo("Halcones");
        assertThat(resultado).bodyJson().extractingPath("$.club.nombre").isEqualTo("Club Halcones");
        assertThat(resultado).bodyJson().extractingPath("$.categoria.nombre").isEqualTo("Libre");
        assertThat(resultado).bodyJson().extractingPath("$.temporada.estado").isEqualTo("ACTIVA");
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("PENDIENTE_VALIDACION");
        assertThat(resultado).bodyJson().extractingPath("$.altaEn").isEqualTo("2026-11-11");
        assertThat(resultado).bodyJson().doesNotHavePath("$.persona.curp");
        Mockito.verify(servicio).registrar(new CrearRegistroJugadorRequest(PERSONA, EQUIPO));
    }

    @Test
    @DisplayName("DEC-85: un cuerpo sin equipoId responde 400 VALIDACION")
    void faltanDatos() {
        given(servicio.registrar(any())).willThrow(ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                "Indica la persona y el equipo del registro."));

        MvcTestResult resultado = enviar("{\"personaId\":\"" + PERSONA + "\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verify(servicio).registrar(new CrearRegistroJugadorRequest(PERSONA, null));
    }

    @Test
    @DisplayName("VAL-017: 409 con las extensiones club y equipo")
    void yaRegistrada() {
        given(servicio.registrar(any())).willThrow(ReglaNegocioException.conflicto("VAL-017",
                        "Esta persona ya está registrada en esta temporada en el club «Club Águilas», equipo «Águilas».")
                .con("club", "Club Águilas").con("equipo", "Águilas"));

        MvcTestResult resultado = enviar(CUERPO);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-017");
        assertThat(resultado).bodyJson().extractingPath("$.club").isEqualTo("Club Águilas");
        assertThat(resultado).bodyJson().extractingPath("$.equipo").isEqualTo("Águilas");
    }

    @Test
    @DisplayName("VAL-025: 422 con la extensión requisito")
    void menorSinRequisitos() {
        given(servicio.registrar(any())).willThrow(ReglaNegocioException.noProcesable("VAL-025",
                "La persona es menor de edad y falta el consentimiento de su tutor.").con("requisito", "CONSENTIMIENTO"));

        MvcTestResult resultado = enviar(CUERPO);

        assertThat(resultado).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("VAL-025");
        assertThat(resultado).bodyJson().extractingPath("$.requisito").isEqualTo("CONSENTIMIENTO");
    }

    @Test
    @DisplayName("RF-013 · RF-101: 422 equipo de baja y 409 club sin tarjetón aprobado")
    void equipoYAfiliacion() {
        given(servicio.registrar(any()))
                .willThrow(ReglaNegocioException.noProcesable("RF-013", "El equipo está dado de baja."))
                .willThrow(ReglaNegocioException.conflicto("RF-101", "El club no tiene su tarjetón aprobado."));

        MvcTestResult baja = enviar(CUERPO);
        MvcTestResult sinTarjeton = enviar(CUERPO);

        assertThat(baja).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(baja).bodyJson().extractingPath("$.codigo").isEqualTo("RF-013");
        assertThat(sinTarjeton).hasStatus(HttpStatus.CONFLICT);
        assertThat(sinTarjeton).bodyJson().extractingPath("$.codigo").isEqualTo("RF-101");
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/registros-jugador")
    Stream<DynamicTest> seguridad() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, RUTA)
                .cuerpoJson(CUERPO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrar(any())).willThrow(error))
                .pruebas();
    }

    private MvcTestResult enviar(String cuerpo) {
        return mvc.post().uri(RUTA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }
}

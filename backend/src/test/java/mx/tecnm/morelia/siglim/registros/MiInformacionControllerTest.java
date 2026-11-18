package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.DocumentoPropioDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.DocumentosPropiosDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.PersonaPropiaDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.RegistroPropioDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.RequisitoPropioDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.registros.TitularesDto.TitularDto;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Contrato HTTP de la consulta propia (convenciones de API §6.4; H-24.1, RF-144, DEC-135 a DEC-137). */
@WebMvcTest(controllers = MiInformacionController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class MiInformacionControllerTest {

    private static final String RUTA = "/api/v1/mi-informacion";
    private static final UUID PERSONA = UUID.fromString("21000000-0000-4000-8000-000000000001");
    private static final UUID MENOR = UUID.fromString("21000000-0000-4000-8000-000000000002");
    private static final UUID REGISTRO = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final TipoDocumentoDto FOTOGRAFIA = new TipoDocumentoDto(5, "FOTOGRAFIA", "Fotografía");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MiInformacionService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    @Test
    @DisplayName("RF-144 · DEC-135: GET /mi-informacion responde 200 con los titulares y no-store")
    void titulares() {
        given(servicio.titulares()).willReturn(new TitularesDto(List.of(
                new TitularDto(PERSONA, "Ana Pérez", true, false),
                new TitularDto(MENOR, "Luis Pérez", false, true))));

        MvcTestResult resultado = mvc.get().uri(RUTA).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.OK);
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.titulares[0].personaId").isEqualTo(PERSONA.toString());
        assertThat(resultado).bodyJson().extractingPath("$.titulares[0].esPropia").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.titulares[1].nombre").isEqualTo("Luis Pérez");
        assertThat(resultado).bodyJson().extractingPath("$.titulares[1].esMenor").isEqualTo(true);
    }

    @Test
    @DisplayName("RF-144 · RNF-012 · RNF-018: GET /mi-informacion/{personaId} responde 200 sin CURP completa ni datos del archivo")
    void detalle() {
        DocumentoPropioDto documento = new DocumentoPropioDto(UUID.randomUUID(), FOTOGRAFIA, EstadoValidacion.RECHAZADO,
                "La foto está borrosa", OffsetDateTime.parse("2026-11-10T10:00:00-06:00"));
        RegistroPropioDto registro = new RegistroPropioDto(REGISTRO,
                new TemporadaResumenDto(UUID.randomUUID(), "Temporada 2026", EstadoTemporada.ACTIVA),
                new ReferenciaDto(UUID.randomUUID(), "Club Halcones"), new ReferenciaDto(UUID.randomUUID(), "Halcones"),
                new ReferenciaDto(UUID.randomUUID(), "Libre"), EstadoRegistro.PENDIENTE_VALIDACION,
                LocalDate.parse("2026-11-01"), null, null, null,
                ReglaElegibilidad.evaluar(EstadoRegistro.PENDIENTE_VALIDACION, List.of()),
                new DocumentosPropiosDto(List.of(new RequisitoPropioDto(FOTOGRAFIA, 1, false, true, 0, 0, 1,
                        List.of(documento))), false, false),
                List.of(), List.of());
        given(servicio.consultar(PERSONA)).willReturn(new MiInformacionDto(
                new PersonaPropiaDto(PERSONA, "Ana Pérez", "GAPA******HMN***05", false), List.of(registro), List.of()));

        MvcTestResult resultado = mvc.get().uri(RUTA + "/{personaId}", PERSONA)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.OK);
        assertThat(resultado).hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(resultado).bodyJson().extractingPath("$.persona.curpEnmascarada").isEqualTo("GAPA******HMN***05");
        assertThat(resultado).bodyJson().doesNotHavePath("$.persona.curp");
        assertThat(resultado).bodyJson().extractingPath("$.registros[0].elegibilidad.elegible").isEqualTo(false);
        assertThat(resultado).bodyJson().extractingPath("$.registros[0].elegibilidad.causas[0].codigo").isEqualTo("RF-019");
        assertThat(resultado).bodyJson().extractingPath("$.registros[0].documentos.requisitos[0].faltan").isEqualTo(1);
        String ruta = "$.registros[0].documentos.requisitos[0].documentos[0]";
        assertThat(resultado).bodyJson().extractingPath(ruta + ".observaciones").isEqualTo("La foto está borrosa");
        assertThat(resultado).bodyJson().extractingPath(ruta + ".tipo.codigo").isEqualTo("FOTOGRAFIA");
        assertThat(resultado).bodyJson().doesNotHavePath(ruta + ".url");
        assertThat(resultado).bodyJson().doesNotHavePath(ruta + ".s3Clave");
        assertThat(resultado).bodyJson().doesNotHavePath(ruta + ".sha256");
        assertThat(resultado).bodyJson().extractingPath("$.sanciones").asArray().isEmpty();
    }

    @Test
    @DisplayName("DEC-136: un personaId mal formado responde 400 VALIDACION sin llegar al servicio")
    void uuidInvalido() {
        MvcTestResult resultado = mvc.get().uri(RUTA + "/no-es-uuid")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @TestFactory
    @DisplayName("RNF-003 · VAL-015 · VAL-024: seguridad de GET /api/v1/mi-informacion")
    Stream<DynamicTest> seguridadTitulares() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, RUTA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.titulares()).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003 · VAL-015 · VAL-024: seguridad de GET /api/v1/mi-informacion/{personaId}")
    Stream<DynamicTest> seguridadDetalle() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, RUTA + "/{personaId}", MENOR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(MENOR)).willThrow(error))
                .pruebas();
    }
}

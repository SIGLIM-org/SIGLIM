package mx.tecnm.morelia.siglim.canchas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

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

/** Contrato HTTP de sedes (convenciones de API §6.6) con la cadena de seguridad real. */
@WebMvcTest(controllers = SedeController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class SedeControllerTest {

    private static final UUID ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final String BASE = "/api/v1/sedes";
    private static final SedeDto SEDE = new SedeDto(ID, "Unidad Norte", null);
    private static final String CUERPO_CREAR = "{\"nombre\":\"Unidad Norte\"}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SedeService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult enviar(HttpMethod metodo, String ruta, String cuerpo) {
        return mvc.method(metodo).uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-028: GET lista las sedes sin paginar y omite la dirección vacía")
    void listar() {
        given(servicio.listar()).willReturn(List.of(SEDE));

        MvcTestResult resultado = mvc.get().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$[0].id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$[0].nombre").isEqualTo("Unidad Norte");
        assertThat(resultado).bodyJson().doesNotHavePath("$[0].direccion");
    }

    @Test
    @DisplayName("RF-028: POST responde 201 con Location")
    void crear() {
        given(servicio.crear(any(CrearSedeRequest.class))).willReturn(SEDE);

        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.nombre").isEqualTo("Unidad Norte");
    }

    @Test
    @DisplayName("RF-028: POST sin nombre responde 400 VALIDACION y no llega al servicio")
    void crearSinNombre() {
        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, "{\"direccion\":\"Av. 1\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("nombre");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-028: POST con nombre de más de 120 caracteres responde 400 VALIDACION")
    void crearNombreLargo() {
        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, "{\"nombre\":\"" + "x".repeat(121) + "\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("nombre");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-028: un nombre repetido responde 409 DUPLICADO en RFC 9457")
    void crearDuplicada() {
        given(servicio.crear(any(CrearSedeRequest.class))).willThrow(ReglaNegocioException.conflicto(
                ErroresTecnicos.DUPLICADO, "Ya existe una sede llamada «Unidad Norte» en la liga."));

        MvcTestResult resultado = enviar(HttpMethod.POST, BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.DUPLICADO);
    }

    @Test
    @DisplayName("RF-028: PATCH devuelve la sede editada")
    void actualizar() {
        given(servicio.actualizar(eq(ID), any(ActualizarSedeRequest.class))).willReturn(SEDE);

        MvcTestResult resultado = enviar(HttpMethod.PATCH, BASE + "/" + ID, "{\"nombre\":\"Unidad Norte\"}");

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/sedes")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar()).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/sedes")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearSedeRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/sedes/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", ID)
                .cuerpoJson("{\"nombre\":\"Unidad Sur\"}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID), any(ActualizarSedeRequest.class)))
                        .willThrow(error))
                .pruebas();
    }
}

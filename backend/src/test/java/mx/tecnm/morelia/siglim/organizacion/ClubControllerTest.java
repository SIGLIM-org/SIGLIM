package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
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

/** Contrato HTTP de clubes (convenciones de API §6.3) con la cadena de seguridad real. */
@WebMvcTest(controllers = ClubController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class ClubControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String BASE = "/api/v1/clubes";
    private static final String CUERPO_CREAR = "{\"nombre\":\"Club Halcones\"}";
    private static final ClubDto CLUB = new ClubDto(ID, "Club Halcones", EstadoClub.ACTIVO,
            OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), null, null, null);
    private static final UUID NUEVO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String CUERPO_CAMBIO =
            "{\"nombre\":\"Club Águilas\",\"motivo\":\"La asamblea aprobó el nuevo nombre\"}";
    private static final ClubDto CLUB_NUEVO = new ClubDto(NUEVO, "Club Águilas", EstadoClub.ACTIVO,
            OffsetDateTime.parse("2030-07-01T06:00:00-06:00"), null, null,
            new ClubDto.ClubAnteriorDto(ID, "Club Halcones"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ClubService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String cuerpo) {
        return mvc.post().uri(BASE).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-008: GET lista con la envoltura de paginación y pasa los filtros")
    void listar() {
        FiltroClubes filtro = new FiltroClubes("halc", EstadoClub.ACTIVO, 1, 10);
        given(servicio.listar(filtro)).willReturn(Pagina.de(List.of(CLUB), 1, 10, 11));

        MvcTestResult resultado = mvc.get().uri(BASE + "?q=halc&estado=ACTIVO&pagina=1&tamano=10")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].nombre").isEqualTo("Club Halcones");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].estado").isEqualTo("ACTIVO");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].cerradoEn");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].motivoCierre");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
        Mockito.verify(servicio).listar(filtro);
    }

    @Test
    @DisplayName("RF-008: GET por id responde el detalle")
    void consultar() {
        given(servicio.consultar(ID)).willReturn(CLUB);

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
    }

    @Test
    @DisplayName("RF-008: POST responde 201 con Location")
    void crear() {
        given(servicio.crear(any(CrearClubRequest.class))).willReturn(CLUB);

        MvcTestResult resultado = post(CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("ACTIVO");
        ArgumentCaptor<CrearClubRequest> peticion = ArgumentCaptor.forClass(CrearClubRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().nombre()).isEqualTo("Club Halcones");
    }

    @Test
    @DisplayName("RF-008: POST sin nombre responde 400 VALIDACION y no llega al servicio")
    void crearSinNombre() {
        MvcTestResult resultado = post("{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("nombre");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-008: un nombre repetido responde 409 DUPLICADO")
    void crearDuplicado() {
        given(servicio.crear(any(CrearClubRequest.class))).willThrow(ReglaNegocioException
                .conflicto(ErroresTecnicos.DUPLICADO, "Ya existe un club llamado «Club Halcones» en la liga."));

        MvcTestResult resultado = post(CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.DUPLICADO);
    }

    private MvcTestResult postCambio(String cuerpo) {
        return mvc.post().uri(BASE + "/" + ID + "/cambio-nombre").header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-105: POST cambio-nombre responde 201 con Location y el club nuevo con clubAnterior")
    void cambiarNombre() {
        given(servicio.cambiarNombre(eq(ID), any(CambioNombreClubRequest.class))).willReturn(CLUB_NUEVO);

        MvcTestResult resultado = postCambio(CUERPO_CAMBIO);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + NUEVO);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(NUEVO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.nombre").isEqualTo("Club Águilas");
        assertThat(resultado).bodyJson().extractingPath("$.clubAnterior.id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().extractingPath("$.clubAnterior.nombre").isEqualTo("Club Halcones");
        ArgumentCaptor<CambioNombreClubRequest> peticion = ArgumentCaptor.forClass(CambioNombreClubRequest.class);
        Mockito.verify(servicio).cambiarNombre(eq(ID), peticion.capture());
        assertThat(peticion.getValue().nombre()).isEqualTo("Club Águilas");
        assertThat(peticion.getValue().motivo()).isEqualTo("La asamblea aprobó el nuevo nombre");
    }

    @Test
    @DisplayName("RF-105: un club sin club anterior omite clubAnterior")
    void sinClubAnterior() {
        given(servicio.consultar(ID)).willReturn(CLUB);

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).bodyJson().doesNotHavePath("$.clubAnterior");
    }

    @Test
    @DisplayName("RF-105: un nombre de más de 150 o un motivo de más de 500 caracteres responde 400 VALIDACION")
    void cambiarNombreDemasiadoLargo() {
        MvcTestResult nombreLargo = postCambio("{\"nombre\":\"" + "a".repeat(151)
                + "\",\"motivo\":\"La asamblea aprobó el nuevo nombre\"}");
        MvcTestResult motivoLargo = postCambio("{\"nombre\":\"Club Águilas\",\"motivo\":\""
                + "m".repeat(501) + "\"}");

        assertThat(nombreLargo).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(nombreLargo).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(nombreLargo).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("nombre");
        assertThat(motivoLargo).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(motivoLargo).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("motivo");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RN-021: sin nombre ni motivo el cuerpo llega al servicio, que decide el orden de errores")
    void cambiarNombreCuerpoVacioLlegaAlServicio() {
        given(servicio.cambiarNombre(eq(ID), any(CambioNombreClubRequest.class))).willThrow(ReglaNegocioException
                .invalida("RN-021", "Escribe un motivo de al menos 10 caracteres para cambiar el nombre del club."));

        MvcTestResult resultado = postCambio("{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-021");
    }

    @Test
    @DisplayName("RN-041: con equipos inscritos o adeudos responde 409 RN-041 en RFC 9457")
    void cambiarNombreConflicto() {
        given(servicio.cambiarNombre(eq(ID), any(CambioNombreClubRequest.class))).willThrow(ReglaNegocioException
                .conflicto("RN-041", "El club tiene cobros pendientes o vencidos: liquídalos antes de registrar"
                        + " el cambio de nombre."));

        MvcTestResult resultado = postCambio(CUERPO_CAMBIO);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-041");
    }

    @Test
    @DisplayName("RF-105: un nombre ya usado responde 409 DUPLICADO")
    void cambiarNombreDuplicado() {
        given(servicio.cambiarNombre(eq(ID), any(CambioNombreClubRequest.class))).willThrow(ReglaNegocioException
                .conflicto(ErroresTecnicos.DUPLICADO, "Ya existe un club llamado «Club Águilas» en la liga."));

        MvcTestResult resultado = postCambio(CUERPO_CAMBIO);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.DUPLICADO);
    }

    @Test
    @DisplayName("RN-041: no existe PATCH de club; el nombre cambia con POST /clubes/{id}/cambio-nombre (405)")
    void sinPatch() {
        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO_CREAR).exchange();

        assertThat(resultado).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/clubes")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroClubes.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/clubes/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/clubes")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearClubRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/clubes/{id}/cambio-nombre")
    Stream<DynamicTest> seguridadCambiarNombre() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/cambio-nombre", ID)
                .cuerpoJson(CUERPO_CAMBIO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.cambiarNombre(eq(ID), any(CambioNombreClubRequest.class)))
                        .willThrow(error))
                .pruebas();
    }
}

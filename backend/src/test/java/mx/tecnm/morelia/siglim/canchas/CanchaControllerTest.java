package mx.tecnm.morelia.siglim.canchas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.canchas.PartidoEnCanchaDto.EquipoDto;
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

/** Contrato HTTP de canchas (convenciones de API §6.6) con la cadena de seguridad real. */
@WebMvcTest(controllers = CanchaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class CanchaControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String BASE = "/api/v1/canchas";
    private static final String DESDE_TEXTO = "2030-03-10T10:00:00-06:00";
    private static final OffsetDateTime DESDE = OffsetDateTime.parse(DESDE_TEXTO);
    private static final Intervalo PERIODO = new Intervalo(DESDE, DESDE.plusHours(2));
    private static final CanchaDto CANCHA = new CanchaDto(ID, "Cancha 1", UbicacionCancha.UNIDAD_DEPORTIVA, null, null,
            PropietarioCancha.LIGA, null, null, null, true, DESDE);
    private static final PartidoEnCanchaDto PARTIDO = new PartidoEnCanchaDto(
            UUID.fromString("44444444-4444-4444-8444-444444444444"), "PROGRAMADO", PERIODO,
            new EquipoDto(UUID.randomUUID(), "Halcones"), new EquipoDto(UUID.randomUUID(), "Toros"));
    private static final String RANGO = "?desde=2030-03-10T00:00:00-06:00&hasta=2030-03-17T00:00:00-06:00";
    private static final String CUERPO_CREAR =
            "{\"nombre\":\"Cancha 1\",\"ubicacion\":\"UNIDAD_DEPORTIVA\",\"propietario\":\"LIGA\"}";
    private static final String CUERPO_BLOQUEO = """
            {"tipo":"BLOQUEO","periodo":{"desde":"2030-03-10T10:00:00-06:00","hasta":"2030-03-10T12:00:00-06:00"},
             "motivo":"Riego de la cancha programado"}
            """;
    private static final String CUERPO_MANTENIMIENTO = """
            {"tipo":"MANTENIMIENTO","periodo":{"desde":"2030-03-10T10:00:00-06:00","hasta":"2030-03-10T12:00:00-06:00"},
             "motivo":"Resiembra del pasto en el área chica","confirmarPartidosAfectados":true}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CanchaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-027: POST responde 201 con Location y omite los campos vacíos")
    void crear() {
        given(servicio.crear(any(CrearCanchaRequest.class))).willReturn(CANCHA);

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        assertThat(resultado).bodyJson().extractingPath("$.id").isEqualTo(ID.toString());
        assertThat(resultado).bodyJson().doesNotHavePath("$.club");
        assertThat(resultado).bodyJson().doesNotHavePath("$.direccion");
    }

    @Test
    @DisplayName("RF-027: POST sin nombre responde 400 VALIDACION y no llega al servicio")
    void crearSinNombre() {
        MvcTestResult resultado = post(BASE, "{\"ubicacion\":\"EXTERNA\",\"propietario\":\"LIGA\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("nombre");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-027: GET lista con la envoltura de paginación y pasa filtros y orden")
    void listar() {
        given(servicio.listar(any(FiltroCanchas.class))).willReturn(Pagina.de(List.of(CANCHA), 1, 10, 11));

        MvcTestResult resultado = mvc.get().uri(BASE + "?activa=true&ubicacion=EXTERNA&q=mor&pagina=1&tamano=10")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].nombre").isEqualTo("Cancha 1");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(11);
        assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
        ArgumentCaptor<FiltroCanchas> filtro = ArgumentCaptor.forClass(FiltroCanchas.class);
        Mockito.verify(servicio).listar(filtro.capture());
        assertThat(filtro.getValue()).isEqualTo(new FiltroCanchas(true, UbicacionCancha.EXTERNA, null, null, "mor", 1,
                10, "nombre,asc"));
    }

    @Test
    @DisplayName("RF-028: GET pasa el filtro sedeId y la cancha incluye su sede")
    void listarPorSede() {
        UUID sede = UUID.fromString("55555555-5555-4555-8555-555555555555");
        CanchaDto conSede = new CanchaDto(ID, "Cancha 1", UbicacionCancha.UNIDAD_DEPORTIVA,
                new CanchaDto.SedeResumenDto(sede, "Unidad Norte"), null, PropietarioCancha.LIGA, null, null, null,
                true, DESDE);
        given(servicio.listar(any(FiltroCanchas.class))).willReturn(Pagina.de(List.of(conSede), 0, 20, 1));

        MvcTestResult resultado = mvc.get().uri(BASE + "?sedeId=" + sede)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].sede.id").isEqualTo(sede.toString());
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].sede.nombre").isEqualTo("Unidad Norte");
        ArgumentCaptor<FiltroCanchas> filtro = ArgumentCaptor.forClass(FiltroCanchas.class);
        Mockito.verify(servicio).listar(filtro.capture());
        assertThat(filtro.getValue().sedeId()).isEqualTo(sede);
    }

    @Test
    @DisplayName("RF-028: una cancha sin sede omite el campo sede")
    void canchaSinSede() {
        given(servicio.consultar(ID)).willReturn(CANCHA);

        MvcTestResult resultado = mvc.get().uri(BASE + "/" + ID).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().doesNotHavePath("$.sede");
    }

    @Test
    @DisplayName("RF-030: el periodo entra y sale como {desde, hasta}")
    void formatoDelIntervalo() {
        given(servicio.registrarDisponibilidad(eq(ID), any(RegistrarDisponibilidadRequest.class)))
                .willReturn(new RegistroDisponibilidadDto(new DisponibilidadDto(UUID.randomUUID(),
                        TipoDisponibilidad.BLOQUEO, PERIODO, "Riego de la cancha programado", DESDE), List.of(PARTIDO)));

        MvcTestResult resultado = post(BASE + "/" + ID + "/disponibilidad", CUERPO_BLOQUEO);

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).bodyJson().extractingPath("$.disponibilidad.periodo.desde").isEqualTo(DESDE_TEXTO);
        assertThat(resultado).bodyJson().extractingPath("$.partidosAfectados[0].periodo.hasta")
                .isEqualTo("2030-03-10T12:00:00-06:00");
        ArgumentCaptor<RegistrarDisponibilidadRequest> peticion =
                ArgumentCaptor.forClass(RegistrarDisponibilidadRequest.class);
        Mockito.verify(servicio).registrarDisponibilidad(eq(ID), peticion.capture());
        assertThat(peticion.getValue().periodo().desde().toInstant()).isEqualTo(DESDE.toInstant());
    }

    @Test
    @DisplayName("RF-031: POST con tipo MANTENIMIENTO responde 201 y llega al servicio como MANTENIMIENTO")
    void registrarMantenimiento() {
        given(servicio.registrarDisponibilidad(eq(ID), any(RegistrarDisponibilidadRequest.class)))
                .willReturn(new RegistroDisponibilidadDto(new DisponibilidadDto(UUID.randomUUID(),
                        TipoDisponibilidad.MANTENIMIENTO, PERIODO, "Resiembra del pasto en el área chica", DESDE),
                        List.of()));

        MvcTestResult resultado = post(BASE + "/" + ID + "/disponibilidad", CUERPO_MANTENIMIENTO);

        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        assertThat(resultado).bodyJson().extractingPath("$.disponibilidad.tipo").isEqualTo("MANTENIMIENTO");
        ArgumentCaptor<RegistrarDisponibilidadRequest> peticion =
                ArgumentCaptor.forClass(RegistrarDisponibilidadRequest.class);
        Mockito.verify(servicio).registrarDisponibilidad(eq(ID), peticion.capture());
        assertThat(peticion.getValue().tipo()).isEqualTo(TipoDisponibilidad.MANTENIMIENTO);
        assertThat(peticion.getValue().motivo()).isEqualTo("Resiembra del pasto en el área chica");
        assertThat(peticion.getValue().confirmarPartidosAfectados()).isTrue();
    }

    @Test
    @DisplayName("RF-030: POST sin extremo del periodo responde 400 VALIDACION")
    void periodoSinHasta() {
        MvcTestResult resultado = post(BASE + "/" + ID + "/disponibilidad",
                "{\"tipo\":\"DISPONIBLE\",\"periodo\":{\"desde\":\"" + DESDE_TEXTO + "\"}}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("periodo.hasta");
    }

    @Test
    @DisplayName("RF-030: el 409 aceptable lleva aceptable y partidosAfectados")
    void conflictoAceptable() {
        given(servicio.registrarDisponibilidad(eq(ID), any(RegistrarDisponibilidadRequest.class)))
                .willThrow(ReglaNegocioException.conflicto("RF-030", "El bloqueo afecta a 1 partido(s).")
                        .con("aceptable", true).con("partidosAfectados", List.of(PARTIDO)));

        MvcTestResult resultado = post(BASE + "/" + ID + "/disponibilidad", CUERPO_BLOQUEO);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-030");
        assertThat(resultado).bodyJson().extractingPath("$.aceptable").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.partidosAfectados[0].id").isEqualTo(PARTIDO.id().toString());
        assertThat(resultado).bodyJson().extractingPath("$.partidosAfectados[0].equipoLocal.nombre")
                .isEqualTo("Halcones");
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/canchas")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroCanchas.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/canchas/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/canchas")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearCanchaRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/canchas/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", ID)
                .cuerpoJson("{\"nombre\":\"Cancha 2\"}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID), any(ActualizarCanchaRequest.class)))
                        .willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/canchas/{id}/disponibilidad")
    Stream<DynamicTest> seguridadListarDisponibilidad() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}/disponibilidad" + RANGO, ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listarDisponibilidad(eq(ID), any(), any())).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/canchas/{id}/disponibilidad")
    Stream<DynamicTest> seguridadRegistrarDisponibilidad() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/disponibilidad", ID)
                .cuerpoJson(CUERPO_BLOQUEO)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.registrarDisponibilidad(eq(ID),
                        any(RegistrarDisponibilidadRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/canchas/{id}/ocupacion")
    Stream<DynamicTest> seguridadOcupacion() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}/ocupacion" + RANGO, ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultarOcupacion(eq(ID), any(), any())).willThrow(error))
                .pruebas();
    }
}

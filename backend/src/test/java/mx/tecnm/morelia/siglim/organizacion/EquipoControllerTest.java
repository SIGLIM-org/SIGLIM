package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.EquipoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.organizacion.EquipoDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
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

/** Contrato HTTP de equipos (convenciones de API §6.3) con la cadena de seguridad real. */
@WebMvcTest(controllers = EquipoController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class EquipoControllerTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TEMPORADA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CATEGORIA = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final String BASE = "/api/v1/equipos";
    private static final String CUERPO_CREAR =
            "{\"clubId\":\"" + CLUB + "\",\"grupoId\":\"" + GRUPO + "\",\"nombre\":\"Halcones\"}";
    private static final String CUERPO_BAJA = "{\"motivo\":\"El club retiró al equipo\"}";
    private static final EquipoDto EQUIPO = equipo(EstadoEquipo.ACTIVO, null, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private EquipoService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private static EquipoDto equipo(EstadoEquipo estado, LocalDate bajaEn, String motivo) {
        return new EquipoDto(ID, "Halcones", estado, new ReferenciaDto(CLUB, "Club Halcones"),
                new ReferenciaDto(GRUPO, "A"), new ReferenciaDto(CATEGORIA, "Libre"),
                new TemporadaResumenDto(TEMPORADA, "Temporada 2030", EstadoTemporada.ACTIVA),
                OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), bajaEn, motivo);
    }

    private MvcTestResult post(String ruta, String cuerpo) {
        return mvc.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).exchange();
    }

    @Test
    @DisplayName("RF-009: GET lista con referencias anidadas y pasa los filtros")
    void listar() {
        FiltroEquipos filtro = new FiltroEquipos(TEMPORADA, GRUPO, CLUB, EstadoEquipo.ACTIVO, 0, 20);
        given(servicio.listar(filtro)).willReturn(Pagina.de(List.of(EQUIPO), 0, 20, 1));

        MvcTestResult resultado = mvc.get()
                .uri(BASE + "?temporadaId=" + TEMPORADA + "&grupoId=" + GRUPO + "&clubId=" + CLUB + "&estado=ACTIVO")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].club.nombre").isEqualTo("Club Halcones");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].grupo.id").isEqualTo(GRUPO.toString());
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].categoria.nombre").isEqualTo("Libre");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].temporada.estado").isEqualTo("ACTIVA");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].bajaEn");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].motivoBaja");
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(1);
        Mockito.verify(servicio).listar(filtro);
    }

    @Test
    @DisplayName("RF-009: un filtro con UUID mal formado responde 400 VALIDACION")
    void listarConFiltroInvalido() {
        MvcTestResult resultado = mvc.get().uri(BASE + "?clubId=no-es-uuid")
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-009: POST responde 201 con Location")
    void crear() {
        given(servicio.crear(any(CrearEquipoRequest.class))).willReturn(EQUIPO);

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasHeader(HttpHeaders.LOCATION, BASE + "/" + ID);
        ArgumentCaptor<CrearEquipoRequest> peticion = ArgumentCaptor.forClass(CrearEquipoRequest.class);
        Mockito.verify(servicio).crear(peticion.capture());
        assertThat(peticion.getValue().clubId()).isEqualTo(CLUB);
        assertThat(peticion.getValue().grupoId()).isEqualTo(GRUPO);
    }

    @Test
    @DisplayName("RF-009: POST sin nombre responde 400 VALIDACION y no llega al servicio")
    void crearSinNombre() {
        MvcTestResult resultado = post(BASE, "{\"clubId\":\"" + CLUB + "\",\"grupoId\":\"" + GRUPO + "\"}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        assertThat(resultado).bodyJson().extractingPath("$.errores[0].campo").isEqualTo("nombre");
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RF-003: crear en una temporada cerrada responde 409 RF-003")
    void crearEnTemporadaCerrada() {
        given(servicio.crear(any(CrearEquipoRequest.class))).willThrow(ReglaNegocioException.conflicto("RF-003",
                "La temporada «Temporada 2029» está cerrada: sus equipos son de solo lectura."));

        MvcTestResult resultado = post(BASE, CUERPO_CREAR);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-003");
    }

    @Test
    @DisplayName("RN-007: el cambio de grupo a otra temporada responde 422 RN-007")
    void actualizarGrupoDeOtraTemporada() {
        given(servicio.actualizar(eq(ID), any(ActualizarEquipoRequest.class))).willThrow(
                ReglaNegocioException.noProcesable("RN-007", "El grupo «B» es de otra temporada."));

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"grupoId\":\"" + GRUPO + "\"}").exchange();

        assertThat(resultado).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-007");
    }

    @Test
    @DisplayName("RF-009: PATCH pasa solo lo indicado")
    void actualizar() {
        given(servicio.actualizar(eq(ID), any(ActualizarEquipoRequest.class))).willReturn(EQUIPO);

        MvcTestResult resultado = mvc.patch().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Halcones B\"}").exchange();

        assertThat(resultado).hasStatusOk();
        ArgumentCaptor<ActualizarEquipoRequest> peticion = ArgumentCaptor.forClass(ActualizarEquipoRequest.class);
        Mockito.verify(servicio).actualizar(eq(ID), peticion.capture());
        assertThat(peticion.getValue().nombre()).isEqualTo("Halcones B");
        assertThat(peticion.getValue().grupoId()).isNull();
    }

    @Test
    @DisplayName("RF-009: la baja responde 200 con el equipo en BAJA, su fecha y su motivo")
    void darDeBaja() {
        given(servicio.darDeBaja(eq(ID), any(BajaEquipoRequest.class)))
                .willReturn(equipo(EstadoEquipo.BAJA, LocalDate.parse("2030-11-02"), "El club retiró al equipo"));

        MvcTestResult resultado = post(BASE + "/" + ID + "/baja", CUERPO_BAJA);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.estado").isEqualTo("BAJA");
        assertThat(resultado).bodyJson().extractingPath("$.bajaEn").isEqualTo("2030-11-02");
        assertThat(resultado).bodyJson().extractingPath("$.motivoBaja").isEqualTo("El club retiró al equipo");
        ArgumentCaptor<BajaEquipoRequest> peticion = ArgumentCaptor.forClass(BajaEquipoRequest.class);
        Mockito.verify(servicio).darDeBaja(eq(ID), peticion.capture());
        assertThat(peticion.getValue().motivo()).isEqualTo("El club retiró al equipo");
    }

    @Test
    @DisplayName("RN-021: el 400 por falta de motivo conserva el código")
    void darDeBajaSinMotivo() {
        given(servicio.darDeBaja(eq(ID), any(BajaEquipoRequest.class)))
                .willThrow(ReglaNegocioException.invalida("RN-021", "Escribe un motivo de al menos 10 caracteres."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/baja", "{}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RN-021");
    }

    @Test
    @DisplayName("RF-009: una baja repetida responde 409 RF-009")
    void darDeBajaRepetida() {
        given(servicio.darDeBaja(eq(ID), any(BajaEquipoRequest.class)))
                .willThrow(ReglaNegocioException.conflicto("RF-009", "El equipo «Halcones» está dado de baja."));

        MvcTestResult resultado = post(BASE + "/" + ID + "/baja", CUERPO_BAJA);

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RF-009");
    }

    @Test
    @DisplayName("RN-005: no existe DELETE de equipos (405)")
    void sinDelete() {
        MvcTestResult resultado = mvc.delete().uri(BASE + "/" + ID)
                .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

        assertThat(resultado).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/equipos")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroEquipos.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/equipos/{id}")
    Stream<DynamicTest> seguridadConsultar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/{id}", ID)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/equipos")
    Stream<DynamicTest> seguridadCrear() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE)
                .cuerpoJson(CUERPO_CREAR)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.crear(any(CrearEquipoRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de PATCH /api/v1/equipos/{id}")
    Stream<DynamicTest> seguridadActualizar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.PATCH, BASE + "/{id}", ID)
                .cuerpoJson("{\"nombre\":\"Halcones B\"}")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.actualizar(eq(ID),
                        any(ActualizarEquipoRequest.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de POST /api/v1/equipos/{id}/baja")
    Stream<DynamicTest> seguridadDarDeBaja() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.POST, BASE + "/{id}/baja", ID)
                .cuerpoJson(CUERPO_BAJA)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.darDeBaja(eq(ID),
                        any(BajaEquipoRequest.class))).willThrow(error))
                .pruebas();
    }
}

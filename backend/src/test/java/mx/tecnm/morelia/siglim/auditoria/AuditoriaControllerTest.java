package mx.tecnm.morelia.siglim.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.seguridad.ArnesAutorizacionEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import org.springframework.web.bind.annotation.GetMapping;
import tools.jackson.databind.json.JsonMapper;

/** Contrato HTTP de la consulta de auditoría (convenciones de API §6.1) con la cadena de seguridad real. */
@WebMvcTest(controllers = AuditoriaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class AuditoriaControllerTest {

    private static final String BASE = "/api/v1/auditoria";
    private static final UUID PERFIL = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String DESDE_TEXTO = "2030-03-10T00:00:00-06:00";
    private static final String HASTA_TEXTO = "2030-03-17T00:00:00-06:00";
    private static final OffsetDateTime OCURRIDO = OffsetDateTime.parse("2030-03-10T10:00:00-06:00");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ConsultaAuditoriaService servicio;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult get(String ruta) {
        return mvc.get().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();
    }

    private MvcTestResult enviar(String metodo, String ruta) {
        return mvc.method(HttpMethod.valueOf(metodo)).uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();
    }

    private FiltroAuditoria filtroRecibido() {
        ArgumentCaptor<FiltroAuditoria> filtro = ArgumentCaptor.forClass(FiltroAuditoria.class);
        Mockito.verify(servicio).listar(filtro.capture());
        return filtro.getValue();
    }

    private void listaVacia() {
        given(servicio.listar(any(FiltroAuditoria.class))).willReturn(Pagina.de(List.of(), 0, 50, 0));
    }

    private static void sinCache(MvcTestResult resultado) {
        assertThat(resultado.getResponse().getHeaders(HttpHeaders.CACHE_CONTROL)).anySatisfy(
                valor -> assertThat(valor).contains("no-store"));
    }

    @Test
    @DisplayName("RF-134: sin parámetros pide 50 filas de la página 0, del más reciente al más antiguo, sin caché")
    void valoresPorDefecto() {
        given(servicio.listar(any(FiltroAuditoria.class))).willReturn(Pagina.de(List.of(), 0, 50, 51));

        MvcTestResult resultado = get(BASE);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido").asArray().isEmpty();
        assertThat(resultado).bodyJson().extractingPath("$.pagina").isEqualTo(0);
        assertThat(resultado).bodyJson().extractingPath("$.tamano").isEqualTo(50);
        assertThat(resultado).bodyJson().extractingPath("$.totalElementos").isEqualTo(51);
        assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(2);
        sinCache(resultado);
        assertThat(filtroRecibido()).isEqualTo(
                new FiltroAuditoria(null, List.of(), List.of(), null, null, 0, 50, "ocurridoEn,desc"));
    }

    @Test
    @DisplayName("RF-134: módulos y acciones separados por coma llegan como listas; perfil y fechas, ya convertidos")
    void filtros() {
        listaVacia();

        MvcTestResult resultado = get(BASE + "?perfilId=" + PERFIL + "&modulo=M-08,M-06"
                + "&accion=APROBAR_CEDULA,EDITAR_CANCHA&desde=" + DESDE_TEXTO + "&hasta=" + HASTA_TEXTO
                + "&pagina=2&tamano=10&orden=ocurridoEn,asc");

        assertThat(resultado).hasStatusOk();
        FiltroAuditoria filtro = filtroRecibido();
        assertThat(filtro.perfilId()).isEqualTo(PERFIL);
        assertThat(filtro.modulos()).containsExactly("M-08", "M-06");
        assertThat(filtro.acciones()).containsExactly("APROBAR_CEDULA", "EDITAR_CANCHA");
        assertThat(filtro.desde().toInstant()).isEqualTo(OffsetDateTime.parse(DESDE_TEXTO).toInstant());
        assertThat(filtro.hasta().toInstant()).isEqualTo(OffsetDateTime.parse(HASTA_TEXTO).toInstant());
        assertThat(filtro.pagina()).isEqualTo(2);
        assertThat(filtro.tamano()).isEqualTo(10);
        assertThat(filtro.orden()).isEqualTo("ocurridoEn,asc");
    }

    @Test
    @DisplayName("RF-134: una fila lleva el perfil anidado, el detalle como objeto y el motivo; los campos vacíos se omiten")
    void formaDeUnaFila() {
        RegistroAuditoriaDto conPerfil = new RegistroAuditoriaDto(7L, OCURRIDO, new PerfilResumenDto(PERFIL, "Ana López"),
                "M-08", "APROBAR_CEDULA", "cedulas", "abc", "Motivo completo",
                JsonMapper.builder().build().readTree("{\"estadoNuevo\":\"APROBADA\",\"goles\":3}"));
        RegistroAuditoriaDto sinPerfil = new RegistroAuditoriaDto(6L, OCURRIDO, null, "M-01", "ACCION_SISTEMA",
                "perfiles", null, null, null);
        given(servicio.listar(any(FiltroAuditoria.class))).willReturn(Pagina.de(List.of(conPerfil, sinPerfil), 0, 50, 2));

        MvcTestResult resultado = get(BASE);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].id").isEqualTo(7);
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].ocurridoEn")
                .isEqualTo("2030-03-10T10:00:00-06:00");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].perfil.id").isEqualTo(PERFIL.toString());
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].perfil.nombreMostrado").isEqualTo("Ana López");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[0].perfil.correo");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].modulo").isEqualTo("M-08");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].accion").isEqualTo("APROBAR_CEDULA");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].entidad").isEqualTo("cedulas");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].entidadId").isEqualTo("abc");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].motivo").isEqualTo("Motivo completo");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].detalle.estadoNuevo").isEqualTo("APROBADA");
        assertThat(resultado).bodyJson().extractingPath("$.contenido[0].detalle.goles").isEqualTo(3);
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[1].perfil");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[1].entidadId");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[1].motivo");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido[1].detalle");
    }

    @Test
    @DisplayName("RF-134: las opciones de los filtros responden sin paginación y sin caché")
    void opcionesFiltro() {
        given(servicio.opcionesFiltro()).willReturn(new OpcionesFiltroAuditoriaDto(
                List.of(new PerfilResumenDto(PERFIL, "Ana López")), List.of("M-08"), List.of("APROBAR_CEDULA")));

        MvcTestResult resultado = get(BASE + "/opciones-filtro");

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.perfiles[0].nombreMostrado").isEqualTo("Ana López");
        assertThat(resultado).bodyJson().doesNotHavePath("$.perfiles[0].correo");
        assertThat(resultado).bodyJson().extractingPath("$.modulos[0]").isEqualTo("M-08");
        assertThat(resultado).bodyJson().extractingPath("$.acciones[0]").isEqualTo("APROBAR_CEDULA");
        assertThat(resultado).bodyJson().doesNotHavePath("$.contenido");
        sinCache(resultado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"perfilId=abc", "desde=ayer", "hasta=ayer"})
    @DisplayName("VALIDACION: un perfil o una fecha mal formados responden 400 y no llegan al servicio")
    void parametroMalFormado(String parametro) {
        MvcTestResult resultado = get(BASE + "?" + parametro);

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.VALIDACION);
        Mockito.verifyNoInteractions(servicio);
    }

    // ── 401, 403 y 404 de cada endpoint (convenciones de API §10) ───────────────────────────────

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/auditoria")
    Stream<DynamicTest> seguridadListar() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE)
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.listar(any(FiltroAuditoria.class))).willThrow(error))
                .pruebas();
    }

    @TestFactory
    @DisplayName("RNF-003: seguridad de GET /api/v1/auditoria/opciones-filtro")
    Stream<DynamicTest> seguridadOpcionesFiltro() {
        return ArnesAutorizacionEndpoint.para(mvc)
                .peticion(HttpMethod.GET, BASE + "/opciones-filtro")
                .dobles(servicio)
                .simularDenegacion(error -> given(servicio.opcionesFiltro()).willThrow(error))
                .pruebas();
    }

    // ── Solo lectura ──────────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    @DisplayName("RNF-015: escribir en /api/v1/auditoria responde 405 con Allow: GET y no toca el servicio")
    void metodoNoPermitido(String metodo) {
        MvcTestResult resultado = enviar(metodo, BASE);

        assertThat(resultado).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.METODO_NO_PERMITIDO);
        assertThat(resultado.getResponse().getHeader(HttpHeaders.ALLOW)).contains("GET");
        Mockito.verifyNoInteractions(servicio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
    @DisplayName("RNF-015: /api/v1/auditoria/{id} no existe: responde 404 NO_ENCONTRADO y no toca el servicio")
    void rutaDeRegistroInexistente(String metodo) {
        MvcTestResult resultado = enviar(metodo, BASE + "/1");

        assertThat(resultado).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_ENCONTRADO);
        Mockito.verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("RNF-015: el controlador solo declara métodos GET")
    void controladorSoloDeLectura() {
        assertThat(Arrays.stream(AuditoriaController.class.getDeclaredMethods()).filter(metodo -> !metodo.isSynthetic()))
                .isNotEmpty()
                .allSatisfy(metodo -> assertThat(metodo.isAnnotationPresent(GetMapping.class))
                        .as(metodo.getName()).isTrue());
    }

    @Test
    @DisplayName("RNF-015: el servicio de consulta solo expone listar y opcionesFiltro")
    void servicioSoloDeLectura() {
        assertThat(Arrays.stream(ConsultaAuditoriaService.class.getDeclaredMethods())
                .filter(metodo -> Modifier.isPublic(metodo.getModifiers()) && !metodo.isSynthetic())
                .map(Method::getName))
                .containsExactlyInAnyOrder("listar", "opcionesFiltro");
    }
}

package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import org.junit.jupiter.api.DynamicTest;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Arnés de pruebas {@code 401/403/404} reutilizable por endpoint (H-02.3, RNF-003).
 *
 * <p>Convenciones de API §10: cada endpoint tiene, como mínimo, pruebas de caso feliz, {@code 401},
 * {@code 403} (sin permiso) y {@code 404} (fuera de ámbito). El caso feliz y la regla principal son
 * propios de cada historia; este arnés genera las <b>tres pruebas de seguridad</b>, iguales para
 * todos, para que ningún endpoint quede sin ellas y todos verifiquen lo mismo.
 *
 * <h2>Qué verifica</h2>
 * <ol>
 *   <li><b>401 sin token y con token inválido</b> (ADR-0003): {@code NO_AUTENTICADO} en RFC 9457, con
 *       {@code WWW-Authenticate: Bearer…} y <b>sin llegar al servicio</b>: la cadena de seguridad lo
 *       corta antes del controlador.</li>
 *   <li><b>403 sin el permiso</b> (VAL-015): el servicio lanza la denegación de
 *       {@link Autorizacion#sinPermiso()} y el endpoint la propaga como {@code 403 VAL-015}.</li>
 *   <li><b>404 fuera de ámbito</b> (VAL-024): el servicio lanza {@link Autorizacion#fueraDeAmbito}
 *       y la respuesta es {@code 404 NO_ENCONTRADO}, <b>idéntica</b> a la de un recurso inexistente y
 *       sin mencionar el permiso.</li>
 * </ol>
 *
 * <h2>Por qué se simula la denegación en el servicio</h2>
 * En una prueba de controlador ({@code @WebMvcTest}) el servicio es un doble ({@code @MockitoBean}),
 * así que {@code autorizacion.exigir(...)} no corre. Lo que el arnés comprueba es la mitad HTTP del
 * contrato: que el endpoint esté detrás de la cadena de seguridad y que <b>no trague ni transforme</b>
 * la denegación (un {@code try/catch} que responda {@code 200}, un {@code Optional.empty()} que se
 * vuelva lista vacía, etc.). La otra mitad, que el servicio llame a {@code exigir} con el permiso y el
 * objetivo correctos, la cubre la prueba unitaria del servicio. Se usan las fábricas de
 * {@link Autorizacion} para que la excepción simulada sea exactamente la de producción.
 *
 * <h2>Uso</h2>
 * <pre>{@code
 * @WebMvcTest(controllers = TemporadaController.class, properties = { ... })
 * @Import(SeguridadPrueba.class)
 * class TemporadaControllerTest {
 *
 *     @Autowired MockMvcTester mvc;
 *     @MockitoBean TemporadaService servicio;
 *
 *     @TestFactory
 *     @DisplayName("RNF-003: seguridad de GET /api/v1/temporadas/{id}")
 *     Stream<DynamicTest> seguridadConsultar() {
 *         return ArnesAutorizacionEndpoint.para(mvc)
 *                 .peticion(HttpMethod.GET, "/api/v1/temporadas/{id}", ID)
 *                 .dobles(servicio)
 *                 .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
 *                 .pruebas();
 *     }
 * }
 * }</pre>
 *
 * <p>Para un método {@code void} del servicio: {@code error -> willThrow(error).given(servicio).cerrar(ID)}.
 * Para un endpoint que solo exige sesión (como {@code GET /api/v1/yo}): {@code .soloSesion()} genera
 * únicamente las pruebas {@code 401}.
 *
 * <p><b>Dobles.</b> Antes de cada escenario el arnés reinicia los dobles indicados en
 * {@link Constructor#dobles} ({@link Mockito#reset}); así cada escenario parte limpio y volver a
 * simular una excepción no la dispara durante la simulación. Los <i>stubs</i> del caso feliz van en
 * pruebas aparte.
 *
 * <p>Refs: H-02.3, H-02, RNF-003, VAL-015, VAL-024, ADR-0003, ADR-0007, convenciones de API §3.1 y §10
 */
public final class ArnesAutorizacionEndpoint {

    /**
     * Permiso ficticio con el que se simula la denegación por ámbito. El texto de la excepción lo
     * incluye; si aparece en la respuesta, el endpoint está filtrando el detalle de la bitácora.
     */
    static final String PERMISO_CENTINELA = "arnes.recurso-centinela:CONSULTAR";

    private ArnesAutorizacionEndpoint() {
    }

    /** Empieza a describir el endpoint a probar con el {@link MockMvcTester} de la prueba. */
    public static Constructor para(MockMvcTester mvc) {
        return new Constructor(Objects.requireNonNull(mvc, "mvc"));
    }

    /** Descripción del endpoint; {@link #pruebas()} genera las pruebas dinámicas. */
    public static final class Constructor {

        private final MockMvcTester mvc;
        private HttpMethod metodo;
        private String plantillaUri;
        private Object[] variablesUri = new Object[0];
        private String cuerpoJson;
        private MockMultipartFile archivo;
        private final Map<String, String> camposMultipart = new LinkedHashMap<>();
        private Object[] dobles = new Object[0];
        private Consumer<RuntimeException> simuladorDenegacion;
        private boolean soloSesion;

        private Constructor(MockMvcTester mvc) {
            this.mvc = mvc;
        }

        /**
         * Método y ruta del endpoint, con las variables de la plantilla.
         *
         * @param metodo       verbo HTTP
         * @param plantillaUri ruta con variables, por ejemplo {@code /api/v1/temporadas/{id}}
         * @param variables    valores de las variables, en orden
         */
        public Constructor peticion(HttpMethod metodo, String plantillaUri, Object... variables) {
            this.metodo = Objects.requireNonNull(metodo, "metodo");
            this.plantillaUri = Objects.requireNonNull(plantillaUri, "plantillaUri");
            this.variablesUri = variables.clone();
            return this;
        }

        /**
         * Cuerpo JSON <b>válido</b> para endpoints {@code POST}/{@code PUT}/{@code PATCH}: debe
         * superar {@code @Valid}, porque si no la petición se rechaza con {@code 400} antes de llegar
         * al servicio y el {@code 403}/{@code 404} no se ejercitaría.
         */
        public Constructor cuerpoJson(String json) {
            this.cuerpoJson = Objects.requireNonNull(json, "json");
            return this;
        }

        /**
         * Cuerpo {@code multipart/form-data} <b>válido</b> (H-31.1): el archivo y los campos de texto.
         * Como con {@link #cuerpoJson}, debe superar {@code @Valid} para que se ejercite el
         * {@code 403}/{@code 404} del servicio.
         */
        public Constructor cuerpoMultipart(MockMultipartFile archivo, Map<String, String> campos) {
            this.archivo = Objects.requireNonNull(archivo, "archivo");
            this.camposMultipart.putAll(campos);
            return this;
        }

        /**
         * Dobles ({@code @MockitoBean}) de los que depende el controlador. Se reinician antes de cada
         * escenario y, en los {@code 401}, se verifica que nadie los haya tocado.
         */
        public Constructor dobles(Object... dobles) {
            this.dobles = dobles.clone();
            return this;
        }

        /**
         * Cómo hacer que el servicio deniegue: recibe la excepción que lanzaría
         * {@code autorizacion.exigir(...)} y la programa en el doble, por ejemplo
         * {@code error -> given(servicio.consultar(ID)).willThrow(error)}.
         */
        public Constructor simularDenegacion(Consumer<RuntimeException> simulador) {
            this.simuladorDenegacion = Objects.requireNonNull(simulador, "simulador");
            return this;
        }

        /**
         * El endpoint solo exige sesión y no evalúa ningún permiso (como {@code GET /api/v1/yo}):
         * solo se generan las pruebas {@code 401}.
         */
        public Constructor soloSesion() {
            this.soloSesion = true;
            return this;
        }

        /**
         * Pruebas dinámicas para devolver desde un método {@code @TestFactory}. Se ejecutan en
         * orden: primero las {@code 401} (sin simulación) y luego {@code 403} y {@code 404}.
         *
         * @throws IllegalStateException si falta la petición, o si el endpoint evalúa permisos y
         *                               faltan los dobles o la simulación de la denegación
         */
        public Stream<DynamicTest> pruebas() {
            validar();
            String nombre = metodo.name() + " " + plantillaUri;
            List<DynamicTest> pruebas = new ArrayList<>();
            pruebas.add(DynamicTest.dynamicTest(
                    "RNF-003: " + nombre + " sin token responde 401 NO_AUTENTICADO y no llega al servicio",
                    () -> verificarNoAutenticado(null)));
            pruebas.add(DynamicTest.dynamicTest(
                    "RNF-003: " + nombre + " con token de otra llave responde 401 NO_AUTENTICADO",
                    () -> verificarNoAutenticado(SeguridadPrueba.tokenFirmadoConLlaveAjena())));
            if (!soloSesion) {
                pruebas.add(DynamicTest.dynamicTest(
                        "VAL-015: " + nombre + " sin el permiso responde 403 VAL-015",
                        this::verificarSinPermiso));
                pruebas.add(DynamicTest.dynamicTest(
                        "VAL-024: " + nombre + " fuera de ámbito responde 404 NO_ENCONTRADO",
                        this::verificarFueraDeAmbito));
                pruebas.add(DynamicTest.dynamicTest(
                        "VAL-024: " + nombre + " fuera de ámbito es idéntico a un recurso inexistente",
                        this::verificarIndistinguible));
            }
            return pruebas.stream();
        }

        // ── Escenarios ─────────────────────────────────────────────────────────────────────────

        private void verificarNoAutenticado(String token) {
            reiniciarDobles();

            MvcTestResult resultado = enviar(token);

            assertThat(resultado)
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_AUTENTICADO);
            assertThat(resultado).bodyJson().extractingPath("$.status").isEqualTo(401);
            assertThat(resultado).bodyJson().extractingPath("$.instance")
                    .isEqualTo(resultado.getRequest().getRequestURI());
            assertThat(resultado.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .as("cabecera WWW-Authenticate del 401 (RFC 6750)")
                    .startsWith("Bearer");
            if (dobles.length > 0) {
                Mockito.verifyNoInteractions(dobles);
            }
        }

        private void verificarSinPermiso() {
            MvcTestResult resultado = enviarConDenegacion(Autorizacion.sinPermiso());

            assertThat(resultado)
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.SIN_PERMISO);
            assertThat(resultado).bodyJson().extractingPath("$.status").isEqualTo(403);
            assertThat(resultado).bodyJson().extractingPath("$.instance")
                    .isEqualTo(resultado.getRequest().getRequestURI());
        }

        private void verificarFueraDeAmbito() {
            MvcTestResult resultado = enviarConDenegacion(Autorizacion.fueraDeAmbito(PERMISO_CENTINELA));

            assertThat(resultado)
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(ErroresTecnicos.NO_ENCONTRADO);
            assertThat(resultado).bodyJson().extractingPath("$.status").isEqualTo(404);
            assertThat(cuerpo(resultado))
                    .as("la respuesta no debe revelar el permiso ni el motivo de la denegación")
                    .doesNotContain(PERMISO_CENTINELA)
                    .doesNotContain("ámbito del recurso");
        }

        private void verificarIndistinguible() {
            MvcTestResult fueraDeAmbito = enviarConDenegacion(Autorizacion.fueraDeAmbito(PERMISO_CENTINELA));
            MvcTestResult inexistente = enviarConDenegacion(
                    new RecursoNoEncontradoException("recurso inexistente simulado por el arnés"));

            assertThat(fueraDeAmbito.getResponse().getStatus())
                    .as("estado HTTP: fuera de ámbito contra inexistente")
                    .isEqualTo(inexistente.getResponse().getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(cuerpo(fueraDeAmbito))
                    .as("cuerpo RFC 9457: fuera de ámbito debe ser idéntico a inexistente (VAL-024)")
                    .isEqualTo(cuerpo(inexistente));
        }

        // ── Apoyo ──────────────────────────────────────────────────────────────────────────────

        private void validar() {
            if (metodo == null || plantillaUri == null) {
                throw new IllegalStateException("Falta peticion(metodo, plantillaUri, variables…)");
            }
            if (!soloSesion && simuladorDenegacion == null) {
                throw new IllegalStateException(
                        "Falta simularDenegacion(...); si el endpoint no evalúa permisos, usa soloSesion()");
            }
            if (!soloSesion && dobles.length == 0) {
                throw new IllegalStateException(
                        "Falta dobles(...): el arnés los reinicia entre escenarios para simular la denegación");
            }
        }

        private void reiniciarDobles() {
            if (dobles.length > 0) {
                Mockito.reset(dobles);
            }
        }

        /** Reinicia los dobles, programa la denegación y envía con un token válido. */
        private MvcTestResult enviarConDenegacion(RuntimeException denegacion) {
            reiniciarDobles();
            simuladorDenegacion.accept(denegacion);
            return enviar(SeguridadPrueba.tokenValido());
        }

        private MvcTestResult enviar(String token) {
            var peticion = mvc.method(metodo).uri(plantillaUri, variablesUri)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
            if (token != null) {
                peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            if (cuerpoJson != null) {
                peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpoJson);
            }
            if (archivo != null) {
                var multipart = peticion.multipart().file(archivo);
                camposMultipart.forEach(multipart::param);
                return multipart.exchange();
            }
            return peticion.exchange();
        }

        private static String cuerpo(MvcTestResult resultado) {
            try {
                return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException("UTF-8 siempre está disponible", e);
            }
        }
    }
}

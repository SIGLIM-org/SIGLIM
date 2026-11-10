package mx.tecnm.morelia.siglim.compartido.error;

import static org.assertj.core.api.Assertions.assertThat;

import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * El manejador global responde todo error en RFC 9457 con {@code codigo} (convenciones de API §3),
 * con la cadena de seguridad real y un token válido.
 */
@WebMvcTest(controllers = ErroresPruebaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class ManejadorGlobalErroresTest {

    private static final String BASE = "/api/v1/prueba/errores";

    @Autowired
    private MockMvcTester mvc;

    private final String autorizacion = "Bearer " + SeguridadPrueba.tokenValido();

    private MvcTestResult get(String ruta) {
        return mvc.get().uri(ruta).header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();
    }

    /** Lo que toda respuesta de error debe cumplir (convenciones §3). */
    private static void esProblema(MvcTestResult resultado, HttpStatus estado, String codigo) {
        assertThat(resultado)
                .hasStatus(estado)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.status").isEqualTo(estado.value());
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
        assertThat(resultado).bodyJson().extractingPath("$.type")
                .isEqualTo("https://siglim.mx/errores/" + codigo.toLowerCase().replace('_', '-'));
    }

    @Nested
    @DisplayName("Errores de los servicios")
    class ErroresDeServicio {

        @Test
        @DisplayName("VAL-004: ReglaNegocioException.conflicto responde 409 con el mensaje del servicio")
        void reglaDeNegocio() {
            MvcTestResult resultado = get(BASE + "/regla");

            esProblema(resultado, HttpStatus.CONFLICT, "VAL-004");
            assertThat(resultado).bodyJson().extractingPath("$.detail").asString().contains("Unidad Deportiva Morelos 2");
            assertThat(resultado).bodyJson().extractingPath("$.instance").isEqualTo(BASE + "/regla");
        }

        @Test
        @DisplayName("RF-030: las extensiones de ReglaNegocioException llegan al nivel raíz del problema")
        void reglaConExtensiones() {
            MvcTestResult resultado = get(BASE + "/regla-aceptable");

            esProblema(resultado, HttpStatus.CONFLICT, "RF-030");
            assertThat(resultado).bodyJson().extractingPath("$.aceptable").isEqualTo(true);
            assertThat(resultado).bodyJson().extractingPath("$.partidosAfectados[0].id").isEqualTo("partido-1");
        }

        @Test
        @DisplayName("VAL-024: RecursoNoEncontradoException responde 404 sin revelar qué se buscó")
        void noEncontrado() {
            MvcTestResult resultado = get(BASE + "/no-encontrado");

            esProblema(resultado, HttpStatus.NOT_FOUND, "NO_ENCONTRADO");
            assertThat(resultado).bodyJson().extractingPath("$.detail").asString().doesNotContain("temporada");
        }

        @Test
        @DisplayName("RNF-011: un error inesperado responde 500 con identificador y sin detalles técnicos")
        void errorInesperado() {
            MvcTestResult resultado = get(BASE + "/inesperado");

            esProblema(resultado, HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO");
            assertThat(resultado).bodyJson().extractingPath("$.instance").asString().startsWith("urn:siglim:error:");
            assertThat(resultado).body().asString()
                    .doesNotContain("SELECT").doesNotContain("perfiles").doesNotContain("IllegalStateException");
        }
    }

    @Nested
    @DisplayName("Criterio de terminado de T-02.4, de punta a punta")
    class ErroresDeBaseDeDatos {

        @Test
        @DisplayName("VAL-003: P0001 con HINT VAL-003 responde 409 con el mensaje del trigger")
        void p0001() {
            MvcTestResult resultado = get(BASE + "/bd/P0001?hint=VAL-003");

            esProblema(resultado, HttpStatus.CONFLICT, "VAL-003");
            assertThat(resultado).bodyJson().extractingPath("$.detail").isEqualTo("Mensaje del trigger.");
        }

        @Test
        @DisplayName("VAL-004: 23P01 responde 409 VAL-004")
        void exclusion() {
            MvcTestResult resultado = get(BASE + "/bd/23P01?restriccion=ex_partidos_cancha_ocupada");

            esProblema(resultado, HttpStatus.CONFLICT, "VAL-004");
            assertThat(resultado).bodyJson().extractingPath("$.detail")
                    .isEqualTo("La cancha ya está ocupada en ese horario.");
        }

        @Test
        @DisplayName("VAL-024: 42501 en una lectura (GET) responde 404")
        void privilegioEnLectura() {
            esProblema(get(BASE + "/bd/42501"), HttpStatus.NOT_FOUND, "NO_ENCONTRADO");
        }

        @Test
        @DisplayName("VAL-015: 42501 en una escritura (POST) responde 403")
        void privilegioEnEscritura() {
            MvcTestResult resultado = mvc.post().uri(BASE + "/bd/42501")
                    .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

            esProblema(resultado, HttpStatus.FORBIDDEN, "VAL-015");
        }
    }

    @Nested
    @DisplayName("Errores de Spring MVC con el mismo formato y en español")
    class ErroresDeSpring {

        @Test
        @DisplayName("Validación de @Valid: 400 VALIDACION con la lista errores por campo")
        void validacionDeCampos() {
            MvcTestResult resultado = mvc.post().uri(BASE + "/validacion")
                    .header(HttpHeaders.AUTHORIZATION, autorizacion)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"\",\"motivo\":\"corto\"}")
                    .exchange();

            esProblema(resultado, HttpStatus.BAD_REQUEST, "VALIDACION");
            assertThat(resultado).bodyJson().extractingPath("$.errores").asArray().hasSize(2);
            assertThat(resultado).bodyJson().extractingPath("$.errores[?(@.campo=='motivo')].mensaje")
                    .asArray().containsExactly("El motivo debe tener al menos 10 caracteres");
        }

        @Test
        @DisplayName("JSON mal formado: 400 VALIDACION con mensaje en español")
        void jsonMalFormado() {
            MvcTestResult resultado = mvc.post().uri(BASE + "/validacion")
                    .header(HttpHeaders.AUTHORIZATION, autorizacion)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ esto no es json")
                    .exchange();

            esProblema(resultado, HttpStatus.BAD_REQUEST, "VALIDACION");
            assertThat(resultado).bodyJson().extractingPath("$.detail").isEqualTo(ErroresTecnicos.DETALLE_VALIDACION);
        }

        @Test
        @DisplayName("Método no permitido: 405 METODO_NO_PERMITIDO conservando la cabecera Allow")
        void metodoNoPermitido() {
            MvcTestResult resultado = mvc.delete().uri(BASE + "/regla")
                    .header(HttpHeaders.AUTHORIZATION, autorizacion).exchange();

            esProblema(resultado, HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO");
            assertThat(resultado).containsHeader(HttpHeaders.ALLOW);
        }

        @Test
        @DisplayName("Ruta inexistente (con sesión): 404 NO_ENCONTRADO")
        void rutaInexistente() {
            esProblema(get("/api/v1/no-existe"), HttpStatus.NOT_FOUND, "NO_ENCONTRADO");
        }
    }

    @Nested
    @DisplayName("Carga de archivos (convenciones §3.1 y §7)")
    class ErroresDeArchivo {

        @Test
        @DisplayName("RF-108: archivo más grande que el límite responde 413 ARCHIVO_DEMASIADO_GRANDE")
        void archivoDemasiadoGrande() {
            MvcTestResult resultado = get(BASE + "/archivo/grande");

            esProblema(resultado, HttpStatus.CONTENT_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE");
            assertThat(resultado).bodyJson().extractingPath("$.detail")
                    .isEqualTo("El archivo supera el tamaño máximo de 5 MB.");
        }

        @Test
        @DisplayName("RF-108: firma binaria no permitida responde 415 TIPO_ARCHIVO_NO_PERMITIDO")
        void tipoNoPermitido() {
            MvcTestResult resultado = get(BASE + "/archivo/tipo");

            esProblema(resultado, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_ARCHIVO_NO_PERMITIDO");
            assertThat(resultado).bodyJson().extractingPath("$.detail")
                    .isEqualTo(ErroresTecnicos.DETALLE_TIPO_ARCHIVO);
        }

        @Test
        @DisplayName("RF-108: el límite multipart de Spring también responde 413 ARCHIVO_DEMASIADO_GRANDE")
        void limiteMultipartDeSpring() {
            esProblema(get(BASE + "/archivo/limite-spring"), HttpStatus.CONTENT_TOO_LARGE, "ARCHIVO_DEMASIADO_GRANDE");
        }

        @Test
        @DisplayName("ADR-0013: S3 no disponible responde 503 SERVICIO_NO_DISPONIBLE sin detalles técnicos")
        void s3NoDisponible() {
            MvcTestResult resultado = get(BASE + "/archivo/s3-caido");

            esProblema(resultado, HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE");
            assertThat(resultado).bodyJson().extractingPath("$.detail")
                    .isEqualTo(ErroresTecnicos.DETALLE_NO_DISPONIBLE);
        }
    }
}

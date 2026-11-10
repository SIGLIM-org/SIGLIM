package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.config.SeguridadPrueba;
import mx.tecnm.morelia.siglim.seguridad.EndpointsArnesPruebaController.CrearRecursoRequest;
import mx.tecnm.morelia.siglim.seguridad.EndpointsArnesPruebaController.ServicioArnesPrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Criterio de terminado de H-02.3: el arnés {@link ArnesAutorizacionEndpoint} genera las pruebas
 * {@code 401/403/404} de un endpoint, pasan en uno bien construido y <b>fallan</b> en uno que traga la
 * denegación o que no exige sesión.
 *
 * <p>Los {@code @TestFactory} de {@link EndpointsCorrectos} son además el ejemplo de uso para las
 * pruebas de controlador de las historias siguientes.
 *
 * <p>Refs: H-02.3, RNF-003, VAL-015, VAL-024
 */
@WebMvcTest(controllers = EndpointsArnesPruebaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class ArnesAutorizacionEndpointTest {

    private static final UUID ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ServicioArnesPrueba servicio;

    @Nested
    @DisplayName("Endpoints bien construidos: las pruebas del arnés pasan")
    class EndpointsCorrectos {

        @TestFactory
        @DisplayName("GET de un recurso (método del servicio que devuelve valor)")
        Stream<DynamicTest> consultar() {
            return ArnesAutorizacionEndpoint.para(mvc)
                    .peticion(HttpMethod.GET, "/api/v1/prueba-arnes/recursos/{id}", ID)
                    .dobles(servicio)
                    .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                    .pruebas();
        }

        @TestFactory
        @DisplayName("POST con cuerpo validado con @Valid")
        Stream<DynamicTest> crear() {
            return ArnesAutorizacionEndpoint.para(mvc)
                    .peticion(HttpMethod.POST, "/api/v1/prueba-arnes/recursos")
                    .cuerpoJson("{\"nombre\": \"Club Halcones\"}")
                    .dobles(servicio)
                    .simularDenegacion(error -> given(servicio.crear(any(CrearRecursoRequest.class)))
                            .willThrow(error))
                    .pruebas();
        }

        @TestFactory
        @DisplayName("POST de acción de estado (método void del servicio)")
        Stream<DynamicTest> cerrar() {
            return ArnesAutorizacionEndpoint.para(mvc)
                    .peticion(HttpMethod.POST, "/api/v1/prueba-arnes/recursos/{id}/cerrar", ID)
                    .dobles(servicio)
                    .simularDenegacion(error -> willThrow(error).given(servicio).cerrar(ID))
                    .pruebas();
        }

        @TestFactory
        @DisplayName("Endpoint que solo exige sesión: genera únicamente los 401")
        Stream<DynamicTest> soloSesion() {
            return ArnesAutorizacionEndpoint.para(mvc)
                    .peticion(HttpMethod.GET, "/api/v1/prueba-arnes/recursos/{id}", ID)
                    .dobles(servicio)
                    .soloSesion()
                    .pruebas();
        }
    }

    @Nested
    @DisplayName("Endpoints mal construidos: el arnés los detecta")
    class EndpointsDefectuosos {

        @Test
        @DisplayName("VAL-015/VAL-024: un endpoint que traga la denegación falla en 403 y 404, pero no en 401")
        void detectaQueSeTragaLaDenegacion() {
            List<DynamicTest> pruebas = ArnesAutorizacionEndpoint.para(mvc)
                    .peticion(HttpMethod.GET, "/api/v1/prueba-arnes/traga-denegacion/{id}", ID)
                    .dobles(servicio)
                    .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                    .pruebas()
                    .toList();

            for (DynamicTest prueba : pruebas) {
                if (prueba.getDisplayName().startsWith("RNF-003")) {
                    pasa(prueba);
                } else {
                    falla(prueba);
                }
            }
        }

        @Test
        @DisplayName("RNF-003: un endpoint en la ruta pública falla la prueba de 401 sin token")
        void detectaEndpointSinSesion() {
            DynamicTest sinToken = ArnesAutorizacionEndpoint.para(mvc)
                    .peticion(HttpMethod.GET, "/api/v1/publico/prueba-arnes/{id}", ID)
                    .dobles(servicio)
                    .soloSesion()
                    .pruebas()
                    .filter(prueba -> prueba.getDisplayName().contains("sin token"))
                    .findFirst()
                    .orElseThrow();

            falla(sinToken);
        }

        private void pasa(DynamicTest prueba) {
            try {
                prueba.getExecutable().execute();
            } catch (Throwable error) {
                throw new AssertionError("Debió pasar: " + prueba.getDisplayName(), error);
            }
        }

        private void falla(DynamicTest prueba) {
            assertThatThrownBy(() -> prueba.getExecutable().execute())
                    .as("Debió fallar: %s", prueba.getDisplayName())
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Nested
    @DisplayName("Uso incompleto del arnés")
    class UsoIncompleto {

        @Test
        @DisplayName("Sin petición no genera pruebas")
        void sinPeticion() {
            assertThatThrownBy(() -> ArnesAutorizacionEndpoint.para(mvc).soloSesion().pruebas())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("peticion");
        }

        @Test
        @DisplayName("Un endpoint con permisos exige simular la denegación")
        void sinSimulacion() {
            assertThatThrownBy(() -> ArnesAutorizacionEndpoint.para(mvc)
                    .peticion(HttpMethod.GET, "/api/v1/prueba-arnes/recursos/{id}", ID)
                    .dobles(servicio)
                    .pruebas())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("simularDenegacion");
        }

        @Test
        @DisplayName("Un endpoint con permisos exige los dobles a reiniciar")
        void sinDobles() {
            assertThatThrownBy(() -> ArnesAutorizacionEndpoint.para(mvc)
                    .peticion(HttpMethod.GET, "/api/v1/prueba-arnes/recursos/{id}", ID)
                    .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                    .pruebas())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("dobles");
        }

        @Test
        @DisplayName("soloSesion genera dos pruebas y el caso completo cinco")
        void cantidadDePruebas() {
            assertThat(ArnesAutorizacionEndpoint.para(mvc)
                    .peticion(HttpMethod.GET, "/api/v1/prueba-arnes/recursos/{id}", ID)
                    .soloSesion()
                    .pruebas()).hasSize(2);
            assertThat(ArnesAutorizacionEndpoint.para(mvc)
                    .peticion(HttpMethod.GET, "/api/v1/prueba-arnes/recursos/{id}", ID)
                    .dobles(servicio)
                    .simularDenegacion(error -> given(servicio.consultar(ID)).willThrow(error))
                    .pruebas()).hasSize(5);
        }
    }
}

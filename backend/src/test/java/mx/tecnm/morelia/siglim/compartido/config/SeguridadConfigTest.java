package mx.tecnm.morelia.siglim.compartido.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Criterio de terminado de T-02.2: peticiones sin token o con token vencido responden
 * {@code 401 NO_AUTENTICADO}; un token válido de Supabase pasa. Además verifica la denegación por
 * defecto, las rutas públicas y CORS.
 */
@WebMvcTest(controllers = RecursosPruebaController.class, properties = {
        "SUPABASE_URL=" + SeguridadPrueba.URL_SUPABASE,
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=" + SeguridadPrueba.ORIGEN_FRONTEND
})
@Import(SeguridadPrueba.class)
class SeguridadConfigTest {

    private static final String PROTEGIDO = "/api/v1/prueba/protegido";

    @Autowired
    private MockMvcTester mvc;

    private MvcTestResult getConToken(String ruta, String token) {
        return mvc.get().uri(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    /** Verifica el cuerpo RFC 9457 de un 401 (convenciones de API §3). */
    private static void esNoAutenticado(MvcTestResult resultado, String detalle, String wwwAuthenticate) {
        assertThat(resultado)
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("NO_AUTENTICADO");
        assertThat(resultado).bodyJson().extractingPath("$.status").isEqualTo(401);
        assertThat(resultado).bodyJson().extractingPath("$.type").isEqualTo("https://siglim.mx/errores/no-autenticado");
        assertThat(resultado).bodyJson().extractingPath("$.detail").isEqualTo(detalle);
    }

    @Nested
    @DisplayName("Criterio de terminado de T-02.2")
    class CriterioDeTerminado {

        @Test
        @DisplayName("RNF-003: sin token responde 401 NO_AUTENTICADO en formato RFC 9457")
        void sinTokenResponde401() {
            MvcTestResult resultado = mvc.get().uri(PROTEGIDO).exchange();

            esNoAutenticado(resultado, PuntoEntradaNoAutenticado.DETALLE_SIN_TOKEN, "Bearer");
            assertThat(resultado).bodyJson().extractingPath("$.instance").isEqualTo(PROTEGIDO);
        }

        @Test
        @DisplayName("RNF-003: token vencido responde 401 NO_AUTENTICADO")
        void tokenVencidoResponde401() {
            Instant hace2Horas = Instant.now().minus(Duration.ofHours(2));
            String vencido = SeguridadPrueba.token(SeguridadPrueba.LLAVE_PROYECTO, reclamos -> reclamos
                    .issuedAt(hace2Horas)
                    .expiresAt(hace2Horas.plus(Duration.ofHours(1))));

            esNoAutenticado(getConToken(PROTEGIDO, vencido),
                    PuntoEntradaNoAutenticado.DETALLE_TOKEN_INVALIDO, "Bearer error=\"invalid_token\"");
        }

        @Test
        @DisplayName("ADR-0003: token válido de Supabase pasa y expone el sub y el perfil de SIGLIM")
        void tokenValidoPasa() {
            MvcTestResult resultado = getConToken(PROTEGIDO, SeguridadPrueba.tokenValido());

            assertThat(resultado).hasStatusOk();
            assertThat(resultado).bodyJson().extractingPath("$.sub").isEqualTo(SeguridadPrueba.SUJETO);
            assertThat(resultado).bodyJson().extractingPath("$.perfilId")
                    .isEqualTo(SeguridadPrueba.PERFIL_ID.toString());
        }
    }

    @Nested
    @DisplayName("Cuenta de Supabase sin perfil activo en SIGLIM (ADR-0003 punto 6)")
    class CuentaSinPerfilActivo {

        private void cuentaNoActiva(String sub) {
            String token = SeguridadPrueba.token(SeguridadPrueba.LLAVE_PROYECTO, reclamos -> reclamos.subject(sub));
            esNoAutenticado(getConToken(PROTEGIDO, token),
                    PuntoEntradaNoAutenticado.DETALLE_CUENTA_NO_ACTIVA, "Bearer error=\"invalid_token\"");
        }

        @Test
        @DisplayName("T-02.3: token válido sin perfil en SIGLIM responde 401 NO_AUTENTICADO")
        void sinPerfil() {
            cuentaNoActiva(SeguridadPrueba.SUJETO_SIN_PERFIL);
        }

        @Test
        @DisplayName("T-02.3: token válido con perfil desactivado responde 401 NO_AUTENTICADO")
        void perfilDesactivado() {
            cuentaNoActiva(SeguridadPrueba.SUJETO_PERFIL_INACTIVO);
        }
    }

    @Nested
    @DisplayName("Tokens que no deben aceptarse")
    class TokensRechazados {

        private void rechazado(String token) {
            esNoAutenticado(getConToken(PROTEGIDO, token),
                    PuntoEntradaNoAutenticado.DETALLE_TOKEN_INVALIDO, "Bearer error=\"invalid_token\"");
        }

        @Test
        @DisplayName("RNF-013: firmado con una llave que no está en el JWKS del proyecto")
        void firmaDeOtraLlave() {
            rechazado(SeguridadPrueba.token(SeguridadPrueba.LLAVE_AJENA, reclamos -> { }));
        }

        @Test
        @DisplayName("ADR-0003: firmado con HS256 (llave secreta heredada de Supabase)")
        void algoritmoSimetricoHs256() {
            var secreto = new SecretKeySpec("una-llave-secreta-heredada-de-32-bytes!!".getBytes(), "HmacSHA256");
            Instant ahora = Instant.now();
            JwtClaimsSet reclamos = JwtClaimsSet.builder()
                    .issuer(DecodificadorJwtConfig.emisor(SeguridadPrueba.URL_SUPABASE))
                    .audience(List.of(DecodificadorJwtConfig.AUDIENCIA))
                    .subject(SeguridadPrueba.SUJETO)
                    .issuedAt(ahora)
                    .expiresAt(ahora.plus(Duration.ofHours(1)))
                    .build();
            String hs256 = NimbusJwtEncoder.withSecretKey(secreto).algorithm(MacAlgorithm.HS256).build()
                    .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), reclamos))
                    .getTokenValue();

            rechazado(hs256);
        }

        @Test
        @DisplayName("ADR-0003: emisor distinto (otro proyecto o la llave pública anon)")
        void emisorDistinto() {
            rechazado(SeguridadPrueba.token(SeguridadPrueba.LLAVE_PROYECTO,
                    reclamos -> reclamos.issuer("supabase")));
        }

        @Test
        @DisplayName("ADR-0003: audiencia distinta de authenticated")
        void audienciaDistinta() {
            rechazado(SeguridadPrueba.token(SeguridadPrueba.LLAVE_PROYECTO,
                    reclamos -> reclamos.audience(List.of("anon"))));
        }

        @Test
        @DisplayName("ADR-0003: sub que no es el UUID de un usuario de Supabase Auth")
        void sujetoNoEsUuid() {
            rechazado(SeguridadPrueba.token(SeguridadPrueba.LLAVE_PROYECTO,
                    reclamos -> reclamos.subject("administrador")));
        }

        @Test
        @DisplayName("RNF-003: texto que no es un JWT")
        void textoQueNoEsJwt() {
            rechazado("esto-no-es-un-jwt");
        }

        @Test
        @DisplayName("RNF-003: cabecera Authorization mal formada responde 401, no 400")
        void cabeceraMalFormada() {
            MvcTestResult resultado = mvc.get().uri(PROTEGIDO)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer con espacios").exchange();

            assertThat(resultado).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("NO_AUTENTICADO");
        }
    }

    @Nested
    @DisplayName("Rutas públicas y denegación por defecto")
    class Rutas {

        @Test
        @DisplayName("RF-089: GET del portal público responde sin token")
        void portalPublicoSinToken() {
            assertThat(mvc.get().uri("/api/v1/publico/prueba")).hasStatusOk();
        }

        @Test
        @DisplayName("VAL-015: el portal es de solo lectura; un POST sin token se rechaza")
        void escrituraEnPortalExigeToken() {
            assertThat(mvc.post().uri("/api/v1/publico/prueba")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("VAL-015: una ruta no declarada exige token (denegación por defecto)")
        void rutaNoDeclaradaExigeToken() {
            assertThat(mvc.get().uri("/api/v1/temporadas")).hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("CORS (convenciones de API §5)")
    class Cors {

        @Test
        @DisplayName("Permite la petición previa desde el origen del frontend")
        void permiteOrigenDelFrontend() {
            assertThat(mvc.options().uri(PROTEGIDO)
                    .header(HttpHeaders.ORIGIN, SeguridadPrueba.ORIGEN_FRONTEND)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                    .hasStatusOk()
                    .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SeguridadPrueba.ORIGEN_FRONTEND);
        }

        @Test
        @DisplayName("Rechaza la petición previa desde un origen no autorizado")
        void rechazaOtroOrigen() {
            assertThat(mvc.options().uri(PROTEGIDO)
                    .header(HttpHeaders.ORIGIN, "https://sitio-malicioso.ejemplo")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}

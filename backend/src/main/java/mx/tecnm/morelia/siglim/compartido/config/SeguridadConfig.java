package mx.tecnm.morelia.siglim.compartido.config;

import java.time.Duration;
import java.util.List;
import mx.tecnm.morelia.siglim.compartido.contexto.ConvertidorAutenticacion;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad HTTP del backend: la API es un <em>OAuth2 Resource Server</em> que acepta el JWT de
 * Supabase Auth en {@code Authorization: Bearer} (ADR-0003, convenciones de API §5).
 *
 * <ul>
 *   <li><b>Denegación por defecto</b> (RNF-003, VAL-015): toda ruta exige token salvo
 *       {@code GET /api/v1/salud}, {@code GET /api/v1/publico/**} y {@code GET /actuator/health}.
 *       Los permisos finos ({@code modulo.recurso:ACCION} con ámbito) los aplican los servicios
 *       (ADR-0007); aquí solo se exige que la persona haya iniciado sesión.</li>
 *   <li><b>Sin sesión en el servidor:</b> cada petición trae su token; no hay cookies de sesión.</li>
 *   <li><b>Sin CSRF:</b> el ataque CSRF abusa de credenciales que el navegador envía solo (cookies);
 *       el token viaja en una cabecera que el frontend agrega a propósito, así que no aplica.</li>
 *   <li><b>CORS</b> solo para los orígenes de {@code SIGLIM_CORS_ORIGEN}.</li>
 * </ul>
 *
 * <p>Refs: T-02.2, RNF-003, RNF-013, VAL-015, ADR-0003
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SiglimPropiedades.class)
public class SeguridadConfig {

    /** [PROPUESTA] Tiempo que el navegador puede guardar la respuesta a la petición previa CORS. */
    private static final Duration CORS_CACHE_PREVIA = Duration.ofHours(1);

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, PuntoEntradaNoAutenticado puntoEntrada,
            ConvertidorAutenticacion convertidor, SiglimPropiedades propiedades) throws Exception {
        http
                // La fuente se pasa explícita: con cors(withDefaults()) Spring Security solo busca un
                // bean llamado exactamente «corsConfigurationSource» y, si no lo encuentra, deja pasar
                // cualquier origen sin cabeceras CORS (error detectado por SeguridadConfigTest).
                .cors(cors -> cors.configurationSource(fuenteCors(propiedades)))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.GET, "/api/v1/salud").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/publico/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Página de error interna de Spring: si no se permite, un error en una ruta
                        // pública se convertiría en 401 y ocultaría el código real.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(recurso -> recurso
                        // Tras validar el token, resuelve el perfil de SIGLIM (T-02.3, ADR-0003 punto 6).
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor))
                        .authenticationEntryPoint(puntoEntrada))
                .exceptionHandling(excepciones -> excepciones.authenticationEntryPoint(puntoEntrada));
        return http.build();
    }

    /**
     * Política CORS: solo los orígenes de {@code SIGLIM_CORS_ORIGEN}. No es un bean para evitar la
     * ambigüedad con {@code HandlerMappingIntrospector}, que también es un
     * {@link CorsConfigurationSource}.
     */
    static CorsConfigurationSource fuenteCors(SiglimPropiedades propiedades) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(propiedades.cors().origenes());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // Cabeceras propias que la interfaz necesita leer: el conteo de avisos de la campana (H-25.1).
        cors.setExposedHeaders(List.of("X-No-Leidas"));
        // El token va en una cabecera, no en cookies: no se permiten credenciales del navegador.
        cors.setAllowCredentials(false);
        cors.setMaxAge(CORS_CACHE_PREVIA);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", cors);
        return fuente;
    }
}

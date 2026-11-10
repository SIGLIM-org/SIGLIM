package mx.tecnm.morelia.siglim.compartido.config;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.compartido.contexto.ConvertidorAutenticacion;
import mx.tecnm.morelia.siglim.compartido.contexto.ResolutorPerfil;
import mx.tecnm.morelia.siglim.compartido.error.DescripcionRestricciones;
import mx.tecnm.morelia.siglim.compartido.error.TraductorErroresBd;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Configuración de seguridad para pruebas de controlador ({@code @WebMvcTest}).
 *
 * <p>Usa la misma cadena de seguridad y las mismas reglas de validación del token que producción
 * ({@link DecodificadorJwtConfig#validador}); solo cambia de dónde salen las llaves públicas: en
 * lugar del JWKS de Supabase, una llave EC P-256 (ES256, el algoritmo del proyecto) generada al
 * vuelo. Así las pruebas firman tokens reales sin red ni secretos.
 *
 * <p>El perfil de SIGLIM se resuelve con un {@link ResolutorPerfil} en memoria: {@link #SUJETO}
 * tiene el perfil activo {@link #PERFIL_ID}, {@link #SUJETO_PERFIL_INACTIVO} uno desactivado y
 * cualquier otro {@code sub} no tiene perfil.
 *
 * <p>También registra el {@link TraductorErroresBd} que necesita el manejador global de errores
 * (un {@code @RestControllerAdvice}, que toda prueba {@code @WebMvcTest} carga), con el comentario
 * de {@code ex_partidos_cancha_ocupada} de la migración y sin consultar la base de datos.
 *
 * <p>Uso: {@code @Import(SeguridadPrueba.class)} y, en {@code @WebMvcTest(properties = …)},
 * {@code SUPABASE_URL=} {@link #URL_SUPABASE}, {@code SIGLIM_CORS_ORIGEN=} {@link #ORIGEN_FRONTEND}
 * y las variables {@code SIGLIM_LIGA_ID}, {@code SIGLIM_S3_BUCKET} y {@code AWS_REGION} que valida
 * {@link SiglimPropiedades}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SeguridadConfig.class, PuntoEntradaNoAutenticado.class, ConvertidorAutenticacion.class,
        TraductorErroresBd.class})
public class SeguridadPrueba {

    public static final String URL_SUPABASE = "https://proyecto-prueba.supabase.co";
    public static final String ORIGEN_FRONTEND = "http://localhost:5173";

    /** Usuario de Supabase Auth de los tokens de prueba. */
    public static final String SUJETO = "6f1d2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b";

    /** Perfil de SIGLIM activo de {@link #SUJETO}. */
    public static final UUID PERFIL_ID = UUID.fromString("0b7c3e1a-2d4f-4a6b-9c8d-7e6f5a4b3c2d");

    /** Cuenta de Supabase Auth cuyo perfil de SIGLIM está desactivado. */
    public static final String SUJETO_PERFIL_INACTIVO = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";

    /** Cuenta de Supabase Auth sin perfil en SIGLIM (como una invitación sin completar). */
    public static final String SUJETO_SIN_PERFIL = "9f8e7d6c-5b4a-4392-8170-6f5e4d3c2b1a";

    /**
     * Identificador de llave ({@code kid}) común al JWKS y a los tokens de prueba. Debe coincidir:
     * el decodificador elige la llave del JWKS por el {@code kid} del encabezado, y si no coincide
     * rechaza todo token antes de revisar la firma o los reclamos, con lo que las pruebas de
     * rechazo pasarían por una razón equivocada.
     */
    static final String KID = "llave-prueba-es256";

    /** Llave con la que «firma Supabase» en las pruebas; su parte pública es el JWKS. */
    static final KeyPair LLAVE_PROYECTO = generarLlaveEc();

    /**
     * Llave de un atacante o de otro proyecto. Firma con el mismo {@link #KID} para que el rechazo
     * se deba a la firma y no a una llave inexistente.
     */
    static final KeyPair LLAVE_AJENA = generarLlaveEc();

    @Bean
    DescripcionRestricciones descripcionRestriccionesPrueba() {
        return nombre -> "ex_partidos_cancha_ocupada".equals(nombre)
                ? Optional.of("VAL-004: la cancha ya está ocupada en ese horario.")
                : Optional.empty();
    }

    @Bean
    ResolutorPerfil resolutorPerfilPrueba() {
        return usuarioAuthId -> {
            if (usuarioAuthId.equals(UUID.fromString(SUJETO))) {
                return Optional.of(new ResolutorPerfil.PerfilSesion(PERFIL_ID, true));
            }
            if (usuarioAuthId.equals(UUID.fromString(SUJETO_PERFIL_INACTIVO))) {
                return Optional.of(new ResolutorPerfil.PerfilSesion(UUID.randomUUID(), false));
            }
            return Optional.empty();
        };
    }

    @Bean
    JwtDecoder decodificadorJwtPrueba() {
        ECKey publica = new ECKey.Builder(Curve.P_256, (ECPublicKey) LLAVE_PROYECTO.getPublic())
                .keyID(KID)
                .build();
        NimbusJwtDecoder decodificador = NimbusJwtDecoder
                .withJwkSource(new ImmutableJWKSet<SecurityContext>(new JWKSet(publica)))
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decodificador.setJwtValidator(
                DecodificadorJwtConfig.validador(DecodificadorJwtConfig.emisor(URL_SUPABASE)));
        return decodificador;
    }

    /** Token de sesión válido, como el que emite Supabase Auth al iniciar sesión. */
    public static String tokenValido() {
        return token(LLAVE_PROYECTO, reclamos -> { });
    }

    /**
     * Token con reclamos válidos pero firmado con una llave que no está en el JWKS del proyecto: la
     * cadena de seguridad debe rechazarlo con {@code 401}. Lo usa el arnés de H-02.3 desde otros
     * paquetes de prueba.
     */
    public static String tokenFirmadoConLlaveAjena() {
        return token(LLAVE_AJENA, reclamos -> { });
    }

    /**
     * Firma un token ES256 con reclamos de sesión válidos y permite alterarlos para probar rechazos.
     *
     * @param llave   llave privada con la que se firma
     * @param ajustes cambios sobre los reclamos válidos (emisor, audiencia, vigencia…)
     */
    public static String token(KeyPair llave, Consumer<JwtClaimsSet.Builder> ajustes) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder reclamos = JwtClaimsSet.builder()
                .issuer(DecodificadorJwtConfig.emisor(URL_SUPABASE))
                .audience(List.of(DecodificadorJwtConfig.AUDIENCIA))
                .subject(SUJETO)
                .claim("role", "authenticated")
                .issuedAt(ahora)
                .expiresAt(ahora.plus(Duration.ofHours(1)));
        ajustes.accept(reclamos);

        NimbusJwtEncoder codificador = NimbusJwtEncoder
                .withKeyPair((ECPublicKey) llave.getPublic(), (ECPrivateKey) llave.getPrivate())
                .jwkPostProcessor(jwk -> jwk.keyID(KID))
                .build();
        return codificador
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.ES256).build(), reclamos.build()))
                .getTokenValue();
    }

    private static KeyPair generarLlaveEc() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("EC");
            generador.initialize(new ECGenParameterSpec("secp256r1"));
            return generador.generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo generar la llave EC de prueba", e);
        }
    }
}

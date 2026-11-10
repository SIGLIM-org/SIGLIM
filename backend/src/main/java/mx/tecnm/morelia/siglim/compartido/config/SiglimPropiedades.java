package mx.tecnm.morelia.siglim.compartido.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración propia de SIGLIM (prefijo {@code siglim} en {@code application.yml}).
 *
 * <p>Cada valor viene de una variable de entorno documentada en {@code backend/.env.example}.
 * Se valida al arrancar: si falta una variable o tiene un formato incorrecto, la aplicación no
 * inicia y el mensaje dice cuál es, en lugar de fallar después con la primera petición.
 *
 * <p>Refs: T-02.1, RNF-019, ADR-0003 (Supabase Auth), convenciones de API §5 (CORS)
 *
 * @param supabase       proyecto de Supabase del que se derivan el JWKS y el emisor del JWT
 * @param cors           orígenes del frontend a los que se permite llamar a la API
 * @param almacenamiento bucket de AWS S3 de los documentos (ADR-0013)
 * @param ligaId         liga activa: los servicios filtran siempre por ella (ADR-0011 §5)
 */
@Validated
@ConfigurationProperties("siglim")
public record SiglimPropiedades(@Valid @NotNull Supabase supabase, @Valid @NotNull Cors cors,
                                @Valid @NotNull Almacenamiento almacenamiento,
                                @NotNull(message = "Define SIGLIM_LIGA_ID en backend/.env") UUID ligaId) {

    /**
     * Datos del proyecto de Supabase.
     *
     * @param url {@code https://<ref>.supabase.co}, sin diagonal final porque a partir de ella se
     *            arman {@code /auth/v1} y {@code /auth/v1/.well-known/jwks.json} (T-02.2)
     */
    public record Supabase(
            @NotBlank(message = "Define SUPABASE_URL en backend/.env")
            @Pattern(regexp = "^https?://\\S*[^/\\s]$",
                    message = "SUPABASE_URL debe ser https://<ref>.supabase.co, sin diagonal final")
            String url) {
    }

    /**
     * Política CORS de la API (convenciones de API §5).
     *
     * @param origenes orígenes exactos del frontend, por ejemplo {@code http://localhost:5173};
     *                 en la variable de entorno se separan con comas
     */
    public record Cors(
            @NotEmpty(message = "Define SIGLIM_CORS_ORIGEN en backend/.env")
            List<@NotBlank @Pattern(regexp = "^https?://[^/\\s*]+$",
                    message = "Cada origen de SIGLIM_CORS_ORIGEN es esquema://host[:puerto], sin ruta ni comodín")
                    String> origenes) {
    }

    /**
     * Bucket privado de AWS S3 de los documentos (ADR-0013, T-04).
     *
     * <p>Las llaves se leen aquí y no las busca el SDK por su cuenta porque {@code backend/.env} se
     * carga en el {@code Environment} de Spring, no en las variables del sistema operativo. Si se
     * dejan vacías, el SDK usa su cadena predeterminada ({@code ~/.aws}, variables reales del
     * sistema).
     *
     * @param bucket          nombre del bucket (reglas de nombre de S3)
     * @param region          región del bucket, p. ej. {@code us-east-2}
     * @param accessKeyId     llave del usuario IAM; opcional, junto con {@code secretAccessKey}
     * @param secretAccessKey secreto del usuario IAM; opcional, junto con {@code accessKeyId}
     * @param vigenciaEnlace  vigencia de las URL prefirmadas de lectura, entre 1 s y 15 min
     * @param tamanoMaximo    tamaño máximo de un archivo; no supera el CHECK de
     *                        {@code siglim.documentos} (5 242 880 bytes)
     * @param endpoint        solo para pruebas (S3Mock): otro endpoint con acceso por ruta
     */
    public record Almacenamiento(
            @NotBlank(message = "Define SIGLIM_S3_BUCKET en backend/.env")
            @Pattern(regexp = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$",
                    message = "SIGLIM_S3_BUCKET debe ser un nombre de bucket de S3: 3 a 63 caracteres en minúsculas, dígitos, punto o guion")
            String bucket,
            @NotBlank(message = "Define AWS_REGION en backend/.env")
            String region,
            String accessKeyId,
            String secretAccessKey,
            @NotNull @DurationMin(seconds = 1) @DurationMax(minutes = 15)
            Duration vigenciaEnlace,
            @NotNull
            DataSize tamanoMaximo,
            URI endpoint) {

        /** Límite del CHECK {@code ck_documentos_tamano} de {@code siglim.documentos}. */
        public static final long TAMANO_MAXIMO_BD = 5_242_880L;

        @AssertTrue(message = "Define AWS_ACCESS_KEY_ID y AWS_SECRET_ACCESS_KEY juntas, o deja las dos vacías para usar ~/.aws")
        public boolean isLlavesCompletas() {
            return tieneLlaves() || (vacia(accessKeyId) && vacia(secretAccessKey));
        }

        @AssertTrue(message = "siglim.almacenamiento.tamano-maximo debe ser mayor que 0 y no pasar de 5MB (CHECK de siglim.documentos)")
        public boolean isTamanoMaximoValido() {
            return tamanoMaximo == null
                    || (tamanoMaximo.toBytes() > 0 && tamanoMaximo.toBytes() <= TAMANO_MAXIMO_BD);
        }

        /** Si se configuraron las dos llaves estáticas. */
        public boolean tieneLlaves() {
            return !vacia(accessKeyId) && !vacia(secretAccessKey);
        }

        private static boolean vacia(String valor) {
            return valor == null || valor.isBlank();
        }
    }
}

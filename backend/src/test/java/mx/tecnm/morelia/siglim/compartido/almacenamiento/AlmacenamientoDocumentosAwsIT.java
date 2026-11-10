package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Prueba contra el bucket real de AWS (T-04.3). <b>Desactivada por defecto</b>: solo corre con
 * {@code -Dsiglim.s3.pruebaReal=true} y lee bucket, región y llaves de {@code backend/.env} (sin
 * imprimirlos).
 *
 * <p>Comprueba lo que S3Mock no puede: que la URL prefirmada vence y que el bucket no es público.
 * El objeto subido queda en el bucket a propósito: el usuario IAM no tiene permiso de borrado.
 */
@EnabledIfSystemProperty(named = "siglim.s3.pruebaReal", matches = "true")
class AlmacenamientoDocumentosAwsIT {

    /** Liga ficticia para que los objetos de prueba no se mezclen con los de una liga real. */
    private static final UUID LIGA_PRUEBA = new UUID(0, 0);
    private static final Duration VIGENCIA = Duration.ofSeconds(5);

    @Test
    @DisplayName("RNF-018: la URL prefirmada sirve antes de vencer, después responde 403 y la URL guardada nunca es pública")
    void enlaceVenceYBucketEsPrivado() throws Exception {
        Properties env = leerEnv();
        String accessKeyId = env.getProperty("AWS_ACCESS_KEY_ID", "");
        var configuracion = new SiglimPropiedades.Almacenamiento(env.getProperty("SIGLIM_S3_BUCKET"),
                env.getProperty("AWS_REGION"), accessKeyId, env.getProperty("AWS_SECRET_ACCESS_KEY", ""),
                VIGENCIA, DataSize.ofMegabytes(5), null);

        try (S3Client s3 = AlmacenamientoConfig.cliente(configuracion);
             S3Presigner firmador = AlmacenamientoConfig.firmador(configuracion);
             HttpClient http = HttpClient.newHttpClient()) {
            var almacenamiento = new AlmacenamientoDocumentosS3(s3, firmador, configuracion, Clock.systemUTC());
            ArchivoAlmacenado archivo = almacenamiento.guardar(new ClaveDocumento(LIGA_PRUEBA,
                    CarpetaDocumento.EVIDENCIAS, UUID.randomUUID(), UUID.randomUUID()), ArchivosPrueba.PDF);
            EnlaceTemporal enlace = almacenamiento.generarEnlaceLectura(archivo.s3Bucket(), archivo.s3Clave());

            assertThat(estado(http, enlace.url())).as("URL prefirmada vigente").isEqualTo(200);

            Thread.sleep(Duration.ofSeconds(8));
            assertThat(estado(http, enlace.url())).as("URL prefirmada vencida").isEqualTo(403);
            assertThat(estado(http, archivo.url())).as("URL guardada, sin firma").isEqualTo(403);
        }
    }

    private static int estado(HttpClient http, String url) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    /** {@code backend/.env}; Maven corre las pruebas con {@code backend/} como directorio de trabajo. */
    private static Properties leerEnv() throws IOException {
        Properties env = new Properties();
        try (Reader lector = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) {
            env.load(lector);
        }
        return env;
    }
}

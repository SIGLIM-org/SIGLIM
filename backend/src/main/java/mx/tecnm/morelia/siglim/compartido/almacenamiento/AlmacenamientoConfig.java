package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Clientes de AWS S3 a partir de {@link SiglimPropiedades.Almacenamiento}.
 *
 * <p>Credenciales: las llaves de {@code backend/.env} si vienen las dos; si no, la cadena
 * predeterminada del SDK ({@code ~/.aws}, variables reales del sistema). El SDK las resuelve en la
 * primera llamada, así que la aplicación arranca aunque falten.
 *
 * <p>Refs: T-04, ADR-0013
 */
@Configuration(proxyBeanMethods = false)
class AlmacenamientoConfig {

    @Bean(destroyMethod = "close")
    S3Client clienteS3(SiglimPropiedades propiedades) {
        return cliente(propiedades.almacenamiento());
    }

    @Bean(destroyMethod = "close")
    S3Presigner firmadorS3(SiglimPropiedades propiedades) {
        return firmador(propiedades.almacenamiento());
    }

    static S3Client cliente(SiglimPropiedades.Almacenamiento configuracion) {
        S3ClientBuilder constructor = S3Client.builder()
                .region(Region.of(configuracion.region()))
                .credentialsProvider(credenciales(configuracion));
        if (configuracion.endpoint() != null) {
            constructor.endpointOverride(configuracion.endpoint()).forcePathStyle(true);
        }
        return constructor.build();
    }

    static S3Presigner firmador(SiglimPropiedades.Almacenamiento configuracion) {
        S3Presigner.Builder constructor = S3Presigner.builder()
                .region(Region.of(configuracion.region()))
                .credentialsProvider(credenciales(configuracion));
        if (configuracion.endpoint() != null) {
            constructor.endpointOverride(configuracion.endpoint())
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return constructor.build();
    }

    private static AwsCredentialsProvider credenciales(SiglimPropiedades.Almacenamiento configuracion) {
        return configuracion.tieneLlaves()
                ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(configuracion.accessKeyId(), configuracion.secretAccessKey()))
                : DefaultCredentialsProvider.builder().build();
    }
}

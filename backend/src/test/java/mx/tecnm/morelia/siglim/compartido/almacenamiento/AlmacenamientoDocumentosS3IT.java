package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ArchivoRechazadoException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Criterio de terminado de T-04 contra un S3 simulado (S3Mock en un contenedor): sube un PDF,
 * rechaza un ejecutable renombrado y descarga con la URL prefirmada.
 *
 * <p>Sin contexto de Spring ni base de datos: el componente se arma directamente con un endpoint
 * propio y acceso por ruta. <b>S3Mock no valida firmas ni expiración</b>: la vigencia de la URL se
 * cubre en {@link AlmacenamientoDocumentosS3Test} y en {@link AlmacenamientoDocumentosAwsIT}, contra
 * AWS real. Requiere Docker; sin Docker se omite.
 */
@Testcontainers(disabledWithoutDocker = true)
class AlmacenamientoDocumentosS3IT {

    private static final String BUCKET = "siglim-documentos-prueba";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Container
    private static final GenericContainer<?> S3MOCK = new GenericContainer<>("adobe/s3mock:4.12.4")
            .withExposedPorts(9090)
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
            .waitingFor(Wait.forHttp("/").forPort(9090));

    private static S3Client s3;
    private static S3Presigner firmador;
    private static AlmacenamientoDocumentos almacenamiento;

    @BeforeAll
    static void armarComponente() {
        var configuracion = new SiglimPropiedades.Almacenamiento(BUCKET, "us-east-2", "prueba", "prueba",
                Duration.ofSeconds(60), DataSize.ofMegabytes(5),
                URI.create("http://" + S3MOCK.getHost() + ":" + S3MOCK.getMappedPort(9090)));
        s3 = AlmacenamientoConfig.cliente(configuracion);
        firmador = AlmacenamientoConfig.firmador(configuracion);
        almacenamiento = new AlmacenamientoDocumentosS3(s3, firmador, configuracion, Clock.systemUTC());
    }

    @AfterAll
    static void cerrar() {
        s3.close();
        firmador.close();
    }

    private static ClaveDocumento nuevaClave() {
        return new ClaveDocumento(LIGA, CarpetaDocumento.EVIDENCIAS, UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("RF-108: sube un PDF y S3 devuelve los mismos bytes con su Content-Type")
    void subeUnPdf() {
        ArchivoAlmacenado archivo = almacenamiento.guardar(nuevaClave(), ArchivosPrueba.PDF);

        ResponseBytes<GetObjectResponse> objeto =
                s3.getObjectAsBytes(peticion -> peticion.bucket(BUCKET).key(archivo.s3Clave()));
        assertThat(objeto.asByteArray()).isEqualTo(ArchivosPrueba.PDF);
        assertThat(objeto.response().contentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("RNF-018: rechaza un ejecutable renombrado con 415 y no deja ningún objeto")
    void rechazaEjecutable() {
        ClaveDocumento clave = nuevaClave();

        assertThatThrownBy(() -> almacenamiento.guardar(clave, ArchivosPrueba.EJECUTABLE))
                .isInstanceOfSatisfying(ArchivoRechazadoException.class,
                        error -> assertThat(error.estado()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        String carpeta = "liga/" + LIGA + "/evidencias/" + clave.entidadId() + "/";
        assertThat(s3.listObjectsV2(peticion -> peticion.bucket(BUCKET).prefix(carpeta)).contents()).isEmpty();
    }

    @Test
    @DisplayName("RNF-018: la URL prefirmada descarga el archivo")
    void descargaConUrlPrefirmada() throws Exception {
        ArchivoAlmacenado archivo = almacenamiento.guardar(nuevaClave(), ArchivosPrueba.PNG);

        EnlaceTemporal enlace = almacenamiento.generarEnlaceLectura(archivo.s3Bucket(), archivo.s3Clave());

        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<byte[]> respuesta = http.send(HttpRequest.newBuilder(URI.create(enlace.url())).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(respuesta.statusCode()).isEqualTo(200);
            assertThat(respuesta.body()).isEqualTo(ArchivosPrueba.PNG);
        }
    }
}

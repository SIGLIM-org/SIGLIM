package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ArchivoRechazadoException;
import mx.tecnm.morelia.siglim.compartido.error.ServicioNoDisponibleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Reglas del componente sin red: el {@link S3Client} es un doble de Mockito y el {@link S3Presigner}
 * es real con credenciales falsas (firmar es un cálculo local) y un reloj fijo.
 */
class AlmacenamientoDocumentosS3Test {

    private static final String BUCKET = "siglim-documentos-prueba";
    private static final Instant AHORA = Instant.parse("2026-10-26T18:00:00Z");
    private static final ClaveDocumento CLAVE = new ClaveDocumento(
            UUID.fromString("a0000000-0000-4000-8000-000000000001"), CarpetaDocumento.EVIDENCIAS,
            UUID.fromString("11111111-1111-4111-8111-111111111111"),
            UUID.fromString("22222222-2222-4222-8222-222222222222"));

    private static final SiglimPropiedades.Almacenamiento CONFIGURACION = new SiglimPropiedades.Almacenamiento(
            BUCKET, "us-east-2", "llave-de-prueba", "secreto-de-prueba", Duration.ofSeconds(60),
            DataSize.ofMegabytes(5), null);

    private final S3Client s3 = mock(S3Client.class);
    private final S3Presigner firmador = AlmacenamientoConfig.firmador(CONFIGURACION);
    private final AlmacenamientoDocumentosS3 almacenamiento =
            new AlmacenamientoDocumentosS3(s3, firmador, CONFIGURACION, Clock.fixed(AHORA, ZoneOffset.UTC));

    @AfterEach
    void cerrar() {
        firmador.close();
    }

    @Test
    @DisplayName("RF-108: un archivo mayor que el límite responde 413 sin llamar a S3")
    void demasiadoGrandeSinLlamarAS3() {
        byte[] grande = Arrays.copyOf(ArchivosPrueba.PDF, (int) DataSize.ofMegabytes(5).toBytes() + 1);

        assertThatThrownBy(() -> almacenamiento.guardar(CLAVE, grande))
                .isInstanceOfSatisfying(ArchivoRechazadoException.class,
                        error -> assertThat(error.estado()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
        verifyNoInteractions(s3);
    }

    @Test
    @DisplayName("RNF-018: un ejecutable renombrado responde 415 sin llamar a S3")
    void tipoNoPermitidoSinLlamarAS3() {
        assertThatThrownBy(() -> almacenamiento.guardar(CLAVE, ArchivosPrueba.EJECUTABLE))
                .isInstanceOfSatisfying(ArchivoRechazadoException.class,
                        error -> assertThat(error.estado()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        verifyNoInteractions(s3);
    }

    @Test
    @DisplayName("RF-108: un archivo vacío responde 400 sin llamar a S3")
    void vacioSinLlamarAS3() {
        assertThatThrownBy(() -> almacenamiento.guardar(CLAVE, new byte[0]))
                .isInstanceOfSatisfying(ArchivoRechazadoException.class,
                        error -> assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(s3);
    }

    @Test
    @DisplayName("RF-108: sube con la clave del tipo detectado, su Content-Type, tamaño y checksum SHA-256")
    void subeConClaveTipoYChecksum() throws Exception {
        given(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        byte[] resumen = MessageDigest.getInstance("SHA-256").digest(ArchivosPrueba.PDF);

        almacenamiento.guardar(CLAVE, ArchivosPrueba.PDF);

        ArgumentCaptor<PutObjectRequest> peticion = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(peticion.capture(), any(RequestBody.class));
        assertThat(peticion.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(peticion.getValue().key()).isEqualTo(CLAVE.valor(TipoArchivo.PDF));
        assertThat(peticion.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(peticion.getValue().contentLength()).isEqualTo(ArchivosPrueba.PDF.length);
        assertThat(peticion.getValue().checksumSHA256()).isEqualTo(Base64.getEncoder().encodeToString(resumen));
    }

    @Test
    @DisplayName("ADR-0013: devuelve los datos de siglim.documentos con el SHA-256 y la URL del objeto")
    void devuelveArchivoAlmacenado() throws Exception {
        given(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ArchivosPrueba.PDF));
        String clave = CLAVE.valor(TipoArchivo.PDF);

        ArchivoAlmacenado archivo = almacenamiento.guardar(CLAVE, ArchivosPrueba.PDF);

        assertThat(archivo).isEqualTo(new ArchivoAlmacenado(BUCKET, clave,
                "https://" + BUCKET + ".s3.us-east-2.amazonaws.com/" + clave, "application/pdf",
                ArchivosPrueba.PDF.length, sha256));
        assertThat(archivo.sha256()).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("ADR-0013: un error de S3 responde 503 SERVICIO_NO_DISPONIBLE")
    void errorDeS3EsServicioNoDisponible() {
        given(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(SdkClientException.create("sin conexión"));

        assertThatThrownBy(() -> almacenamiento.guardar(CLAVE, ArchivosPrueba.PNG))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    @DisplayName("RNF-018: el enlace prefirmado vence a los 60 s y expiraEn es el reloj más la vigencia")
    void enlaceConVigencia() {
        EnlaceTemporal enlace = almacenamiento.generarEnlaceLectura(BUCKET, CLAVE.valor(TipoArchivo.PDF));

        assertThat(URI.create(enlace.url()).getQuery()).contains("X-Amz-Expires=60");
        assertThat(enlace.url()).startsWith("https://" + BUCKET + ".s3.us-east-2.amazonaws.com/liga/");
        assertThat(enlace.expiraEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA.plusSeconds(60), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("ADR-0013: no firma enlaces de un bucket distinto del configurado")
    void rechazaBucketAjeno() {
        assertThatThrownBy(() -> almacenamiento.generarEnlaceLectura("otro-bucket", "liga/x/registros/y/z.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

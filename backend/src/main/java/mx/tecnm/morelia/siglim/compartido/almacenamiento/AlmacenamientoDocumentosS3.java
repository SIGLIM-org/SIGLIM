package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ArchivoRechazadoException;
import mx.tecnm.morelia.siglim.compartido.error.ServicioNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * {@link AlmacenamientoDocumentos} sobre AWS S3 con el AWS SDK para Java v2.
 *
 * <p>Valida antes de llamar a S3, en este orden: vacío (400), tamaño (413) y firma binaria (415).
 * Sube con el {@code Content-Type} del tipo detectado y el SHA-256 en {@code x-amz-checksum-sha256}
 * para que S3 verifique la integridad. Los errores del SDK se responden como 503
 * {@code SERVICIO_NO_DISPONIBLE}.
 *
 * <p>No autoriza, no audita, no borra ni escribe en la base de datos (ver la interfaz). En bitácoras
 * solo va la clave, que no tiene datos personales; nunca la URL prefirmada, las credenciales ni el
 * contenido (RNF-011).
 *
 * <p>Refs: T-04, ADR-0013, RNF-018, RF-108
 */
@Component
class AlmacenamientoDocumentosS3 implements AlmacenamientoDocumentos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoDocumentosS3.class);

    private final S3Client s3;
    private final S3Presigner firmador;
    private final SiglimPropiedades.Almacenamiento configuracion;
    private final Clock reloj;
    private final S3Utilities utilidades;

    @Autowired
    AlmacenamientoDocumentosS3(S3Client s3, S3Presigner firmador, SiglimPropiedades propiedades, Clock reloj) {
        this(s3, firmador, propiedades.almacenamiento(), reloj);
    }

    AlmacenamientoDocumentosS3(S3Client s3, S3Presigner firmador, SiglimPropiedades.Almacenamiento configuracion,
            Clock reloj) {
        this.s3 = s3;
        this.firmador = firmador;
        this.configuracion = configuracion;
        this.reloj = reloj;
        this.utilidades = S3Utilities.builder().region(Region.of(configuracion.region())).build();
    }

    @Override
    public ArchivoAlmacenado guardar(ClaveDocumento clave, byte[] contenido) {
        Objects.requireNonNull(clave, "clave es obligatoria");
        Objects.requireNonNull(contenido, "contenido es obligatorio");
        if (contenido.length == 0) {
            throw ArchivoRechazadoException.vacio();
        }
        if (contenido.length > configuracion.tamanoMaximo().toBytes()) {
            throw ArchivoRechazadoException.demasiadoGrande(configuracion.tamanoMaximo());
        }
        TipoArchivo tipo = DetectorTipoArchivo.detectar(contenido)
                .orElseThrow(ArchivoRechazadoException::tipoNoPermitido);

        String s3Clave = clave.valor(tipo);
        byte[] resumen = sha256(contenido);
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(configuracion.bucket())
                            .key(s3Clave)
                            .contentType(tipo.mime())
                            .contentLength((long) contenido.length)
                            .checksumSHA256(Base64.getEncoder().encodeToString(resumen))
                            .build(),
                    RequestBody.fromBytes(contenido));
        } catch (SdkException error) {
            throw new ServicioNoDisponibleException("No se pudo subir a S3 el documento " + s3Clave, error);
        }
        log.info("Documento subido a S3: {} ({} bytes, {})", s3Clave, contenido.length, tipo.mime());

        String url = utilidades.getUrl(GetUrlRequest.builder().bucket(configuracion.bucket()).key(s3Clave).build())
                .toExternalForm();
        return new ArchivoAlmacenado(configuracion.bucket(), s3Clave, url, tipo.mime(), contenido.length,
                HexFormat.of().formatHex(resumen));
    }

    @Override
    public EnlaceTemporal generarEnlaceLectura(String s3Bucket, String s3Clave) {
        if (!configuracion.bucket().equals(s3Bucket)) {
            throw new IllegalArgumentException("El documento no está en el bucket configurado: " + s3Bucket);
        }
        Objects.requireNonNull(s3Clave, "s3Clave es obligatoria");
        try {
            String url = firmador.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(configuracion.vigenciaEnlace())
                            .getObjectRequest(peticion -> peticion.bucket(s3Bucket).key(s3Clave))
                            .build())
                    .url()
                    .toExternalForm();
            return new EnlaceTemporal(url, OffsetDateTime.now(reloj).plus(configuracion.vigenciaEnlace()));
        } catch (SdkException error) {
            throw new ServicioNoDisponibleException("No se pudo firmar el enlace de lectura de " + s3Clave, error);
        }
    }

    private static byte[] sha256(byte[] contenido) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(contenido);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("La JVM no ofrece SHA-256", error);
        }
    }
}

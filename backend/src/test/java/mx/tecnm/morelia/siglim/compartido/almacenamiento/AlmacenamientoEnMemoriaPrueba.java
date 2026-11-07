package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mx.tecnm.morelia.siglim.compartido.error.ArchivoRechazadoException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Doble de AWS S3 para las pruebas de integración con base de datos (H-31.1, DEC-56).
 *
 * <p>{@code BaseDatosPrueba} lo importa para que todas las {@code *IT} compartan un solo contexto de
 * Spring y ninguna llame a AWS. El bean {@link Primary} reemplaza a {@link AlmacenamientoDocumentosS3}
 * en la inyección; el cliente real se crea pero nunca se usa.
 *
 * <p>Imita lo que importa del almacenamiento real: rechaza un archivo vacío (400) o con una firma no
 * permitida (415) con las mismas excepciones, arma la clave con {@link ClaveDocumento#valor} y
 * devuelve un {@link ArchivoAlmacenado} que cumple los CHECK de {@code siglim.documentos} (URL
 * {@code https://}, SHA-256 real de 64 hexadecimales, MIME permitido). Guarda los objetos en memoria
 * para que la prueba compruebe qué se subió.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AlmacenamientoEnMemoriaPrueba {

    public static final String BUCKET = "siglim-documentos-prueba";

    @Bean
    @Primary
    AlmacenamientoEnMemoria almacenamientoEnMemoria() {
        return new AlmacenamientoEnMemoria();
    }

    /** Almacenamiento en memoria; {@link #objetos()} expone lo subido, por clave. */
    public static final class AlmacenamientoEnMemoria implements AlmacenamientoDocumentos {

        private final Map<String, byte[]> objetos = new ConcurrentHashMap<>();

        @Override
        public ArchivoAlmacenado guardar(ClaveDocumento clave, byte[] contenido) {
            if (contenido == null || contenido.length == 0) {
                throw ArchivoRechazadoException.vacio();
            }
            TipoArchivo tipo = DetectorTipoArchivo.detectar(contenido)
                    .orElseThrow(ArchivoRechazadoException::tipoNoPermitido);
            String s3Clave = clave.valor(tipo);
            objetos.put(s3Clave, contenido.clone());
            return new ArchivoAlmacenado(BUCKET, s3Clave,
                    "https://" + BUCKET + ".s3.us-east-2.amazonaws.com/" + s3Clave, tipo.mime(), contenido.length,
                    sha256(contenido));
        }

        @Override
        public EnlaceTemporal generarEnlaceLectura(String s3Bucket, String s3Clave) {
            throw new UnsupportedOperationException("El doble de S3 no genera enlaces prefirmados");
        }

        /** Objetos subidos durante las pruebas, por clave. */
        public Map<String, byte[]> objetos() {
            return objetos;
        }

        private static String sha256(byte[] contenido) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 siempre está disponible", e);
            }
        }
    }
}

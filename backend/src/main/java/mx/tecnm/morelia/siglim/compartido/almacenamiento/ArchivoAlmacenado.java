package mx.tecnm.morelia.siglim.compartido.almacenamiento;

/**
 * Lo que queda de un archivo subido, con los nombres de las columnas de {@code siglim.documentos}
 * para que el servicio que lo usa inserte la fila directamente (ADR-0013 punto 5).
 *
 * @param s3Bucket    bucket
 * @param s3Clave     clave del objeto
 * @param url         {@code https://<bucket>.s3.<región>.amazonaws.com/<clave>}: referencia, no es
 *                    pública; se lee con una URL prefirmada
 * @param tipoMime    MIME del tipo detectado por firma binaria
 * @param tamanoBytes tamaño en bytes
 * @param sha256      SHA-256 del contenido, 64 caracteres hexadecimales en minúsculas
 */
public record ArchivoAlmacenado(String s3Bucket, String s3Clave, String url, String tipoMime, int tamanoBytes,
                                String sha256) {
}

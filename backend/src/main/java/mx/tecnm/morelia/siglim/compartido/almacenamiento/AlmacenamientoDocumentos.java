package mx.tecnm.morelia.siglim.compartido.almacenamiento;

/**
 * Almacenamiento privado de documentos e imágenes (ADR-0013). Los módulos no conocen al proveedor.
 *
 * <p><b>Es infraestructura:</b> no autoriza, no audita, no borra ni escribe en la base de datos. El
 * servicio que lo usa exige el permiso y el ámbito (ADR-0007), registra la fila de
 * {@code siglim.documentos} con el {@link ArchivoAlmacenado} y audita, todo en su transacción.
 *
 * <p><b>Objetos huérfanos:</b> la subida no participa de la transacción. Si la transacción de quien
 * llama falla después de subir, el objeto queda en el bucket sin fila que lo referencie. No se borra a
 * propósito (el usuario IAM no tiene {@code s3:DeleteObject}); se depura con reglas de ciclo de vida de
 * S3 según la política de retención de la Liga (PEN-040).
 *
 * <p>Refs: T-04, ADR-0013, RNF-018, RF-108
 */
public interface AlmacenamientoDocumentos {

    /**
     * Valida el archivo y lo sube a S3 con su tipo, tamaño y SHA-256.
     *
     * @param clave     dónde guardarlo; la extensión sale del tipo detectado
     * @param contenido bytes del archivo (a lo más el tamaño máximo configurado)
     * @return los datos para la fila de {@code siglim.documentos}
     * @throws mx.tecnm.morelia.siglim.compartido.error.ArchivoRechazadoException     400 si está
     *         vacío, 413 si supera el tamaño máximo o 415 si su firma no es JPEG, PNG, WebP o PDF
     * @throws mx.tecnm.morelia.siglim.compartido.error.ServicioNoDisponibleException si S3 falla
     */
    ArchivoAlmacenado guardar(ClaveDocumento clave, byte[] contenido);

    /**
     * Genera una URL prefirmada de {@code GET} con la vigencia configurada (RNF-018).
     *
     * @param s3Bucket bucket guardado en {@code siglim.documentos}; debe ser el configurado
     * @param s3Clave  clave guardada en {@code siglim.documentos}
     * @throws IllegalArgumentException si el bucket no es el configurado
     * @throws mx.tecnm.morelia.siglim.compartido.error.ServicioNoDisponibleException si no se puede
     *         firmar (por ejemplo, sin credenciales)
     */
    EnlaceTemporal generarEnlaceLectura(String s3Bucket, String s3Clave);
}

package mx.tecnm.morelia.siglim.compartido.almacenamiento;

/**
 * Tipos de archivo permitidos; los MIME coinciden con el CHECK {@code ck_documentos_tipo} de
 * {@code siglim.documentos} (ADR-0013 punto 4).
 *
 * <p>Refs: T-04, ADR-0013, RF-108
 */
public enum TipoArchivo {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf");

    private final String mime;
    private final String extension;

    TipoArchivo(String mime, String extension) {
        this.mime = mime;
        this.extension = extension;
    }

    public String mime() {
        return mime;
    }

    /** Extensión de la clave en S3, sin punto. */
    public String extension() {
        return extension;
    }
}

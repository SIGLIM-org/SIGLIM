package mx.tecnm.morelia.siglim.compartido.almacenamiento;

/**
 * Carpeta del documento dentro de la liga, segundo nivel de la clave en S3 (ADR-0013 punto 3).
 *
 * <p>Refs: T-04, ADR-0013
 */
public enum CarpetaDocumento {
    REGISTROS("registros"),
    TUTORES("tutores"),
    AFILIACIONES("afiliaciones"),
    EVIDENCIAS("evidencias"),
    COMPROBANTES("comprobantes");

    private final String segmento;

    CarpetaDocumento(String segmento) {
        this.segmento = segmento;
    }

    /** Segmento de la clave, en minúsculas. */
    public String segmento() {
        return segmento;
    }
}

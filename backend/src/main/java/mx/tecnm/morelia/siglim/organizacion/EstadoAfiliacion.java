package mx.tecnm.morelia.siglim.organizacion;

/**
 * Estado del tarjetón de afiliación, igual que el enum {@code siglim.estado_afiliacion}.
 *
 * <p>La máquina de estados es {@code SOLICITADA → APROBADA} o {@code SOLICITADA → RECHAZADA}; ambos
 * estados finales son terminales. No hay borrado: el tarjetón se conserva con su folio (RN-005).
 *
 * <p>Refs: H-08.1, RF-101, CU-018, DEC-09
 */
public enum EstadoAfiliacion {

    SOLICITADA("solicitada"),
    APROBADA("aprobada"),
    RECHAZADA("rechazada");

    private final String texto;

    EstadoAfiliacion(String texto) {
        this.texto = texto;
    }

    /** Texto para los mensajes a la persona, en minúsculas. */
    public String texto() {
        return texto;
    }
}

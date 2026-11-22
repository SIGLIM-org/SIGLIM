package mx.tecnm.morelia.siglim.arbitraje;

/**
 * Estado del árbitro, igual que el enum {@code siglim.estado_arbitro}.
 *
 * <p>El club presenta al árbitro y queda {@code PROPUESTO}; la Comisión de Arbitraje lo valida o lo
 * rechaza. Solo un árbitro {@code VALIDADO} puede designarse a un partido (RF-113). {@code BAJA} se
 * conserva para el catálogo histórico, pero no se produce desde esta historia.
 *
 * <p>Refs: H-11.1, RF-021, RF-112, RF-113
 */
public enum EstadoArbitro {

    PROPUESTO("propuesto"),
    VALIDADO("validado"),
    RECHAZADO("rechazado"),
    BAJA("baja");

    private final String texto;

    EstadoArbitro(String texto) {
        this.texto = texto;
    }

    /** Texto para los mensajes a la persona, en minúsculas. */
    public String texto() {
        return texto;
    }
}

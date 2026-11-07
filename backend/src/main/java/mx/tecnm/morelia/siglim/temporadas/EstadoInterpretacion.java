package mx.tecnm.morelia.siglim.temporadas;

/**
 * Estado de una interpretación de regla, igual que el enum {@code siglim.estado_interpretacion}.
 *
 * <p>Toda interpretación nace {@code PENDIENTE} y solo el Presidente la resuelve, como
 * {@code AUTORIZADA} o {@code RECHAZADA}; una resolución no se reabre (RF-007, RN-006).
 *
 * <p>Refs: B-03.1, RF-007, RN-006
 */
public enum EstadoInterpretacion {

    PENDIENTE("pendiente de autorización"),
    AUTORIZADA("autorizada"),
    RECHAZADA("rechazada");

    private final String texto;

    EstadoInterpretacion(String texto) {
        this.texto = texto;
    }

    /** Texto para los mensajes a la persona, en minúsculas. */
    public String texto() {
        return texto;
    }

    /** Si ya tiene la resolución del Presidente: deja de admitir otra (RF-007). */
    public boolean resuelta() {
        return this != PENDIENTE;
    }
}

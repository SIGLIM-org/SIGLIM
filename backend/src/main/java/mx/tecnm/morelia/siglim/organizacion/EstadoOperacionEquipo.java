package mx.tecnm.morelia.siglim.organizacion;

/**
 * Estado de una operación de equipo, igual que el enum {@code siglim.estado_operacion_equipo}.
 *
 * <p>Máquina de estados (RN-040): {@code SOLICITADA → AUTORIZADA} o {@code SOLICITADA → RECHAZADA}
 * por el Presidente; una operación {@code AUTORIZADA} pasa a {@code APLICADA} al cerrar la
 * temporada, sin perder el historial de propietarios. La aplicación al cierre es de H-04.1; esta
 * subtarea solo registra y resuelve la solicitud.
 *
 * <p>Refs: B-05.1, RF-103, RF-104, RN-040
 */
public enum EstadoOperacionEquipo {

    SOLICITADA("solicitada"),
    AUTORIZADA("autorizada"),
    RECHAZADA("rechazada"),
    APLICADA("aplicada");

    private final String texto;

    EstadoOperacionEquipo(String texto) {
        this.texto = texto;
    }

    /** Texto para los mensajes a la persona, en minúsculas. */
    public String texto() {
        return texto;
    }
}

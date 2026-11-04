package mx.tecnm.morelia.siglim.publicacion;

/**
 * Estado de publicación de un comunicado, igual que el enum {@code siglim.estado_publicacion}.
 *
 * <p>Las transiciones válidas son {@code BORRADOR → REVISION → PUBLICADO} y
 * {@code REVISION → BORRADOR} cuando el Presidente devuelve el comunicado con observaciones
 * (ERS §4.3.6). Las valida {@code tg_comunicados_publicacion} además del servicio.
 *
 * <p>Refs: B-27.1, RF-090, UI-031
 */
public enum EstadoPublicacion {

    BORRADOR("borrador"),
    REVISION("revisión"),
    PUBLICADO("publicado");

    private final String texto;

    EstadoPublicacion(String texto) {
        this.texto = texto;
    }

    /** Texto para los mensajes a la persona, en minúsculas. */
    public String texto() {
        return texto;
    }
}

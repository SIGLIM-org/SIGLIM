package mx.tecnm.morelia.siglim.programacion;

/**
 * Estado de publicación de una jornada, igual que el enum {@code siglim.estado_publicacion}.
 *
 * <p>Las transiciones válidas son {@code BORRADOR → REVISION → PUBLICADO} y
 * {@code REVISION → BORRADOR} cuando el Presidente devuelve la jornada con observaciones
 * (ERS §4.3.6). Las valida {@code tg_jornadas_publicacion} además del servicio.
 *
 * <p>Refs: H-12.1, RF-090, RF-121
 */
public enum EstadoJornada {

    BORRADOR("borrador"),
    REVISION("revisión"),
    PUBLICADO("publicado");

    private final String texto;

    EstadoJornada(String texto) {
        this.texto = texto;
    }

    /** Texto para los mensajes a la persona, en minúsculas. */
    public String texto() {
        return texto;
    }
}

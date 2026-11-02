package mx.tecnm.morelia.siglim.temporadas;

/**
 * Estado de una temporada, igual que el enum {@code siglim.estado_temporada}.
 *
 * <p>Las transiciones válidas son {@code CONFIGURACION → ACTIVA → CERRADA} y las valida
 * {@code tg_temporadas_estado} además del servicio (RF-001, RN-058).
 *
 * <p>Refs: H-04.1, RF-001, RF-003, RN-058
 */
public enum EstadoTemporada {

    CONFIGURACION("configuración"),
    ACTIVA("activa"),
    CERRADA("cerrada");

    private final String texto;

    EstadoTemporada(String texto) {
        this.texto = texto;
    }

    /** Texto para los mensajes a la persona, en minúsculas. */
    public String texto() {
        return texto;
    }
}

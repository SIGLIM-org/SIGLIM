package mx.tecnm.morelia.siglim.organizacion;

/**
 * Tipo de la operación sobre un equipo, igual que el enum {@code siglim.tipo_operacion_equipo}.
 *
 * <ul>
 *   <li>{@link #VENTA}: cambia el propietario del equipo; exige un club destino distinto del actual.</li>
 *   <li>{@link #CONSERVACION_PLAZA}: conserva la categoría y la plaza deportiva; se registra como
 *       operación <b>independiente</b> de la venta y no lleva club destino (RF-104, DEC-14).</li>
 * </ul>
 *
 * <p>Refs: B-05.1, RF-103, RF-104, RN-040, DEC-14
 */
public enum TipoOperacionEquipo {

    VENTA,
    CONSERVACION_PLAZA;

    /** Requisito del ERS que rige esta operación, para los mensajes de error y la trazabilidad. */
    public String requisito() {
        return this == VENTA ? "RF-103" : "RF-104";
    }
}

package mx.tecnm.morelia.siglim.partidos;

/**
 * Estado de la cédula del partido ({@code siglim.estado_cedula}, ERS §4.3.2, DEC-19).
 *
 * <p>{@code EN_CAPTURA → PENDIENTE_VALIDACION → APROBADA | DEVUELTA}, y {@code DEVUELTA → EN_CAPTURA}.
 * El contenido (alineación, marcador, eventos e incidencias) solo se modifica en {@link #EN_CAPTURA}
 * (RF-122); el trigger {@code tg_cedula_en_captura} es la última garantía.
 *
 * <p>Refs: H-16.1, RF-025, RF-122, DEC-19
 */
public enum EstadoCedula {
    EN_CAPTURA,
    PENDIENTE_VALIDACION,
    DEVUELTA,
    APROBADA
}

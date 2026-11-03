package mx.tecnm.morelia.siglim.organizacion;

/**
 * Estado de un club, igual que el enum {@code siglim.estado_club}.
 *
 * <p>Un club nace {@code ACTIVO}. Pasa a {@code CERRADO} cuando cambia de nombre (RN-041, B-04): el
 * club cerrado conserva su historial y no recibe equipos nuevos.
 *
 * <p>Refs: H-06.1, RF-008, RN-041, DEC-15
 */
public enum EstadoClub {
    ACTIVO,
    CERRADO
}

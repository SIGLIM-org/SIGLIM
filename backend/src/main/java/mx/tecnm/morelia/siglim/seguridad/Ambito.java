package mx.tecnm.morelia.siglim.seguridad;

/**
 * Ámbito de una asignación de rol (RN-054, ADR-0007).
 *
 * <p>Coincide con el enum {@code siglim.ambito} de la base de datos y con el de
 * {@link Autorizacion.AlcanceAutorizacion}: {@code LIGA} alcanza toda la liga, {@code CLUB} solo los
 * clubes indicados y {@code PROPIO} solo los recursos de los que la persona es titular.
 *
 * <p>Refs: H-03.1, RF-094, RN-054, ADR-0007
 */
public enum Ambito {
    LIGA,
    CLUB,
    PROPIO
}

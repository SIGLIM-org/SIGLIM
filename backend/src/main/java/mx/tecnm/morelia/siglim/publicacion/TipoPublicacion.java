package mx.tecnm.morelia.siglim.publicacion;

/**
 * Qué se somete a la aprobación del Presidente. {@code JORNADA} y {@code COMUNICADO} son los valores
 * del enum {@code siglim.entidad_publicable}; {@code FECHA} agrupa las jornadas de una fecha de
 * programación, que se aprueban completas (H-35.3, DEC-175) y no tiene historial propio: cada jornada
 * deja el suyo.
 *
 * <p>Refs: H-13.1, H-35.3, RF-090, RF-121, DEC-175
 */
public enum TipoPublicacion {

    JORNADA,
    COMUNICADO,
    FECHA
}

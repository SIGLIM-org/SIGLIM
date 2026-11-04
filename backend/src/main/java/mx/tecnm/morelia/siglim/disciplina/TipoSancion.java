package mx.tecnm.morelia.siglim.disciplina;

/**
 * Tipo de sanción de un artículo del reglamento (DEC-30). Son los valores del enum
 * {@code siglim.tipo_sancion}; el frontend los traduce a texto (convenciones de API §9.3).
 */
public enum TipoSancion {
    SUSPENSION_PARTIDOS,
    MULTA,
    PERDIDA_PUNTOS,
    PERDIDA_PARTIDO,
    BAJA
}

package mx.tecnm.morelia.siglim.temporadas;

/**
 * Criterio de desempate de la tabla de posiciones, igual que el enum
 * {@code siglim.criterio_desempate}. {@code calcular_tabla()} los aplica en el orden configurado en
 * la versión vigente de los parámetros.
 *
 * <p>Ningún criterio usa el nombre del equipo (RF-050): un valor fuera de esta lista, como un orden
 * alfabético, lo rechaza la lectura del JSON con {@code 400 VALIDACION}.
 *
 * <p>Refs: H-05.2, RF-050, DEC-27
 */
public enum CriterioDesempate {
    PUNTOS,
    DIFERENCIA_GOLES,
    GOLES_FAVOR,
    MENOS_GOLES_CONTRA,
    PARTIDOS_GANADOS,
    ENFRENTAMIENTO_DIRECTO,
    MENOS_EXPULSIONES,
    SORTEO
}

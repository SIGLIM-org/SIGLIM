package mx.tecnm.morelia.siglim.temporadas;

import java.util.List;

/**
 * Valores por defecto de {@code parametros_temporada} (los {@code DEFAULT} de la migración), para
 * precargar el formulario de la primera versión (D8). Son [DEMO] (DEC-24 a DEC-29), sin categorías
 * propias ni motivo.
 *
 * <p>Refs: H-05.2, RF-100, DEC-24 a DEC-29
 */
public record ParametrosPredeterminadosDto(
        int puntosVictoria,
        int puntosEmpate,
        int puntosDerrota,
        List<CriterioDesempate> criteriosDesempate,
        int golesDefaultGanador,
        int golesDefaultPerdedor,
        int plazoValidacionCedulaHoras,
        int duracionJuegoMin,
        int bloqueCanchaMin,
        int clasificadosLiguilla,
        FormatoLiguilla formatoLiguilla,
        boolean empateLiguillaPorPenales,
        int equiposAscienden,
        int equiposDescienden,
        boolean permiteCambioClub,
        boolean esDemo) {

    /**
     * Los {@code DEFAULT} de {@code parametros_temporada} en la migración
     * {@code 20261020090000_esquema_inicial.sql}; {@code ParametrosIT} comprueba que coincidan.
     */
    static final ParametrosPredeterminadosDto DE_LA_TABLA = new ParametrosPredeterminadosDto(
            3, 1, 0,
            List.of(CriterioDesempate.PUNTOS, CriterioDesempate.DIFERENCIA_GOLES, CriterioDesempate.GOLES_FAVOR,
                    CriterioDesempate.ENFRENTAMIENTO_DIRECTO, CriterioDesempate.MENOS_EXPULSIONES,
                    CriterioDesempate.SORTEO),
            3, 0, 48, 90, 120, 8, FormatoLiguilla.PARTIDO_UNICO, true, 2, 2, true, true);

    public ParametrosPredeterminadosDto {
        criteriosDesempate = List.copyOf(criteriosDesempate);
    }
}

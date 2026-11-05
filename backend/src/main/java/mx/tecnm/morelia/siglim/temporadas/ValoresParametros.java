package mx.tecnm.morelia.siglim.temporadas;

import java.util.List;
import java.util.UUID;

/**
 * Valores de una versión de parámetros ya validados, sin identidad ni motivo. Es la forma común con
 * la que {@link ParametrosService} compara la versión nueva con la anterior ({@code camposCambiados})
 * y con la que {@link ParametrosRepository} la inserta.
 *
 * <p>Refs: H-05.2, RF-006, RF-100
 *
 * @param categorias filas propias por categoría; vacía si todas usan los valores de la temporada
 */
record ValoresParametros(
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
        boolean esDemo,
        List<ValorCategoria> categorias) {

    ValoresParametros {
        criteriosDesempate = List.copyOf(criteriosDesempate);
        categorias = List.copyOf(categorias);
    }

    /** Los mismos valores con otras filas por categoría. */
    ValoresParametros conCategorias(List<ValorCategoria> otras) {
        return new ValoresParametros(puntosVictoria, puntosEmpate, puntosDerrota, criteriosDesempate,
                golesDefaultGanador, golesDefaultPerdedor, plazoValidacionCedulaHoras, duracionJuegoMin,
                bloqueCanchaMin, clasificadosLiguilla, formatoLiguilla, empateLiguillaPorPenales, equiposAscienden,
                equiposDescienden, permiteCambioClub, esDemo, otras);
    }

    /** Duración de juego y bloque de cancha de una categoría (D7). */
    record ValorCategoria(UUID categoriaId, int duracionJuegoMin, int bloqueCanchaMin) {
    }
}

package mx.tecnm.morelia.siglim.temporadas;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Valores de una versión de parámetros ya validados, sin identidad ni motivo. Es la forma común con
 * la que {@link ParametrosService} compara la versión nueva con la anterior ({@code camposCambiados})
 * y con la que {@link ParametrosRepository} la inserta.
 *
 * <p>Refs: H-05.2, H-32.3, RF-006, RF-100, DEC-174, DEC-176
 *
 * @param fechasFaseRegular            total de fechas de la fase regular; {@code null} si no se
 *                                     definió (RN-049)
 * @param fechasFinalesUnidadDeportiva últimas fechas sin canchas externas, 10 [DEMO] (VAL-027)
 * @param vueltas                      vueltas del calendario, de 1 a 4 (DEC-174)
 * @param horaInicioJuego              inicio del horario de juego por defecto (DEC-176)
 * @param horaFinJuego                 fin del horario de juego por defecto (DEC-176)
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
        int equiposPorArbitro,
        Integer fechasFaseRegular,
        int fechasFinalesUnidadDeportiva,
        int vueltas,
        LocalTime horaInicioJuego,
        LocalTime horaFinJuego,
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
                equiposDescienden, permiteCambioClub, equiposPorArbitro, fechasFaseRegular,
                fechasFinalesUnidadDeportiva, vueltas, horaInicioJuego, horaFinJuego, esDemo, otras);
    }

    /** Los mismos valores con otro divisor de la cuota de árbitros (RF-114). */
    ValoresParametros conEquiposPorArbitro(int otro) {
        return new ValoresParametros(puntosVictoria, puntosEmpate, puntosDerrota, criteriosDesempate,
                golesDefaultGanador, golesDefaultPerdedor, plazoValidacionCedulaHoras, duracionJuegoMin,
                bloqueCanchaMin, clasificadosLiguilla, formatoLiguilla, empateLiguillaPorPenales, equiposAscienden,
                equiposDescienden, permiteCambioClub, otro, fechasFaseRegular, fechasFinalesUnidadDeportiva, vueltas,
                horaInicioJuego, horaFinJuego, esDemo, categorias);
    }

    /** Los mismos valores con otros parámetros de localía (RF-120, RN-049). */
    ValoresParametros conLocalia(Integer otrasFechasFaseRegular, int otrasFechasFinales) {
        return new ValoresParametros(puntosVictoria, puntosEmpate, puntosDerrota, criteriosDesempate,
                golesDefaultGanador, golesDefaultPerdedor, plazoValidacionCedulaHoras, duracionJuegoMin,
                bloqueCanchaMin, clasificadosLiguilla, formatoLiguilla, empateLiguillaPorPenales, equiposAscienden,
                equiposDescienden, permiteCambioClub, equiposPorArbitro, otrasFechasFaseRegular, otrasFechasFinales,
                vueltas, horaInicioJuego, horaFinJuego, esDemo, categorias);
    }

    /** Los mismos valores con otras vueltas y otro horario de juego (DEC-174, DEC-176). */
    ValoresParametros conCalendario(int otrasVueltas, LocalTime inicio, LocalTime fin) {
        return new ValoresParametros(puntosVictoria, puntosEmpate, puntosDerrota, criteriosDesempate,
                golesDefaultGanador, golesDefaultPerdedor, plazoValidacionCedulaHoras, duracionJuegoMin,
                bloqueCanchaMin, clasificadosLiguilla, formatoLiguilla, empateLiguillaPorPenales, equiposAscienden,
                equiposDescienden, permiteCambioClub, equiposPorArbitro, fechasFaseRegular,
                fechasFinalesUnidadDeportiva, otrasVueltas, inicio, fin, esDemo, categorias);
    }

    /** Duración de juego y bloque de cancha de una categoría (D7). */
    record ValorCategoria(UUID categoriaId, int duracionJuegoMin, int bloqueCanchaMin) {
    }
}

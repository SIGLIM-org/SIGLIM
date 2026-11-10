package mx.tecnm.morelia.siglim.temporadas;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.RegistrarParametrosRequest.CategoriaRequest;

/**
 * Fábrica de cuerpos de {@code POST /temporadas/{id}/parametros} para las pruebas (H-05.2, D11).
 *
 * <p>{@link #juego()} parte de un juego completo y válido; cada prueba cambia solo lo que quiere
 * romper. Las pruebas de integración que necesitan una temporada con parámetros la preparan con
 * {@link #valido()} y {@link ParametrosService}, no con un {@code INSERT} directo.
 */
final class ParametrosPrueba {

    static final String MOTIVO = "Parámetros de prueba de la temporada";

    private ParametrosPrueba() {
    }

    /** Juego completo y válido, con los valores por defecto de la tabla y sin categorías. */
    static RegistrarParametrosRequest valido() {
        return juego().peticion();
    }

    static Juego juego() {
        return new Juego();
    }

    /** Constructor mutable del cuerpo; los campos son públicos para que la prueba sea corta. */
    static final class Juego {
        Integer puntosVictoria = 3;
        Integer puntosEmpate = 1;
        Integer puntosDerrota = 0;
        List<CriterioDesempate> criteriosDesempate = new ArrayList<>(List.of(CriterioDesempate.PUNTOS,
                CriterioDesempate.DIFERENCIA_GOLES, CriterioDesempate.GOLES_FAVOR,
                CriterioDesempate.ENFRENTAMIENTO_DIRECTO, CriterioDesempate.MENOS_EXPULSIONES,
                CriterioDesempate.SORTEO));
        Integer golesDefaultGanador = 3;
        Integer golesDefaultPerdedor = 0;
        Integer plazoValidacionCedulaHoras = 48;
        Integer duracionJuegoMin = 90;
        Integer bloqueCanchaMin = 120;
        Integer clasificadosLiguilla = 8;
        FormatoLiguilla formatoLiguilla = FormatoLiguilla.PARTIDO_UNICO;
        Boolean empateLiguillaPorPenales = true;
        Integer equiposAscienden = 2;
        Integer equiposDescienden = 2;
        Boolean permiteCambioClub = true;
        Boolean esDemo;
        List<CategoriaRequest> categorias;
        String motivo = MOTIVO;

        Juego puntos(Integer victoria, Integer empate, Integer derrota) {
            puntosVictoria = victoria;
            puntosEmpate = empate;
            puntosDerrota = derrota;
            return this;
        }

        Juego criterios(CriterioDesempate... criterios) {
            criteriosDesempate = criterios == null ? null : new ArrayList<>(List.of(criterios));
            return this;
        }

        Juego goles(Integer ganador, Integer perdedor) {
            golesDefaultGanador = ganador;
            golesDefaultPerdedor = perdedor;
            return this;
        }

        Juego tiempos(Integer duracion, Integer bloque) {
            duracionJuegoMin = duracion;
            bloqueCanchaMin = bloque;
            return this;
        }

        Juego plazo(Integer horas) {
            plazoValidacionCedulaHoras = horas;
            return this;
        }

        Juego esDemo(Boolean valor) {
            esDemo = valor;
            return this;
        }

        Juego categoria(UUID categoriaId, Integer duracion, Integer bloque) {
            if (categorias == null) {
                categorias = new ArrayList<>();
            }
            categorias.add(new CategoriaRequest(categoriaId, duracion, bloque));
            return this;
        }

        Juego motivo(String texto) {
            motivo = texto;
            return this;
        }

        RegistrarParametrosRequest peticion() {
            return new RegistrarParametrosRequest(puntosVictoria, puntosEmpate, puntosDerrota,
                    criteriosDesempate == null ? null : List.copyOf(criteriosDesempate), golesDefaultGanador,
                    golesDefaultPerdedor, plazoValidacionCedulaHoras, duracionJuegoMin, bloqueCanchaMin,
                    clasificadosLiguilla, formatoLiguilla, empateLiguillaPorPenales, equiposAscienden,
                    equiposDescienden, permiteCambioClub, esDemo, categorias == null ? null : List.copyOf(categorias),
                    motivo);
        }
    }
}

package mx.tecnm.morelia.siglim.temporadas;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/temporadas/{id}/parametros}: el juego <b>completo</b> de una
 * versión nueva de parámetros deportivos (RF-006, RF-100). Nada se hereda de la versión anterior
 * (D3): todos los campos son obligatorios salvo {@code equiposPorArbitro}, los dos de localía, las
 * vueltas y el horario de juego (que, como {@code equiposPorArbitro} y la localía, si se omiten
 * conservan los de la versión vigente), {@code esDemo} y {@code categorias}. Las dos horas del
 * horario de juego van juntas o ninguna (DEC-194).
 *
 * <p>Los tipos son envolventes para distinguir «ausente» de cero. Las reglas (obligatorios, rangos,
 * orden de criterios, motivo) las valida {@link ParametrosService} después del permiso, para
 * responder {@code 400} con un mensaje claro (convenciones de API §6.2).
 *
 * <p>Refs: H-05.2, H-32.3, RF-006, RF-034, RF-050, RF-100, RN-021, DEC-174, DEC-176, DEC-194, DEC-195
 *
 * @param equiposPorArbitro equipos activos por cada árbitro validado (RF-114); si se omite,
 *                          conserva el de la versión vigente y la primera versión usa 4 [DEMO]
 * @param fechasFaseRegular total de fechas de la fase regular (RN-049); si se omite, conserva el de
 *                          la versión vigente y la primera versión lo deja sin definir
 * @param fechasFinalesUnidadDeportiva últimas fechas en que se advierte una cancha externa
 *                          (VAL-027); si se omite, conserva el de la versión vigente y la primera
 *                          versión usa 10 [DEMO]
 * @param vueltas    vueltas del calendario, de 1 a 4 (DEC-174); si se omite, conserva las de la
 *                   versión vigente y la primera versión usa 1
 * @param horaInicioJuego inicio del horario de juego por defecto, en horas y minutos (DEC-176); si
 *                   se omite junto con {@code horaFinJuego}, conserva el de la versión vigente y la
 *                   primera versión usa 08:00 [DEMO]
 * @param horaFinJuego fin del horario de juego, posterior al inicio; la primera versión usa 18:00
 *                   [DEMO]
 * @param esDemo     si los valores son de demostración y se rotulan «no oficial»; ausente =
 *                   {@code true} (D2)
 * @param categorias duración de juego y bloque de cancha de las categorías que no usan los de la
 *                   temporada (D7); una categoría ausente usa los de la temporada
 * @param motivo     por qué se registra la versión, de 10 a 500 caracteres (RN-021, D5)
 */
public record RegistrarParametrosRequest(
        Integer puntosVictoria,
        Integer puntosEmpate,
        Integer puntosDerrota,
        List<CriterioDesempate> criteriosDesempate,
        Integer golesDefaultGanador,
        Integer golesDefaultPerdedor,
        Integer plazoValidacionCedulaHoras,
        Integer duracionJuegoMin,
        Integer bloqueCanchaMin,
        Integer clasificadosLiguilla,
        FormatoLiguilla formatoLiguilla,
        Boolean empateLiguillaPorPenales,
        Integer equiposAscienden,
        Integer equiposDescienden,
        Boolean permiteCambioClub,
        Integer equiposPorArbitro,
        Integer fechasFaseRegular,
        Integer fechasFinalesUnidadDeportiva,
        Integer vueltas,
        LocalTime horaInicioJuego,
        LocalTime horaFinJuego,
        Boolean esDemo,
        List<CategoriaRequest> categorias,
        String motivo) {

    /** Duración de juego y bloque de cancha de una categoría asignada a la temporada (D7). */
    public record CategoriaRequest(UUID categoriaId, Integer duracionJuegoMin, Integer bloqueCanchaMin) {
    }
}

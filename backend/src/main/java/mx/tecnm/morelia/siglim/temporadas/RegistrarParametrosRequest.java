package mx.tecnm.morelia.siglim.temporadas;

import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/temporadas/{id}/parametros}: el juego <b>completo</b> de una
 * versión nueva de parámetros deportivos (RF-006, RF-100). Nada se hereda de la versión anterior
 * (D3): todos los campos son obligatorios salvo {@code esDemo} y {@code categorias}.
 *
 * <p>Los tipos son envolventes para distinguir «ausente» de cero. Las reglas (obligatorios, rangos,
 * orden de criterios, motivo) las valida {@link ParametrosService} después del permiso, para
 * responder {@code 400} con un mensaje claro (convenciones de API §6.2).
 *
 * <p>Refs: H-05.2, RF-006, RF-050, RF-100, RN-021
 *
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
        Boolean esDemo,
        List<CategoriaRequest> categorias,
        String motivo) {

    /** Duración de juego y bloque de cancha de una categoría asignada a la temporada (D7). */
    public record CategoriaRequest(UUID categoriaId, Integer duracionJuegoMin, Integer bloqueCanchaMin) {
    }
}

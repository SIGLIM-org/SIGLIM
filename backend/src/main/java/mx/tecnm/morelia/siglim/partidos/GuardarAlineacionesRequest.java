package mx.tecnm.morelia.siglim.partidos;

import java.util.List;
import java.util.UUID;

/**
 * Alineación completa de los dos equipos (PUT: reemplaza la anterior). La forma y la elegibilidad de
 * cada jugador las valida el servicio para responder un error por jugador (convenciones de API §6.8).
 *
 * <p>Refs: H-16.1, RF-019, RF-020, RF-110, VAL-002, VAL-018
 *
 * @param jugadores jugadores de ambos equipos; una lista vacía quita la alineación capturada
 */
public record GuardarAlineacionesRequest(List<JugadorRequest> jugadores) {

    /**
     * Un jugador de la alineación.
     *
     * @param registroJugadorId registro del jugador en la temporada del partido
     * @param equipoId          equipo con el que juega: el de su registro o, en la categoría inmediata
     *                          superior de su club, otro (RF-110)
     * @param numeroPlayera     de 0 a 99; opcional
     * @param titular           {@code true} si inicia el partido; si se omite, {@code true}
     */
    public record JugadorRequest(UUID registroJugadorId, UUID equipoId, Integer numeroPlayera, Boolean titular) {
    }
}

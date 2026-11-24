package mx.tecnm.morelia.siglim.partidos;

import java.util.UUID;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.ReferenciaDto;

/**
 * Jugador que se puede proponer para la alineación de la cédula (convenciones de API §6.8, H-16.3,
 * DEC-172), con su elegibilidad calculada con la misma regla de {@code PUT /cedulas/{id}/alineaciones}.
 * Nunca lleva la CURP.
 *
 * <p>Refs: H-16.3, RF-019, RF-020, RF-110, VAL-002, VAL-018, DEC-172
 *
 * @param equipoId          equipo del partido con el que jugaría
 * @param equipoRegistro    equipo de su registro; distinto de {@code equipoId} si viene de la categoría
 *                          inmediata inferior de su club
 * @param categoriaSuperior {@code true} si jugaría en la categoría inmediata superior (RF-110)
 * @param causa             por qué no puede jugar ({@code RF-019}, {@code RF-020} o {@code VAL-018});
 *                          {@code null} si es elegible
 */
public record JugadorDisponibleDto(UUID registroJugadorId, String nombre, UUID equipoId, ReferenciaDto equipoRegistro,
                                  boolean categoriaSuperior, boolean elegible, CausaDto causa) {

    /** Código del ERS y mensaje con el nombre del jugador. */
    public record CausaDto(String codigo, String mensaje) {
    }
}

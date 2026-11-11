package mx.tecnm.morelia.siglim.registros;

import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/registros-jugador} (RF-013). La temporada es la del grupo del
 * equipo. Los obligatorios los valida el servicio, después del permiso (DEC-85).
 *
 * <p>Refs: H-09.2, RF-013, DEC-85
 */
public record CrearRegistroJugadorRequest(UUID personaId, UUID equipoId) {
}

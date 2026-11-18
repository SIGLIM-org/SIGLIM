package mx.tecnm.morelia.siglim.registros;

import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/registros-jugador/renovaciones} y
 * {@code POST /api/v1/registros-jugador/transferencias} (RF-015, RF-016, DEC-139). La temporada es
 * la del grupo del equipo de destino; el registro anterior lo deduce el servicio. Los obligatorios
 * los valida el servicio, después del permiso (DEC-85).
 *
 * <p>Refs: B-06, RF-015, RF-016, DEC-139
 */
public record MovimientoRegistroRequest(UUID personaId, UUID equipoId) {
}

package mx.tecnm.morelia.siglim.partidos.eventos;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de dominio que publica el envío de la cédula a validación (RF-122).
 *
 * <p>Se publica dentro de la transacción del envío. Lo escucha {@code notificaciones} (H-25.1) para
 * avisar al árbitro central designado, sin que este módulo dependa de notificaciones (ADR-0006).
 *
 * <p>Refs: H-16.2, H-25.1, RF-122, ADR-0006
 *
 * @param ligaId       liga de la cédula
 * @param cedulaId     cédula enviada
 * @param partidoId    partido de la cédula, para encontrar al árbitro designado
 * @param plazoVenceEn fin del plazo del árbitro para validarla (RF-124)
 */
public record CedulaEnviada(UUID ligaId, UUID cedulaId, UUID partidoId, OffsetDateTime plazoVenceEn) {
}

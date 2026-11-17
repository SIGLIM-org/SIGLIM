package mx.tecnm.morelia.siglim.programacion.eventos;

import java.util.UUID;

/**
 * Evento de dominio que publica {@code JornadaService} cuando el Presidente aprueba una jornada y
 * queda {@code PUBLICADO} (RF-121, CU-007).
 *
 * <p>Se publica dentro de la misma transacción que cambia el estado: el oyente síncrono de
 * {@code tesoreria} genera los cobros de arbitraje y cancha (RF-136) y, si falla, se revierte la
 * publicación completa (RNF-004, ADR-0006).
 *
 * <p>Refs: B-30, RF-121, RF-136, ADR-0006
 *
 * @param ligaId     liga de la jornada (ADR-0011)
 * @param jornadaId  jornada que se publicó
 * @param aprobadaPor perfil del Presidente que la aprobó
 */
public record JornadaPublicada(UUID ligaId, UUID jornadaId, UUID aprobadaPor) {
}

package mx.tecnm.morelia.siglim.partidos;

import java.util.UUID;

/**
 * Cuerpo de {@code POST /cedulas/{id}/eventos} (RF-025). El equipo se toma de la alineación del
 * jugador.
 *
 * <p>Refs: H-16.2, RF-025
 *
 * @param minuto            de 0 a 150; opcional
 * @param alineacionId      fila de la alineación del jugador del evento (el que sale, en una sustitución)
 * @param alineacionEntraId solo en una sustitución: el jugador que entra, del mismo equipo
 * @param observaciones     hasta 500 caracteres
 */
public record RegistrarEventoRequest(TipoEventoCedula tipo, Integer minuto, UUID alineacionId, UUID alineacionEntraId,
                                     String observaciones) {
}

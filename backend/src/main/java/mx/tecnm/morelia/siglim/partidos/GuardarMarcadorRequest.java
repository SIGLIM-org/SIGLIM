package mx.tecnm.morelia.siglim.partidos;

import java.util.UUID;

/**
 * Marcador de la cédula (PUT). Con un equipo no presentado, el marcador lo asigna el sistema con los
 * parámetros vigentes de la temporada y la petición no lleva goles ni penales (RF-126, DEC-24). Las
 * reglas las valida el servicio (convenciones de API §6.8).
 *
 * <p>Refs: H-16.1, RF-047, RF-126, DEC-24, DEC-28
 *
 * @param penalesLocal         solo si el partido terminó empatado; con el del visitante o sin ninguno
 * @param noPresentadoEquipoId uno de los dos equipos del partido, o {@code null} si ambos jugaron
 */
public record GuardarMarcadorRequest(Integer golesLocal, Integer golesVisitante, Integer penalesLocal,
                                     Integer penalesVisitante, UUID noPresentadoEquipoId) {
}

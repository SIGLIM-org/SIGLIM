package mx.tecnm.morelia.siglim.partidos;

import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /cedulas/{id}/incidencias} (RF-026).
 *
 * <p>Refs: H-16.2, RF-026
 *
 * @param tipoIncidenciaId tipo activo del catálogo de la liga (RF-128)
 * @param minuto           de 0 a 150; opcional
 * @param descripcion      obligatoria, hasta 1000 caracteres
 * @param involucrados     al menos uno: un jugador alineado en la cédula o uno de los dos equipos
 */
public record RegistrarIncidenciaRequest(Long tipoIncidenciaId, Integer minuto, String descripcion,
                                         List<InvolucradoRequest> involucrados) {

    /** Persona ({@code personaId}) o equipo ({@code equipoId}): exactamente uno de los dos. */
    public record InvolucradoRequest(UUID personaId, UUID equipoId) {
    }
}

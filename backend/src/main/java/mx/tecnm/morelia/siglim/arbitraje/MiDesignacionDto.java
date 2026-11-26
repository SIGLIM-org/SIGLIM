package mx.tecnm.morelia.siglim.arbitraje;

import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;

/**
 * Elemento de {@code GET /api/v1/mis-designaciones} (DEC-205): una designación vigente del árbitro de
 * la sesión en una jornada publicada, con el partido y, si ya se abrió, su cédula. {@code cedula} se
 * serializa {@code null} si no existe.
 *
 * <p>Refs: H-14.1, RF-023, RF-123, DEC-205
 *
 * @param designacionId designación
 * @param funcion       central o asistente
 * @param partido       partido designado
 * @param cedula        cédula del partido o {@code null}
 */
public record MiDesignacionDto(UUID designacionId, FuncionArbitral funcion, PartidoDto partido, CedulaDto cedula) {

    /** Partido con sus referencias, en la zona de negocio. */
    public record PartidoDto(UUID id, OffsetDateTime inicio, OffsetDateTime finBloque, EstadoPartido estado,
                             ReferenciaDto cancha, ReferenciaDto equipoLocal, ReferenciaDto equipoVisitante,
                             ReferenciaDto categoria, ReferenciaDto grupo, JornadaDto jornada) {
    }

    /** Jornada del partido. */
    public record JornadaDto(UUID id, int numero) {
    }

    /** Cédula del partido con su estado ({@code EN_CAPTURA}, {@code PENDIENTE_VALIDACION}…). */
    public record CedulaDto(UUID id, String estado) {
    }
}

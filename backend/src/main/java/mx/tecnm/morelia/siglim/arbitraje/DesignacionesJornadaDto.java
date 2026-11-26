package mx.tecnm.morelia.siglim.arbitraje;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.programacion.EstadoJornada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;

/**
 * Respuesta de {@code GET /api/v1/designaciones?jornadaId=} (UI-025, F-24): la jornada, el avance de
 * la designación y sus partidos no cancelados con el central y los asistentes vigentes.
 *
 * <p>Refs: H-14.1, RF-023, UI-025, DEC-181, DEC-201
 *
 * @param jornada  jornada con su grupo y categoría
 * @param avance   partidos listados, con central y con los dos asistentes
 * @param partidos partidos por inicio e identificador
 */
public record DesignacionesJornadaDto(JornadaDto jornada, AvanceDto avance, List<PartidoDto> partidos) {

    /**
     * @param id                jornada
     * @param numero            número de la jornada
     * @param estadoPublicacion borrador, revisión o publicado
     * @param grupo             grupo de la jornada
     * @param categoria         categoría del grupo
     */
    public record JornadaDto(UUID id, int numero, EstadoJornada estadoPublicacion, ReferenciaDto grupo,
                             ReferenciaDto categoria) {
    }

    /**
     * @param partidos            partidos no cancelados de la jornada
     * @param conCentral          partidos con árbitro central vigente
     * @param asistentesCompletos partidos con sus dos asistentes vigentes
     */
    public record AvanceDto(int partidos, int conCentral, int asistentesCompletos) {
    }

    /**
     * Partido con sus designaciones vigentes. {@code central} se serializa {@code null} si no hay.
     *
     * @param designable verdadero si el partido está {@code PROGRAMADO} o {@code REPROGRAMADO}: solo
     *                   así se designa o retira (DEC-201, DEC-203)
     */
    public record PartidoDto(UUID id, OffsetDateTime inicio, OffsetDateTime finBloque, EstadoPartido estado,
                             ReferenciaDto cancha, ReferenciaDto equipoLocal, ReferenciaDto equipoVisitante,
                             boolean designable, DesignacionDto central, List<DesignacionDto> asistentes) {
    }
}

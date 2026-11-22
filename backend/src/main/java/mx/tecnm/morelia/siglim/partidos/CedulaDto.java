package mx.tecnm.morelia.siglim.partidos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;

/**
 * Cédula del partido con su alineación y su marcador (convenciones de API §6.8, H-16.1).
 *
 * <p>Las fechas van en {@code America/Mexico_City}. Los jugadores se identifican por su nombre y su
 * registro; nunca se responde la CURP. Los eventos, las incidencias y el envío a validación los agrega
 * H-16.2.
 *
 * <p>Refs: H-16.1, RF-025, RF-047, RF-126, DEC-19, DEC-24
 *
 * @param capturista perfil que abrió la cédula; nunca podrá aprobarla (VAL-021)
 */
public record CedulaDto(
        UUID id,
        PartidoDto partido,
        EstadoCedula estado,
        ReferenciaDto capturista,
        MarcadorDto marcador,
        List<JugadorAlineadoDto> alineaciones,
        String observaciones,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn) {

    /** Identificador y nombre de una entidad relacionada (convenciones de API §9.1). */
    public record ReferenciaDto(UUID id, String nombre) {
    }

    /** Partido de la cédula: equipos, inicio y estado. */
    public record PartidoDto(UUID id, ReferenciaDto equipoLocal, ReferenciaDto equipoVisitante,
                             OffsetDateTime inicio, EstadoPartido estado) {
    }

    /**
     * Marcador capturado. Sin captura, los goles van nulos.
     *
     * @param noPresentado equipo que no se presentó (RF-126); {@code null} si ambos jugaron
     * @param porDefault   {@code true} si el marcador es el de no presentación, asignado con los
     *                     parámetros vigentes de la temporada (DEC-24)
     * @param valorDemo    {@code true} si ese marcador por default sale de parámetros de demostración:
     *                     la interfaz lo rotula «No oficial» (DEC-72)
     */
    public record MarcadorDto(Integer golesLocal, Integer golesVisitante, Integer penalesLocal,
                              Integer penalesVisitante, ReferenciaDto noPresentado, boolean porDefault,
                              boolean valorDemo) {
    }

    /**
     * Jugador alineado.
     *
     * @param categoriaSuperior {@code true} si juega en la categoría inmediata superior de su club, en
     *                          un equipo distinto del de su registro (RF-110, DEC-12)
     */
    public record JugadorAlineadoDto(UUID id, UUID registroJugadorId, String nombre, UUID equipoId,
                                     Integer numeroPlayera, boolean titular, boolean categoriaSuperior) {
    }
}

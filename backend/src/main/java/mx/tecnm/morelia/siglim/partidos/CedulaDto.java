package mx.tecnm.morelia.siglim.partidos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;

/**
 * Cédula del partido con su alineación, marcador, eventos e incidencias (convenciones de API §6.8,
 * H-16.1, H-16.2).
 *
 * <p>Las fechas van en {@code America/Mexico_City}. Los jugadores se identifican por su nombre y su
 * registro; nunca se responde la CURP.
 *
 * <p>Refs: H-16.1, H-16.2, RF-025, RF-026, RF-047, RF-122, RF-126, DEC-19, DEC-24
 *
 * @param capturista   perfil que abrió la cédula; nunca podrá aprobarla (VAL-021)
 * @param eventos      goles, tarjetas y sustituciones, por minuto (H-16.2)
 * @param incidencias  incidencias con su tipo e involucrados (H-16.2)
 * @param enviadaEn    último envío a validación; {@code null} mientras no se envía
 * @param plazoVenceEn fin del plazo del árbitro para validarla (RF-124); {@code null} sin envío
 */
public record CedulaDto(
        UUID id,
        PartidoDto partido,
        EstadoCedula estado,
        ReferenciaDto capturista,
        MarcadorDto marcador,
        List<JugadorAlineadoDto> alineaciones,
        List<EventoDto> eventos,
        List<IncidenciaDto> incidencias,
        String observaciones,
        OffsetDateTime enviadaEn,
        OffsetDateTime plazoVenceEn,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn) {

    /**
     * Gol, autogol, tarjeta o sustitución (RF-025).
     *
     * @param jugador jugador del evento (el que sale, en una sustitución): {@code id} es su fila de
     *                {@code alineaciones}
     * @param entra   en una sustitución, el jugador que entra; {@code null} en los demás
     */
    public record EventoDto(UUID id, TipoEventoCedula tipo, Integer minuto, UUID equipoId, ReferenciaDto jugador,
                            ReferenciaDto entra, String observaciones) {
    }

    /** Incidencia con su tipo del catálogo y las personas o equipos involucrados (RF-026). */
    public record IncidenciaDto(UUID id, TipoIncidenciaDto tipo, Integer minuto, String descripcion,
                                List<InvolucradoDto> involucrados) {
    }

    /** Tipo de incidencia; {@code abreExpediente} anticipa que irá a la Comisión Disciplinaria (DEC-22). */
    public record TipoIncidenciaDto(long id, String nombre, boolean abreExpediente) {
    }

    /** Persona o equipo involucrado: uno de los dos identificadores va {@code null}. */
    public record InvolucradoDto(UUID personaId, UUID equipoId, String nombre) {
    }

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

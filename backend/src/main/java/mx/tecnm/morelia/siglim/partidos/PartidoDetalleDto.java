package mx.tecnm.morelia.siglim.partidos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.programacion.EstadoJornada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;

/**
 * Detalle del partido (convenciones de API §6.8 y §9.1, UI-010): estado, árbitros, cédula, resultado
 * oficial vigente, historial y las acciones que la sesión puede hacer con su estado (RF-046).
 *
 * <p>Las fechas van en {@code America/Mexico_City}. Sin datos personales de jugadores.
 *
 * <p>Refs: H-15.1, RF-043, RF-045, RF-046, UI-010
 *
 * @param finBloque          fin del bloque de cancha (DEC-25)
 * @param arbitros           designaciones vigentes; vacío mientras no se designen (H-14)
 * @param cedula             estado de la cédula, o {@code null} si todavía no se abre (H-16)
 * @param resultado          resultado oficial vigente, o {@code null} (RF-048)
 * @param accionesPermitidas acciones manuales que admite el estado y que la sesión puede hacer
 */
public record PartidoDetalleDto(
        UUID id,
        JornadaDto jornada,
        ReferenciaDto temporada,
        ReferenciaDto categoria,
        ReferenciaDto grupo,
        ReferenciaDto equipoLocal,
        ReferenciaDto equipoVisitante,
        ReferenciaDto cancha,
        OffsetDateTime inicio,
        OffsetDateTime finBloque,
        EstadoPartido estado,
        List<ArbitroDto> arbitros,
        CedulaResumenDto cedula,
        ResultadoDto resultado,
        List<CambioEstadoPartidoDto> historial,
        List<AccionPartido> accionesPermitidas) {

    /** Identificador y nombre de una entidad relacionada (convenciones de API §9.1). */
    public record ReferenciaDto(UUID id, String nombre) {
    }

    /** Jornada del partido con su número y su estado de publicación. */
    public record JornadaDto(UUID id, int numero, EstadoJornada estadoPublicacion) {
    }

    /** Árbitro designado con su función ({@code CENTRAL} o asistente). */
    public record ArbitroDto(UUID id, String nombre, String funcion) {
    }

    /** Cédula del partido. */
    public record CedulaResumenDto(UUID id, String estado) {
    }

    /** Resultado oficial vigente (RF-048, RF-052). */
    public record ResultadoDto(int golesLocal, int golesVisitante, Integer penalesLocal, Integer penalesVisitante,
                               boolean porDefault, int version) {
    }
}

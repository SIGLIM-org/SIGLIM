package mx.tecnm.morelia.siglim.programacion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code POST /fechas/{id}/asignacion} (H-33.2): la fecha con sus bloques, como
 * {@code GET /fechas/{id}}, más lo que solo tiene sentido al asignar. Va aparte de {@link FechaDto}
 * para no agregar esos campos a todas las consultas de la fecha.
 *
 * <p>Refs: H-33.2, H-34.2, RF-022, RF-023, RF-041, RF-042, RN-017, DEC-177, DEC-180, DEC-218, DEC-219
 *
 * @param fecha        detalle de la fecha después de asignar y designar
 * @param totales      conteos de esta operación
 * @param noAsignados  emparejamientos sin partido con su código y causa
 * @param sinArbitro   partidos que se quedaron sin árbitro central automático, con la causa de DEC-216
 *                     (DEC-219); vacía si la sesión no tiene {@code arbitraje.designacion:AUTOMATICA}
 * @param advertencias avisos que no bloquean: los del motor (DEC-177), la falta del permiso de
 *                     designación automática (DEC-218) y los centrales con {@code RF-022} (DEC-219)
 * @param resumen      franjas libres y partidos requeridos por día (RF-042)
 */
public record AsignacionFechaDto(FechaDto fecha, Totales totales, List<NoAsignado> noAsignados,
        List<SinArbitro> sinArbitro, List<String> advertencias, List<ResumenDia> resumen) {

    /**
     * Conteos de la operación (DEC-219).
     *
     * @param asignados   partidos insertados
     * @param noAsignados emparejamientos que se quedaron sin partido
     * @param cancelados  partidos cancelados al regenerar
     * @param centrales   árbitros centrales designados automáticamente
     * @param sinArbitro  partidos que se quedaron sin central automático
     */
    public record Totales(int asignados, int noAsignados, int cancelados, int centrales, int sinArbitro) {
    }

    /** Partido sin árbitro central elegible (RF-023, DEC-216, DEC-219); no bloquea el envío. */
    public record SinArbitro(UUID partidoId, String codigo, String causa) {
    }

    /** Emparejamiento que se quedó sin partido (DEC-180). */
    public record NoAsignado(UUID emparejamientoId, String codigo, String causa) {
    }

    /** Capacidad y demanda de un día de la fecha (RF-042, RN-017). */
    public record ResumenDia(LocalDate dia, int franjasLibres, int bloqueMinutos, int partidosRequeridos) {
    }
}

package mx.tecnm.morelia.siglim.programacion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code POST /fechas/{id}/asignacion} (H-33.2): la fecha con sus bloques, como
 * {@code GET /fechas/{id}}, más lo que solo tiene sentido al asignar. Va aparte de {@link FechaDto}
 * para no agregar esos campos a todas las consultas de la fecha.
 *
 * <p>Refs: H-33.2, RF-041, RF-042, RN-017, DEC-177, DEC-180
 *
 * @param fecha        detalle de la fecha después de asignar
 * @param totales      partidos asignados, no asignados y cancelados en esta operación
 * @param noAsignados  emparejamientos sin partido con su código y causa
 * @param advertencias avisos del motor que no bloquean (DEC-177)
 * @param resumen      franjas libres y partidos requeridos por día (RF-042)
 */
public record AsignacionFechaDto(FechaDto fecha, Totales totales, List<NoAsignado> noAsignados,
        List<String> advertencias, List<ResumenDia> resumen) {

    /** Conteos de la operación. */
    public record Totales(int asignados, int noAsignados, int cancelados) {
    }

    /** Emparejamiento que se quedó sin partido (DEC-180). */
    public record NoAsignado(UUID emparejamientoId, String codigo, String causa) {
    }

    /** Capacidad y demanda de un día de la fecha (RF-042, RN-017). */
    public record ResumenDia(LocalDate dia, int franjasLibres, int bloqueMinutos, int partidosRequeridos) {
    }
}

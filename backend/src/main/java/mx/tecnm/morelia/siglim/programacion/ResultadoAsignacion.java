package mx.tecnm.morelia.siglim.programacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lo que propone {@link MotorAsignacion} para una fecha. No guarda nada: H-33.2 confirma cada
 * asignado con {@code ValidadorConflictos} y lo inserta, y guarda los no asignados en
 * {@code emparejamientos}.
 *
 * <p>Refs: H-33.1, RF-042, RN-017, VAL-006, VAL-007, VAL-027, DEC-177, DEC-179, DEC-180
 *
 * @param asignados    partidos con cancha y horario, ordenados por inicio y cancha
 * @param noAsignados  partidos sin lugar o con un equipo no elegible, en el orden en que se procesaron
 * @param advertencias avisos que no impiden asignar (por ejemplo, DEC-177)
 * @param resumen      franjas libres y partidos requeridos por día, en orden de día
 */
record ResultadoAsignacion(List<PartidoAsignado> asignados, List<PartidoNoAsignado> noAsignados,
        List<String> advertencias, List<ResumenDia> resumen) {

    ResultadoAsignacion {
        asignados = List.copyOf(asignados);
        noAsignados = List.copyOf(noAsignados);
        advertencias = List.copyOf(advertencias);
        resumen = List.copyOf(resumen);
    }

    /**
     * Partido colocado.
     *
     * @param fin           {@code inicio} más el bloque de la categoría ({@code fin_bloque})
     * @param motivoLocalia texto «Asignación automática: …» para {@code partidos.motivo_localia} si no se
     *                      respetó la localía (DEC-179); {@code null} si no hace falta
     */
    record PartidoAsignado(UUID emparejamientoId, UUID canchaId, OffsetDateTime inicio, OffsetDateTime fin,
            String motivoLocalia) {
    }

    /**
     * Partido que se queda sin partido en {@code emparejamientos} (DEC-180).
     *
     * @param codigo {@code VAL-006} (equipo no elegible) o {@code VAL-007} (sin franja libre)
     * @param causa  explicación legible para el Programador
     */
    record PartidoNoAsignado(UUID emparejamientoId, String codigo, String causa) {
    }

    /**
     * Capacidad y demanda de un día de la fecha (RF-042, RN-017). [PROPUESTA] El diseño no define
     * cómo contarlas: las franjas son los bloques de {@code bloqueMinutos} que cabían en las canchas
     * antes de asignar (ya descontadas las ocupaciones previas), medidas con el bloque menor de las
     * categorías que juegan ese día; los partidos requeridos son los elegibles por colocar que tienen
     * ese día permitido (una categoría que juega sábado y domingo cuenta en los dos).
     */
    record ResumenDia(LocalDate dia, int franjasLibres, int bloqueMinutos, int partidosRequeridos) {
    }
}

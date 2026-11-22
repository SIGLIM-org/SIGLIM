package mx.tecnm.morelia.siglim.programacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Partido programado manualmente en una jornada (RF-033, RF-036). {@code finBloque} lo calcula el
 * trigger {@code tg_partidos_programacion} con el bloque de cancha de la categoría (DEC-25), así que
 * no se envía al crear ni al ajustar. Los campos opcionales vacíos se omiten (convenciones de API §1).
 *
 * <p>Refs: H-12.1, RF-033, RF-036, RF-040, DEC-25
 *
 * @param equipoLocal      equipo local con su nombre, para no pedir otra consulta por fila
 * @param equipoVisitante  equipo visitante con su nombre
 * @param cancha           cancha asignada con su nombre
 * @param inicio           fecha y hora de inicio, en la zona de negocio
 * @param finBloque        fin del bloque de cancha que calcula la base
 * @param motivoLocalia    motivo con el que se aceptó una advertencia de localía (RF-120, backlog)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartidoDto(
        UUID id,
        UUID jornadaId,
        ReferenciaDto equipoLocal,
        ReferenciaDto equipoVisitante,
        ReferenciaDto cancha,
        OffsetDateTime inicio,
        OffsetDateTime finBloque,
        EstadoPartido estado,
        String motivoLocalia,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn) {

    /** Identificador con el nombre, para que la pantalla muestre equipos y canchas sin otra consulta. */
    public record ReferenciaDto(UUID id, String nombre) {
    }
}

package mx.tecnm.morelia.siglim.programacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.programacion.PartidoDto.ReferenciaDto;

/**
 * Fecha de programación: las jornadas de todos los grupos de una semana, que se envían y se aprueban
 * completas (DEC-175).
 *
 * <p>El listado omite {@code bloques}; el detalle los trae en el formato de F-22: un bloque por
 * «Categoría – Grupo» con Local vs Visitante, cancha, horario y árbitro, más los descansos y los
 * emparejamientos sin asignar con su causa. El estado se deriva de las jornadas; los datos del flujo
 * de publicación salen de ellas y se omiten mientras están vacíos.
 *
 * <p>Los conteos salen solo del calendario vigente (DEC-189). {@code PATCH /fechas/{id}} responde
 * con la misma forma que el listado, sin {@code bloques} (H-32.2).
 *
 * <p>Refs: H-35.1, H-32.2, RF-033, RF-037, RF-042, RF-090, RF-121, UI-009, DEC-175, DEC-180, DEC-189
 *
 * @param estadoPublicacion estado derivado de sus jornadas: el menos avanzado
 * @param jornadas          jornadas ligadas a la fecha
 * @param partidos          partidos vigentes (sin los cancelados)
 * @param sinAsignar        emparejamientos con partido pendiente, sin contar descansos
 * @param descansos         equipos que descansan en la fecha (RF-037; H-32.2)
 * @param enviadaPor        quien envió la fecha a aprobación por última vez
 * @param aprobadaPor       Presidente que la aprobó; solo en {@code PUBLICADO}
 * @param observaciones     observaciones del último dictamen (las de una devolución, en {@code BORRADOR})
 * @param bloques           solo en el detalle
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FechaDto(
        UUID id,
        UUID temporadaId,
        int numero,
        List<LocalDate> dias,
        EstadoJornada estadoPublicacion,
        long jornadas,
        long partidos,
        long sinAsignar,
        long descansos,
        OffsetDateTime generadaEn,
        UUID enviadaPor,
        OffsetDateTime enviadaEn,
        UUID aprobadaPor,
        OffsetDateTime aprobadaEn,
        OffsetDateTime publicadaEn,
        String observaciones,
        List<Bloque> bloques) {

    public FechaDto {
        dias = dias == null ? List.of() : List.copyOf(dias);
        bloques = bloques == null ? null : List.copyOf(bloques);
    }

    /**
     * Un bloque «Categoría – Grupo» de F-22.
     *
     * @param diasJuego         días ISO de juego de la categoría (6 = sábado, 7 = domingo; DEC-177)
     * @param jornadaId         jornada del grupo en la fecha; nula si el grupo aún no la tiene
     * @param estadoPublicacion estado de esa jornada
     * @param partidos          partidos vigentes por inicio
     * @param descansos         equipos que descansan (RF-037)
     * @param sinAsignar        emparejamientos sin partido, con su causa (DEC-180)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Bloque(
            ReferenciaDto categoria,
            ReferenciaDto grupo,
            List<Integer> diasJuego,
            UUID jornadaId,
            EstadoJornada estadoPublicacion,
            List<FilaPartido> partidos,
            List<Descanso> descansos,
            List<SinAsignar> sinAsignar) {

        public Bloque {
            diasJuego = diasJuego == null ? List.of() : List.copyOf(diasJuego);
            partidos = partidos == null ? List.of() : List.copyOf(partidos);
            descansos = descansos == null ? List.of() : List.copyOf(descansos);
            sinAsignar = sinAsignar == null ? List.of() : List.copyOf(sinAsignar);
        }
    }

    /**
     * Una fila Local vs Visitante con cancha, horario y árbitro central.
     *
     * @param emparejamientoId        emparejamiento del calendario; nulo en un partido manual
     * @param arbitro                 árbitro central vigente; nulo es «Sin árbitro» (advertencia, DEC-182)
     * @param designacionAutomatica   si el central lo designó el motor (H-34); nulo sin árbitro
     * @param asignadoAutomaticamente insignia «Automático»: cancha y horario los puso el motor (H-33)
     * @param ajustado                insignia «Ajustado»: se ajustó con motivo (RF-040)
     * @param motivoLocalia           insignia «Localía»: motivo con que se aceptó la advertencia (RF-120)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FilaPartido(
            UUID id,
            UUID jornadaId,
            UUID emparejamientoId,
            ReferenciaDto equipoLocal,
            ReferenciaDto equipoVisitante,
            ReferenciaDto cancha,
            OffsetDateTime inicio,
            OffsetDateTime finBloque,
            EstadoPartido estado,
            ReferenciaDto arbitro,
            Boolean designacionAutomatica,
            boolean asignadoAutomaticamente,
            boolean ajustado,
            String motivoLocalia) {
    }

    /** Equipo que descansa en la fecha (RF-037). */
    public record Descanso(UUID emparejamientoId, ReferenciaDto equipo) {
    }

    /**
     * Emparejamiento sin partido (DEC-180): «Asignar» abre el alta manual con estos equipos y el
     * {@code emparejamientoId}.
     *
     * @param codigo código de la causa ({@code VAL-007}, {@code VAL-006}…); nulo si la asignación
     *               automática todavía no se ejecutó
     * @param causa  explicación legible
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SinAsignar(UUID emparejamientoId, ReferenciaDto equipoLocal, ReferenciaDto equipoVisitante,
                             String codigo, String causa) {
    }
}

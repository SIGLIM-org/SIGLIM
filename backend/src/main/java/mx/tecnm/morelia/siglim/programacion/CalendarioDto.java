package mx.tecnm.morelia.siglim.programacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.programacion.PartidoDto.ReferenciaDto;

/**
 * Calendario vigente de la temporada por grupo y fecha (H-32.2, RF-034, RF-037): respuesta de
 * {@code GET /api/v1/temporadas/{id}/calendario}. Incluye todos los grupos, también los que aún no
 * tienen calendario ({@code generado = false}, sin fechas). Las filas reemplazadas al regenerar no
 * aparecen (DEC-189).
 *
 * <p>Refs: H-32.2, RF-034, RF-037, DEC-174, DEC-189
 *
 * @param temporadaId temporada consultada
 * @param grupos      por el orden de la categoría y el nombre del grupo
 */
public record CalendarioDto(UUID temporadaId, List<GrupoDto> grupos) {

    public CalendarioDto {
        grupos = grupos == null ? List.of() : List.copyOf(grupos);
    }

    /**
     * Calendario de un grupo.
     *
     * @param generado {@code true} si el grupo tiene calendario vigente
     * @param fechas   fechas del grupo por número; vacía si no está generado
     */
    public record GrupoDto(UUID grupoId, String nombre, ReferenciaDto categoria, boolean generado,
                           List<FechaDto> fechas) {

        public GrupoDto {
            fechas = fechas == null ? List.of() : List.copyOf(fechas);
        }
    }

    /**
     * Una fecha del grupo.
     *
     * @param numero   número de la fecha en la temporada
     * @param vuelta   vuelta del calendario a la que pertenece (DEC-174)
     * @param partidos enfrentamientos de la fecha
     * @param descansa equipo que descansa (RF-037); se omite si el grupo es par
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FechaDto(int numero, int vuelta, List<PartidoDto> partidos, EquipoDto descansa) {

        public FechaDto {
            partidos = partidos == null ? List.of() : List.copyOf(partidos);
        }
    }

    /**
     * Un enfrentamiento del calendario.
     *
     * @param partidoId partido ligado al asignar cancha y horario; se omite mientras no tiene
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PartidoDto(UUID emparejamientoId, EquipoDto local, EquipoDto visitante, UUID partidoId) {
    }

    /**
     * Equipo con su estado, para advertir uno dado de baja después de generar el calendario.
     *
     * @param estado valor de {@code estado_equipo}
     */
    public record EquipoDto(UUID id, String nombre, String estado) {
    }
}

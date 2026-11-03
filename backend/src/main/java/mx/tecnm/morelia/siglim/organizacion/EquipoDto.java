package mx.tecnm.morelia.siglim.organizacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;

/**
 * Equipo de un club en un grupo de la temporada (RF-009), con sus referencias anidadas para que la
 * interfaz no haga una petición por fila (convenciones de API §9.1). Los campos opcionales vacíos se
 * omiten (§1).
 *
 * <p>Refs: H-06.1, RF-009, RN-005, RN-007
 *
 * @param bajaEn     fecha de la baja; solo en un equipo {@code BAJA}
 * @param motivoBaja motivo de la baja; solo en un equipo {@code BAJA}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EquipoDto(
        UUID id,
        String nombre,
        EstadoEquipo estado,
        ReferenciaDto club,
        ReferenciaDto grupo,
        ReferenciaDto categoria,
        TemporadaResumenDto temporada,
        OffsetDateTime creadoEn,
        LocalDate bajaEn,
        String motivoBaja) {

    /** Identificador y nombre de una entidad relacionada. */
    public record ReferenciaDto(UUID id, String nombre) {
    }

    /** Temporada del grupo, con su estado: decide si el equipo admite cambios (RF-003, RN-007). */
    public record TemporadaResumenDto(UUID id, String nombre, EstadoTemporada estado) {
    }
}

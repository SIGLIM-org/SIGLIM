package mx.tecnm.morelia.siglim.programacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Jornada de un grupo con su estado de publicación (RF-033, RF-121).
 *
 * <p>El listado no anida partidos ({@code partidos} se omite); el detalle los trae ordenados por
 * inicio para armar la programación de F-22 con una sola petición (convenciones de API §9.1).
 *
 * <p>Refs: H-12.1, RF-033, RF-121
 *
 * @param faseId     fase final a la que pertenece; nulo en la fase regular
 * @param partidos   partidos de la jornada, ordenados por inicio; solo en el detalle
 * @param creadoEn   fecha de alta, en la zona de negocio
 * @param actualizadoEn fecha de la última modificación
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JornadaDto(
        UUID id,
        UUID grupoId,
        UUID faseId,
        int numero,
        EstadoJornada estadoPublicacion,
        List<PartidoDto> partidos,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn) {

    public JornadaDto {
        partidos = partidos == null ? null : List.copyOf(partidos);
    }
}

package mx.tecnm.morelia.siglim.arbitraje;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Periodo de disponibilidad de un árbitro (RF-022).
 *
 * <p>{@code disponible} es {@code true} cuando el árbitro puede ser designado en el periodo y
 * {@code false} cuando declara que no puede. El periodo incluye {@code desde} y excluye
 * {@code hasta} (convenciones de API §1).
 *
 * <p>Refs: B-09.1, RF-022
 *
 * @param id         identificador del registro
 * @param arbitroId  árbitro al que pertenece la disponibilidad
 * @param periodo    fecha y horario de la disponibilidad
 * @param disponible verdadero si está disponible; falso si no
 * @param creadoEn   fecha y hora del registro, en la zona de negocio
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisponibilidadArbitralDto(
        UUID id,
        UUID arbitroId,
        PeriodoArbitral periodo,
        boolean disponible,
        OffsetDateTime creadoEn) {
}

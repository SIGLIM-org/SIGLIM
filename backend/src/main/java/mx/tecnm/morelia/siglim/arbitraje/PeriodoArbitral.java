package mx.tecnm.morelia.siglim.arbitraje;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Periodo de disponibilidad arbitral en el API (convenciones de API §1): incluye {@code desde} y
 * excluye {@code hasta}, igual que el {@code tstzrange} de {@code disponibilidad_arbitral}. Así
 * {@code [10:00, 12:00)} y {@code [12:00, 14:00)} no se traslapan.
 *
 * <p>Es el mismo contrato que el {@code Intervalo} de las canchas, pero del módulo M-05: la
 * disponibilidad del árbitro es independiente de la de una cancha y no comparte tabla ni servicio.
 *
 * <p>Refs: B-09.1, RF-022
 *
 * @param desde inicio, incluido
 * @param hasta fin, excluido
 */
public record PeriodoArbitral(@NotNull(message = "Indica el inicio del periodo") OffsetDateTime desde,
                              @NotNull(message = "Indica el fin del periodo") OffsetDateTime hasta) {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    /** La misma fecha con el desplazamiento de la zona de negocio, como se responde en el API. */
    public static OffsetDateTime enZonaDeNegocio(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    /** Periodo leído de la base, en la zona de negocio. */
    public static PeriodoArbitral enZonaDeNegocio(OffsetDateTime desde, OffsetDateTime hasta) {
        return new PeriodoArbitral(enZonaDeNegocio(desde), enZonaDeNegocio(hasta));
    }
}

package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Intervalo {@code tstzrange} en el API (convenciones de API §1): incluye {@code desde} y excluye
 * {@code hasta}, así que {@code [10:00, 12:00)} y {@code [12:00, 14:00)} no se traslapan.
 *
 * @param desde inicio, incluido
 * @param hasta fin, excluido
 */
public record Intervalo(@NotNull OffsetDateTime desde, @NotNull OffsetDateTime hasta) {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** La misma fecha con el desplazamiento de la zona de negocio, como se responde en el API. */
    public static OffsetDateTime enZonaDeNegocio(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    /** Intervalo leído de la base, en la zona de negocio. */
    public static Intervalo enZonaDeNegocio(OffsetDateTime desde, OffsetDateTime hasta) {
        return new Intervalo(enZonaDeNegocio(desde), enZonaDeNegocio(hasta));
    }

    /** Texto para los mensajes a la persona: {@code 25/10/2026 10:00 a 25/10/2026 12:00}. */
    String comoTexto() {
        return desde.atZoneSameInstant(ZONA_DE_NEGOCIO).format(FORMATO) + " a "
                + hasta.atZoneSameInstant(ZONA_DE_NEGOCIO).format(FORMATO);
    }
}

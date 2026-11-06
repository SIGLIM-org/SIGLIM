package mx.tecnm.morelia.siglim.tesoreria;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Adeudos por club y equipo (RF-140): suma de los cobros {@code PENDIENTE} y {@code VENCIDO} que
 * alcanza el ámbito del usuario. Los cobros {@code PAGADO} y {@code RECHAZADO} no son adeudo.
 *
 * <p>Los totales van en {@link BigDecimal} con dos decimales (convenciones de API §1). Un club
 * aparece solo si tiene algún adeudo; sus cobros sin equipo suman en el club y no en un equipo.
 *
 * <p>Refs: H-29.1, RF-140, UI-027
 *
 * @param total suma de todos los adeudos visibles
 * @param cobros número de cobros que forman el adeudo
 */
public record AdeudosDto(
        BigDecimal total,
        BigDecimal pendiente,
        BigDecimal vencido,
        long cobros,
        List<ClubAdeudoDto> clubes) {

    public AdeudosDto {
        clubes = List.copyOf(clubes);
    }

    /** Adeudo de un club, con su desglose por equipo. */
    public record ClubAdeudoDto(
            ReferenciaDto club,
            BigDecimal total,
            BigDecimal pendiente,
            BigDecimal vencido,
            long cobros,
            List<EquipoAdeudoDto> equipos) {

        public ClubAdeudoDto {
            equipos = List.copyOf(equipos);
        }
    }

    /** Adeudo de un equipo dentro de un club. */
    public record EquipoAdeudoDto(
            ReferenciaDto equipo,
            BigDecimal total,
            BigDecimal pendiente,
            BigDecimal vencido,
            long cobros) {
    }

    /** Identificador y nombre de una entidad relacionada. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReferenciaDto(UUID id, String nombre) {
    }
}

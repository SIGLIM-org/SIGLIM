package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Lo que ocupa una cancha en un rango, para el calendario de F-21 y para H-12: registros de
 * disponibilidad y partidos no cancelados, ordenados por inicio. Un {@code BLOQUEO} o
 * {@code MANTENIMIENTO} prevalece sobre un {@code DISPONIBLE} que coincida (D4, [PROPUESTA]).
 */
public record OcupacionDto(CanchaResumenDto cancha, boolean activa, Intervalo periodo,
                           List<BloqueOcupacionDto> bloques) {

    public OcupacionDto {
        bloques = List.copyOf(bloques);
    }

    public record CanchaResumenDto(UUID id, String nombre) {
    }

    /** {@code partido} solo si el tipo es {@code PARTIDO}; {@code motivo} solo si existe. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BloqueOcupacionDto(TipoBloqueOcupacion tipo, Intervalo periodo, String motivo,
                                     PartidoEnCanchaDto partido) {
    }
}

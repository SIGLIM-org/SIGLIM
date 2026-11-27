package mx.tecnm.morelia.siglim.arbitraje;

import java.util.List;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionDto.ArbitroDesignadoDto;

/**
 * Árbitro validado de la liga con su elegibilidad para un partido
 * ({@code GET /api/v1/partidos/{id}/arbitros-candidatos}, DEC-204).
 *
 * <p>{@code causas} son las reglas que harían rechazar la designación ({@code VAL-019},
 * {@code RF-039}, {@code YA_DESIGNADO}); {@code elegible} es verdadero si no hay ninguna.
 * {@code advertencias} no bloquean: hoy solo {@code RF-022} (el árbitro declaró que no está
 * disponible en ese horario).
 *
 * <p>Refs: H-14.1, RF-022, RF-023, RF-039, RF-115, VAL-019, DEC-204
 *
 * @param arbitro      árbitro con su club
 * @param elegible     verdadero si {@code causas} está vacía
 * @param causas       motivos por los que no se puede designar
 * @param advertencias avisos que no impiden designar
 * @param carga        designaciones vigentes en partidos no cancelados de la temporada del partido
 */
public record CandidatoArbitralDto(ArbitroDesignadoDto arbitro, boolean elegible, List<CausaDto> causas,
                                   List<CausaDto> advertencias, long carga) {

    public CandidatoArbitralDto {
        causas = List.copyOf(causas);
        advertencias = List.copyOf(advertencias);
    }

    /**
     * Causa o advertencia con el código de la regla y un mensaje para la persona.
     *
     * @param codigo  código del ERS o técnico ({@code YA_DESIGNADO})
     * @param mensaje explicación en español
     */
    public record CausaDto(String codigo, String mensaje) {
    }
}

package mx.tecnm.morelia.siglim.programacion;

import java.util.List;
import java.util.UUID;

/**
 * Lo que hizo {@link DesignadorArbitroCentral} con los partidos de una asignación. Las dos listas son
 * inmutables y van en el orden en que se procesaron los partidos (por inicio y después id).
 *
 * <p>Refs: H-34.1, RF-023, DEC-182, DEC-213, DEC-216
 *
 * @param designados partidos que recibieron central automático
 * @param sinCentral partidos que se quedaron sin central, con la causa
 */
public record ResultadoCentrales(List<CentralDesignado> designados, List<SinCentral> sinCentral) {

    public ResultadoCentrales {
        designados = List.copyOf(designados);
        sinCentral = List.copyOf(sinCentral);
    }

    /** Sin partidos que procesar. */
    public static ResultadoCentrales vacio() {
        return new ResultadoCentrales(List.of(), List.of());
    }

    /**
     * Central designado con {@code automatica = true}.
     *
     * @param advertencias códigos que no impidieron designarlo, p. ej. {@code RF-022} (declaró que no
     *                     está disponible en ese horario)
     */
    public record CentralDesignado(UUID partidoId, UUID arbitroId, UUID designacionId, List<String> advertencias) {

        public CentralDesignado {
            advertencias = List.copyOf(advertencias);
        }
    }

    /**
     * Partido sin árbitro elegible: «Sin árbitro» como advertencia que no bloquea el envío (DEC-182).
     *
     * @param codigo {@code RF-023}
     * @param causa  explicación con los conteos por regla (DEC-216)
     */
    public record SinCentral(UUID partidoId, String codigo, String causa) {
    }
}

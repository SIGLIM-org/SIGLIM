package mx.tecnm.morelia.siglim.programacion;

import java.util.List;
import java.util.UUID;

/**
 * Puerto con el que la programación automática pide el árbitro central de los partidos que acaba de
 * asignar (DEC-182). Lo define este paquete y lo implementa {@code arbitraje.DesignacionAutomatica}
 * (DEC-213): {@code programacion} está en un nivel inferior a {@code arbitraje} y no lo importa
 * (ADR-0006).
 *
 * <p>Refs: H-34.1, RF-023, DEC-182, DEC-213, ADR-0006, ADR-0015
 */
public interface DesignadorArbitroCentral {

    /**
     * Designa, a cada partido que lo necesite, un árbitro central elegido de forma equitativa entre
     * los elegibles (menor carga en la temporada, desempate determinista; DEC-214).
     *
     * <p>Requiere una <b>transacción abierta</b> (se une a la de quien llama) y que la sesión tenga
     * {@code arbitraje.designacion:AUTOMATICA} en ámbito de liga; sin él lanza {@code 403 VAL-015}.
     * Omite, sin incluirlos en ninguna lista, los partidos que no son de la liga, los que no están
     * {@code PROGRAMADO} ni {@code REPROGRAMADO} y los que ya tienen central vigente.
     *
     * @param partidoIds partidos de la liga; {@code null} o vacía no consulta nada
     * @return designados y partidos sin central, en el orden en que se procesaron (por inicio)
     */
    ResultadoCentrales designarCentrales(List<UUID> partidoIds);
}

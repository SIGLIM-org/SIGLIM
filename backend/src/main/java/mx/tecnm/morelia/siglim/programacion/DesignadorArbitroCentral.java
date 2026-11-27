package mx.tecnm.morelia.siglim.programacion;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puerto con el que la programación automática pide el árbitro central de los partidos de una fecha
 * (DEC-182). Lo define este paquete y lo implementa {@code arbitraje.DesignacionAutomatica}
 * (DEC-213): {@code programacion} está en un nivel inferior a {@code arbitraje} y no lo importa
 * (ADR-0006).
 *
 * <p>Lo llaman {@link AsignacionFechaService}, al asignar la fecha con
 * {@code arbitraje.designacion:AUTOMATICA} (DEC-217), y {@link FechaService#verificar}, para explicar
 * por qué un partido sigue sin central (DEC-220).
 *
 * <p>Refs: H-34.1, H-34.2, RF-023, DEC-182, DEC-213, DEC-216, DEC-217, DEC-220, ADR-0006, ADR-0015
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

    /**
     * Explica, sin escribir ni bloquear nada, por qué cada partido sigue sin árbitro central: la causa
     * de DEC-216 si ningún árbitro validado puede pitarlo, o cuántos podrían si alguno puede.
     *
     * <p>Requiere una <b>transacción abierta</b>, que puede ser {@code readOnly}. No exige un permiso
     * propio: quien llama ya exigió {@code programacion.jornada:CONSULTAR} y la causa solo lleva
     * conteos, sin nombres de árbitros (DEC-220).
     *
     * @param partidoIds partidos que se quieren explicar; {@code null} o vacía devuelve un mapa vacío
     *                   sin consultar
     * @return mapa inmutable, en el orden recibido y sin duplicados, solo con los partidos de la liga
     *         {@code PROGRAMADO} o {@code REPROGRAMADO} que no tienen central vigente
     */
    Map<UUID, String> explicarSinCentral(List<UUID> partidoIds);
}

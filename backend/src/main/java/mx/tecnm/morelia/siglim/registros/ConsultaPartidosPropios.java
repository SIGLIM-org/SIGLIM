package mx.tecnm.morelia.siglim.registros;

import java.util.List;
import java.util.UUID;

/**
 * Partidos del equipo de un registro, tal como los necesita la consulta propia (H-24.1, RF-144). La
 * implementa {@code partidos}: está en un nivel superior y {@code registros} no consulta
 * {@code partidos}, {@code jornadas} ni {@code resultados} directamente (ADR-0006, como
 * el ejemplo {@code partidos.ConsultaSanciones} de ese ADR).
 *
 * <p>La implementación corre con {@code JdbcClient} dentro de la transacción del servicio que llama:
 * con su contexto de usuario y sujeta a RLS (ADR-0014). Es de solo lectura.
 *
 * <p><b>[PROPUESTA]</b> Mientras no haya alineaciones (H-16), los partidos de un jugador son los de su
 * equipo, como local o visitante (DEC-144).
 *
 * <p>Refs: H-24.1, RF-144, ADR-0006, ADR-0014, DEC-143, DEC-144
 */
public interface ConsultaPartidosPropios {

    /**
     * Partidos del equipo de jornadas {@code PUBLICADO} que no estén {@code FINALIZADO} ni
     * {@code CANCELADO}, por {@code inicio} ascendente.
     *
     * @param limite cuántos partidos devolver como máximo
     */
    List<PartidoPropio> proximos(UUID equipoId, int limite);

    /**
     * Partidos del equipo con resultado oficial vigente, por {@code inicio} descendente.
     *
     * @param limite cuántos partidos devolver como máximo
     */
    List<PartidoPropio> jugados(UUID equipoId, int limite);
}

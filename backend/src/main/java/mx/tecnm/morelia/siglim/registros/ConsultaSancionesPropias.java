package mx.tecnm.morelia.siglim.registros;

import java.util.List;
import java.util.UUID;

/**
 * Sanciones de una persona, tal como las necesitan la consulta propia (H-24.1, RF-144) y la
 * elegibilidad ({@link ReglaElegibilidad}, RF-020). La implementa {@code disciplina}: está en un nivel
 * superior y {@code registros} no consulta {@code sanciones}, {@code resoluciones} ni
 * {@code expedientes} directamente (como el ejemplo {@code partidos.ConsultaSanciones} de ADR-0006).
 *
 * <p>La implementación corre con {@code JdbcClient} dentro de la transacción del servicio que llama:
 * con su contexto de usuario y sujeta a RLS (ADR-0014). Es de solo lectura.
 *
 * <p>Refs: H-24.1, RF-020, RF-144, ADR-0006, ADR-0014, DEC-137, DEC-139
 */
public interface ConsultaSancionesPropias {

    /**
     * Sanciones de sujeto {@code JUGADOR} de la persona en la liga: las {@code VIGENTE} de cualquier
     * temporada más todas las de temporadas que no estén {@code CERRADA}, de la más reciente a la más
     * antigua ({@code vigente_desde} y fecha de creación).
     */
    List<SancionPropia> dePersona(UUID personaId);
}

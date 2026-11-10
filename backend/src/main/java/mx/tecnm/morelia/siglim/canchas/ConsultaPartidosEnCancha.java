package mx.tecnm.morelia.siglim.canchas;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Partidos de una cancha, tal como los necesita este módulo. La implementa {@code programacion}:
 * está en un nivel superior y {@code canchas} no consulta {@code partidos} directamente (ADR-0006).
 *
 * <p>Refs: H-07.1, RF-030, PEN-007, ADR-0006
 */
public interface ConsultaPartidosEnCancha {

    /** Partidos {@code PROGRAMADO} o {@code REPROGRAMADO} cuyo bloque se traslapa con el periodo. */
    List<PartidoEnCanchaDto> listarProgramados(UUID canchaId, Intervalo periodo);

    /** Partidos {@code PROGRAMADO} o {@code REPROGRAMADO} cuyo bloque termina después de esa fecha (en curso o futuros). */
    List<PartidoEnCanchaDto> listarProgramadosDesde(UUID canchaId, OffsetDateTime desde);

    /** Partidos no cancelados cuyo bloque se traslapa con el periodo. */
    List<PartidoEnCanchaDto> listarNoCancelados(UUID canchaId, Intervalo periodo);
}

package mx.tecnm.morelia.siglim.programacion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.canchas.ConsultaPartidosEnCancha;
import mx.tecnm.morelia.siglim.canchas.Intervalo;
import mx.tecnm.morelia.siglim.canchas.PartidoEnCanchaDto;
import org.springframework.stereotype.Component;

/**
 * Le da a {@code canchas} los partidos de una cancha sin que ese módulo, de un nivel inferior,
 * dependa de este (ADR-0006). Corre en la transacción del servicio que llama.
 *
 * <p>Refs: H-07.1, RF-030, PEN-007, ADR-0006
 */
@Component
class ConsultaPartidosEnCanchaJdbc implements ConsultaPartidosEnCancha {

    private final PartidosEnCanchaRepository repositorio;

    ConsultaPartidosEnCanchaJdbc(PartidosEnCanchaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public List<PartidoEnCanchaDto> listarProgramados(UUID canchaId, Intervalo periodo) {
        return repositorio.listarProgramados(canchaId, periodo);
    }

    @Override
    public List<PartidoEnCanchaDto> listarProgramadosDesde(UUID canchaId, OffsetDateTime desde) {
        return repositorio.listarProgramadosDesde(canchaId, desde);
    }

    @Override
    public List<PartidoEnCanchaDto> listarNoCancelados(UUID canchaId, Intervalo periodo) {
        return repositorio.listarNoCancelados(canchaId, periodo);
    }
}

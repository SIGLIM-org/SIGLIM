package mx.tecnm.morelia.siglim.canchas;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.regex.Pattern;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** GET / PUT de horarios por categoría y comodato (RF-119). */
@Service
public class HorariosCanchaClubService {

    private static final String MODULO = "M-06";
    private static final String CONSULTAR = "canchas.cancha:CONSULTAR";
    private static final String EDITAR = "canchas.cancha:EDITAR";
    private static final String ENTIDAD = "horarios_cancha_club";

    private final HorariosCanchaClubRepository horarios;
    private final CanchaRepository canchas;

    HorariosCanchaClubService(HorariosCanchaClubRepository horarios, CanchaRepository canchas) {
        this.horarios = horarios;
        this.canchas = canchas;
    }

    @Transactional(readOnly = true)
    public List<HorariosCanchaClubDto> listar(UUID canchaId) {
        CanchaDto cancha = canchas.buscar(canchaId).orElseThrow(() -> noExiste(canchaId));
        autorizacion.exigir(CONSULTAR, objetivo(cancha));
        return horarios.listarPorCancha(canchaId);
    }

    @Transactional
    public List<HorariosCanchaClubDto> reemplazar(UUID canchaId, RegistrarHorarioRequest request) {
        CanchaDto cancha = canchas.buscar(canchaId).orElseThrow(() -> noExiste(canchaId));
        autorizacion.exigir(EDITAR, objetivo(cancha));
        validarContenido(request);
        // TODO: implementar lógica de reemplazo real de la tabla horarios_cancha_club.
        List<HorariosCanchaClubDto> nuevos = request.horarios().stream()
                .map(fila -> new HorariosCanchaClubDto(null, canchaId, cancha.clubId(), null,
                        fila.categoriaId(), Integer.parseInt(fila.desde()),
                        fila.desde(), fila.hasta(), Boolean.TRUE.equals(fila.clubId())))
                .toList();
        return nuevos;
    }

    private void validarContenido(RegistrarHorarioRequest request) {
        if (request == null || request.horarios() == null || request.horarios().isEmpty()) {
            return;
        }
        for (RegistrarHorarioRequest.HorarioCanchaClubItem item : request.horarios()) {
            if (item.categoriaId() == null) {
                throw new IllegalArgumentException("Cada horario debe incluir categoriaId.");
            }
            if (StringUtils.isEmpty(item.desde()) || StringUtils.isEmpty(item.hasta())) {
                throw new IllegalArgumentException("desde y hasta son obligatorios.");
            }
            if (!iso8601Pattern.matcher(item.desde()).matches() || !iso8601Pattern.matcher(item.hasta()).matches()) {
                throw new IllegalArgumentException("desde y hasta deben ser ISO 8601 (timestamptz).");
            }
        }
    }

    private static final Pattern iso8601Pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?");

    private static RecursoNoEncontradoException noExiste(UUID canchaId) {
        return new RecursoNoEncontradoException("Cancha " + canchaId);
    }

    private static ObjetivoAutorizacion objetivo(CanchaDto cancha) {
        return Autorizacion.objetivo(cancha.propietario(), cancha.clubId());
    }

    private static final Autorizacion autorizacion = Autorizacion.self();
}

package mx.tecnm.morelia.siglim.canchas;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta y reemplaza horarios de cancha de club y comodato (B-13.1, RF-119). */
@Service
public class HorariosCanchaClubService {

    private static final String CONSULTAR = "canchas.cancha:CONSULTAR";
    private static final String EDITAR = "canchas.cancha:EDITAR";

    private final HorariosCanchaClubRepository horarios;
    private final CanchaRepository canchas;
    private final Autorizacion autorizacion;
    private final UUID ligaId;

    HorariosCanchaClubService(HorariosCanchaClubRepository horarios, CanchaRepository canchas,
            Autorizacion autorizacion, SiglimPropiedades configuracion) {
        this.horarios = horarios;
        this.canchas = canchas;
        this.autorizacion = autorizacion;
        this.ligaId = configuracion.ligaId();
    }

    /** Devuelve los horarios ordenados por categoría, temporada, día y hora (RF-119). */
    @Transactional(readOnly = true)
    public List<HorariosCanchaClubDto> listar(UUID canchaId) {
        CanchaDto cancha = canchas.buscar(ligaId, canchaId).orElseThrow(() -> noExiste(canchaId));
        autorizacion.exigir(CONSULTAR, objetivo(cancha));
        return horarios.listarPorCancha(canchaId);
    }

    /** Reemplaza de forma atómica el conjunto completo de horarios (RF-119). */
    @Transactional
    public List<HorariosCanchaClubDto> reemplazar(UUID canchaId, RegistrarHorarioRequest request) {
        CanchaDto cancha = canchas.buscarParaActualizar(ligaId, canchaId)
                .orElseThrow(() -> noExiste(canchaId));
        autorizacion.exigir(EDITAR, objetivo(cancha));
        validarContenido(cancha, request);

        horarios.eliminarPorCancha(canchaId);
        if (request.horarios() != null) {
            for (RegistrarHorarioRequest.HorarioCanchaClubItem item : request.horarios()) {
                UUID clubId = item.clubId() == null ? clubDeCancha(cancha) : item.clubId();
                horarios.insertar(canchaId, clubId, item.temporadaId(), item.categoriaId(),
                        item.diaSemana(), item.horaInicio(), item.horaFin(), item.esComodato());
            }
        }
        return horarios.listarPorCancha(canchaId);
    }

    private void validarContenido(CanchaDto cancha, RegistrarHorarioRequest request) {
        if (request == null || request.horarios() == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "El cuerpo debe incluir la lista horarios.");
        }

        Map<ClaveHorario, List<IntervaloHorario>> horariosPorCategoriaDia = new HashMap<>();
        for (RegistrarHorarioRequest.HorarioCanchaClubItem item : request.horarios()) {
            if (item == null || item.temporadaId() == null || item.categoriaId() == null
                    || item.diaSemana() == null || item.horaInicio() == null || item.horaFin() == null) {
                throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                        "Cada horario debe incluir temporadaId, categoriaId, diaSemana, horaInicio y horaFin.");
            }
            if (item.diaSemana() < 1 || item.diaSemana() > 7) {
                throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                        "diaSemana debe ser un número del 1 (lunes) al 7 (domingo).");
            }
            if (!item.horaInicio().isBefore(item.horaFin())) {
                throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                        "horaInicio debe ser anterior a horaFin.");
            }
            if (!horarios.existeCategoriaEnTemporada(ligaId, item.temporadaId(), item.categoriaId())) {
                throw referenciaInvalida("La temporada o categoría indicada no existe en esta liga.");
            }
            UUID clubId = item.clubId() == null ? clubDeCancha(cancha) : item.clubId();
            if (clubId == null || !horarios.existeClubDeLiga(ligaId, clubId)) {
                throw referenciaInvalida("Indica un club existente de esta liga para el horario.");
            }
            ClaveHorario clave = new ClaveHorario(item.categoriaId(), item.diaSemana());
            List<IntervaloHorario> intervalos = horariosPorCategoriaDia.computeIfAbsent(clave,
                    ignorada -> new ArrayList<>());
            if (intervalos.stream().anyMatch(intervalo -> item.horaInicio().isBefore(intervalo.hasta())
                    && intervalo.desde().isBefore(item.horaFin()))) {
                throw ReglaNegocioException.noProcesable("RF-119",
                        "Los horarios de una misma categoría y día no pueden traslaparse en la cancha.");
            }
            intervalos.add(new IntervaloHorario(item.horaInicio(), item.horaFin()));
        }
    }

    private static UUID clubDeCancha(CanchaDto cancha) {
        return cancha.club() == null ? null : cancha.club().id();
    }

    private static ReglaNegocioException referenciaInvalida(String mensaje) {
        return ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA, mensaje);
    }

    private static RecursoNoEncontradoException noExiste(UUID canchaId) {
        return new RecursoNoEncontradoException("Cancha " + canchaId);
    }

    private static ObjetivoAutorizacion objetivo(CanchaDto cancha) {
        return cancha.propietario() == PropietarioCancha.CLUB && cancha.club() != null
                ? ObjetivoAutorizacion.deClubes(cancha.club().id())
                : ObjetivoAutorizacion.deLiga();
    }

    private record ClaveHorario(UUID categoriaId, int diaSemana) {
    }

    private record IntervaloHorario(LocalTime desde, LocalTime hasta) {
    }
}

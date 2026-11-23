package mx.tecnm.morelia.siglim.partidos;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.partidos.PartidosRepository.DatosPartido;
import mx.tecnm.morelia.siglim.programacion.EstadoJornada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import mx.tecnm.morelia.siglim.programacion.ValidacionReprogramacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta del partido y sus transiciones manuales (M-08.1, H-15.1).
 *
 * <ul>
 *   <li><b>Consulta (RF-043).</b> Listado, detalle e historial con {@code partidos.partido:CONSULTAR}
 *       acotado por su ámbito (ADR-0007): la liga ve todo; el club, los partidos de sus equipos; el
 *       ámbito propio, los de su equipo o los que arbitra. Club y propio solo ven jornadas publicadas
 *       <b>[PROPUESTA]</b> (DEC-161). Un partido fuera de alcance responde {@code 404}.</li>
 *   <li><b>Acciones permitidas (RF-046, RN-019).</b> Cada partido trae {@code accionesPermitidas}:
 *       las que admite su estado según la máquina de DEC-23 ({@link AccionPartido#desde}) y solo si la
 *       sesión tiene {@code partidos.partido:CAMBIAR_ESTADO} y la jornada está publicada; si no, la
 *       lista va vacía, para que la interfaz no muestre botones que el servidor rechazaría.</li>
 *   <li><b>Transiciones (RF-043, RF-045, RN-021).</b> {@code POST /partidos/{id}/transiciones}
 *       suspende, reprograma o cancela con motivo de 10 a 500 caracteres ({@code 400 RN-021}). Una
 *       transición que el estado no admite, o hacia {@code EN_CURSO}/{@code FINALIZADO} (automáticas,
 *       RF-127), responde {@code 409 RF-046} (VAL-008) con {@code estadoActual} y
 *       {@code accionesPermitidas}. Solo en jornadas publicadas: en borrador o revisión el partido se
 *       ajusta con {@code PATCH /partidos/{id}} y responde {@code 409 RF-090} <b>[PROPUESTA]</b>
 *       (DEC-162).</li>
 *   <li><b>Reprogramar (DEC-160).</b> Exige {@code inicio} futuro y admite otra {@code canchaId}; la
 *       nueva fecha y cancha pasan por las reglas de conflictos de la programación
 *       ({@link ValidacionReprogramacion}: {@code 409 VAL-003} a {@code VAL-006} o {@code RF-100}).
 *       Los triggers de programación y la restricción GiST siguen siendo la última garantía.</li>
 *   <li><b>Historial y auditoría (RF-045, RF-093).</b> El trigger {@code tg_partidos_historial}
 *       guarda usuario, fecha y motivo de cada cambio; el servicio fija el motivo de la operación y
 *       audita {@code CAMBIAR_ESTADO_PARTIDO} en M-08 con los estados, el inicio y la cancha, sin datos
 *       personales.</li>
 * </ul>
 *
 * <p>Refs: H-15.1, RF-043, RF-045, RF-046, RF-093, RF-127, RN-019, RN-020, RN-021, VAL-008, DEC-23,
 * DEC-160, DEC-161, DEC-162, ADR-0007
 */
@Service
public class PartidosService {

    static final String CONSULTAR = "partidos.partido:CONSULTAR";
    static final String CAMBIAR_ESTADO = "partidos.partido:CAMBIAR_ESTADO";
    private static final String MODULO = "M-08";
    private static final String ACCION_AUDITORIA = "CAMBIAR_ESTADO_PARTIDO";
    /** [PROPUESTA] Igual que las demás transiciones del sistema (RN-021). */
    static final int MINIMO_MOTIVO = 10;
    static final int MAXIMO_MOTIVO = 500;

    private final PartidosRepository partidos;
    private final ValidacionReprogramacion reprogramacion;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final Clock reloj;
    private final UUID ligaId;

    PartidosService(PartidosRepository partidos, ValidacionReprogramacion reprogramacion, Autorizacion autorizacion,
            AuditoriaService auditoria, MotivoOperacion motivoOperacion, Clock reloj, SiglimPropiedades propiedades) {
        this.partidos = partidos;
        this.reprogramacion = reprogramacion;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /** Partidos visibles para la sesión, por inicio (RF-043). */
    @Transactional(readOnly = true)
    public Pagina<PartidoResumenDto> listar(FiltroPartidos filtro) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        filtro.validar();
        boolean puedeCambiar = puedeCambiarEstado();
        List<PartidoResumenDto> contenido = partidos.listar(ligaId, filtro, alcance).stream()
                .map(partido -> new PartidoResumenDto(partido.id(), partido.jornada().id(), partido.jornada().numero(),
                        partido.equipoLocal(), partido.equipoVisitante(), partido.cancha(), partido.inicio(),
                        partido.finBloque(), partido.estado(), acciones(partido, puedeCambiar)))
                .toList();
        return Pagina.de(contenido, filtro.pagina(), filtro.tamano(), partidos.contar(ligaId, filtro, alcance));
    }

    /** Detalle con árbitros, cédula, resultado vigente, historial y acciones (RF-043, RF-046). */
    @Transactional(readOnly = true)
    public PartidoDetalleDto obtener(UUID partidoId) {
        DatosPartido partido = visible(partidoId);
        return detalle(partido, puedeCambiarEstado());
    }

    /** Historial de transiciones con usuario, fecha y motivo (RF-045). */
    @Transactional(readOnly = true)
    public List<CambioEstadoPartidoDto> historial(UUID partidoId) {
        visible(partidoId);
        return partidos.historial(partidoId);
    }

    /**
     * Suspende, reprograma o cancela el partido (RF-043) con motivo (RN-021).
     *
     * @return el detalle del partido ya con su nuevo estado
     */
    @Transactional
    public PartidoDetalleDto transicionar(UUID partidoId, TransicionPartidoRequest peticion) {
        autorizacion.exigir(CAMBIAR_ESTADO, ObjetivoAutorizacion.deLiga());
        validarPeticion(peticion);
        DatosPartido partido = partidos.bloquear(ligaId, partidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Partido " + partidoId));
        AccionPartido accion = AccionPartido.hacia(peticion.estadoNuevo());
        List<AccionPartido> admitidas = AccionPartido.desde(partido.estado());
        if (accion == null || !admitidas.contains(accion)) {
            throw ReglaNegocioException.conflicto("RF-046", mensajeNoPermitida(partido.estado(), peticion.estadoNuevo()))
                    .con("estadoActual", partido.estado())
                    .con("accionesPermitidas", partido.jornada().estadoPublicacion() == EstadoJornada.PUBLICADO
                            ? admitidas : List.of());
        }
        if (partido.jornada().estadoPublicacion() != EstadoJornada.PUBLICADO) {
            throw ReglaNegocioException.conflicto("RF-090",
                    "La jornada " + partido.jornada().numero() + " todavía no está publicada: ajusta el partido "
                            + "desde la programación en lugar de cambiar su estado.");
        }
        UUID canchaId = null;
        if (accion == AccionPartido.REPROGRAMAR) {
            canchaId = peticion.canchaId() == null ? partido.cancha().id() : peticion.canchaId();
            reprogramacion.validar(partidoId, peticion.inicio(), canchaId);
        }
        String motivo = peticion.motivo().strip();
        motivoOperacion.fijar(motivo);
        if (accion == AccionPartido.REPROGRAMAR) {
            partidos.reprogramar(partidoId, peticion.inicio(), canchaId);
        } else {
            // Solo el estado: suspender o cancelar no pasa por las reglas de programación (DEC-162).
            partidos.cambiarEstado(partidoId, peticion.estadoNuevo());
        }
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, ACCION_AUDITORIA, "partidos", partidoId.toString(),
                motivo, detalleAuditoria(partido, peticion, canchaId)));
        DatosPartido actualizado = partidos.bloquear(ligaId, partidoId).orElseThrow();
        return detalle(actualizado, true);
    }

    private void validarPeticion(TransicionPartidoRequest peticion) {
        if (peticion == null || peticion.estadoNuevo() == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, "Indica el estado nuevo del partido.");
        }
        String motivo = peticion.motivo() == null ? "" : peticion.motivo().strip();
        if (motivo.length() < MINIMO_MOTIVO || motivo.length() > MAXIMO_MOTIVO) {
            throw ReglaNegocioException.invalida("RN-021", "Explica el motivo del cambio de estado con "
                    + MINIMO_MOTIVO + " a " + MAXIMO_MOTIVO + " caracteres: queda en el historial del partido.");
        }
        boolean reprogramar = peticion.estadoNuevo() == EstadoPartido.REPROGRAMADO;
        if (!reprogramar && (peticion.inicio() != null || peticion.canchaId() != null)) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "La fecha y la cancha solo se indican al reprogramar el partido.");
        }
        if (reprogramar && peticion.inicio() == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Indica la nueva fecha y hora del partido reprogramado.");
        }
        if (reprogramar && !peticion.inicio().isAfter(OffsetDateTime.now(reloj))) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "La nueva fecha y hora del partido debe ser posterior al momento actual.");
        }
    }

    private DatosPartido visible(UUID partidoId) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        return partidos.buscarVisible(ligaId, partidoId, alcance)
                .orElseThrow(() -> new RecursoNoEncontradoException("Partido " + partidoId));
    }

    private PartidoDetalleDto detalle(DatosPartido partido, boolean puedeCambiar) {
        UUID id = partido.id();
        return new PartidoDetalleDto(id, partido.jornada(), partido.temporada(), partido.categoria(), partido.grupo(),
                partido.equipoLocal(), partido.equipoVisitante(), partido.cancha(), partido.inicio(),
                partido.finBloque(), partido.estado(), partidos.arbitros(id), partidos.cedula(id).orElse(null),
                partidos.resultadoVigente(id).orElse(null), partidos.historial(id), acciones(partido, puedeCambiar));
    }

    private boolean puedeCambiarEstado() {
        return autorizacion.permite(CAMBIAR_ESTADO, ObjetivoAutorizacion.deLiga());
    }

    private static List<AccionPartido> acciones(DatosPartido partido, boolean puedeCambiar) {
        if (!puedeCambiar || partido.jornada().estadoPublicacion() != EstadoJornada.PUBLICADO) {
            return List.of();
        }
        return AccionPartido.desde(partido.estado());
    }

    private static String mensajeNoPermitida(EstadoPartido actual, EstadoPartido nuevo) {
        if (nuevo == EstadoPartido.EN_CURSO || nuevo == EstadoPartido.FINALIZADO || nuevo == EstadoPartido.PROGRAMADO) {
            return "El partido no se pasa a " + nuevo + " a mano: inicia a su hora y se finaliza al aprobarse su "
                    + "cédula (RF-127).";
        }
        if (actual == nuevo) {
            return "El partido ya está " + actual + ".";
        }
        return "Un partido " + actual + " no puede pasar a " + nuevo + "."
                + (AccionPartido.desde(actual).isEmpty() ? " Su estado ya no admite cambios manuales." : "");
    }

    private static Map<String, Object> detalleAuditoria(DatosPartido partido, TransicionPartidoRequest peticion,
            UUID canchaId) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", partido.estado().name());
        detalle.put("estadoNuevo", peticion.estadoNuevo().name());
        if (peticion.estadoNuevo() == EstadoPartido.REPROGRAMADO) {
            detalle.put("inicioAnterior", partido.inicio().toString());
            detalle.put("inicioNuevo", peticion.inicio().toString());
            detalle.put("canchaAnteriorId", partido.cancha().id().toString());
            detalle.put("canchaNuevaId", canchaId.toString());
        }
        return detalle;
    }
}

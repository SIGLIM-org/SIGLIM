package mx.tecnm.morelia.siglim.arbitraje;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionRepository.DesignacionVigente;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionRepository.PartidoDeJornada;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionRepository.PartidoDesignable;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionesJornadaDto.AvanceDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionesJornadaDto.JornadaDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionesJornadaDto.PartidoDto;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Designación de árbitros a los partidos de una jornada y «Mis designaciones» (RF-023, H-14.1).
 *
 * <ul>
 *   <li><b>Quién designa (Apéndice E).</b> La Comisión de Arbitraje con
 *       {@code arbitraje.designacion:EDITAR} y ámbito {@code LIGA}: la vista por jornada usa
 *       {@code arbitraje.designacion:CONSULTAR} y los candidatos, designar y retirar usan
 *       {@code EDITAR}. Todo con {@code ObjetivoAutorizacion.deLiga()} (ADR-0007).</li>
 *   <li><b>Designar (DEC-201).</b> Orden: cuerpo ({@code 400 VALIDACION}) → partido de la liga,
 *       bloqueado ({@code 404}) → permiso ({@code 403 VAL-015}, después del {@code 404} como en
 *       {@link DisponibilidadArbitralService}) → árbitro de la liga, bloqueado
 *       ({@code 422 REFERENCIA_INVALIDA}) → partido {@code PROGRAMADO} o {@code REPROGRAMADO}
 *       ({@code 409 RF-023} <b>[PROPUESTA]</b>) → árbitro ya designado en el partido con cualquier
 *       función ({@code 409 RF-023}) → a lo más dos asistentes ({@code 409 RF-023}
 *       <b>[PROPUESTA]</b>) → alta. Siempre se bloquea primero el partido y después el árbitro.</li>
 *   <li><b>Reglas del trigger.</b> RF-113 (solo validados), VAL-019 (club de un equipo, RF-115) y
 *       RF-039 (choque de horario) <b>no</b> se repiten aquí: las aplica
 *       {@code tg_designaciones_validar} y {@code TraductorErroresBd} responde {@code 409 RF-113},
 *       {@code 422 VAL-019} y {@code 409 RF-039}. La interfaz las anticipa con
 *       {@link ElegibilidadArbitral} (DEC-204), que usa las mismas condiciones.</li>
 *   <li><b>Sustituir al central (DEC-202).</b> Si ya hay central vigente, designar otro central
 *       exige motivo de 10 a 500 caracteres tras recortarlo ({@code 400 RN-021}): la anterior queda
 *       {@code vigente = false} con {@code motivo_cambio} y después se inserta la nueva. Sin central
 *       vigente, el motivo se ignora.</li>
 *   <li><b>Retirar (DEC-203).</b> Motivo de 10 a 500 caracteres ({@code 400 RN-021}) →
 *       designación de la liga ({@code 404}) → permiso → partido bloqueado → la designación sigue
 *       vigente y el partido está {@code PROGRAMADO} o {@code REPROGRAMADO} ({@code 409 RF-023}).
 *       Nada se borra (RN-005).</li>
 *   <li><b>Mis designaciones (DEC-205).</b> Exige que alguna asignación otorgue
 *       {@code arbitraje.designacion:CONSULTAR} ({@code 403 VAL-015} si ninguna) y filtra
 *       <b>siempre</b> por {@code es_titular} de la persona del árbitro, sea cual sea el ámbito:
 *       solo designaciones vigentes en jornadas {@code PUBLICADO}.</li>
 *   <li><b>Auditoría (RF-093).</b> {@code DESIGNAR_ARBITRO}, {@code SUSTITUIR_ARBITRO} y
 *       {@code RETIRAR_DESIGNACION} en {@code M-05} sobre {@code designaciones}, con
 *       {@code partidoId}, {@code arbitroId}, {@code funcion} y, si aplica,
 *       {@code designacionAnteriorId} y {@code motivo}; sin nombres ni CURP.</li>
 * </ul>
 *
 * <p>El filtro {@code arbitroId} de {@code GET /designaciones} y el historial de designaciones
 * (RF-024) llegan con B-10.
 *
 * <p>Refs: H-14.1, RF-023, RF-039, RF-093, RF-113, RF-115, RN-005, RN-021, VAL-015, VAL-019, UI-025,
 * CU-020, DEC-181, DEC-201, DEC-202, DEC-203, DEC-204, DEC-205, ADR-0007
 */
@Service
public class DesignacionService {

    static final String CONSULTAR = "arbitraje.designacion:CONSULTAR";
    static final String EDITAR = "arbitraje.designacion:EDITAR";
    private static final String MODULO = "M-05";
    private static final String ENTIDAD = "designaciones";
    /** [PROPUESTA] Igual que los demás motivos del sistema (RN-021). */
    static final int MINIMO_MOTIVO = 10;
    static final int MAXIMO_MOTIVO = 500;
    /** [PROPUESTA] Asistentes vigentes por partido (DEC-201). */
    static final int MAXIMO_ASISTENTES = 2;

    private final DesignacionRepository designaciones;
    private final ElegibilidadArbitral elegibilidad;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    DesignacionService(DesignacionRepository designaciones, ElegibilidadArbitral elegibilidad,
            Autorizacion autorizacion, AuditoriaService auditoria, SiglimPropiedades propiedades) {
        this.designaciones = designaciones;
        this.elegibilidad = elegibilidad;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Partidos no cancelados de la jornada con sus designaciones vigentes y el avance (UI-025).
     *
     * @throws ReglaNegocioException 400 {@code VALIDACION} si falta {@code jornadaId}
     * @throws RecursoNoEncontradoException 404 si la jornada no existe en la liga
     */
    @Transactional(readOnly = true)
    public DesignacionesJornadaDto listarPorJornada(UUID jornadaId) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        if (jornadaId == null) {
            throw invalida("Indica la jornada de la que quieres ver las designaciones.");
        }
        JornadaDto jornada = designaciones.buscarJornada(ligaId, jornadaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Jornada " + jornadaId));
        Map<UUID, List<DesignacionDto>> porPartido = designaciones.vigentesDeJornada(jornadaId).stream()
                .collect(Collectors.groupingBy(DesignacionDto::partidoId, LinkedHashMap::new, Collectors.toList()));

        List<PartidoDto> partidos = new ArrayList<>();
        int conCentral = 0;
        int asistentesCompletos = 0;
        for (PartidoDeJornada partido : designaciones.partidosDeJornada(jornadaId)) {
            List<DesignacionDto> vigentes = porPartido.getOrDefault(partido.id(), List.of());
            DesignacionDto central = vigentes.stream()
                    .filter(d -> d.funcion() == FuncionArbitral.CENTRAL)
                    .findFirst().map(DesignacionDto::enJornada).orElse(null);
            List<DesignacionDto> asistentes = vigentes.stream()
                    .filter(d -> d.funcion() == FuncionArbitral.ASISTENTE)
                    .map(DesignacionDto::enJornada).toList();
            if (central != null) {
                conCentral++;
            }
            if (asistentes.size() >= MAXIMO_ASISTENTES) {
                asistentesCompletos++;
            }
            partidos.add(new PartidoDto(partido.id(), partido.inicio(), partido.finBloque(), partido.estado(),
                    partido.cancha(), partido.equipoLocal(), partido.equipoVisitante(),
                    designable(partido.estado()), central, asistentes));
        }
        return new DesignacionesJornadaDto(jornada, new AvanceDto(partidos.size(), conCentral, asistentesCompletos),
                partidos);
    }

    /**
     * Árbitros validados de la liga con su elegibilidad, advertencias y carga para el partido
     * (DEC-204).
     *
     * @throws RecursoNoEncontradoException 404 si el partido no existe en la liga
     */
    @Transactional(readOnly = true)
    public List<CandidatoArbitralDto> candidatos(UUID partidoId) {
        buscarPartido(partidoId);
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        return elegibilidad.candidatos(ligaId, partidoId);
    }

    /**
     * Designa un árbitro central o asistente; si ya hay central, lo sustituye con motivo (RF-023,
     * DEC-201, DEC-202). El orden de los errores está en la descripción de la clase.
     *
     * @return la designación nueva, vigente, con {@code partidoId}
     */
    @Transactional
    public DesignacionDto designar(UUID partidoId, DesignarArbitroRequest peticion) {
        if (peticion == null || peticion.arbitroId() == null || peticion.funcion() == null) {
            throw invalida("Indica el árbitro y si es central o asistente.");
        }
        PartidoDesignable partido = designaciones.bloquearPartido(ligaId, partidoId)
                .orElseThrow(() -> noExistePartido(partidoId));
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        UUID arbitroId = peticion.arbitroId();
        if (!designaciones.bloquearArbitro(ligaId, arbitroId)) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                    "El árbitro no existe en la liga. Actualiza la lista de árbitros e intenta de nuevo.");
        }
        exigirDesignable(partido.estado(), "Solo se designan árbitros a partidos programados o reprogramados");

        List<DesignacionVigente> vigentes = designaciones.vigentesDelPartido(partidoId);
        Optional<DesignacionVigente> repetida = vigentes.stream()
                .filter(d -> d.arbitroId().equals(arbitroId)).findFirst();
        if (repetida.isPresent()) {
            throw ReglaNegocioException.conflicto("RF-023", "El árbitro ya está designado en este partido como "
                    + repetida.get().funcion().texto() + ".");
        }
        FuncionArbitral funcion = peticion.funcion();
        if (funcion == FuncionArbitral.ASISTENTE
                && vigentes.stream().filter(d -> d.funcion() == FuncionArbitral.ASISTENTE).count() >= MAXIMO_ASISTENTES) {
            throw ReglaNegocioException.conflicto("RF-023", "El partido ya tiene sus " + MAXIMO_ASISTENTES
                    + " asistentes; retira uno antes de designar otro.");
        }

        Optional<DesignacionVigente> centralAnterior = funcion == FuncionArbitral.CENTRAL
                ? vigentes.stream().filter(d -> d.funcion() == FuncionArbitral.CENTRAL).findFirst()
                : Optional.empty();
        String motivo = null;
        if (centralAnterior.isPresent()) {
            motivo = motivoValido(peticion.motivo(), "sustituir al árbitro central");
            designaciones.retirar(centralAnterior.get().id(), motivo);
        }

        UUID id = designaciones.insertar(partidoId, arbitroId, funcion, UsuarioActual.idRequerido(), false);
        Map<String, Object> detalle = detalle(partidoId, arbitroId, funcion);
        centralAnterior.ifPresent(anterior -> detalle.put("designacionAnteriorId", anterior.id().toString()));
        if (motivo != null) {
            detalle.put("motivo", motivo);
        }
        auditar(centralAnterior.isPresent() ? "SUSTITUIR_ARBITRO" : "DESIGNAR_ARBITRO", id, motivo, detalle);
        return designaciones.buscar(ligaId, id).orElseThrow();
    }

    /**
     * Retira una designación vigente con motivo (DEC-203). La fila se conserva con
     * {@code vigente = false} y {@code motivo_cambio}.
     *
     * @return la designación ya sin vigencia
     */
    @Transactional
    public DesignacionDto retirar(UUID designacionId, RetirarDesignacionRequest peticion) {
        String motivo = motivoValido(peticion == null ? null : peticion.motivo(), "retirar la designación");
        DesignacionDto encontrada = designacionId == null ? null
                : designaciones.buscar(ligaId, designacionId).orElse(null);
        if (encontrada == null) {
            throw new RecursoNoEncontradoException("Designación " + designacionId);
        }
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        PartidoDesignable partido = designaciones.bloquearPartido(ligaId, encontrada.partidoId())
                .orElseThrow(() -> noExistePartido(encontrada.partidoId()));
        DesignacionDto actual = designaciones.bloquear(ligaId, designacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Designación " + designacionId));
        if (!Boolean.TRUE.equals(actual.vigente())) {
            throw ReglaNegocioException.conflicto("RF-023", "La designación ya no está vigente: se retiró o se "
                    + "sustituyó antes. Actualiza la página.");
        }
        exigirDesignable(partido.estado(), "Solo se retiran designaciones de partidos programados o reprogramados");

        designaciones.retirar(designacionId, motivo);
        Map<String, Object> detalle = detalle(actual.partidoId(), actual.arbitro().id(), actual.funcion());
        detalle.put("motivo", motivo);
        auditar("RETIRAR_DESIGNACION", designacionId, motivo, detalle);
        return designaciones.buscar(ligaId, designacionId).orElseThrow();
    }

    /**
     * Designaciones vigentes del árbitro de la sesión (o de un menor bajo su tutela) en jornadas
     * publicadas, por inicio del partido (DEC-205).
     *
     * @throws ReglaNegocioException 403 {@code VAL-015} si ninguna asignación otorga el permiso; 400
     *                               {@code VALIDACION} si la página o el rango no son válidos
     */
    @Transactional(readOnly = true)
    public Pagina<MiDesignacionDto> listarPropias(FiltroMisDesignaciones filtro) {
        // Solo exige el permiso: el listado es «mío» con cualquier ámbito (es_titular en el SQL).
        autorizacion.alcance(CONSULTAR);
        filtro.validar();
        return Pagina.de(designaciones.misDesignaciones(ligaId, filtro), filtro.pagina(), filtro.tamano(),
                designaciones.contarMisDesignaciones(ligaId, filtro));
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private PartidoDesignable buscarPartido(UUID partidoId) {
        if (partidoId == null) {
            throw noExistePartido(null);
        }
        return designaciones.buscarPartido(ligaId, partidoId).orElseThrow(() -> noExistePartido(partidoId));
    }

    private static RecursoNoEncontradoException noExistePartido(UUID id) {
        return new RecursoNoEncontradoException("Partido " + id);
    }

    /** Solo un partido {@code PROGRAMADO} o {@code REPROGRAMADO} admite designar o retirar [PROPUESTA]. */
    static boolean designable(EstadoPartido estado) {
        return estado == EstadoPartido.PROGRAMADO || estado == EstadoPartido.REPROGRAMADO;
    }

    private static void exigirDesignable(EstadoPartido estado, String regla) {
        if (!designable(estado)) {
            throw ReglaNegocioException.conflicto("RF-023", regla + "; este está " + texto(estado) + ".");
        }
    }

    static String texto(EstadoPartido estado) {
        return switch (estado) {
            case PROGRAMADO -> "programado";
            case EN_CURSO -> "en curso";
            case FINALIZADO -> "finalizado";
            case SUSPENDIDO -> "suspendido";
            case REPROGRAMADO -> "reprogramado";
            case CANCELADO -> "cancelado";
        };
    }

    /** Motivo de 10 a 500 caracteres tras recortarlo (RN-021). */
    private static String motivoValido(String motivo, String accion) {
        String recortado = motivo == null ? "" : motivo.strip();
        if (recortado.length() < MINIMO_MOTIVO || recortado.length() > MAXIMO_MOTIVO) {
            throw ReglaNegocioException.invalida("RN-021", "Escribe un motivo de " + MINIMO_MOTIVO + " a "
                    + MAXIMO_MOTIVO + " caracteres para " + accion + ".");
        }
        return recortado;
    }

    private static Map<String, Object> detalle(UUID partidoId, UUID arbitroId, FuncionArbitral funcion) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("partidoId", partidoId.toString());
        detalle.put("arbitroId", arbitroId.toString());
        detalle.put("funcion", funcion.name());
        return detalle;
    }

    private void auditar(String accion, UUID designacionId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, designacionId.toString(), motivo,
                detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}

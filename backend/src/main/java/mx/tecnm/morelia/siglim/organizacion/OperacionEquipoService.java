package mx.tecnm.morelia.siglim.organizacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoRepository.EquipoOperable;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Venta de un equipo y conservación de su plaza (RF-103, RF-104, CU-026, RN-040, UI-034).
 *
 * <ul>
 *   <li><b>Estados (RN-040).</b> La operación nace {@code SOLICITADA}; solo el Presidente la
 *       resuelve a {@code AUTORIZADA} o {@code RECHAZADA}, y esos dos estados son terminales aquí.
 *       {@code APLICADA} la fija el cierre de la temporada (H-04.1, B-05.2), que también mueve el
 *       equipo de club; esta subtarea no lo aplica. Repetir la resolución responde {@code 409} con
 *       el código del tipo ({@code RF-103} venta, {@code RF-104} conservación).</li>
 *   <li><b>Venta.</b> Exige un {@code clubDestinoId} distinto del club actual del equipo. La
 *       conservación de plaza es una operación <b>independiente</b> y no lleva club destino: se
 *       registran por separado, sin bloquearse entre sí (DEC-14).</li>
 *   <li><b>Una vigente por tipo (DEC-132).</b> Un equipo no puede tener dos operaciones del mismo
 *       tipo en {@code SOLICITADA} o {@code AUTORIZADA}: la segunda responde {@code 409} con el
 *       código del tipo. Así el cierre de la temporada (H-04.1) sabe a qué club pasa el equipo.</li>
 *   <li><b>Autorización.</b> Al autorizar se vuelve a comprobar que la temporada siga abierta
 *       ({@code 409 RF-003}) y, en la venta, que el club destino siga {@code ACTIVO}
 *       ({@code 409 RN-041}); el rechazo no lo exige.</li>
 *   <li><b>Ámbito (ADR-0007).</b> Registrar usa {@code organizacion.venta:SOLICITAR} sobre el club
 *       actual del equipo; resolver usa {@code organizacion.venta:APROBAR}. Un equipo o una
 *       operación de otro club responde {@code 404} igual que uno inexistente (VAL-024); nunca se
 *       compara el nombre del rol.</li>
 *   <li><b>Consulta (B-05.3).</b> La bandeja usa el alcance unificado de {@code SOLICITAR} y
 *       {@code APROBAR} en el {@code WHERE} y en el {@code COUNT}; el detalle exige el mismo alcance
 *       sobre el club de origen. El contexto entrega los clubes {@code ACTIVO} de la liga para
 *       elegir el destino de una venta, sin datos personales.</li>
 *   <li><b>Referencias.</b> El equipo no puede estar {@code BAJA} ({@code 409 RF-009}) ni ser de una
 *       temporada {@code CERRADA} ({@code 409 RF-003}). El club destino debe existir en la liga
 *       ({@code 422 REFERENCIA_INVALIDA}) y estar {@code ACTIVO} ({@code 409 RN-041}).</li>
 *   <li><b>Auditoría (RF-093).</b> Solicitud y dictamen se registran en {@code M-03} sobre
 *       {@code operaciones_equipo}, con identificadores, tipo y estados; la solicitud agrega sus
 *       observaciones al detalle cuando las lleva (el dictamen puede reemplazarlas en la fila) y el
 *       rechazo deja las suyas como motivo.</li>
 * </ul>
 *
 * <p>Refs: B-05.1, H-06, RF-103, RF-104, CU-026, RN-040, DEC-14, DEC-132, RN-021, RN-041, VAL-024, ADR-0007
 */
@Service
public class OperacionEquipoService {

    private static final String SOLICITAR = "organizacion.venta:SOLICITAR";
    private static final String APROBAR = "organizacion.venta:APROBAR";
    private static final String MODULO = "M-03";
    private static final String ENTIDAD = "operaciones_equipo";

    /** Observaciones del dictamen: de 10 a 500 caracteres, como las demás transiciones (RN-021). */
    private static final int MINIMO_OBSERVACIONES = 10;
    private static final int MAXIMO_OBSERVACIONES = 500;

    private final OperacionEquipoRepository operaciones;
    private final ClubRepository clubes;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final Clock reloj;
    private final UUID ligaId;

    OperacionEquipoService(OperacionEquipoRepository operaciones, ClubRepository clubes,
            Autorizacion autorizacion, AuditoriaService auditoria, MotivoOperacion motivoOperacion,
            Clock reloj, SiglimPropiedades propiedades) {
        this.operaciones = operaciones;
        this.clubes = clubes;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Bandeja de operaciones de la liga, filtrada por el alcance de {@code SOLICITAR} o
     * {@code APROBAR} en el SQL y en el total (B-05.3, RF-103, RF-104, ADR-0007).
     *
     * <p>La Directiva ve las operaciones de su club; el Presidente, las de toda la liga. Una
     * temporada o un estado fuera del alcance del usuario dan una página vacía, no un error.
     */
    @Transactional(readOnly = true)
    public Pagina<OperacionEquipoDto> listar(FiltroOperacionesEquipo filtro) {
        AlcanceAutorizacion alcance = alcanceConsulta();
        filtro.validar();
        return Pagina.de(operaciones.listar(ligaId, filtro, alcance), filtro.pagina(), filtro.tamano(),
                operaciones.contar(ligaId, filtro, alcance));
    }

    /**
     * Detalle de una operación; la de otro club responde {@code 404} igual que una inexistente
     * (criterio 5 de B-05, VAL-024).
     */
    @Transactional(readOnly = true)
    public OperacionEquipoDto consultar(UUID id) {
        OperacionEquipoDto operacion = buscar(id);
        exigirConsulta(operacion.clubOrigen().id());
        return operacion;
    }

    /**
     * Clubes {@code ACTIVO} de la liga para elegir el destino de una venta (B-05.3). La Directiva
     * necesita el catálogo completo aunque su ámbito de consulta de clubes solo alcance el propio;
     * solo se expone identificador y nombre, nunca datos personales.
     */
    @Transactional(readOnly = true)
    public ContextoOperacionesEquipoDto contexto() {
        alcanceConsulta();
        return new ContextoOperacionesEquipoDto(clubes.listarActivosDeLiga(ligaId).stream()
                .map(club -> new ReferenciaDto(club.id(), club.nombre()))
                .toList());
    }

    /**
     * Registra la venta de un equipo o la conservación de su plaza y la deja {@code SOLICITADA}
     * (criterios 1 y 4 de B-05, RF-103, RF-104).
     *
     * <p>Orden: sin tipo o equipo, {@code 400 VALIDACION}; equipo inexistente o fuera de ámbito,
     * {@code 404}; sin permiso, {@code 403}; temporada {@code CERRADA}, {@code 409 RF-003}; equipo
     * {@code BAJA}, {@code 409 RF-009}; reglas del tipo (venta sin club destino o con el de origen,
     * conservación con club destino), {@code 400 VALIDACION}; club destino inexistente, {@code 422
     * REFERENCIA_INVALIDA}; club destino cerrado, {@code 409 RN-041}; observaciones de más de 500
     * caracteres, {@code 400 VALIDACION}; operación vigente del mismo tipo, {@code 409 RF-103} o
     * {@code RF-104} (DEC-132).
     *
     * @throws ReglaNegocioException 400 si la venta no indica un club destino o repite el de origen,
     *                               o si la conservación de plaza lleva club destino; 409 si el
     *                               equipo ya tiene una operación vigente del mismo tipo
     */
    @Transactional
    public OperacionEquipoDto crear(RegistrarOperacionEquipoRequest peticion) {
        if (peticion == null || peticion.tipo() == null) {
            throw invalida("Indica si la operación es una venta o una conservación de plaza.");
        }
        if (peticion.equipoId() == null) {
            throw invalida("Indica el equipo de la operación.");
        }

        EquipoOperable equipo = operaciones.buscarEquipo(ligaId, peticion.equipoId())
                .orElseThrow(() -> noExisteEquipo(peticion.equipoId()));
        autorizacion.exigir(SOLICITAR, ObjetivoAutorizacion.deClubes(equipo.clubId()));
        exigirTemporadaAbierta(equipo);
        if (equipo.estado() == EstadoEquipo.BAJA) {
            throw ReglaNegocioException.conflicto("RF-009",
                    "El equipo «" + equipo.nombre() + "» está dado de baja: no admite venta ni "
                            + "conservación de plaza.");
        }

        UUID clubDestinoId = clubDestinoValido(peticion, equipo);
        String observaciones = textoOpcional(peticion.observaciones());
        exigirSinOperacionVigente(peticion.tipo(), equipo);

        UUID perfil = UsuarioActual.idRequerido();
        UUID id = operaciones.insertar(peticion.tipo(), equipo.id(), equipo.clubId(), clubDestinoId,
                perfil, observaciones);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tipo", peticion.tipo().name());
        detalle.put("equipoId", equipo.id().toString());
        detalle.put("clubOrigenId", equipo.clubId().toString());
        if (clubDestinoId != null) {
            detalle.put("clubDestinoId", clubDestinoId.toString());
        }
        detalle.put("estado", EstadoOperacionEquipo.SOLICITADA.name());
        if (observaciones != null) {
            detalle.put("observaciones", observaciones);
        }
        auditar("SOLICITAR_OPERACION_EQUIPO", id, null, detalle);
        return buscar(id);
    }

    /**
     * Aplica el dictamen del Presidente sobre una operación {@code SOLICITADA} (criterio 2 de B-05,
     * RN-040). La autorización admite una nota; el rechazo exige observaciones de 10 a 500
     * caracteres, que se guardan como motivo (RN-021). Autorizar sin nota conserva las observaciones
     * de la solicitud.
     *
     * <p>Orden: inexistente o de otro club, {@code 404}; sin permiso, {@code 403}; ya resuelta,
     * {@code 409 RF-103} o {@code RF-104}; sin decisión u observaciones de más de 500 caracteres,
     * {@code 400 VALIDACION}; al autorizar, temporada {@code CERRADA}, {@code 409 RF-003}, y club
     * destino de la venta {@code CERRADO}, {@code 409 RN-041}; al rechazar sin observaciones
     * suficientes, {@code 400 RN-021}.
     *
     * @throws ReglaNegocioException 409 {@code RF-103} o {@code RF-104} si ya está resuelta, 409
     *                               {@code RF-003} o {@code RN-041} si al autorizar la temporada o el
     *                               club destino ya están cerrados; 400 {@code RN-021} si rechaza sin
     *                               observaciones suficientes
     */
    @Transactional
    public OperacionEquipoDto resolver(UUID id, ResolverOperacionEquipoRequest peticion) {
        OperacionEquipoDto actual = operaciones.buscarParaActualizar(ligaId, id)
                .orElseThrow(() -> noExiste(id));
        autorizacion.exigir(APROBAR, ObjetivoAutorizacion.deClubes(actual.clubOrigen().id()));
        if (actual.estado() != EstadoOperacionEquipo.SOLICITADA) {
            throw ReglaNegocioException.conflicto(actual.tipo().requisito(),
                    "Solo se resuelve una operación en estado SOLICITADA; esta ya está "
                            + actual.estado().texto() + ".");
        }
        DecisionOperacionEquipo decision = peticion == null ? null : peticion.decision();
        if (decision == null) {
            throw invalida("Indica si la operación se autoriza o se rechaza.");
        }

        String observaciones = textoOpcional(peticion.observaciones());
        if (decision == DecisionOperacionEquipo.RECHAZADA) {
            observaciones = observacionesValidas(observaciones);
            motivoOperacion.fijar(observaciones);
        } else {
            exigirVigenteAlAutorizar(actual);
        }

        UUID perfil = UsuarioActual.idRequerido();
        operaciones.resolver(id, decision.estado(), perfil, OffsetDateTime.now(reloj), observaciones);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tipo", actual.tipo().name());
        detalle.put("equipoId", actual.equipo().id().toString());
        detalle.put("estadoAnterior", EstadoOperacionEquipo.SOLICITADA.name());
        detalle.put("estado", decision.estado().name());
        auditar(decision == DecisionOperacionEquipo.AUTORIZADA
                ? "AUTORIZAR_OPERACION_EQUIPO"
                : "RECHAZAR_OPERACION_EQUIPO", id,
                decision == DecisionOperacionEquipo.RECHAZADA ? observaciones : null, detalle);
        return buscar(id);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private OperacionEquipoDto buscar(UUID id) {
        return operaciones.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    // ── Autorización de consulta (SOLICITAR o APROBAR) ─────────────────────────────────────────

    /**
     * Alcance del listado con cualquiera de los dos permisos de la operación: la Directiva solicita
     * y el Presidente aprueba. Los alcances se unen para no ocultar filas cuando una persona tiene
     * ambos.
     */
    private AlcanceAutorizacion alcanceConsulta() {
        AlcanceAutorizacion solicitar = alcanceOpcional(SOLICITAR);
        AlcanceAutorizacion aprobar = alcanceOpcional(APROBAR);
        if (solicitar == null && aprobar == null) {
            throw Autorizacion.sinPermiso();
        }
        if (solicitar == null) {
            return aprobar;
        }
        if (aprobar == null) {
            return solicitar;
        }
        Set<UUID> clubes = new LinkedHashSet<>(solicitar.clubes());
        clubes.addAll(aprobar.clubes());
        return new AlcanceAutorizacion(solicitar.todaLaLiga() || aprobar.todaLaLiga(), clubes,
                solicitar.propio() || aprobar.propio());
    }

    private AlcanceAutorizacion alcanceOpcional(String permiso) {
        try {
            return autorizacion.alcance(permiso);
        } catch (ReglaNegocioException sinPermiso) {
            if (sinPermiso.estado() == HttpStatus.FORBIDDEN) {
                return null;
            }
            throw sinPermiso;
        }
    }

    /**
     * Exige cualquiera de los dos permisos sobre el club de origen. Se evalúa con el alcance
     * unificado en lugar de llamar a {@code exigir} dos veces: atrapar su {@code 403} dejaría la
     * transacción en rollback-only. Un club fuera del alcance responde {@code 404} igual que uno
     * inexistente (VAL-024).
     */
    private void exigirConsulta(UUID clubId) {
        AlcanceAutorizacion alcance = alcanceConsulta();
        if (!alcance.todaLaLiga() && !alcance.clubes().contains(clubId)) {
            throw Autorizacion.fueraDeAmbito(SOLICITAR);
        }
    }

    /**
     * Valida el club destino según el tipo de operación: obligatorio y distinto del actual en la
     * venta; ausente en la conservación de plaza, que es independiente (DEC-14, RF-104).
     */
    private UUID clubDestinoValido(RegistrarOperacionEquipoRequest peticion, EquipoOperable equipo) {
        if (peticion.tipo() == TipoOperacionEquipo.CONSERVACION_PLAZA) {
            if (peticion.clubDestinoId() != null) {
                throw invalida("La conservación de plaza no lleva club destino: es una operación "
                        + "independiente de la venta.");
            }
            return null;
        }
        if (peticion.clubDestinoId() == null) {
            throw invalida("Indica el club destino de la venta.");
        }
        if (peticion.clubDestinoId().equals(equipo.clubId())) {
            throw invalida("El club destino de la venta debe ser distinto del club actual del equipo.");
        }
        ClubDto destino = clubes.buscarParaCompartir(ligaId, peticion.clubDestinoId())
                .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "El club destino indicado no existe en la liga. Actualiza la página y elige otro."));
        if (destino.estado() == EstadoClub.CERRADO) {
            throw ReglaNegocioException.conflicto("RN-041",
                    "El club «" + destino.nombre() + "» está cerrado: no puede recibir equipos.");
        }
        return destino.id();
    }

    /**
     * Un equipo solo tiene una operación vigente ({@code SOLICITADA} o {@code AUTORIZADA}) de cada
     * tipo (DEC-132). El candado del equipo, tomado al leerlo, serializa dos solicitudes simultáneas.
     */
    private void exigirSinOperacionVigente(TipoOperacionEquipo tipo, EquipoOperable equipo) {
        operaciones.estadoVigente(equipo.id(), tipo).ifPresent(estado -> {
            throw ReglaNegocioException.conflicto(tipo.requisito(),
                    "El equipo «" + equipo.nombre() + "» ya tiene una "
                            + (tipo == TipoOperacionEquipo.VENTA ? "venta" : "conservación de plaza")
                            + " " + estado.texto() + ": espera a que se resuelva o se aplique antes "
                            + "de registrar otra.");
        });
    }

    /**
     * Lo que pudo cambiar entre la solicitud y la autorización: la temporada debe seguir abierta y,
     * en la venta, el club destino debe seguir {@code ACTIVO} (RF-003, RN-041).
     */
    private void exigirVigenteAlAutorizar(OperacionEquipoDto actual) {
        if (actual.temporada().estado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + actual.temporada().nombre() + "» está cerrada: la operación "
                            + "ya no se puede autorizar.");
        }
        if (actual.tipo() == TipoOperacionEquipo.VENTA && actual.clubDestino() != null) {
            ClubDto destino = clubes.buscarParaCompartir(ligaId, actual.clubDestino().id())
                    .orElse(null);
            if (destino == null || destino.estado() == EstadoClub.CERRADO) {
                throw ReglaNegocioException.conflicto("RN-041",
                        "El club «" + actual.clubDestino().nombre() + "» está cerrado: no puede "
                                + "recibir equipos, así que la venta no se puede autorizar.");
            }
        }
    }

    private static void exigirTemporadaAbierta(EquipoOperable equipo) {
        if (equipo.temporadaEstado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + equipo.temporadaNombre() + "» está cerrada: sus equipos no "
                            + "admiten operaciones.");
        }
    }

    private static String observacionesValidas(String observaciones) {
        String texto = observaciones == null ? "" : observaciones.strip();
        if (texto.length() < MINIMO_OBSERVACIONES) {
            throw ReglaNegocioException.invalida("RN-021",
                    "Escribe observaciones de al menos " + MINIMO_OBSERVACIONES
                            + " caracteres para rechazar la operación.");
        }
        return texto;
    }

    private static String textoOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String valor = texto.strip();
        if (valor.length() > MAXIMO_OBSERVACIONES) {
            throw invalida("Las observaciones admiten hasta " + MAXIMO_OBSERVACIONES + " caracteres.");
        }
        return valor;
    }

    private void auditar(String accion, UUID entidadId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, entidadId.toString(),
                motivo, detalle));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Operación de equipo " + id);
    }

    private static RecursoNoEncontradoException noExisteEquipo(UUID id) {
        return new RecursoNoEncontradoException("Equipo " + id);
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}

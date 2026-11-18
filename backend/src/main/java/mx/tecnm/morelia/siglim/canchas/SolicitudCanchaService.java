package mx.tecnm.morelia.siglim.canchas;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.canchas.SolicitudCanchaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trámites de cancha: préstamo y comodato (Formato 1), oferta (Formato 2) y su carta con número de
 * oficio (RF-116, RF-117, RF-118, CU-024, CU-025, UI-028).
 *
 * <ul>
 *   <li><b>Estados (DEC-35).</b> El Formato 1 nace {@code PENDIENTE} y el Presidente lo deja
 *       {@code APROBADA} o {@code NO_APROBADA}; el Formato 2 nace {@code PENDIENTE_NEGOCIACION} y
 *       queda {@code ACEPTADA} o {@code RECHAZADA}. Los estados finales no cambian: dictaminar de
 *       nuevo responde {@code 409} con el requisito del formato ({@code RF-117} o
 *       {@code RF-116}).</li>
 *   <li><b>Solicitante.</b> El préstamo y el comodato los pide un club. La oferta la hace un club o
 *       un externo sin cuenta; la de un externo la captura un ámbito de liga (el Secretario), porque
 *       se autoriza sobre la liga y no sobre un club.</li>
 *   <li><b>Número de oficio (RF-118).</b> Lo asigna el sistema al capturar, consecutivo por liga y
 *       año con la forma {@code OF-AAAA-NNNN} (DEC-135, [PROPUESTA]). La carta se arma con los datos del
 *       trámite y la plantilla del formato del cliente (R8); no se guarda un PDF.</li>
 *   <li><b>Permisos (ADR-0007).</b> Capturar exige {@code canchas.solicitud:CREAR} sobre el club o
 *       la liga; dictaminar, {@code canchas.solicitud:APROBAR}; la carta,
 *       {@code canchas.oficio:GENERAR} (DEC-137: en la matriz inicial solo el Secretario; la
 *       Directiva no imprime su carta salvo que la matriz se lo otorgue). El catálogo no tiene un
 *       permiso de consulta propio: la bandeja usa el alcance unificado de los tres (DEC-136,
 *       [PROPUESTA]), en el {@code WHERE} y en el
 *       {@code COUNT}. Un trámite fuera del alcance responde {@code 404} igual que uno inexistente
 *       (VAL-024).</li>
 *   <li><b>Fuera de esta historia.</b> Aceptar una oferta o aprobar un comodato <b>no</b> da de alta
 *       la cancha ni sus horarios (DEC-138): la cancha se registra con RF-027 y los horarios con
 *       RF-119 (B-13).</li>
 *   <li><b>Auditoría (RF-093).</b> Captura y dictamen se registran en {@code M-06} sobre
 *       {@code solicitudes_cancha} con identificadores, tipo, número de oficio y estados; nunca con
 *       los nombres o teléfonos del formato. La decisión desfavorable deja sus observaciones como
 *       motivo (RN-021).</li>
 * </ul>
 *
 * <p>Refs: B-14, RF-116, RF-117, RF-118, RF-093, CU-024, CU-025, DEC-35, DEC-135 a DEC-138, RN-021, VAL-015, VAL-024,
 * ADR-0007
 */
@Service
public class SolicitudCanchaService {

    static final String CREAR = "canchas.solicitud:CREAR";
    static final String APROBAR = "canchas.solicitud:APROBAR";
    static final String GENERAR_OFICIO = "canchas.oficio:GENERAR";
    private static final String MODULO = "M-06";
    private static final String ENTIDAD = "solicitudes_cancha";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    /** Observaciones del dictamen: de 10 a 500 caracteres, como las demás transiciones (RN-021). */
    private static final int MINIMO_OBSERVACIONES = 10;
    private static final int MAXIMO_OBSERVACIONES = 500;

    private final SolicitudCanchaRepository solicitudes;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final Clock reloj;
    private final UUID ligaId;

    SolicitudCanchaService(SolicitudCanchaRepository solicitudes, Autorizacion autorizacion,
            AuditoriaService auditoria, MotivoOperacion motivoOperacion, Clock reloj,
            SiglimPropiedades propiedades) {
        this.solicitudes = solicitudes;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Bandeja de trámites visibles para la sesión: la Directiva ve los de su club; el Presidente y el
     * Secretario, los de toda la liga, incluidas las ofertas de externos (B-14, ADR-0007).
     */
    @Transactional(readOnly = true)
    public Pagina<SolicitudCanchaDto> listar(FiltroSolicitudesCancha filtro) {
        AlcanceAutorizacion alcance = alcanceConsulta();
        filtro.validar();
        return Pagina.de(solicitudes.listar(ligaId, filtro, alcance), filtro.pagina(), filtro.tamano(),
                solicitudes.contar(ligaId, filtro, alcance));
    }

    /**
     * Catálogos del formulario de captura: clubes por los que la sesión puede capturar, canchas
     * activas y si puede capturar ofertas de externos. Sin {@code CREAR} las listas van vacías.
     */
    @Transactional(readOnly = true)
    public ContextoSolicitudesCanchaDto contexto() {
        alcanceConsulta();
        AlcanceAutorizacion crear = alcanceOpcional(CREAR);
        if (crear == null) {
            return new ContextoSolicitudesCanchaDto(List.of(), List.of(), false);
        }
        return new ContextoSolicitudesCanchaDto(
                solicitudes.listarClubesActivos(ligaId, crear.todaLaLiga(), new ArrayList<>(crear.clubes())),
                solicitudes.listarCanchasActivas(ligaId),
                crear.todaLaLiga());
    }

    /**
     * Captura un préstamo, un comodato o una oferta con los campos de su formato, le asigna el
     * número de oficio y lo deja en su estado inicial (RF-116, RF-117, RF-118).
     *
     * <p>Orden: tipo, bloque del formato o solicitante incoherentes, {@code 400 VALIDACION}; sin
     * permiso, {@code 403}; club fuera del ámbito, {@code 404}; club inexistente o cerrado, o cancha
     * inexistente, {@code 422 REFERENCIA_INVALIDA}; hora de término no posterior al inicio,
     * {@code 400 VALIDACION}.
     */
    @Transactional
    public SolicitudCanchaDto crear(CrearSolicitudCanchaRequest peticion) {
        if (peticion == null || peticion.tipo() == null) {
            throw invalida("Indica si el trámite es un préstamo, un comodato o una oferta.");
        }
        TipoSolicitudCancha tipo = peticion.tipo();
        Object datos = datosDelFormato(peticion);
        String externo = textoOpcional(peticion.solicitanteExterno());
        validarSolicitante(tipo, peticion.clubId(), externo);

        autorizacion.exigir(CREAR, peticion.clubId() == null
                ? ObjetivoAutorizacion.deLiga()
                : ObjetivoAutorizacion.deClubes(peticion.clubId()));
        if (peticion.clubId() != null && solicitudes.buscarClubActivo(ligaId, peticion.clubId()).isEmpty()) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                    "El club indicado no existe en la liga o está cerrado. Actualiza la página y elige otro.");
        }
        if (peticion.canchaId() != null && !solicitudes.existeCancha(ligaId, peticion.canchaId())) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                    "La cancha indicada no existe en la liga. Actualiza la página y elige otra.");
        }
        if (datos instanceof DatosPrestamo prestamo) {
            validarHorario(prestamo);
        }

        int anio = OffsetDateTime.now(reloj).atZoneSameInstant(ZONA_DE_NEGOCIO).getYear();
        String numeroOficio = solicitudes.siguienteNumeroOficio(ligaId, anio);
        UUID id = solicitudes.insertar(ligaId, tipo, numeroOficio, peticion.clubId(), externo,
                peticion.canchaId(), datos, UsuarioActual.idRequerido());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tipo", tipo.name());
        detalle.put("formato", tipo.formato());
        detalle.put("numeroOficio", numeroOficio);
        if (peticion.clubId() != null) {
            detalle.put("clubId", peticion.clubId().toString());
        } else {
            detalle.put("oferenteExterno", true);
        }
        if (peticion.canchaId() != null) {
            detalle.put("canchaId", peticion.canchaId().toString());
        }
        detalle.put("estado", tipo.estadoInicial().name());
        auditar("REGISTRAR_SOLICITUD_CANCHA", id, null, detalle);
        return buscar(id);
    }

    /**
     * Dictamen del Presidente sobre un trámite pendiente (RF-116, RF-117). La decisión desfavorable
     * exige observaciones de 10 a 500 caracteres, que quedan como motivo (RN-021); la favorable las
     * admite como condiciones.
     *
     * <p>Orden: inexistente o fuera del ámbito, {@code 404}; sin permiso, {@code 403}; ya
     * dictaminado, {@code 409 RF-117} o {@code RF-116}; sin decisión, decisión de otro formato u
     * observaciones de más de 500 caracteres, {@code 400 VALIDACION}; desfavorable sin observaciones
     * suficientes, {@code 400 RN-021}.
     */
    @Transactional
    public SolicitudCanchaDto resolver(UUID id, ResolverSolicitudCanchaRequest peticion) {
        SolicitudCanchaDto actual = solicitudes.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
        autorizacion.exigir(APROBAR, objetivo(actual));
        if (!actual.estado().pendiente()) {
            throw ReglaNegocioException.conflicto(actual.tipo().requisito(),
                    "Este trámite ya se dictaminó: está " + actual.estado().texto() + ".");
        }
        DecisionSolicitudCancha decision = peticion == null ? null : peticion.decision();
        if (decision == null) {
            throw invalida("Indica la decisión sobre el trámite.");
        }
        if (!decision.admitidaPara(actual.tipo())) {
            throw invalida(actual.tipo().esOferta()
                    ? "Una oferta de cancha se acepta o se rechaza."
                    : "Un préstamo o comodato se aprueba o no se aprueba.");
        }

        String observaciones = observacionesOpcionales(peticion.observaciones());
        if (!decision.favorable()) {
            observaciones = observacionesObligatorias(observaciones);
            motivoOperacion.fijar(observaciones);
        }

        if (solicitudes.resolver(id, decision.estado(), UsuarioActual.idRequerido(),
                OffsetDateTime.now(reloj), observaciones) == 0) {
            throw ReglaNegocioException.conflicto(actual.tipo().requisito(),
                    "Este trámite ya se dictaminó. Actualiza la página para ver su estado.");
        }

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tipo", actual.tipo().name());
        detalle.put("numeroOficio", actual.numeroOficio());
        detalle.put("estadoAnterior", actual.estado().name());
        detalle.put("estado", decision.estado().name());
        auditar("RESOLVER_SOLICITUD_CANCHA", id, decision.favorable() ? null : observaciones, detalle);
        return buscar(id);
    }

    /**
     * Datos de la carta del trámite con su número de oficio, para la vista previa y el PDF (RF-118).
     * Exige {@code canchas.oficio:GENERAR} sobre el club del trámite o sobre la liga (externos).
     */
    @Transactional(readOnly = true)
    public CartaOficioDto oficio(UUID id) {
        SolicitudCanchaDto solicitud = buscar(id);
        autorizacion.exigir(GENERAR_OFICIO, objetivo(solicitud));
        TipoSolicitudCancha tipo = solicitud.tipo();
        String asunto = switch (tipo) {
            case PRESTAMO -> "Solicitud de préstamo de cancha deportiva.";
            case COMODATO -> "Solicitud de comodato de cancha deportiva.";
            case OFERTA -> "Oferta de disponibilidad de cancha deportiva.";
        };
        return new CartaOficioDto(
                solicitud.numeroOficio(),
                tipo,
                tipo.formato(),
                asunto,
                solicitudes.nombreLiga(ligaId),
                solicitud.capturadaEn().atZoneSameInstant(ZONA_DE_NEGOCIO).toLocalDate(),
                solicitud.nombreSolicitante(),
                solicitud.prestamo(),
                solicitud.oferta(),
                solicitud.estado(),
                solicitud.resueltaEn(),
                solicitud.resueltaPor() == null ? null : solicitud.resueltaPor().nombreMostrado(),
                solicitud.observaciones());
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    /** Exige el bloque del formato del tipo y rechaza el del otro formato. */
    private static Object datosDelFormato(CrearSolicitudCanchaRequest peticion) {
        if (peticion.tipo().esOferta()) {
            if (peticion.oferta() == null || peticion.prestamo() != null) {
                throw invalida("Una oferta se captura con los campos del Formato 2 (oferta de cancha).");
            }
            return peticion.oferta();
        }
        if (peticion.prestamo() == null || peticion.oferta() != null) {
            throw invalida("Un préstamo o comodato se captura con los campos del Formato 1 (solicitud de "
                    + "préstamo).");
        }
        return peticion.prestamo();
    }

    /**
     * El préstamo y el comodato los pide un club (RF-117). La oferta la hace un club o un externo,
     * nunca los dos (RF-116, {@code ck_solicitudes_cancha_solicitante}).
     */
    private static void validarSolicitante(TipoSolicitudCancha tipo, UUID clubId, String externo) {
        if (!tipo.esOferta()) {
            if (clubId == null || externo != null) {
                throw invalida("El " + tipo.texto() + " lo solicita un club: indica el club y deja vacío "
                        + "el oferente externo.");
            }
            return;
        }
        if ((clubId == null) == (externo == null)) {
            throw invalida("Indica quién ofrece la cancha: un club o un oferente externo, no ambos.");
        }
    }

    private static void validarHorario(DatosPrestamo prestamo) {
        if (prestamo.horaInicio() != null && prestamo.horaTermino() != null
                && !prestamo.horaTermino().isAfter(prestamo.horaInicio())) {
            throw invalida("La hora de término debe ser posterior a la hora de inicio.");
        }
    }

    /** Objetivo de autorización: el club del trámite o, para un oferente externo, la liga. */
    private static ObjetivoAutorizacion objetivo(SolicitudCanchaDto solicitud) {
        return solicitud.club() == null
                ? ObjetivoAutorizacion.deLiga()
                : ObjetivoAutorizacion.deClubes(solicitud.club().id());
    }

    private SolicitudCanchaDto buscar(UUID id) {
        return solicitudes.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    // ── Alcance de consulta (CREAR, APROBAR o GENERAR) ─────────────────────────────────────────

    /**
     * Une el alcance de los tres permisos del trámite: quien captura, quien dictamina y quien genera
     * la carta ven la bandeja (DEC-136, [PROPUESTA]). Sin ninguno, {@code 403 VAL-015}.
     */
    private AlcanceAutorizacion alcanceConsulta() {
        boolean todaLaLiga = false;
        boolean propio = false;
        boolean alguno = false;
        Set<UUID> clubes = new LinkedHashSet<>();
        for (String permiso : List.of(CREAR, APROBAR, GENERAR_OFICIO)) {
            AlcanceAutorizacion alcance = alcanceOpcional(permiso);
            if (alcance != null) {
                alguno = true;
                todaLaLiga |= alcance.todaLaLiga();
                propio |= alcance.propio();
                clubes.addAll(alcance.clubes());
            }
        }
        if (!alguno) {
            throw Autorizacion.sinPermiso();
        }
        return new AlcanceAutorizacion(todaLaLiga, clubes, propio);
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

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private static String observacionesObligatorias(String observaciones) {
        if (observaciones == null || observaciones.length() < MINIMO_OBSERVACIONES) {
            throw ReglaNegocioException.invalida("RN-021",
                    "Escribe observaciones de al menos " + MINIMO_OBSERVACIONES
                            + " caracteres que expliquen la decisión.");
        }
        return observaciones;
    }

    private static String observacionesOpcionales(String texto) {
        String valor = textoOpcional(texto);
        if (valor != null && valor.length() > MAXIMO_OBSERVACIONES) {
            throw invalida("Las observaciones admiten hasta " + MAXIMO_OBSERVACIONES + " caracteres.");
        }
        return valor;
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private void auditar(String accion, UUID id, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, id.toString(), motivo, detalle));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Solicitud de cancha " + id);
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}

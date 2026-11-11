package mx.tecnm.morelia.siglim.arbitraje;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroRepository.ResumenArbitro;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de árbitros presentados por los clubes y validados por la Comisión de Arbitraje
 * (RF-021, RF-112, RF-113, UI-007).
 *
 * <ul>
 *   <li><b>Presentación (RF-112, DEC-17).</b> La Directiva de club registra a una persona ya
 *       existente con su {@code personaId} y su club; el árbitro nace {@code PROPUESTO}, ligado al
 *       club y al perfil de la sesión. La identidad es única (RF-010, DEC-10): si la persona ya está
 *       en el catálogo, responde {@code 409 DUPLICADO} con el club y el estado actuales.</li>
 *   <li><b>Ámbito (ADR-0007).</b> El alta exige {@code arbitraje.arbitro:PROPONER} sobre el club; el
 *       listado usa {@code arbitraje.arbitro:CONSULTAR} y filtra en el SQL y en el {@code COUNT} por
 *       los clubes del alcance, así que la Directiva solo ve los suyos y la Comisión (ámbito
 *       {@code LIGA}) ve todos. Un club de otra liga responde {@code 404} (VAL-024).</li>
 *   <li><b>Validación (RF-113).</b> Solo desde {@code PROPUESTO} a {@code VALIDADO} o
 *       {@code RECHAZADO}; repetir el dictamen responde {@code 409 RF-113}. El rechazo exige
 *       observaciones de 10 a 500 caracteres, que se guardan y se fijan como motivo (RN-021). Se
 *       registran el perfil y la fecha de validación. Solo los {@code VALIDADO} pueden designarse:
 *       lo garantiza el trigger de {@code designaciones} ({@code RF-113}).</li>
 *   <li><b>Auditoría (RF-093).</b> Alta, validación y rechazo se registran en {@code M-05} sobre
 *       {@code arbitros}, con club, persona, estado y observaciones; nunca con nombres ni CURP.</li>
 * </ul>
 *
 * <p>Refs: H-11.1, RF-021, RF-112, RF-113, UI-007, RF-093, RF-010, DEC-10, DEC-17, ADR-0007
 */
@Service
public class ArbitroService {

    private static final String CONSULTAR = "arbitraje.arbitro:CONSULTAR";
    private static final String PROPONER = "arbitraje.arbitro:PROPONER";
    private static final String VALIDAR = "arbitraje.arbitro:VALIDAR";
    private static final String MODULO = "M-05";
    private static final String ENTIDAD = "arbitros";

    /** Observaciones del dictamen: de 10 a 500 caracteres, como las demás transiciones (RN-021). */
    private static final int MINIMO_OBSERVACIONES = 10;
    private static final int MAXIMO_OBSERVACIONES = 500;

    private final ArbitroRepository arbitros;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final Clock reloj;
    private final UUID ligaId;

    ArbitroService(ArbitroRepository arbitros, Autorizacion autorizacion, AuditoriaService auditoria,
            MotivoOperacion motivoOperacion, Clock reloj, SiglimPropiedades propiedades) {
        this.arbitros = arbitros;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /** Listado por estado y club, filtrado por ámbito en el SQL y en el total (RF-021). */
    @Transactional(readOnly = true)
    public Pagina<ArbitroDto> listar(FiltroArbitros filtro) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        filtro.validar();
        return Pagina.de(arbitros.listar(ligaId, filtro, alcance), filtro.pagina(), filtro.tamano(),
                arbitros.contar(ligaId, filtro, alcance));
    }

    /**
     * Presenta a una persona como árbitro del club y la deja {@code PROPUESTO} (criterio 1 de H-11,
     * RF-112, DEC-17).
     *
     * <p>Orden: club de la liga ({@code 404}) → permiso sobre ese club ({@code 403}/{@code 404}) →
     * persona ya registrada ({@code 409 DUPLICADO}) → alta. La persona inexistente la rechaza la
     * llave foránea como {@code 422 REFERENCIA_INVALIDA}.
     *
     * @throws ReglaNegocioException 409 si la persona ya está en el catálogo; 422 si la persona no
     *                               existe; 400 si falta un dato obligatorio
     */
    @Transactional
    public ArbitroDto crear(CrearArbitroRequest peticion) {
        if (peticion == null || peticion.personaId() == null) {
            throw invalida("Indica la persona que se presenta como árbitro.");
        }
        if (peticion.clubId() == null) {
            throw invalida("Indica el club que presenta al árbitro.");
        }

        if (!arbitros.existeClub(ligaId, peticion.clubId())) {
            throw new RecursoNoEncontradoException("Club " + peticion.clubId());
        }
        autorizacion.exigir(PROPONER, ObjetivoAutorizacion.deClubes(peticion.clubId()));

        ResumenArbitro existente = arbitros.buscarPorPersona(peticion.personaId()).orElse(null);
        if (existente != null) {
            throw duplicado(existente);
        }

        UUID perfil = UsuarioActual.idRequerido();
        UUID id = insertarSinDuplicado(peticion.clubId(), peticion.personaId(), perfil);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clubId", peticion.clubId().toString());
        detalle.put("personaId", peticion.personaId().toString());
        detalle.put("estado", EstadoArbitro.PROPUESTO.name());
        auditar("PROPONER_ARBITRO", id, null, detalle);
        return buscar(id);
    }

    /**
     * Aplica el dictamen de la Comisión sobre un árbitro {@code PROPUESTO} (criterio 2 de H-11,
     * RF-113). La validación puede llevar una nota; el rechazo exige observaciones de 10 a 500
     * caracteres, que se guardan como motivo.
     *
     * @throws ReglaNegocioException 409 {@code RF-113} si ya no está {@code PROPUESTO}; 400
     *                               {@code RF-113} si rechaza sin observaciones suficientes; 400
     *                               {@code VALIDACION} si las observaciones pasan de 500 caracteres
     */
    @Transactional
    public ArbitroDto validar(UUID id, ValidarArbitroRequest peticion) {
        ArbitroDto actual = buscarConCandado(id);
        autorizacion.exigir(VALIDAR, ObjetivoAutorizacion.deClubes(actual.club().id()));
        if (actual.estado() != EstadoArbitro.PROPUESTO) {
            throw ReglaNegocioException.conflicto("RF-113",
                    "Solo se valida un árbitro en estado PROPUESTO; este ya está "
                            + actual.estado().texto() + ".");
        }
        DecisionArbitro decision = peticion == null ? null : peticion.decision();
        if (decision == null) {
            throw invalida("Indica si el árbitro se valida o se rechaza.");
        }

        String observaciones = textoOpcional(peticion.observaciones());
        if (decision == DecisionArbitro.RECHAZADO) {
            observaciones = observacionesValidas(observaciones);
            motivoOperacion.fijar(observaciones);
        }

        UUID perfil = UsuarioActual.idRequerido();
        OffsetDateTime validadoEn = OffsetDateTime.now(reloj);
        arbitros.resolver(id, decision.estado(), perfil, validadoEn, observaciones);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clubId", actual.club().id().toString());
        detalle.put("personaId", actual.persona().id().toString());
        detalle.put("estadoAnterior", EstadoArbitro.PROPUESTO.name());
        detalle.put("estado", decision.estado().name());
        auditar(decision == DecisionArbitro.VALIDADO ? "VALIDAR_ARBITRO" : "RECHAZAR_ARBITRO", id,
                decision == DecisionArbitro.RECHAZADO ? observaciones : null, detalle);
        return buscar(id);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private ArbitroDto buscar(UUID id) {
        return arbitros.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private ArbitroDto buscarConCandado(UUID id) {
        return arbitros.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Árbitro " + id);
    }

    private UUID insertarSinDuplicado(UUID clubId, UUID personaId, UUID perfil) {
        try {
            return arbitros.insertar(clubId, personaId, perfil);
        } catch (DuplicateKeyException carrera) {
            ResumenArbitro existente = arbitros.buscarPorPersona(personaId).orElse(null);
            if (existente != null) {
                throw duplicado(existente);
            }
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "No se pudo registrar al árbitro. Recarga la página e intenta de nuevo.");
        }
    }

    private static ReglaNegocioException duplicado(ResumenArbitro existente) {
        return ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                        "La persona ya está registrada como árbitro del club «"
                                + existente.clubNombre() + "» en estado " + existente.estado().texto() + ".")
                .con("arbitroId", existente.id().toString())
                .con("clubId", existente.clubId().toString())
                .con("estado", existente.estado().name());
    }

    private static String observacionesValidas(String observaciones) {
        String texto = observaciones == null ? "" : observaciones.strip();
        if (texto.length() < MINIMO_OBSERVACIONES) {
            throw ReglaNegocioException.invalida("RF-113",
                    "Escribe observaciones de al menos " + MINIMO_OBSERVACIONES
                            + " caracteres para rechazar al árbitro.");
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
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, entidadId.toString(), motivo,
                detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}

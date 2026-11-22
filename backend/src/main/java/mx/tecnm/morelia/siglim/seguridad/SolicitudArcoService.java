package mx.tecnm.morelia.siglim.seguridad;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro y seguimiento de las solicitudes de acceso, rectificación, cancelación y oposición
 * (RF-098, DEC-40, DEC-67).
 *
 * <ul>
 *   <li><b>Permiso.</b> {@code seguridad.arco:GESTIONAR} (matriz inicial: Administrador general con
 *       ámbito {@code LIGA}). Registrar se autoriza sobre la persona titular, así que un ámbito
 *       {@code PROPIO} configurado alcanzaría al titular o a su tutor. Cambiar el estado exige ámbito
 *       {@code LIGA}, igual que la política RLS {@code arco_editar}.</li>
 *   <li><b>Listado.</b> Con {@code alcance} en el {@code WHERE} y en el {@code COUNT}: toda la liga o,
 *       con ámbito {@code PROPIO}, las solicitudes de las que la sesión es titular. Un ámbito
 *       {@code CLUB} no ve filas: la solicitud no pertenece a un club <b>[PROPUESTA]</b>.</li>
 *   <li><b>Seguimiento [PROPUESTA].</b> Transiciones de {@link EstadoSolicitudArco}. Al cerrar
 *       ({@code ATENDIDA} o {@code RECHAZADA}) la respuesta al titular es obligatoria y se guardan el
 *       perfil y la fecha de atención.</li>
 *   <li><b>Cancelación (DEC-67).</b> Una solicitud de cancelación se atiende como bloqueo, no como
 *       borrado físico: este servicio solo registra su seguimiento y nunca borra datos.</li>
 *   <li><b>Auditoría (RF-093).</b> {@code REGISTRAR_SOLICITUD_ARCO} y
 *       {@code CAMBIAR_ESTADO_SOLICITUD_ARCO} en M-01, con el tipo y los estados; sin la descripción
 *       ni la respuesta, que pueden traer datos personales.</li>
 * </ul>
 *
 * <p>El plazo legal de respuesta no se calcula: el ERS no lo fija y depende de PEN-039.
 *
 * <p>Refs: B-01, RF-098, RF-093, VAL-015, VAL-024, DEC-40, DEC-67, ADR-0007
 */
@Service
public class SolicitudArcoService {

    static final String GESTIONAR = "seguridad.arco:GESTIONAR";
    private static final String MODULO = "M-01";
    private static final String ENTIDAD = "solicitudes_arco";

    private final SolicitudArcoRepository solicitudes;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    SolicitudArcoService(SolicitudArcoRepository solicitudes, Autorizacion autorizacion, AuditoriaService auditoria,
            SiglimPropiedades propiedades) {
        this.solicitudes = solicitudes;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /** Solicitudes visibles para la sesión, las más recientes primero. */
    @Transactional(readOnly = true)
    public Pagina<SolicitudArcoDto> listar(FiltroSolicitudesArco filtro) {
        AlcanceAutorizacion alcance = autorizacion.alcance(GESTIONAR);
        filtro.validar();
        if (!alcance.todaLaLiga() && !alcance.propio()) {
            return Pagina.de(List.of(), filtro.pagina(), filtro.tamano(), 0);
        }
        return Pagina.de(solicitudes.listar(ligaId, filtro, alcance), filtro.pagina(), filtro.tamano(),
                solicitudes.contar(ligaId, filtro, alcance));
    }

    /**
     * Registra una solicitud recibida.
     *
     * <p>Orden: titular indicado ({@code 400 VALIDACION}) → permiso sobre el titular ({@code 403
     * VAL-015} o {@code 404}) → tipo y descripción ({@code 400 VALIDACION}) → la persona existe
     * ({@code 422 REFERENCIA_INVALIDA}).
     */
    @Transactional
    public SolicitudArcoDto registrar(RegistrarSolicitudArcoRequest peticion) {
        if (peticion == null || peticion.personaId() == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Indica la persona titular de los datos.");
        }
        autorizacion.exigir(GESTIONAR, ObjetivoAutorizacion.dePersonas(peticion.personaId()));
        TipoSolicitudArco tipo = tipoValido(peticion.tipo());
        String descripcion = textoRequerido(peticion.descripcion(),
                "Describe lo que pide el titular de los datos.");
        if (!solicitudes.existePersona(peticion.personaId())) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                    "La persona indicada no existe. Búscala por su CURP y vuelve a intentarlo.");
        }

        UUID id = solicitudes.insertar(ligaId, peticion.personaId(), tipo, descripcion);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tipo", tipo.name());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "REGISTRAR_SOLICITUD_ARCO", ENTIDAD,
                id.toString(), null, detalle));
        return solicitudes.buscar(id).orElseThrow();
    }

    /**
     * Cambia el estado del seguimiento.
     *
     * <p>Orden: permiso en la liga ({@code 403 VAL-015} o {@code 404}) → datos ({@code 400
     * VALIDACION}) → la solicitud existe ({@code 404}) → la transición es válida ({@code 422
     * RF-098}).
     */
    @Transactional
    public SolicitudArcoDto actualizar(UUID id, ActualizarSolicitudArcoRequest peticion) {
        autorizacion.exigir(GESTIONAR, ObjetivoAutorizacion.deLiga());
        if (peticion == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, "Indica el nuevo estado.");
        }
        EstadoSolicitudArco nuevo = estadoValido(peticion.estado());
        String respuesta = null;
        if (nuevo.esFinal()) {
            respuesta = textoRequerido(peticion.respuesta(),
                    "Escribe la respuesta que se entrega al titular para cerrar la solicitud.");
        } else if (peticion.respuesta() != null && !peticion.respuesta().isBlank()) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "La respuesta al titular se registra al cerrar la solicitud (ATENDIDA o RECHAZADA).");
        }

        EstadoSolicitudArco actual = solicitudes.bloquearEstado(ligaId, id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud ARCO " + id));
        if (!actual.siguientes().contains(nuevo)) {
            throw ReglaNegocioException.noProcesable("RF-098", mensajeTransicion(actual, nuevo));
        }

        solicitudes.actualizarEstado(id, nuevo, respuesta, UsuarioActual.idRequerido());
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", actual.name());
        detalle.put("estadoNuevo", nuevo.name());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "CAMBIAR_ESTADO_SOLICITUD_ARCO", ENTIDAD,
                id.toString(), null, detalle));
        return solicitudes.buscar(id).orElseThrow();
    }

    private static String mensajeTransicion(EstadoSolicitudArco actual, EstadoSolicitudArco nuevo) {
        if (actual.esFinal()) {
            return "La solicitud ya está " + actual.name() + " y no admite más cambios de estado.";
        }
        if (actual == nuevo) {
            return "La solicitud ya está " + actual.name() + ".";
        }
        return "Una solicitud " + actual.name() + " no puede pasar a " + nuevo.name() + ".";
    }

    private static TipoSolicitudArco tipoValido(String tipo) {
        try {
            return TipoSolicitudArco.valueOf(textoRequerido(tipo, mensajeTipo()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensajeTipo());
        }
    }

    private static String mensajeTipo() {
        return "El tipo de solicitud debe ser ACCESO, RECTIFICACION, CANCELACION u OPOSICION.";
    }

    private static EstadoSolicitudArco estadoValido(String estado) {
        String mensaje = "El nuevo estado debe ser EN_ATENCION, ATENDIDA o RECHAZADA.";
        EstadoSolicitudArco valor;
        try {
            valor = EstadoSolicitudArco.valueOf(textoRequerido(estado, mensaje).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
        }
        if (valor == EstadoSolicitudArco.RECIBIDA) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
        }
        return valor;
    }

    private static String textoRequerido(String texto, String mensaje) {
        if (texto == null || texto.isBlank()) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
        }
        return texto.strip();
    }
}

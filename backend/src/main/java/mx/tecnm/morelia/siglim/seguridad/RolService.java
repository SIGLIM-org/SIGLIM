package mx.tecnm.morelia.siglim.seguridad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.RolRepository.FilaParIncompatible;
import mx.tecnm.morelia.siglim.seguridad.RolRepository.FilaPermiso;
import mx.tecnm.morelia.siglim.seguridad.RolRepository.FilaRol;
import mx.tecnm.morelia.siglim.seguridad.SolicitudCambioPermisosRepository.FilaSolicitud;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de roles, catálogo de permisos y edición de la matriz de permisos con control dual
 * (RF-094, RF-095, RF-096, VAL-022, VAL-023).
 *
 * <ul>
 *   <li><b>Lectura.</b> {@code GET /roles}, {@code GET /roles/{id}} y {@code GET /permisos} exigen
 *       {@code seguridad.rol:CONSULTAR} con ámbito de liga; los roles no pertenecen a un club.</li>
 *   <li><b>Edición completa.</b> {@code PATCH /roles/{id}/permisos} recibe el conjunto final de
 *       códigos y calcula la diferencia contra la base, para que la matriz no dependa del orden de
 *       las casillas ni de escrituras parciales.</li>
 *   <li><b>Rol propio (VAL-022).</b> El Administrador no modifica los permisos de un rol que tiene
 *       asignado, aunque el cambio no altere nada. Se compara la asignación activa, nunca el nombre
 *       del rol (DEC-04, ADR-0007).</li>
 *   <li><b>Control dual (RF-096, VAL-023).</b> Si el conjunto final crea un par incompatible que el
 *       rol no tenía, el cambio <b>no se aplica</b>: se guarda como solicitud {@code SOLICITADO} y
 *       se responde {@code 202} hasta que el Presidente la apruebe. Un par que ya estaba aprobado
 *       no vuelve a bloquear la edición.</li>
 *   <li><b>Dictamen (RF-096).</b> {@code GET /solicitudes-cambio-permisos} lista los trámites y
 *       {@code POST /solicitudes-cambio-permisos/{id}/resolucion} los aprueba o rechaza con motivo.
 *       Al aprobar se aplica el cambio guardado; quien solicitó no puede dictaminar, aunque su
 *       cuenta tenga los dos permisos (DEC-04).</li>
 *   <li><b>Auditoría (RF-093).</b> Aplicar un cambio se registra como {@code CAMBIAR_PERMISOS_ROL},
 *       dejar uno pendiente como {@code SOLICITAR_CAMBIO_PERMISOS} y resolverlo como
 *       {@code APROBAR_CAMBIO_PERMISOS} o {@code RECHAZAR_CAMBIO_PERMISOS}, con el rol, los
 *       permisos agregados y quitados y el motivo. Nunca se copian datos personales.</li>
 * </ul>
 *
 * <p>Refs: H-20.1, RF-094, RF-095, RF-096, RF-093, VAL-022, VAL-023, DEC-04, ADR-0007, ADR-0011
 */
@Service
public class RolService {

    private static final String CONSULTAR = "seguridad.rol:CONSULTAR";
    private static final String EDITAR = "seguridad.rol:EDITAR";
    private static final String APROBAR_INCOMPATIBLE = "seguridad.permisos:APROBAR_INCOMPATIBLE";
    private static final String MODULO = "M-01";
    private static final String ENTIDAD_ROL = "roles";
    private static final String ENTIDAD_SOLICITUD = "solicitudes_cambio_permisos";
    private static final int LONGITUD_MINIMA_MOTIVO = 10;
    private static final int TAMANO_MAXIMO = 100;
    private static final String MENSAJE_YA_RESUELTA = "La solicitud ya fue aprobada o rechazada:"
            + " un dictamen no se reabre.";
    private static final String MENSAJE_QUIEN_SOLICITA = "Quien solicitó el cambio de permisos no"
            + " puede aprobarlo: el dictamen corresponde a otra cuenta (DEC-04).";
    private static final String MENSAJE_ROL_PROPIO = "No puedes cambiar los permisos de tu propio"
            + " rol (VAL-022). Otra cuenta con el permiso debe hacerlo.";

    private final RolRepository roles;
    private final SolicitudCambioPermisosRepository solicitudes;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    RolService(RolRepository roles, SolicitudCambioPermisosRepository solicitudes,
            Autorizacion autorizacion, AuditoriaService auditoria, SiglimPropiedades propiedades) {
        this.roles = roles;
        this.solicitudes = solicitudes;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /** Todos los roles de la liga, con su ámbito predeterminado y si se pueden asignar. */
    @Transactional(readOnly = true)
    public List<RolDto> listar() {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        return roles.listar(ligaId).stream().map(RolService::aDto).toList();
    }

    /** Detalle del rol con los permisos que tiene asignados (RF-095, F-13). */
    @Transactional(readOnly = true)
    public RolDetalleDto consultar(UUID id) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        return detalle(buscarRol(id));
    }

    /** Catálogo completo de permisos y pares incompatibles para la matriz de F-13 (RF-095, RF-096). */
    @Transactional(readOnly = true)
    public CatalogoPermisosDto catalogoPermisos() {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        return new CatalogoPermisosDto(
                roles.listarPermisos().stream().map(RolService::aPermiso).toList(),
                roles.listarParesIncompatibles().stream().map(RolService::aPar).toList());
    }

    /**
     * Solicitudes de cambio de permisos de la liga, de la más reciente a la más antigua (RF-096).
     *
     * <p>Los roles y sus solicitudes son datos de la liga: se exige
     * {@code seguridad.permisos:APROBAR_INCOMPATIBLE} con ámbito de liga. Un ámbito de club no las
     * alcanza y responde como un recurso fuera de ámbito (VAL-024), sin que el total delate filas.
     *
     * @param estado filtro opcional; {@code SOLICITADO} es el que usa la pestaña de dictamen
     * @param rolId  filtro opcional por rol
     * @param pagina página, desde 0
     * @param tamano filas por página, de 1 a 100
     */
    @Transactional(readOnly = true)
    public Pagina<SolicitudCambioPermisosDto> listarSolicitudes(EstadoSolicitudPermisos estado, UUID rolId,
            int pagina, int tamano) {
        AlcanceAutorizacion alcance = autorizacion.alcance(APROBAR_INCOMPATIBLE);
        if (!alcance.todaLaLiga()) {
            throw Autorizacion.fueraDeAmbito(APROBAR_INCOMPATIBLE);
        }
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw invalida("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        long desplazamiento = (long) pagina * tamano;
        List<SolicitudCambioPermisosDto> contenido = solicitudes
                .listar(ligaId, estado, rolId, tamano, desplazamiento).stream()
                .map(SolicitudCambioPermisosDto::desde)
                .toList();
        return Pagina.de(contenido, pagina, tamano, solicitudes.contar(ligaId, estado, rolId));
    }

    /**
     * Aplica el dictamen del Presidente sobre un cambio pendiente (RF-096, VAL-023).
     *
     * <p>Al aprobar, el cambio guardado en la solicitud se aplica a la matriz del rol; al rechazar,
     * solo se descarta. En los dos casos la solicitud queda resuelta y la decisión se audita con su
     * motivo. La fila de la solicitud se bloquea para que dos dictámenes simultáneos no se pisen y
     * el cambio nunca se aplique dos veces.
     *
     * <p>Orden: permiso ({@code 403 VAL-015}) → solicitud ({@code 404}) → ya resuelta
     * ({@code 409 RF-096}) → quien la solicitó ({@code 403 RF-096}, DEC-04) → decisión
     * ({@code 400 VALIDACION}) → motivo ({@code 400 RN-021}).
     *
     * @throws ReglaNegocioException 409 {@code RF-096} si ya estaba resuelta; 403 {@code RF-096} si
     *                               la resuelve quien la solicitó; 400 {@code RN-021} sin motivo
     */
    @Transactional
    public SolicitudCambioPermisosDto resolverSolicitud(UUID id,
            ResolverSolicitudCambioPermisosRequest peticion) {
        autorizacion.exigir(APROBAR_INCOMPATIBLE, ObjetivoAutorizacion.deLiga());
        FilaSolicitud solicitud = solicitudes.buscarParaActualizar(ligaId, id)
                .orElseThrow(() -> noExisteSolicitud(id));
        if (solicitud.estado().resuelta()) {
            throw ReglaNegocioException.conflicto("RF-096", MENSAJE_YA_RESUELTA);
        }

        UUID perfil = UsuarioActual.idRequerido();
        if (perfil.equals(solicitud.solicitadoPor())) {
            throw ReglaNegocioException.prohibida("RF-096", MENSAJE_QUIEN_SOLICITA);
        }

        DecisionSolicitudPermisos decision = peticion == null ? null : peticion.decision();
        if (decision == null) {
            throw invalida("Indica si el cambio de permisos se aprueba o se rechaza.");
        }
        String motivo = motivoValido(peticion.motivo());

        if (decision == DecisionSolicitudPermisos.APROBADO) {
            // La relación rol → permisos solo cambia al aprobar; se bloquea el rol para no chocar con
            // otra edición simultánea (RF-095).
            FilaRol rol = roles.buscarParaActualizar(ligaId, solicitud.rolId())
                    .orElseThrow(() -> noExiste(solicitud.rolId()));
            roles.aplicarCambios(rol.id(), solicitud.permisosAgregadosIds(),
                    solicitud.permisosQuitadosIds());
        }
        solicitudes.resolver(id, decision.aEstado(), perfil, motivo);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("rol", solicitud.rolCodigo());
        detalle.put("permisosAgregados", solicitud.permisosAgregados());
        detalle.put("permisosQuitados", solicitud.permisosQuitados());
        auditar(decision == DecisionSolicitudPermisos.APROBADO
                ? "APROBAR_CAMBIO_PERMISOS" : "RECHAZAR_CAMBIO_PERMISOS", ENTIDAD_SOLICITUD,
                id.toString(), motivo, detalle);

        return solicitudes.buscar(ligaId, id)
                .map(SolicitudCambioPermisosDto::desde)
                .orElseThrow(() -> new IllegalStateException(
                        "La solicitud de cambio de permisos recién resuelta no se encontró"));
    }

    /**
     * Reemplaza la matriz del rol con el conjunto de permisos indicado (RF-095).
     *
     * <p>Orden: permiso ({@code 403 VAL-015}) → rol ({@code 404}) → rol propio
     * ({@code 403 VAL-022}) → motivo ({@code 400 RN-021}) → permisos del catálogo
     * ({@code 400 VALIDACION}) → control dual. Con un par incompatible nuevo se responde
     * {@code 202} con la solicitud y el rol no se modifica (VAL-023).
     *
     * @throws ReglaNegocioException 403 {@code VAL-022} si el rol es del usuario actual; 400
     *                               {@code RN-021} sin motivo; 400 {@code VALIDACION} si algún
     *                               permiso no existe
     */
    @Transactional
    public ResultadoCambioPermisos actualizarPermisos(UUID rolId, ActualizarPermisosRolRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        FilaRol rol = roles.buscarParaActualizar(ligaId, rolId)
                .orElseThrow(() -> noExiste(rolId));
        rechazarRolPropio(rol.id());

        String motivo = motivoValido(peticion == null ? null : peticion.motivo());
        List<FilaPermiso> propuestos = permisosDelCatalogo(peticion == null ? null : peticion.permisos());
        List<FilaPermiso> actuales = roles.listarPermisosDeRol(rol.id());

        Set<Long> idsActuales = actuales.stream().map(FilaPermiso::id).collect(Collectors.toSet());
        Set<Long> idsPropuestos = propuestos.stream().map(FilaPermiso::id).collect(Collectors.toSet());
        List<Long> agregados = idsPropuestos.stream().filter(id -> !idsActuales.contains(id)).toList();
        List<Long> quitados = idsActuales.stream().filter(id -> !idsPropuestos.contains(id)).toList();

        if (agregados.isEmpty() && quitados.isEmpty()) {
            // Sin cambio real no hay evento que auditar; se responde el detalle actual.
            return ResultadoCambioPermisos.aplicado(detalle(rol));
        }

        Map<Long, String> codigoPorId = codigosPorId(actuales, propuestos);
        List<String> codigosAgregados = codigosOrdenados(agregados, codigoPorId);
        List<String> codigosQuitados = codigosOrdenados(quitados, codigoPorId);
        List<FilaParIncompatible> paresNuevos = paresNuevos(idsActuales, idsPropuestos);

        if (!paresNuevos.isEmpty()) {
            UUID solicitudId = solicitudes.insertar(rol.id(), agregados, quitados, motivo,
                    UsuarioActual.idRequerido());
            auditar("SOLICITAR_CAMBIO_PERMISOS", ENTIDAD_SOLICITUD, solicitudId.toString(), motivo,
                    detalleCambio(rol, codigosAgregados, codigosQuitados, paresNuevos));
            FilaSolicitud creada = solicitudes.buscar(ligaId, solicitudId)
                    .orElseThrow(() -> new IllegalStateException(
                            "La solicitud de cambio de permisos recién creada no se encontró"));
            return ResultadoCambioPermisos.pendiente(SolicitudCambioPermisosDto.desde(creada));
        }

        roles.aplicarCambios(rol.id(), agregados, quitados);
        auditar("CAMBIAR_PERMISOS_ROL", ENTIDAD_ROL, rol.id().toString(), motivo,
                detalleCambio(rol, codigosAgregados, codigosQuitados, List.of()));
        return ResultadoCambioPermisos.aplicado(detalle(rol));
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private FilaRol buscarRol(UUID id) {
        return roles.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Rol " + id);
    }

    private static RecursoNoEncontradoException noExisteSolicitud(UUID id) {
        return new RecursoNoEncontradoException("Solicitud de cambio de permisos " + id);
    }

    private RolDetalleDto detalle(FilaRol rol) {
        return new RolDetalleDto(rol.id(), rol.codigo(), rol.nombre(), rol.descripcion(),
                Ambito.valueOf(rol.ambitoPredeterminado()), rol.asignable(),
                roles.listarPermisosDeRol(rol.id()).stream().map(RolService::aPermiso).toList());
    }

    /**
     * Separación de funciones (VAL-022, DEC-04): el usuario no modifica los permisos de un rol que
     * tiene asignado. La misma condición la repite el trigger de la base como última garantía.
     */
    private void rechazarRolPropio(UUID rolId) {
        if (roles.tieneAsignacionActiva(UsuarioActual.idRequerido(), rolId)) {
            throw ReglaNegocioException.prohibida("VAL-022", MENSAJE_ROL_PROPIO);
        }
    }

    /** Códigos finales sin repetir; cualquiera que no exista en el catálogo es un 400. */
    private List<FilaPermiso> permisosDelCatalogo(List<String> codigos) {
        if (codigos == null) {
            throw invalida("Indica los permisos del rol.");
        }
        Set<String> solicitados = new LinkedHashSet<>();
        for (String codigo : codigos) {
            if (codigo == null || codigo.isBlank()) {
                throw invalida("El código de un permiso no puede estar vacío.");
            }
            solicitados.add(codigo.strip());
        }

        List<FilaPermiso> encontrados = roles.listarPermisosPorCodigos(solicitados);
        if (encontrados.size() != solicitados.size()) {
            Set<String> existentes = encontrados.stream().map(FilaPermiso::codigo)
                    .collect(Collectors.toSet());
            String faltante = solicitados.stream().filter(codigo -> !existentes.contains(codigo))
                    .findFirst().orElseThrow();
            throw invalida("El permiso «" + faltante + "» no existe en el catálogo.");
        }
        return encontrados;
    }

    /**
     * Pares incompatibles que el cambio <b>introduce</b>: ambos permisos están en el conjunto final
     * y no estaban juntos en el actual. Un par ya aprobado antes no vuelve a bloquear la edición.
     */
    private List<FilaParIncompatible> paresNuevos(Set<Long> idsActuales, Set<Long> idsPropuestos) {
        List<FilaPermiso> catalogo = roles.listarPermisos();
        Map<String, Long> idPorCodigo = catalogo.stream()
                .collect(Collectors.toMap(FilaPermiso::codigo, FilaPermiso::id));
        List<FilaParIncompatible> nuevos = new ArrayList<>();
        for (FilaParIncompatible par : roles.listarParesIncompatibles()) {
            Long a = idPorCodigo.get(par.permisoA());
            Long b = idPorCodigo.get(par.permisoB());
            if (a == null || b == null) {
                continue;
            }
            boolean enPropuestos = idsPropuestos.contains(a) && idsPropuestos.contains(b);
            boolean yaEstaba = idsActuales.contains(a) && idsActuales.contains(b);
            if (enPropuestos && !yaEstaba) {
                nuevos.add(par);
            }
        }
        return nuevos;
    }

    private static Map<Long, String> codigosPorId(Collection<FilaPermiso>... grupos) {
        Map<Long, String> codigos = new LinkedHashMap<>();
        for (Collection<FilaPermiso> grupo : grupos) {
            for (FilaPermiso permiso : grupo) {
                codigos.put(permiso.id(), permiso.codigo());
            }
        }
        return codigos;
    }

    private static List<String> codigosOrdenados(Collection<Long> ids, Map<Long, String> codigoPorId) {
        return ids.stream().map(codigoPorId::get).sorted().toList();
    }

    private Map<String, Object> detalleCambio(FilaRol rol, List<String> agregados,
            List<String> quitados, List<FilaParIncompatible> pares) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("rol", rol.codigo());
        detalle.put("permisosAgregados", agregados);
        detalle.put("permisosQuitados", quitados);
        if (!pares.isEmpty()) {
            detalle.put("paresIncompatibles", pares.stream()
                    .map(par -> Map.of("permisoA", par.permisoA(), "permisoB", par.permisoB()))
                    .toList());
        }
        return detalle;
    }

    private void auditar(String accion, String entidad, String entidadId, String motivo,
            Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, entidad, entidadId, motivo,
                detalle));
    }

    /** Motivo obligatorio de una acción auditada (RF-093); mínimo 10 caracteres [PROPUESTA]. */
    private static String motivoValido(String motivo) {
        String texto = motivo == null ? "" : motivo.strip();
        if (texto.length() < LONGITUD_MINIMA_MOTIVO) {
            throw ReglaNegocioException.invalida("RN-021",
                    "El motivo debe explicar el cambio (mínimo " + LONGITUD_MINIMA_MOTIVO
                            + " caracteres).");
        }
        return texto;
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }

    private static RolDto aDto(FilaRol fila) {
        return new RolDto(fila.id(), fila.codigo(), fila.nombre(), fila.descripcion(),
                Ambito.valueOf(fila.ambitoPredeterminado()), fila.asignable());
    }

    private static PermisoDto aPermiso(FilaPermiso fila) {
        return new PermisoDto(fila.codigo(), fila.modulo(), fila.descripcion());
    }

    private static ParIncompatibleDto aPar(FilaParIncompatible fila) {
        return new ParIncompatibleDto(fila.permisoA(), fila.permisoB(), fila.motivo());
    }
}

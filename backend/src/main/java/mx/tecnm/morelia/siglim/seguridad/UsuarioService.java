package mx.tecnm.morelia.siglim.seguridad;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import mx.tecnm.morelia.siglim.seguridad.RolRepository.FilaRol;
import mx.tecnm.morelia.siglim.seguridad.UsuarioDto.AsignacionDto;
import mx.tecnm.morelia.siglim.seguridad.UsuarioDto.ClubDto;
import mx.tecnm.morelia.siglim.seguridad.UsuarioDto.RolDto;
import mx.tecnm.morelia.siglim.seguridad.UsuarioRepository.FilaAsignacion;
import mx.tecnm.morelia.siglim.seguridad.UsuarioRepository.FilaPerfil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuentas internas de SIGLIM: invitación, consulta, activación, desactivación y asignación de roles
 * con ámbito (RF-094, RN-054).
 *
 * <ul>
 *   <li><b>Autorización.</b> Gestionar cuentas es una operación de toda la liga:
 *       {@code seguridad.usuario:CONSULTAR}, {@code :CREAR} y {@code :EDITAR} se exigen con
 *       {@link ObjetivoAutorizacion#deLiga()}. Un permiso con ámbito de club no alcanza el listado y
 *       recibe una página vacía, igual que la auditoría (ADR-0007).</li>
 *   <li><b>Invitación (ADR-0003 punto 5).</b> Primero se invita la cuenta en Supabase Auth, que
 *       envía el correo, y después se crea el perfil y sus asignaciones en la misma transacción. Si
 *       la base rechaza el alta, se elimina la cuenta de Auth como compensación de mejor esfuerzo
 *       para no dejarla huérfana.</li>
 *   <li><b>Ámbito del rol.</b> Si la petición no indica ámbito, se usa el predeterminado del rol
 *       ({@code roles.ambito_predeterminado}). Con {@code CLUB} el club es obligatorio y debe ser de
 *       la liga; con los demás ámbitos se omite (RN-054).</li>
 *   <li><b>Sin borrado (RN-005).</b> Las asignaciones se revocan con motivo y las cuentas se
 *       desactivan; nada se borra. Una cuenta inactiva se rechaza aunque su token siga vigente
 *       (ADR-0003 punto 6).</li>
 *   <li><b>Auditoría (RF-093).</b> Invitar, asignar, revocar, activar y desactivar se registran en
 *       la misma transacción. El detalle solo lleva identificadores, roles, ámbitos y estados, nunca
 *       el nombre ni el correo (RNF-012).</li>
 * </ul>
 *
 * <p>Refs: H-03.1, RF-094, RF-093, RN-005, RN-054, RNF-012, VAL-022, ADR-0003, ADR-0007
 */
@Service
public class UsuarioService {

    private static final String CONSULTAR = "seguridad.usuario:CONSULTAR";
    private static final String CREAR = "seguridad.usuario:CREAR";
    private static final String EDITAR = "seguridad.usuario:EDITAR";
    private static final String MODULO = "M-01";
    private static final String ENTIDAD = "perfiles";
    private static final String ENTIDAD_ASIGNACION = "asignaciones_rol";
    private static final int LONGITUD_MINIMA_MOTIVO = 10;

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final InvitadorAuth invitador;
    private final UUID ligaId;

    UsuarioService(UsuarioRepository usuarios, RolRepository roles, Autorizacion autorizacion,
            AuditoriaService auditoria, InvitadorAuth invitador, SiglimPropiedades propiedades) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.invitador = invitador;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Cuentas visibles, con sus asignaciones activas y los filtros de F-12 (RF-094).
     *
     * <p>Con el permiso pero sin ámbito de liga, la respuesta es una página vacía: las cuentas no
     * pertenecen a ningún club y no se revela su existencia (VAL-024).
     */
    @Transactional(readOnly = true)
    public Pagina<UsuarioDto> listar(FiltroUsuarios filtro) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        filtro.validar();
        if (!alcance.todaLaLiga()) {
            return Pagina.de(List.of(), filtro.pagina(), filtro.tamano(), 0);
        }

        List<FilaPerfil> perfiles = usuarios.listar(filtro, alcance);
        Map<UUID, List<FilaAsignacion>> porPerfil = usuarios
                .listarAsignaciones(perfiles.stream().map(FilaPerfil::id).toList(), true).stream()
                .collect(Collectors.groupingBy(FilaAsignacion::perfilId));

        return Pagina.de(
                perfiles.stream()
                        .map(perfil -> aDto(perfil, porPerfil.getOrDefault(perfil.id(), List.of())))
                        .toList(),
                filtro.pagina(), filtro.tamano(), usuarios.contar(filtro, alcance));
    }

    /** Detalle de una cuenta con todas sus asignaciones, incluidas las revocadas (RF-094). */
    @Transactional(readOnly = true)
    public UsuarioDto consultar(UUID id) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        return detalle(id);
    }

    /**
     * Invita una cuenta en Supabase Auth y crea su perfil con las asignaciones indicadas
     * (RF-094; ADR-0003 punto 5).
     *
     * @throws ReglaNegocioException 400 {@code VALIDACION} si los datos o las asignaciones no son
     *                               coherentes; 409 {@code DUPLICADO} si el correo ya tiene cuenta
     */
    @Transactional
    public UsuarioDto invitar(CrearUsuarioRequest peticion) {
        autorizacion.exigir(CREAR, ObjetivoAutorizacion.deLiga());
        String nombre = nombreValido(peticion.nombreMostrado());
        String correo = correoValido(peticion.correo());
        List<AsignacionValidada> asignaciones = validarAsignaciones(peticion.asignaciones());
        if (usuarios.existeCorreo(correo)) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "Ya existe una cuenta con el correo «" + correo + "».");
        }

        UUID usuarioAuthId = invitador.invitar(correo);
        try {
            UUID perfilId = usuarios.insertarPerfil(usuarioAuthId, nombre, correo);
            insertarAsignaciones(perfilId, asignaciones);
            auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "INVITAR_USUARIO", ENTIDAD,
                    perfilId.toString(), null, detalleAsignaciones(asignaciones)));
            return detalle(perfilId);
        } catch (RuntimeException error) {
            // La transacción se revierte; la cuenta de Auth ya existe y no debe quedar huérfana.
            invitador.eliminar(usuarioAuthId);
            throw error;
        }
    }

    /** Agrega una asignación de rol a una cuenta existente (RF-094, RN-054). */
    @Transactional
    public AsignacionDto asignar(UUID perfilId, AsignacionRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        buscarPerfil(perfilId);

        AsignacionValidada asignacion = validarAsignaciones(List.of(peticion)).getFirst();
        if (usuarios.existeAsignacionActiva(perfilId, asignacion.rolId(), asignacion.clubId())) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "La cuenta ya tiene activa esa asignación de rol.");
        }

        UUID id = usuarios.insertarAsignacion(perfilId, asignacion.rolId(), ligaId, asignacion.ambito(),
                asignacion.clubId(), UsuarioActual.idRequerido());
        auditar("ASIGNAR_ROL", id, asignacion, null);
        return buscarAsignacion(id);
    }

    /**
     * Revoca una asignación con motivo: no se borra, queda inactiva con su fecha (RF-094, RN-005).
     *
     * @throws ReglaNegocioException 400 {@code RN-021} sin motivo; 409 {@code RF-094} si ya estaba
     *                               revocada
     */
    @Transactional
    public AsignacionDto revocar(UUID asignacionId, RevocarAsignacionRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        FilaAsignacion asignacion = usuarios.buscarAsignacion(asignacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Asignación " + asignacionId));
        if (!asignacion.activa()) {
            throw ReglaNegocioException.conflicto("RF-094", "La asignación ya está revocada.");
        }
        String motivo = motivoValido(peticion.motivo());

        usuarios.revocarAsignacion(asignacionId);
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "REVOCAR_ASIGNACION", ENTIDAD_ASIGNACION,
                asignacionId.toString(), motivo, detalleAsignacion(asignacion.rolCodigo(),
                        asignacion.ambito(), asignacion.clubId())));
        return buscarAsignacion(asignacionId);
    }

    /**
     * Activa o desactiva una cuenta (RF-094; ADR-0003 punto 6). Desactivar exige motivo y queda
     * auditado; el detalle no guarda datos personales (RF-093, RNF-012).
     */
    @Transactional
    public UsuarioDto actualizar(UUID perfilId, ActualizarUsuarioRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        FilaPerfil perfil = buscarPerfil(perfilId);
        boolean activo = peticion.activo();

        if (activo == perfil.activo()) {
            // Ya está en ese estado: la operación es idempotente y no genera un evento de auditoría.
            return detalle(perfilId);
        }

        String motivo = null;
        if (!activo) {
            motivo = motivoValido(peticion.motivo());
        } else if (peticion.motivo() != null && !peticion.motivo().isBlank()) {
            motivo = peticion.motivo().strip();
        }

        usuarios.actualizarActivo(perfilId, activo);
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO,
                activo ? "ACTIVAR_CUENTA" : "DESACTIVAR_CUENTA", ENTIDAD, perfilId.toString(), motivo,
                Map.of("activo", activo)));
        return detalle(perfilId);
    }

    // ── Validación y conversión ────────────────────────────────────────────────────────────────

    private UsuarioDto detalle(UUID perfilId) {
        FilaPerfil perfil = buscarPerfil(perfilId);
        return aDto(perfil, usuarios.listarAsignaciones(perfilId, false));
    }

    private FilaPerfil buscarPerfil(UUID perfilId) {
        return usuarios.buscarPerfil(perfilId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + perfilId));
    }

    private AsignacionDto buscarAsignacion(UUID asignacionId) {
        return aAsignacion(usuarios.buscarAsignacion(asignacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Asignación " + asignacionId)));
    }

    private void insertarAsignaciones(UUID perfilId, List<AsignacionValidada> asignaciones) {
        UUID creadoPor = UsuarioActual.idRequerido();
        for (AsignacionValidada asignacion : asignaciones) {
            usuarios.insertarAsignacion(perfilId, asignacion.rolId(), ligaId, asignacion.ambito(),
                    asignacion.clubId(), creadoPor);
        }
    }

    /**
     * Valida cada asignación contra el catálogo de roles y la liga. Si no se indica ámbito se usa
     * el predeterminado del rol; con {@code CLUB}, el club es obligatorio y debe existir en la liga.
     */
    private List<AsignacionValidada> validarAsignaciones(List<AsignacionRequest> solicitadas) {
        if (solicitadas == null || solicitadas.isEmpty()) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Asigna al menos un rol a la cuenta.");
        }

        List<AsignacionValidada> validas = new ArrayList<>();
        Set<String> repetidas = new HashSet<>();
        for (AsignacionRequest solicitud : solicitadas) {
            if (solicitud == null || solicitud.rolId() == null) {
                throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                        "Cada asignación necesita un rol.");
            }

            FilaRol rol = roles.buscar(ligaId, solicitud.rolId())
                    .orElseThrow(() -> ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                            "El rol indicado no existe en la liga."));
            if (!rol.asignable()) {
                throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                        "El rol «" + rol.nombre() + "» no se puede asignar a una cuenta.");
            }
            Ambito ambito = solicitud.ambito() == null
                    ? Ambito.valueOf(rol.ambitoPredeterminado())
                    : solicitud.ambito();
            if (ambito == Ambito.CLUB) {
                if (solicitud.clubId() == null) {
                    throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                            "El club es obligatorio cuando el ámbito es CLUB.");
                }
                if (!usuarios.existeClub(ligaId, solicitud.clubId())) {
                    throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                            "El club indicado no existe en la liga.");
                }
            } else if (solicitud.clubId() != null) {
                throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                        "El club solo se indica cuando el ámbito es CLUB.");
            }

            String clave = solicitud.rolId() + "|" + ambito + "|"
                    + (solicitud.clubId() == null ? "" : solicitud.clubId());
            if (!repetidas.add(clave)) {
                throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                        "La asignación del rol «" + rol.nombre() + "» está repetida.");
            }
            validas.add(new AsignacionValidada(rol.id(), rol.codigo(), ambito.name(),
                    solicitud.clubId()));
        }
        return validas;
    }

    private static String nombreValido(String nombre) {
        String texto = nombre == null ? "" : nombre.strip();
        if (texto.isEmpty()) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, "El nombre es obligatorio.");
        }
        return texto;
    }

    private static String correoValido(String correo) {
        String texto = correo == null ? "" : correo.strip().toLowerCase(Locale.ROOT);
        if (texto.isEmpty() || !texto.contains("@")) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "El correo no tiene un formato válido.");
        }
        return texto;
    }

    /** Motivo obligatorio de una acción con historial (RN-021); mínimo 10 caracteres **[PROPUESTA]**. */
    private static String motivoValido(String motivo) {
        String texto = motivo == null ? "" : motivo.strip();
        if (texto.length() < LONGITUD_MINIMA_MOTIVO) {
            throw ReglaNegocioException.invalida("RN-021",
                    "El motivo debe explicar el cambio (mínimo " + LONGITUD_MINIMA_MOTIVO + " caracteres).");
        }
        return texto;
    }

    private void auditar(String accion, UUID asignacionId, AsignacionValidada asignacion, String motivo) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD_ASIGNACION,
                asignacionId.toString(), motivo,
                detalleAsignacion(asignacion.rolCodigo(), asignacion.ambito(), asignacion.clubId())));
    }

    /** Detalle de una asignación sin datos personales: solo rol, ámbito y club (RNF-012). */
    private static Map<String, Object> detalleAsignacion(String rol, String ambito, UUID clubId) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("rol", rol);
        detalle.put("ambito", ambito);
        if (clubId != null) {
            detalle.put("clubId", clubId.toString());
        }
        return detalle;
    }

    private static Map<String, Object> detalleAsignaciones(List<AsignacionValidada> asignaciones) {
        return Map.of("asignaciones", asignaciones.stream()
                .map(asignacion -> detalleAsignacion(asignacion.rolCodigo(), asignacion.ambito(),
                        asignacion.clubId()))
                .toList());
    }

    private static UsuarioDto aDto(FilaPerfil perfil, List<FilaAsignacion> asignaciones) {
        return new UsuarioDto(perfil.id(), perfil.nombreMostrado(), perfil.correo(), perfil.activo(),
                perfil.creadoEn(), asignaciones.stream().map(UsuarioService::aAsignacion).toList());
    }

    private static AsignacionDto aAsignacion(FilaAsignacion fila) {
        ClubDto club = fila.clubId() == null ? null : new ClubDto(fila.clubId(), fila.clubNombre());
        return new AsignacionDto(fila.id(), new RolDto(fila.rolId(), fila.rolCodigo(), fila.rolNombre()),
                fila.ambito(), club, fila.activa(), fila.creadoEn(), fila.revocadaEn());
    }

    /** Asignación ya validada contra el catálogo; lista para insertarse. */
    private record AsignacionValidada(UUID rolId, String rolCodigo, String ambito, UUID clubId) {
    }
}

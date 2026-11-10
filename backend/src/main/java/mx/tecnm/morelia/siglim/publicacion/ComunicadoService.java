package mx.tecnm.morelia.siglim.publicacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import mx.tecnm.morelia.siglim.publicacion.ComunicadoRepository.DatosComunicado;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flujo editorial de los comunicados: redacción, envío a revisión, aprobación y publicación
 * (RF-090, CU-027, UI-031).
 *
 * <ul>
 *   <li><b>Flujo (ERS §4.3.6).</b> {@code BORRADOR → REVISION → PUBLICADO}; el Presidente también
 *       puede devolverlo a {@code BORRADOR} con observaciones. El trigger
 *       {@code tg_comunicados_publicacion} valida la misma máquina de estados y guarda el historial
 *       en {@code historial_publicacion}. Publicar sin aprobación o devolver sin motivo se rechaza
 *       también en la base.</li>
 *   <li><b>Redacción.</b> Solo se edita en {@code BORRADOR}. Al enviar a revisión el comunicado deja
 *       de ser editable para que lo que dictamine el Presidente sea lo que se publique.</li>
 *   <li><b>Separación de funciones.</b> Quien redactó no puede aprobar su propio comunicado
 *       ({@code ck_comunicados_dual}); el servicio lo rechaza con {@code 403 RF-090} y un mensaje
 *       claro, y la restricción de la tabla es la última garantía.</li>
 *   <li><b>Permisos.</b> Redactar y enviar exigen {@code publicacion.comunicado:REDACTAR}; aprobar y
 *       devolver, {@code publicacion.comunicado:APROBAR}. La consulta acepta cualquiera de los dos:
 *       el catálogo inicial no tiene un permiso de consulta para M-11 y tanto la Secretaría como la
 *       Presidencia necesitan leer el comunicado. Sin asignación de liga no se consulta (ADR-0007);
 *       los comunicados pertenecen a la liga, no a un club.</li>
 *   <li><b>Auditoría (RF-093).</b> Cada alta, edición y transición deja un evento con los estados,
 *       sin copiar el cuerpo del comunicado.</li>
 * </ul>
 *
 * <p>Refs: B-27.1, RF-090, RF-093, CU-027, UI-031, ERS §4.3.6, ADR-0007, ADR-0011
 */
@Service
public class ComunicadoService {

    private static final String REDACTAR = "publicacion.comunicado:REDACTAR";
    private static final String APROBAR = "publicacion.comunicado:APROBAR";
    private static final String MODULO = "M-11";
    private static final String ENTIDAD = "comunicados";

    /** [PROPUESTA] Convenciones de API §5 y de las demás transiciones: mínimo de las observaciones. */
    private static final int MINIMO_OBSERVACIONES = 10;

    private static final int TAMANO_MAXIMO = 100;
    private static final int LONGITUD_MAXIMA_TITULO = 200;

    private final ComunicadoRepository comunicados;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final Clock reloj;
    private final UUID ligaId;

    ComunicadoService(ComunicadoRepository comunicados, Autorizacion autorizacion,
            AuditoriaService auditoria, MotivoOperacion motivoOperacion, Clock reloj,
            SiglimPropiedades propiedades) {
        this.comunicados = comunicados;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Listado de comunicados de la liga, del más reciente al más antiguo (RF-090).
     *
     * @param estado filtro opcional por estado de publicación
     * @param pagina página, desde 0
     * @param tamano filas por página, de 1 a 100
     */
    @Transactional(readOnly = true)
    public Pagina<ComunicadoDto> listar(EstadoPublicacion estado, int pagina, int tamano) {
        exigirConsulta();
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw invalida("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        long desplazamiento = (long) pagina * tamano;
        List<ComunicadoDto> contenido = comunicados.listar(ligaId, estado, tamano, desplazamiento)
                .stream()
                .map(ComunicadoService::aDto)
                .toList();
        return Pagina.de(contenido, pagina, tamano, comunicados.contar(ligaId, estado));
    }

    /** Detalle de un comunicado para la vista previa o el dictamen (RF-090). */
    @Transactional(readOnly = true)
    public ComunicadoDto consultar(UUID id) {
        DatosComunicado datos = buscar(id);
        exigirConsulta();
        return aDto(datos);
    }

    /** Registra un comunicado nuevo en {@code BORRADOR} con la autoría de la sesión (RF-090). */
    @Transactional
    public ComunicadoDto crear(CrearComunicadoRequest peticion) {
        autorizacion.exigir(REDACTAR, ObjetivoAutorizacion.deLiga());
        String titulo = tituloValido(peticion == null ? null : peticion.titulo());
        String cuerpo = cuerpoValido(peticion == null ? null : peticion.cuerpo());

        UUID id = comunicados.insertar(ligaId, titulo, cuerpo, UsuarioActual.idRequerido());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estado", EstadoPublicacion.BORRADOR.name());
        auditar("REDACTAR_COMUNICADO", id, null, detalle);
        return aDto(buscar(id));
    }

    /**
     * Edita título o cuerpo; lo ausente conserva su valor (RF-090).
     *
     * @throws ReglaNegocioException 409 {@code RF-090} si el comunicado ya no está en borrador
     */
    @Transactional
    public ComunicadoDto actualizar(UUID id, ActualizarComunicadoRequest peticion) {
        DatosComunicado actual = buscarConCandado(id);
        autorizacion.exigir(REDACTAR, ObjetivoAutorizacion.deLiga());
        if (actual.estadoPublicacion() != EstadoPublicacion.BORRADOR) {
            throw ReglaNegocioException.conflicto("RF-090",
                    "Solo se edita un comunicado en borrador; este está " + actual.estadoPublicacion().texto() + ".");
        }

        String titulo = peticion.titulo() == null ? actual.titulo() : tituloValido(peticion.titulo());
        String cuerpo = peticion.cuerpo() == null ? actual.cuerpo() : cuerpoValido(peticion.cuerpo());
        List<String> campos = camposCambiados(actual, titulo, cuerpo);
        if (campos.isEmpty()) {
            return aDto(actual);
        }
        comunicados.actualizar(id, titulo, cuerpo);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", campos);
        auditar("EDITAR_COMUNICADO", id, null, detalle);
        return aDto(buscar(id));
    }

    /**
     * Envía a revisión del Presidente un comunicado en borrador (RF-090).
     *
     * @throws ReglaNegocioException 409 {@code RF-090} si no está en borrador
     */
    @Transactional
    public ComunicadoDto enviarARevision(UUID id) {
        DatosComunicado actual = buscarConCandado(id);
        autorizacion.exigir(REDACTAR, ObjetivoAutorizacion.deLiga());
        if (actual.estadoPublicacion() != EstadoPublicacion.BORRADOR) {
            throw ReglaNegocioException.conflicto("RF-090",
                    "Solo un comunicado en borrador se envía a revisión; este está "
                            + actual.estadoPublicacion().texto() + ".");
        }

        comunicados.enviarARevision(id);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", EstadoPublicacion.BORRADOR.name());
        detalle.put("estado", EstadoPublicacion.REVISION.name());
        auditar("ENVIAR_COMUNICADO_A_REVISION", id, null, detalle);
        return aDto(buscar(id));
    }

    /**
     * Aplica el dictamen del Presidente sobre un comunicado en revisión (RF-090, CU-027).
     *
     * <p>{@link DecisionComunicado#PUBLICADO} fija {@code aprobado_por} y la fecha de aprobación; el
     * trigger fija {@code publicado_en} y registra el paso en {@code historial_publicacion}.
     * {@link DecisionComunicado#BORRADOR} exige observaciones, las guarda y fija el motivo para que
     * el trigger pueda registrar la devolución.
     *
     * @throws ReglaNegocioException 409 {@code RF-090} si no está en revisión; 403 {@code RF-090} si
     *                               quien aprueba es la misma persona que redactó; 400 {@code RF-090}
     *                               si devuelve sin observaciones suficientes
     */
    @Transactional
    public ComunicadoDto resolver(UUID id, ResolverComunicadoRequest peticion) {
        DatosComunicado actual = buscarConCandado(id);
        autorizacion.exigir(APROBAR, ObjetivoAutorizacion.deLiga());
        if (actual.estadoPublicacion() != EstadoPublicacion.REVISION) {
            throw ReglaNegocioException.conflicto("RF-090",
                    "Solo un comunicado en revisión se aprueba o se devuelve; este está "
                            + actual.estadoPublicacion().texto() + ".");
        }
        DecisionComunicado decision = peticion == null ? null : peticion.decision();
        if (decision == null) {
            throw invalida("Indica si el comunicado se publica o se devuelve.");
        }

        UUID perfil = UsuarioActual.idRequerido();
        if (perfil.equals(actual.redactadoPor())) {
            throw ReglaNegocioException.prohibida("RF-090",
                    "Quien redactó el comunicado no puede aprobarlo: la aprobación corresponde al Presidente.");
        }

        return decision == DecisionComunicado.PUBLICADO
                ? publicar(id, perfil, peticion.observaciones())
                : devolver(id, peticion.observaciones());
    }

    // ── Dictamen ────────────────────────────────────────────────────────────────────────────────

    private ComunicadoDto publicar(UUID id, UUID perfil, String observaciones) {
        String nota = textoOpcional(observaciones);
        comunicados.resolver(id, EstadoPublicacion.PUBLICADO, perfil, OffsetDateTime.now(reloj), nota);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", EstadoPublicacion.REVISION.name());
        detalle.put("estado", EstadoPublicacion.PUBLICADO.name());
        auditar("APROBAR_COMUNICADO", id, null, detalle);
        return aDto(buscar(id));
    }

    private ComunicadoDto devolver(UUID id, String observaciones) {
        String motivo = observacionesValidas(observaciones);
        motivoOperacion.fijar(motivo);
        comunicados.resolver(id, EstadoPublicacion.BORRADOR, null, null, motivo);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", EstadoPublicacion.REVISION.name());
        detalle.put("estado", EstadoPublicacion.BORRADOR.name());
        auditar("DEVOLVER_COMUNICADO", id, motivo, detalle);
        return aDto(buscar(id));
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private DatosComunicado buscar(UUID id) {
        return comunicados.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private DatosComunicado buscarConCandado(UUID id) {
        return comunicados.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Comunicado " + id);
    }

    /**
     * La consulta la necesitan la Secretaría (redacta) y la Presidencia (aprueba), pero el catálogo
     * inicial solo define {@code REDACTAR} y {@code APROBAR}. Se acepta cualquiera de los dos con
     * ámbito de liga; un ámbito de club no alcanza un comunicado de la liga (ADR-0007, VAL-024).
     */
    private void exigirConsulta() {
        boolean asignacionSinAlcance = false;
        for (String permiso : List.of(REDACTAR, APROBAR)) {
            try {
                if (autorizacion.alcance(permiso).todaLaLiga()) {
                    return;
                }
                asignacionSinAlcance = true;
            } catch (ReglaNegocioException sinPermiso) {
                // Este permiso no está asignado: se prueba el otro.
            }
        }
        if (asignacionSinAlcance) {
            throw Autorizacion.fueraDeAmbito(REDACTAR);
        }
        throw Autorizacion.sinPermiso();
    }

    private static String tituloValido(String titulo) {
        String texto = titulo == null ? "" : titulo.strip();
        if (texto.isEmpty()) {
            throw invalida("El título del comunicado es obligatorio.");
        }
        if (texto.length() > LONGITUD_MAXIMA_TITULO) {
            throw invalida("El título admite hasta " + LONGITUD_MAXIMA_TITULO + " caracteres.");
        }
        return texto;
    }

    private static String cuerpoValido(String cuerpo) {
        String texto = cuerpo == null ? "" : cuerpo.strip();
        if (texto.isEmpty()) {
            throw invalida("El cuerpo del comunicado es obligatorio.");
        }
        return texto;
    }

    private static String observacionesValidas(String observaciones) {
        String texto = observaciones == null ? "" : observaciones.strip();
        if (texto.length() < MINIMO_OBSERVACIONES) {
            throw ReglaNegocioException.invalida("RF-090",
                    "Escribe observaciones de al menos " + MINIMO_OBSERVACIONES
                            + " caracteres para devolver el comunicado.");
        }
        return texto;
    }

    private static String textoOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.strip();
    }

    private static List<String> camposCambiados(DatosComunicado actual, String titulo, String cuerpo) {
        List<String> campos = new ArrayList<>();
        if (!Objects.equals(titulo, actual.titulo())) {
            campos.add("titulo");
        }
        if (!Objects.equals(cuerpo, actual.cuerpo())) {
            campos.add("cuerpo");
        }
        return campos;
    }

    private static ComunicadoDto aDto(DatosComunicado datos) {
        return new ComunicadoDto(datos.id(), datos.titulo(), datos.cuerpo(), datos.estadoPublicacion(),
                datos.redactadoPor(), datos.aprobadoPor(), datos.aprobadoEn(), datos.publicadoEn(),
                datos.observaciones(), datos.creadoEn(), datos.actualizadoEn());
    }

    private void auditar(String accion, UUID entidadId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, entidadId.toString(), motivo,
                detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}

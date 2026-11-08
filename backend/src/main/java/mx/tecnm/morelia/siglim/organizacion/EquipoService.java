package mx.tecnm.morelia.siglim.organizacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import mx.tecnm.morelia.siglim.organizacion.EquipoRepository.GrupoTemporada;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Equipos de los clubes en los grupos de la temporada: alta, edición y baja.
 *
 * <ul>
 *   <li><b>Ámbito (ADR-0007).</b> Un equipo pertenece a su club: toda operación se autoriza con
 *       {@link ObjetivoAutorizacion#deClubes} de ese club, así que la directiva de otro club recibe
 *       404 igual que con un equipo inexistente (VAL-024). El listado filtra con el alcance del
 *       permiso y cuenta con la misma condición; un {@code clubId} ajeno da una página vacía.</li>
 *   <li><b>Club activo (RN-041).</b> Un club {@code CERRADO} no recibe equipos nuevos: {@code 409
 *       RN-041}.</li>
 *   <li><b>Temporada no cerrada (RF-003, RN-005).</b> Alta, edición y baja solo con la temporada del
 *       grupo en {@code CONFIGURACION} o {@code ACTIVA}; cerrada responde {@code 409 RF-003}.</li>
 *   <li><b>Un grupo por temporada (RN-007, [PROPUESTA]).</b> El equipo solo cambia de grupo con la
 *       temporada en {@code CONFIGURACION} ({@code 409 RN-007} si está activa) y hacia un grupo de la
 *       misma temporada ({@code 422 RN-007}). El club no se edita aquí: la venta es B-05.</li>
 *   <li><b>Baja con motivo (RF-009, RN-005, RN-021).</b> {@code ACTIVO → BAJA} con un motivo de al menos
 *       10 caracteres ([PROPUESTA], convenciones de API §5), fecha del día en la zona de negocio y
 *       auditoría. La fila nunca se borra; una baja repetida o un {@code PATCH} sobre un equipo dado
 *       de baja responde {@code 409 RF-009}. Los efectos sobre sus partidos (RN-056, RF-130, RF-132)
 *       son B y no se aplican aquí.</li>
 *   <li><b>Candado.</b> La edición y la baja leen el equipo con {@code FOR UPDATE} para no cruzarse. El
 *       alta lee el club con {@code FOR SHARE} para no colarse en un club que se está cerrando por
 *       cambio de nombre (B-04).</li>
 * </ul>
 *
 * <p>Refs: H-06.1, RF-003, RF-009, RN-005, RN-007, RN-021, RN-041, VAL-024, ADR-0007
 */
@Service
public class EquipoService {

    private static final String CONSULTAR = "organizacion.equipo:CONSULTAR";
    private static final String EDITAR = "organizacion.equipo:EDITAR";
    private static final String MODULO = "M-03";
    private static final String ENTIDAD = "equipos";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private final EquipoRepository equipos;
    private final ClubRepository clubes;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final Clock reloj;
    private final UUID ligaId;

    EquipoService(EquipoRepository equipos, ClubRepository clubes, Autorizacion autorizacion,
            AuditoriaService auditoria, MotivoOperacion motivoOperacion, Clock reloj, SiglimPropiedades propiedades) {
        this.equipos = equipos;
        this.clubes = clubes;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /** Listado filtrado por ámbito en el SQL, también en el total (RF-009, ADR-0007). */
    @Transactional(readOnly = true)
    public Pagina<EquipoDto> listar(FiltroEquipos filtro) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        filtro.validar();
        return Pagina.de(equipos.listar(ligaId, filtro, alcance), filtro.pagina(), filtro.tamano(),
                equipos.contar(ligaId, filtro, alcance));
    }

    /** Detalle de un equipo; fuera de ámbito responde 404 (RF-009, VAL-024). */
    @Transactional(readOnly = true)
    public EquipoDto consultar(UUID id) {
        EquipoDto equipo = buscar(id);
        autorizacion.exigir(CONSULTAR, objetivo(equipo));
        return equipo;
    }

    /**
     * Registra un equipo {@code ACTIVO} del club en un grupo de la temporada (RF-009, RN-007).
     *
     * <p>Orden: club existe (404) → permiso sobre ese club → club activo ({@code 409 RN-041}) → grupo
     * existe ({@code 422 REFERENCIA_INVALIDA}) → temporada no cerrada ({@code 409 RF-003}) → nombre
     * libre en el grupo ({@code 409 DUPLICADO}).
     */
    @Transactional
    public EquipoDto crear(CrearEquipoRequest peticion) {
        // FOR SHARE: un cambio de nombre concurrente (B-04) no cierra el club mientras se registra.
        ClubDto club = clubes.buscarParaCompartir(ligaId, peticion.clubId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Club " + peticion.clubId()));
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deClubes(club.id()));
        if (club.estado() == EstadoClub.CERRADO) {
            throw ReglaNegocioException.conflicto("RN-041",
                    "El club «" + club.nombre() + "» está cerrado: no puede registrar equipos nuevos.");
        }
        GrupoTemporada grupo = grupoExistente(peticion.grupoId());
        exigirTemporadaAbierta(grupo.temporadaNombre(), grupo.temporadaEstado());
        String nombre = nombreValido(peticion.nombre());
        validarNombreLibre(grupo, nombre, null);

        UUID id = equipos.insertar(club.id(), grupo.id(), nombre);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clubId", club.id().toString());
        detalle.put("grupoId", grupo.id().toString());
        detalle.put("temporadaId", grupo.temporadaId().toString());
        detalle.put("estado", EstadoEquipo.ACTIVO.name());
        auditar("REGISTRAR_EQUIPO", id, null, detalle);
        return buscar(id);
    }

    /**
     * Edita el nombre o el grupo; lo ausente conserva su valor (RF-009, [PROPUESTA]). Sin cambios
     * responde el equipo sin auditar.
     *
     * @throws ReglaNegocioException 409 {@code RF-009} si está de baja, 409 {@code RF-003} si la
     *                               temporada está cerrada, 409 o 422 {@code RN-007} si el cambio de
     *                               grupo no procede
     */
    @Transactional
    public EquipoDto actualizar(UUID id, ActualizarEquipoRequest peticion) {
        EquipoDto actual = buscarConCandado(id);
        autorizacion.exigir(EDITAR, objetivo(actual));
        exigirActivo(actual, "no admite cambios");
        exigirTemporadaAbierta(actual.temporada().nombre(), actual.temporada().estado());

        UUID grupoActual = actual.grupo().id();
        UUID grupoId = peticion.grupoId() == null ? grupoActual : peticion.grupoId();
        GrupoTemporada destino = null;
        if (!grupoId.equals(grupoActual)) {
            destino = grupoDestino(actual, grupoId);
        }
        if (peticion.nombre() != null && peticion.nombre().isBlank()) {
            throw invalida("El nombre del equipo no puede estar vacío.");
        }
        String nombre = peticion.nombre() == null ? actual.nombre() : peticion.nombre().strip();

        List<String> campos = new ArrayList<>();
        if (!nombre.equals(actual.nombre())) {
            campos.add("nombre");
        }
        if (destino != null) {
            campos.add("grupoId");
        }
        if (campos.isEmpty()) {
            return actual;
        }
        if (destino != null) {
            validarNombreLibre(destino, nombre, id);
        } else if (equipos.existeNombre(grupoActual, nombre, id)) {
            throw duplicado(nombre, actual.grupo().nombre());
        }

        equipos.actualizar(id, nombre, grupoId);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", campos);
        auditar("EDITAR_EQUIPO", id, null, detalle);
        return buscar(id);
    }

    /**
     * Da de baja el equipo con motivo obligatorio; la fila se conserva (RF-009, RN-005, RN-021).
     *
     * <p>Orden: equipo existe (404) → permiso sobre su club → no está de baja ({@code 409 RF-009}) →
     * temporada no cerrada ({@code 409 RF-003}) → motivo de al menos 10 caracteres ({@code 400 RN-021}).
     */
    @Transactional
    public EquipoDto darDeBaja(UUID id, BajaEquipoRequest peticion) {
        EquipoDto actual = buscarConCandado(id);
        autorizacion.exigir(EDITAR, objetivo(actual));
        exigirActivo(actual, "ya está dado de baja");
        exigirTemporadaAbierta(actual.temporada().nombre(), actual.temporada().estado());
        String motivo = motivoValido(peticion == null ? null : peticion.motivo());

        motivoOperacion.fijar(motivo);
        LocalDate bajaEn = LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
        equipos.darDeBaja(id, motivo, bajaEn);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", EstadoEquipo.ACTIVO.name());
        detalle.put("estado", EstadoEquipo.BAJA.name());
        detalle.put("bajaEn", bajaEn.toString());
        auditar("DAR_BAJA_EQUIPO", id, motivo, detalle);
        return buscar(id);
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    private EquipoDto buscar(UUID id) {
        return equipos.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private EquipoDto buscarConCandado(UUID id) {
        return equipos.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Equipo " + id);
    }

    private static ObjetivoAutorizacion objetivo(EquipoDto equipo) {
        return ObjetivoAutorizacion.deClubes(equipo.club().id());
    }

    private GrupoTemporada grupoExistente(UUID grupoId) {
        return equipos.buscarGrupo(ligaId, grupoId).orElseThrow(() -> ReglaNegocioException.noProcesable(
                ErroresTecnicos.REFERENCIA_INVALIDA, "El grupo indicado no existe."));
    }

    /** RN-007: el cambio de grupo es en configuración y dentro de la misma temporada. */
    private GrupoTemporada grupoDestino(EquipoDto actual, UUID grupoId) {
        if (actual.temporada().estado() != EstadoTemporada.CONFIGURACION) {
            throw ReglaNegocioException.conflicto("RN-007",
                    "El equipo solo cambia de grupo mientras la temporada «" + actual.temporada().nombre()
                            + "» está en configuración; ahora está " + actual.temporada().estado().texto() + ".");
        }
        GrupoTemporada destino = grupoExistente(grupoId);
        if (!destino.temporadaId().equals(actual.temporada().id())) {
            throw ReglaNegocioException.noProcesable("RN-007",
                    "El grupo «" + destino.nombre() + "» es de otra temporada; el equipo solo cambia a un grupo de «"
                            + actual.temporada().nombre() + "».");
        }
        return destino;
    }

    private static void exigirTemporadaAbierta(String temporada, EstadoTemporada estado) {
        if (estado == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + temporada + "» está cerrada: sus equipos son de solo lectura.");
        }
    }

    private static void exigirActivo(EquipoDto equipo, String consecuencia) {
        if (equipo.estado() == EstadoEquipo.BAJA) {
            throw ReglaNegocioException.conflicto("RF-009",
                    "El equipo «" + equipo.nombre() + "» está dado de baja: " + consecuencia + ".");
        }
    }

    private void validarNombreLibre(GrupoTemporada grupo, String nombre, UUID excepto) {
        if (equipos.existeNombre(grupo.id(), nombre, excepto)) {
            throw duplicado(nombre, grupo.nombre());
        }
    }

    private static ReglaNegocioException duplicado(String nombre, String grupo) {
        return ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                "Ya existe un equipo llamado «" + nombre + "» en el grupo «" + grupo + "».");
    }

    private static String nombreValido(String nombre) {
        String texto = nombre == null ? "" : nombre.strip();
        if (texto.isEmpty()) {
            throw invalida("El nombre del equipo es obligatorio.");
        }
        return texto;
    }

    private static String motivoValido(String motivo) {
        return MotivoTransicion.exigir(motivo, "dar de baja el equipo");
    }

    // ── Auditoría (RF-093): solo identificadores y estados, nunca datos personales ──────────────

    private void auditar(String accion, UUID entidadId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, entidadId.toString(), motivo,
                detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}

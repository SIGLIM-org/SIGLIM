package mx.tecnm.morelia.siglim.organizacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import mx.tecnm.morelia.siglim.organizacion.AfiliacionDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionRepository.CategoriaContexto;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionRepository.ResumenAfiliacion;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionRepository.TemporadaAfiliacion;
import mx.tecnm.morelia.siglim.organizacion.ContextoAfiliacionDto.CategoriaContextoDto;
import mx.tecnm.morelia.siglim.organizacion.ContextoAfiliacionDto.GrupoContextoDto;
import mx.tecnm.morelia.siglim.organizacion.ContextoAfiliacionDto.TemporadaContextoDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarjetón de afiliación de un club por temporada (RF-101, CU-018, UI-023, DEC-09).
 *
 * <ul>
 *   <li><b>Folio.</b> Lo asigna el sistema al capturar el tarjetón, en el formato
 *       {@code AF-<año de inicio de la temporada>-<consecutivo>} <b>[PROPUESTA]</b>, tomado del
 *       ejemplo de F-16. La temporada se bloquea con {@code FOR UPDATE} antes de contar, así que dos
 *       altas simultáneas de la misma temporada obtienen consecutivos distintos. El índice
 *       {@code UNIQUE (temporada_id, folio)} es la última garantía.</li>
 *   <li><b>Estados (ERS §4.3).</b> El tarjetón nace {@code SOLICITADA}; el Presidente lo aprueba o lo
 *       rechaza y ambos son terminales. Solo se edita en {@code SOLICITADA}; repetir la resolución o
 *       editar uno resuelto responde {@code 409 RF-101}. El rechazo exige observaciones de 10 a 500
 *       caracteres y la aprobación las admite opcionales con el mismo tope. Se registran el perfil y
 *       la fecha de resolución. Si el club se cerró antes de editar o resolver, responde
 *       {@code 409 RN-041}.</li>
 *   <li><b>Ámbito (ADR-0007).</b> La captura, la edición y la consulta usan
 *       {@code organizacion.afiliacion:CAPTURAR} sobre el club del tarjetón; quien solo tiene
 *       {@code organizacion.afiliacion:APROBAR} (el Presidente) también lo consulta y es el único que
 *       resuelve. La consulta evalúa el alcance unificado de ambos permisos; un club ajeno responde
 *       {@code 404} igual que uno inexistente (VAL-024). Los listados filtran por el alcance de
 *       cualquiera de los dos permisos, en el {@code WHERE} y en el {@code COUNT}. El nombre del
 *       perfil que capturó o resolvió solo se incluye si RLS permite verlo.</li>
 *   <li><b>Referencias.</b> El club debe existir y estar {@code ACTIVO} ({@code 409 RN-041} si está
 *       cerrado); la temporada debe ser de la liga y no estar {@code CERRADA} ({@code 422
 *       REFERENCIA_INVALIDA} y {@code 409 RF-003}); las categorías deben participar en la temporada
 *       ({@code 422 REFERENCIA_INVALIDA} con {@code categoriasInvalidas}). Debe declararse al menos
 *       una categoría y los colores del uniforme.</li>
 *   <li><b>Auditoría (RF-093).</b> Alta, edición y resolución se registran en {@code M-03} sobre
 *       {@code afiliaciones}, con folio, estados e identificadores; nunca con datos personales.</li>
 * </ul>
 *
 * <p>Refs: H-08.1, RF-101, RF-093, CU-018, UI-023, DEC-09, RN-005, RN-041, VAL-024, RF-003, ADR-0007
 */
@Service
public class AfiliacionService {

    private static final String CAPTURAR = "organizacion.afiliacion:CAPTURAR";
    private static final String APROBAR = "organizacion.afiliacion:APROBAR";
    private static final String MODULO = "M-03";
    private static final String ENTIDAD = "afiliaciones";

    /** Observaciones del dictamen: de 10 a 500 caracteres, como las demás transiciones (RN-021). */
    private static final int MINIMO_OBSERVACIONES = 10;
    private static final int MAXIMO_OBSERVACIONES = 500;

    /** [PROPUESTA] Prefijo del folio interno del tarjetón, tomado del ejemplo de F-16. */
    private static final String PREFIJO_FOLIO = "AF";

    private static final int LONGITUD_MAXIMA_COLORES = 120;

    private final AfiliacionRepository afiliaciones;
    private final ClubRepository clubes;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final Clock reloj;
    private final UUID ligaId;

    AfiliacionService(AfiliacionRepository afiliaciones, ClubRepository clubes, Autorizacion autorizacion,
            AuditoriaService auditoria, MotivoOperacion motivoOperacion, Clock reloj,
            SiglimPropiedades propiedades) {
        this.afiliaciones = afiliaciones;
        this.clubes = clubes;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /** Listado por temporada y estado, filtrado por ámbito en el SQL y en el total (RF-101). */
    @Transactional(readOnly = true)
    public Pagina<AfiliacionDto> listar(FiltroAfiliaciones filtro) {
        AlcanceAutorizacion alcance = alcanceConsulta();
        filtro.validar();
        return Pagina.de(afiliaciones.listar(ligaId, filtro, alcance), filtro.pagina(), filtro.tamano(),
                afiliaciones.contar(ligaId, filtro, alcance));
    }

    /** Detalle del tarjetón; fuera de ámbito responde 404 (RF-101, VAL-024). */
    @Transactional(readOnly = true)
    public AfiliacionDto consultar(UUID id) {
        AfiliacionDto afiliacion = buscar(id);
        exigirConsulta(afiliacion.club().id());
        return afiliacion;
    }

    /**
     * Temporadas de la liga con sus categorías y grupos para la pantalla de afiliación (F-16,
     * H-08.2). Se autoriza con cualquiera de los dos permisos del tarjetón y no con el permiso de
     * M-02: la Directiva necesita este contexto para capturar y no tiene
     * {@code temporadas.temporada:CONSULTAR} en la matriz inicial. Las temporadas y categorías son
     * de la liga, no de un club, así que el ámbito no las filtra; el permiso sí.
     */
    @Transactional(readOnly = true)
    public ContextoAfiliacionDto contexto() {
        alcanceConsulta();

        Map<UUID, Map<UUID, CategoriaAcumulada>> porTemporada = new LinkedHashMap<>();
        for (CategoriaContexto fila : afiliaciones.categoriasConGruposDeLiga(ligaId)) {
            Map<UUID, CategoriaAcumulada> categorias =
                    porTemporada.computeIfAbsent(fila.temporadaId(), clave -> new LinkedHashMap<>());
            CategoriaAcumulada categoria = categorias.computeIfAbsent(fila.categoriaId(),
                    clave -> new CategoriaAcumulada(fila.categoriaId(), fila.categoriaNombre()));
            if (fila.grupoId() != null) {
                categoria.grupos.add(new GrupoContextoDto(fila.grupoId(), fila.grupoNombre(), fila.equipos()));
            }
        }

        List<TemporadaContextoDto> temporadas = afiliaciones.temporadasDeLiga(ligaId).stream()
                .map(temporada -> new TemporadaContextoDto(
                        temporada.id(), temporada.nombre(), temporada.estado(), temporada.fechaInicio(),
                        porTemporada.getOrDefault(temporada.id(), Map.of()).values().stream()
                                .map(CategoriaAcumulada::aDto)
                                .toList()))
                .toList();
        return new ContextoAfiliacionDto(temporadas);
    }

    /**
     * Captura el tarjetón de un club en una temporada y lo deja {@code SOLICITADA} con folio
     * (criterio 1 de H-08, RF-101).
     *
     * <p>Orden: club de la liga ({@code 404}) → permiso sobre ese club ({@code 403}/{@code 404}) →
     * club cerrado ({@code 409 RN-041}) → temporada de la liga ({@code 422 REFERENCIA_INVALIDA}) →
     * temporada cerrada ({@code 409 RF-003}) → tarjetón ya existente ({@code 409 DUPLICADO}) →
     * colores y categorías ({@code 400 VALIDACION}) → categorías de la temporada ({@code 422
     * REFERENCIA_INVALIDA}).
     *
     * @throws ReglaNegocioException 409 si el club está cerrado, la temporada está cerrada o el club
     *                               ya tiene tarjetón en esa temporada; 422 si una referencia no
     *                               existe; 400 si falta un dato obligatorio
     */
    @Transactional
    public AfiliacionDto crear(CrearAfiliacionRequest peticion) {
        if (peticion == null || peticion.clubId() == null) {
            throw invalida("Indica el club que se afilia.");
        }
        if (peticion.temporadaId() == null) {
            throw invalida("Indica la temporada de la afiliación.");
        }

        // FOR SHARE: un cambio de nombre concurrente no cierra el club mientras se captura.
        ClubDto club = clubes.buscarParaCompartir(ligaId, peticion.clubId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Club " + peticion.clubId()));
        autorizacion.exigir(CAPTURAR, ObjetivoAutorizacion.deClubes(club.id()));
        if (club.estado() == EstadoClub.CERRADO) {
            throw ReglaNegocioException.conflicto("RN-041",
                    "El club «" + club.nombre() + "» está cerrado: no puede solicitar la afiliación.");
        }

        // FOR UPDATE: serializa el consecutivo del folio de esta temporada.
        TemporadaAfiliacion temporada = afiliaciones.bloquearTemporada(ligaId, peticion.temporadaId())
                .orElseThrow(AfiliacionService::temporadaInvalida);
        exigirTemporadaAbierta(temporada.nombre(), temporada.estado());

        ResumenAfiliacion existente = afiliaciones
                .buscarPorClubTemporada(ligaId, club.id(), temporada.id())
                .orElse(null);
        if (existente != null) {
            throw duplicada(existente, club, temporada);
        }

        String colores = coloresValidos(peticion.coloresUniformes());
        Set<UUID> categorias = categoriasValidas(peticion.categoriaIds());
        exigirCategoriasDeTemporada(temporada, categorias);

        String folio = folio(temporada, afiliaciones.contarDeTemporada(temporada.id()) + 1);
        UUID perfil = UsuarioActual.idRequerido();
        UUID id = insertarSinDuplicado(club, temporada, folio, colores, perfil);
        afiliaciones.insertarCategorias(id, categorias);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("clubId", club.id().toString());
        detalle.put("temporadaId", temporada.id().toString());
        detalle.put("folio", folio);
        detalle.put("estado", EstadoAfiliacion.SOLICITADA.name());
        detalle.put("categorias", categorias.stream().map(UUID::toString).toList());
        auditar("REGISTRAR_AFILIACION", id, null, detalle);
        return buscar(id);
    }

    /**
     * Edita colores o categorías mientras el tarjetón está {@code SOLICITADA} (RF-101). Lo ausente
     * conserva su valor; sin cambios responde el tarjetón sin auditar.
     *
     * @throws ReglaNegocioException 409 {@code RF-101} si ya está resuelto, {@code RN-041} si el
     *                               club se cerró o {@code RF-003} si la temporada está cerrada;
     *                               422 si una categoría no es de la temporada
     */
    @Transactional
    public AfiliacionDto actualizar(UUID id, ActualizarAfiliacionRequest peticion) {
        AfiliacionDto actual = buscarConCandado(id);
        autorizacion.exigir(CAPTURAR, ObjetivoAutorizacion.deClubes(actual.club().id()));
        exigirClubAbierto(actual, "no admite cambios");
        exigirTemporadaAbierta(actual.temporada().nombre(), actual.temporada().estado());
        if (actual.estado() != EstadoAfiliacion.SOLICITADA) {
            throw ReglaNegocioException.conflicto("RF-101",
                    "Solo se edita el tarjetón en estado SOLICITADA; este está " + actual.estado().texto() + ".");
        }
        if (peticion == null) {
            throw invalida("Envía los colores del uniforme o las categorías que cambian.");
        }

        String colores = peticion.coloresUniformes() == null
                ? actual.coloresUniformes()
                : coloresValidos(peticion.coloresUniformes());
        Set<UUID> categorias = peticion.categoriaIds() == null
                ? null
                : categoriasValidas(peticion.categoriaIds());

        boolean cambianColores = !colores.equals(actual.coloresUniformes());
        boolean cambianCategorias = categorias != null && !mismoConjunto(categorias, actual.categorias());
        if (!cambianColores && !cambianCategorias) {
            return actual;
        }
        if (cambianCategorias) {
            exigirCategoriasDeTemporada(actual.temporada().id(), categorias);
        }

        List<String> campos = new ArrayList<>();
        if (cambianColores) {
            afiliaciones.actualizarColores(id, colores);
            campos.add("coloresUniformes");
        }
        if (cambianCategorias) {
            afiliaciones.reemplazarCategorias(id, categorias);
            campos.add("categorias");
        }

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("folio", actual.folio());
        detalle.put("campos", campos);
        auditar("EDITAR_AFILIACION", id, null, detalle);
        return buscar(id);
    }

    /**
     * Aplica el dictamen del Presidente sobre un tarjetón {@code SOLICITADA} (criterio 2 de H-08,
     * RF-101, CU-018). La aprobación puede llevar una nota; el rechazo exige observaciones de 10 a
     * 500 caracteres, que se guardan como motivo.
     *
     * @throws ReglaNegocioException 409 {@code RF-101} si ya está resuelto, {@code RN-041} si el
     *                               club se cerró o {@code RF-003} si la temporada está cerrada; 400
     *                               {@code RF-101} si rechaza sin observaciones suficientes; 400
     *                               {@code VALIDACION} si las observaciones pasan de 500 caracteres.
     */
    @Transactional
    public AfiliacionDto resolver(UUID id, ResolverAfiliacionRequest peticion) {
        AfiliacionDto actual = buscarConCandado(id);
        autorizacion.exigir(APROBAR, ObjetivoAutorizacion.deClubes(actual.club().id()));
        exigirClubAbierto(actual, "ya no admite resolución");
        exigirTemporadaAbierta(actual.temporada().nombre(), actual.temporada().estado());
        if (actual.estado() != EstadoAfiliacion.SOLICITADA) {
            throw ReglaNegocioException.conflicto("RF-101",
                    "Solo se resuelve un tarjetón en estado SOLICITADA; este ya está "
                            + actual.estado().texto() + ".");
        }
        DecisionAfiliacion decision = peticion == null ? null : peticion.decision();
        if (decision == null) {
            throw invalida("Indica si la afiliación se aprueba o se rechaza.");
        }

        String observaciones = textoOpcional(peticion.observaciones());
        if (decision == DecisionAfiliacion.RECHAZADA) {
            observaciones = observacionesValidas(observaciones);
            motivoOperacion.fijar(observaciones);
        }

        UUID perfil = UsuarioActual.idRequerido();
        afiliaciones.resolver(id, decision.estado(), perfil, OffsetDateTime.now(reloj), observaciones);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("folio", actual.folio());
        detalle.put("estadoAnterior", EstadoAfiliacion.SOLICITADA.name());
        detalle.put("estado", decision.estado().name());
        auditar(decision == DecisionAfiliacion.APROBADA ? "APROBAR_AFILIACION" : "RECHAZAR_AFILIACION",
                id, decision == DecisionAfiliacion.RECHAZADA ? observaciones : null, detalle);
        return buscar(id);
    }

    // ── Autorización de consulta (CAPTURAR o APROBAR) ───────────────────────────────────────────

    /**
     * Alcance del listado con cualquiera de los dos permisos del tarjetón: la Secretaría y la
     * Directiva capturan, el Presidente aprueba. Los alcances se unen para no ocultar filas cuando
     * una persona tiene ambos.
     */
    private AlcanceAutorizacion alcanceConsulta() {
        AlcanceAutorizacion capturar = alcanceOpcional(CAPTURAR);
        AlcanceAutorizacion aprobar = alcanceOpcional(APROBAR);
        if (capturar == null && aprobar == null) {
            throw Autorizacion.sinPermiso();
        }
        if (capturar == null) {
            return aprobar;
        }
        if (aprobar == null) {
            return capturar;
        }
        Set<UUID> clubes = new LinkedHashSet<>(capturar.clubes());
        clubes.addAll(aprobar.clubes());
        return new AlcanceAutorizacion(capturar.todaLaLiga() || aprobar.todaLaLiga(), clubes,
                capturar.propio() || aprobar.propio());
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
     * Exige cualquiera de los dos permisos sobre el club, también cuando la persona tiene ambos con
     * ámbitos distintos (por ejemplo, Presidenta y a la vez Directiva de un club). Se evalúa con el
     * alcance unificado en lugar de llamar a {@code exigir} dos veces: atrapar su {@code 403} dejaría
     * la transacción en rollback-only. Un club fuera del alcance responde {@code 404} igual que uno
     * inexistente (VAL-024).
     */
    private void exigirConsulta(UUID clubId) {
        AlcanceAutorizacion alcance = alcanceConsulta();
        if (!alcance.todaLaLiga() && !alcance.clubes().contains(clubId)) {
            throw Autorizacion.fueraDeAmbito(CAPTURAR);
        }
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private AfiliacionDto buscar(UUID id) {
        return afiliaciones.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private AfiliacionDto buscarConCandado(UUID id) {
        return afiliaciones.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Afiliación " + id);
    }

    private UUID insertarSinDuplicado(ClubDto club, TemporadaAfiliacion temporada, String folio,
            String colores, UUID perfil) {
        try {
            return afiliaciones.insertar(club.id(), temporada.id(), folio, colores, perfil);
        } catch (DuplicateKeyException carrera) {
            ResumenAfiliacion existente = afiliaciones
                    .buscarPorClubTemporada(ligaId, club.id(), temporada.id())
                    .orElse(null);
            if (existente != null) {
                throw duplicada(existente, club, temporada);
            }
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "No se pudo asignar el folio de la afiliación. Recarga la página e intenta de nuevo.");
        }
    }

    private static ReglaNegocioException duplicada(ResumenAfiliacion existente, ClubDto club,
            TemporadaAfiliacion temporada) {
        return ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                        "El club «" + club.nombre() + "» ya tiene una afiliación en la temporada «"
                                + temporada.nombre() + "»: folio " + existente.folio() + ", estado "
                                + existente.estado().texto() + ".")
                .con("afiliacionId", existente.id().toString())
                .con("folio", existente.folio());
    }

    /**
     * El club debe seguir abierto: cerrarlo (por ejemplo, por cambio de nombre) deja el tarjetón en
     * solo lectura (RN-041). Se lee con {@code FOR SHARE} para no cruzarse con un cierre concurrente.
     */
    private void exigirClubAbierto(AfiliacionDto afiliacion, String consecuencia) {
        ClubDto club = clubes.buscarParaCompartir(ligaId, afiliacion.club().id())
                .orElseThrow(() -> new RecursoNoEncontradoException("Club " + afiliacion.club().id()));
        if (club.estado() == EstadoClub.CERRADO) {
            throw ReglaNegocioException.conflicto("RN-041",
                    "El club «" + club.nombre() + "» está cerrado: el tarjetón " + consecuencia + ".");
        }
    }

    private static void exigirTemporadaAbierta(String nombre, EstadoTemporada estado) {
        if (estado == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + nombre + "» está cerrada: su tarjetón es de solo lectura.");
        }
    }

    private static String folio(TemporadaAfiliacion temporada, long consecutivo) {
        return "%s-%d-%04d".formatted(PREFIJO_FOLIO, temporada.fechaInicio().getYear(), consecutivo);
    }

    private static String coloresValidos(String colores) {
        String texto = colores == null ? "" : colores.strip();
        if (texto.isEmpty()) {
            throw invalida("Los colores del uniforme son obligatorios.");
        }
        if (texto.length() > LONGITUD_MAXIMA_COLORES) {
            throw invalida("Los colores del uniforme admiten hasta " + LONGITUD_MAXIMA_COLORES
                    + " caracteres.");
        }
        return texto;
    }

    private static Set<UUID> categoriasValidas(List<UUID> categoriaIds) {
        if (categoriaIds == null || categoriaIds.isEmpty()) {
            throw invalida("Indica al menos una categoría del tarjetón.");
        }
        Set<UUID> categorias = new LinkedHashSet<>();
        for (UUID categoriaId : categoriaIds) {
            if (categoriaId == null) {
                throw invalida("Las categorías del tarjetón no pueden incluir un valor vacío.");
            }
            categorias.add(categoriaId);
        }
        return categorias;
    }

    private void exigirCategoriasDeTemporada(TemporadaAfiliacion temporada, Set<UUID> categorias) {
        exigirCategoriasDeTemporada(temporada.id(), categorias);
    }

    private void exigirCategoriasDeTemporada(UUID temporadaId, Set<UUID> categorias) {
        Set<UUID> asignadas = afiliaciones.categoriasDeTemporada(temporadaId);
        List<String> invalidas = categorias.stream()
                .filter(categoriaId -> !asignadas.contains(categoriaId))
                .map(UUID::toString)
                .toList();
        if (!invalidas.isEmpty()) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                            "Solo se inscriben categorías asignadas a la temporada; revisa las categorías indicadas.")
                    .con("categoriasInvalidas", invalidas);
        }
    }

    private static boolean mismoConjunto(Set<UUID> categorias, List<ReferenciaDto> actuales) {
        Set<UUID> idsActuales = new HashSet<>();
        for (ReferenciaDto categoria : actuales) {
            idsActuales.add(categoria.id());
        }
        return idsActuales.equals(new HashSet<>(categorias));
    }

    private static ReglaNegocioException temporadaInvalida() {
        return ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                "La temporada indicada no existe. Actualiza la página y elige otra.");
    }

    private static String observacionesValidas(String observaciones) {
        String texto = observaciones == null ? "" : observaciones.strip();
        if (texto.length() < MINIMO_OBSERVACIONES) {
            throw ReglaNegocioException.invalida("RF-101",
                    "Escribe observaciones de al menos " + MINIMO_OBSERVACIONES
                            + " caracteres para rechazar la afiliación.");
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

    /** Acumula grupos de una categoría mientras se recorre la consulta plana. */
    private static final class CategoriaAcumulada {

        private final UUID id;
        private final String nombre;
        private final List<GrupoContextoDto> grupos = new ArrayList<>();

        private CategoriaAcumulada(UUID id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        private CategoriaContextoDto aDto() {
            return new CategoriaContextoDto(id, nombre, grupos);
        }
    }
}

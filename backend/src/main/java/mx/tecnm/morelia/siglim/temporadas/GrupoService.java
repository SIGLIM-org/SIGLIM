package mx.tecnm.morelia.siglim.temporadas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.CategoriaRepository.FilaCategoria;
import mx.tecnm.morelia.siglim.temporadas.CategoriaTemporadaDto.GrupoResumenDto;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.CategoriaAsignada;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.ContextoCategoriaTemporada;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.GrupoDeCategoria;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.GrupoTemporada;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.TemporadaEstado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estructura deportiva de la temporada: las categorías que participan (RF-004) y sus grupos
 * (RF-005). Con ellas se cumple la lista de verificación de activación de {@link TemporadaService}.
 *
 * <ul>
 *   <li><b>Permisos (D1).</b> Las consultas exigen {@code temporadas.temporada:CONSULTAR} y las
 *       escrituras {@code temporadas.temporada:EDITAR}, ambas sobre la liga.</li>
 *   <li><b>Estado de la temporada (D6).</b> Asignar una categoría y crear un grupo solo con la
 *       temporada en {@code CONFIGURACION}: activa responde {@code 409 RF-004} o {@code 409 RF-005}.
 *       Renombrar un grupo procede en configuración o activa. Cerrada responde {@code 409 RF-003} en
 *       las tres (RN-005).</li>
 *   <li><b>Categoría activa (RF-004).</b> Solo una categoría activa del catálogo se asigna:
 *       inactiva responde {@code 409 RF-004}; inexistente, {@code 422 REFERENCIA_INVALIDA}.</li>
 *   <li><b>Sin borrado (D7).</b> No se quita una categoría de la temporada ni se borra un grupo: el
 *       rol {@code siglim_app} no tiene {@code DELETE} sobre esas tablas.</li>
 *   <li><b>Días de juego (H-32.2, DEC-177, DEC-191).</b> {@code PATCH} de la categoría de la
 *       temporada con {@code diasJuego}: de 1 a 7 valores ISO (1 = lunes … 7 = domingo), sin repetir
 *       ({@code 400 VALIDACION}); se guardan ordenados. Procede en configuración o activa; cerrada
 *       responde {@code 409 RF-003}. Exige {@code programacion.jornada:EDITAR}, no el permiso de
 *       M-02: los días solo sirven para programar (DEC-177) y quien los ajusta es el Programador,
 *       que no tiene {@code temporadas.temporada:EDITAR} en la matriz del Apéndice E (DEC-191). El
 *       permiso es un texto; el paquete no depende de {@code programacion} (ADR-0006).</li>
 *   <li><b>Auditoría (RF-093, D9).</b> {@code ASIGNAR_CATEGORIA_TEMPORADA}, {@code REGISTRAR_GRUPO},
 *       {@code EDITAR_GRUPO} y {@code CAMBIAR_DIAS_JUEGO} (con {@code diasAnteriores} y
 *       {@code diasJuego}) en el módulo M-02, sin motivo; el detalle lleva solo identificadores y
 *       valores. Un renombre o un cambio de días sin cambios no escribe ni se audita.</li>
 * </ul>
 *
 * <p>Refs: H-05.1, H-32.2, RF-003, RF-004, RF-005, RF-035, RF-093, RN-005, DEC-177, DEC-191, ADR-0007,
 * ADR-0011
 */
@Service
public class GrupoService {

    private static final String CONSULTAR = "temporadas.temporada:CONSULTAR";
    private static final String EDITAR = "temporadas.temporada:EDITAR";
    /** DEC-191: los días de juego los ajusta el Programador con el permiso de M-07. */
    private static final String EDITAR_PROGRAMACION = "programacion.jornada:EDITAR";
    private static final String MODULO = "M-02";
    private static final int DIA_MINIMO = 1;
    private static final int DIA_MAXIMO = 7;
    private static final int LONGITUD_NOMBRE = 60;

    private final GrupoRepository grupos;
    private final CategoriaRepository categorias;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    GrupoService(GrupoRepository grupos, CategoriaRepository categorias, Autorizacion autorizacion,
            AuditoriaService auditoria, SiglimPropiedades propiedades) {
        this.grupos = grupos;
        this.categorias = categorias;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    // ── Categorías de la temporada ─────────────────────────────────────────────────────────────

    /** Categorías de la temporada por el orden del catálogo, con sus grupos anidados (RF-004, D8). */
    @Transactional(readOnly = true)
    public List<CategoriaTemporadaDto> listarCategorias(UUID temporadaId) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        buscarTemporada(temporadaId);
        return conGrupos(temporadaId, null);
    }

    /** Una categoría de la temporada; inexistente o de otra temporada responde 404 (RF-004). */
    @Transactional(readOnly = true)
    public CategoriaTemporadaDto consultarCategoria(UUID temporadaId, UUID categoriaTemporadaId) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        buscarTemporada(temporadaId);
        return categoriaDeTemporada(temporadaId, categoriaTemporadaId);
    }

    /**
     * Asigna una categoría del catálogo a la temporada (RF-004).
     *
     * <p>Orden: permiso → temporada existe ({@code 404}) → categoría existe ({@code 422}) → temporada
     * cerrada ({@code 409 RF-003}) o activa ({@code 409 RF-004}) → categoría inactiva ({@code 409
     * RF-004}) → ya asignada ({@code 409 DUPLICADO}).
     */
    @Transactional
    public CategoriaTemporadaDto asignarCategoria(UUID temporadaId, AsignarCategoriaRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (peticion == null || peticion.categoriaId() == null) {
            throw invalida("La categoría es obligatoria.");
        }
        TemporadaEstado temporada = grupos.bloquearTemporada(ligaId, temporadaId)
                .orElseThrow(() -> temporadaNoExiste(temporadaId));
        FilaCategoria categoria = categorias.buscarParaAsignar(ligaId, peticion.categoriaId())
                .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "La categoría indicada no existe."));
        exigirEnConfiguracion(temporada, "RF-004", "no admite categorías nuevas");
        if (!categoria.activa()) {
            throw ReglaNegocioException.conflicto("RF-004",
                    "La categoría «" + categoria.nombre() + "» está inactiva: reactívala en el catálogo antes "
                            + "de asignarla a la temporada.");
        }
        if (grupos.categoriaAsignada(temporada.id(), categoria.id())) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "La categoría «" + categoria.nombre() + "» ya participa en la temporada «"
                            + temporada.nombre() + "».");
        }

        UUID id = grupos.asignarCategoria(temporada.id(), categoria.id());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("temporadaId", temporada.id().toString());
        detalle.put("categoriaId", categoria.id().toString());
        auditar("ASIGNAR_CATEGORIA_TEMPORADA", "categorias_temporada", id, detalle);
        return categoriaDeTemporada(temporada.id(), id);
    }

    /**
     * Cambia los días de juego de la categoría en la temporada (H-32.2, RF-035, DEC-177, DEC-191).
     *
     * <p>Orden: permiso {@code programacion.jornada:EDITAR} ({@code 403}) → días ({@code 400
     * VALIDACION}) → temporada de la liga ({@code 404}) → categoría de esa temporada ({@code 404}) →
     * temporada cerrada ({@code 409 RF-003}). Sin cambios responde la categoría sin escribir ni auditar.
     * El cambio aplica a las asignaciones futuras; no mueve partidos ya programados.
     */
    @Transactional
    public CategoriaTemporadaDto actualizarDiasJuego(UUID temporadaId, UUID categoriaTemporadaId,
            ActualizarDiasJuegoRequest peticion) {
        autorizacion.exigir(EDITAR_PROGRAMACION, ObjetivoAutorizacion.deLiga());
        List<Integer> dias = diasValidos(peticion == null ? null : peticion.diasJuego());
        buscarTemporada(temporadaId);
        ContextoCategoriaTemporada contexto = grupos.bloquearCategoriaParaEditar(ligaId, categoriaTemporadaId)
                .filter(encontrada -> encontrada.temporada().id().equals(temporadaId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría de la temporada " + categoriaTemporadaId));
        exigirNoCerrada(contexto.temporada());
        List<Integer> anteriores = grupos.diasJuego(contexto.id());
        if (anteriores.equals(dias)) {
            return categoriaDeTemporada(temporadaId, contexto.id());
        }

        grupos.actualizarDiasJuego(contexto.id(), dias);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("temporadaId", temporadaId.toString());
        detalle.put("diasAnteriores", anteriores);
        detalle.put("diasJuego", dias);
        auditar("CAMBIAR_DIAS_JUEGO", "categorias_temporada", contexto.id(), detalle);
        return categoriaDeTemporada(temporadaId, contexto.id());
    }

    /** De 1 a 7 días ISO distintos, cada uno de 1 a 7; los devuelve ordenados (DEC-191). */
    static List<Integer> diasValidos(List<Integer> dias) {
        if (dias == null || dias.isEmpty() || dias.size() > DIA_MAXIMO) {
            throw invalida("Indica de 1 a 7 días de juego (1 = lunes … 7 = domingo).");
        }
        Set<Integer> vistos = new HashSet<>();
        for (Integer dia : dias) {
            if (dia == null || dia < DIA_MINIMO || dia > DIA_MAXIMO) {
                throw invalida("Cada día de juego va de 1 (lunes) a 7 (domingo).");
            }
            if (!vistos.add(dia)) {
                throw invalida("El día " + dia + " está repetido en los días de juego.");
            }
        }
        List<Integer> ordenados = new ArrayList<>(vistos);
        ordenados.sort(null);
        return List.copyOf(ordenados);
    }

    // ── Grupos ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Grupos de una categoría de la temporada por nombre (RF-005).
     *
     * @throws ReglaNegocioException 400 {@code VALIDACION} sin {@code categoriaTemporadaId}
     */
    @Transactional(readOnly = true)
    public List<GrupoDto> listar(UUID categoriaTemporadaId) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        if (categoriaTemporadaId == null) {
            throw invalida("Indica la categoría de la temporada con el filtro categoriaTemporadaId.");
        }
        grupos.buscarCategoriaTemporada(ligaId, categoriaTemporadaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría de la temporada " + categoriaTemporadaId));
        return grupos.listarGrupos(categoriaTemporadaId);
    }

    /** Detalle de un grupo con su temporada y su categoría (RF-005). */
    @Transactional(readOnly = true)
    public GrupoDto consultar(UUID id) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        return buscarGrupo(id).grupo();
    }

    /**
     * Crea un grupo en una categoría de la temporada (RF-005).
     *
     * <p>Orden: permiso → nombre ({@code 400}) → categoría de la temporada existe ({@code 422}) →
     * temporada cerrada ({@code 409 RF-003}) o activa ({@code 409 RF-005}) → nombre libre en la
     * categoría ({@code 409 DUPLICADO}).
     */
    @Transactional
    public GrupoDto crear(CrearGrupoRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        String nombre = nombreValido(peticion.nombre());
        if (peticion.categoriaTemporadaId() == null) {
            throw invalida("La categoría de la temporada es obligatoria.");
        }
        ContextoCategoriaTemporada contexto = grupos.bloquearCategoriaTemporada(ligaId, peticion.categoriaTemporadaId())
                .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "La categoría de la temporada indicada no existe."));
        exigirEnConfiguracion(contexto.temporada(), "RF-005", "no admite grupos nuevos");
        if (grupos.existeNombreGrupo(contexto.id(), nombre, null)) {
            throw duplicado(nombre, contexto.categoria().nombre());
        }

        UUID id = grupos.insertarGrupo(contexto.id(), nombre);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("categoriaTemporadaId", contexto.id().toString());
        detalle.put("temporadaId", contexto.temporada().id().toString());
        auditar("REGISTRAR_GRUPO", "grupos", id, detalle);
        return buscarGrupo(id).grupo();
    }

    /**
     * Renombra el grupo (RF-005). Sin nombre o con el mismo, responde el grupo sin auditar.
     *
     * <p>Orden: permiso → nombre ({@code 400}) → grupo existe ({@code 404}) → temporada cerrada
     * ({@code 409 RF-003}) → nombre libre en la categoría ({@code 409 DUPLICADO}).
     */
    @Transactional
    public GrupoDto actualizar(UUID id, ActualizarGrupoRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        String nombre = peticion.nombre() == null ? null : nombreValido(peticion.nombre());
        GrupoTemporada actual = grupos.buscarGrupoParaActualizar(ligaId, id).orElseThrow(() -> grupoNoExiste(id));
        exigirNoCerrada(actual.temporada());
        if (nombre == null || nombre.equals(actual.grupo().nombre())) {
            return actual.grupo();
        }
        if (grupos.existeNombreGrupo(actual.grupo().categoriaTemporadaId(), nombre, id)) {
            throw duplicado(nombre, actual.grupo().categoria().nombre());
        }

        grupos.renombrarGrupo(id, nombre);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", List.of("nombre"));
        detalle.put("temporadaId", actual.temporada().id().toString());
        auditar("EDITAR_GRUPO", "grupos", id, detalle);
        return buscarGrupo(id).grupo();
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    private TemporadaEstado buscarTemporada(UUID temporadaId) {
        return grupos.buscarTemporada(ligaId, temporadaId).orElseThrow(() -> temporadaNoExiste(temporadaId));
    }

    private static RecursoNoEncontradoException temporadaNoExiste(UUID temporadaId) {
        return new RecursoNoEncontradoException("Temporada " + temporadaId);
    }

    private GrupoTemporada buscarGrupo(UUID id) {
        return grupos.buscarGrupo(ligaId, id).orElseThrow(() -> grupoNoExiste(id));
    }

    private static RecursoNoEncontradoException grupoNoExiste(UUID id) {
        return new RecursoNoEncontradoException("Grupo " + id);
    }

    private CategoriaTemporadaDto categoriaDeTemporada(UUID temporadaId, UUID categoriaTemporadaId) {
        List<CategoriaTemporadaDto> encontradas = conGrupos(temporadaId, categoriaTemporadaId);
        if (encontradas.isEmpty()) {
            throw new RecursoNoEncontradoException("Categoría de la temporada " + categoriaTemporadaId);
        }
        return encontradas.getFirst();
    }

    /** Arma las categorías con sus grupos con dos consultas, no una por categoría (§9.1). */
    private List<CategoriaTemporadaDto> conGrupos(UUID temporadaId, UUID categoriaTemporadaId) {
        List<CategoriaAsignada> asignadas = grupos.listarCategorias(temporadaId, categoriaTemporadaId);
        if (asignadas.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<GrupoResumenDto>> porCategoria = grupos.listarGruposDeTemporada(temporadaId,
                        categoriaTemporadaId).stream()
                .collect(Collectors.groupingBy(GrupoDeCategoria::categoriaTemporadaId, LinkedHashMap::new,
                        Collectors.mapping(GrupoDeCategoria::grupo, Collectors.toList())));
        return asignadas.stream()
                .map(asignada -> new CategoriaTemporadaDto(asignada.id(), asignada.categoria(), asignada.diasJuego(),
                        porCategoria.getOrDefault(asignada.id(), List.of())))
                .toList();
    }

    /** D6: las altas de estructura solo en configuración; cerrada es de solo lectura (RF-003). */
    private static void exigirEnConfiguracion(TemporadaEstado temporada, String codigo, String consecuencia) {
        exigirNoCerrada(temporada);
        if (temporada.estado() != EstadoTemporada.CONFIGURACION) {
            throw ReglaNegocioException.conflicto(codigo,
                    "La temporada «" + temporada.nombre() + "» está " + temporada.estado().texto() + ": "
                            + consecuencia + ". Su estructura se define mientras está en configuración.");
        }
    }

    private static void exigirNoCerrada(TemporadaEstado temporada) {
        if (temporada.estado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + temporada.nombre() + "» está cerrada: su estructura es de solo lectura.");
        }
    }

    private static ReglaNegocioException duplicado(String nombre, String categoria) {
        return ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                "Ya existe un grupo llamado «" + nombre + "» en la categoría «" + categoria + "».");
    }

    private static String nombreValido(String nombre) {
        String texto = nombre == null ? "" : nombre.strip();
        if (texto.isEmpty()) {
            throw invalida("El nombre del grupo es obligatorio.");
        }
        if (texto.length() > LONGITUD_NOMBRE) {
            throw invalida("El nombre del grupo admite hasta " + LONGITUD_NOMBRE + " caracteres.");
        }
        return texto;
    }

    // ── Auditoría (RF-093): solo identificadores ───────────────────────────────────────────────

    private void auditar(String accion, String entidad, UUID entidadId, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, entidad, entidadId.toString(), null,
                detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}

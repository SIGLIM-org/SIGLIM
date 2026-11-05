package mx.tecnm.morelia.siglim.temporadas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.TemporadaEstado;
import mx.tecnm.morelia.siglim.temporadas.ParametrosRepository.FilaCategoria;
import mx.tecnm.morelia.siglim.temporadas.ParametrosRepository.FilaVersion;
import mx.tecnm.morelia.siglim.temporadas.ParametrosRepository.VersionCreada;
import mx.tecnm.morelia.siglim.temporadas.RegistrarParametrosRequest.CategoriaRequest;
import mx.tecnm.morelia.siglim.temporadas.ValoresParametros.ValorCategoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parámetros deportivos versionados de la temporada (RF-006, RF-100): puntos, criterios de
 * desempate, marcador por default, plazo de la cédula, tiempos de juego y de cancha, liguilla,
 * ascensos y cambio de club. Con al menos una versión se cumple el requisito {@code RF-100} de la
 * lista de activación de {@link TemporadaService}.
 *
 * <ul>
 *   <li><b>Permisos (D1).</b> Las consultas exigen {@code temporadas.temporada:CONSULTAR} y el
 *       registro {@code temporadas.parametros:EDITAR}, ambos sobre la liga; la liga sale de la
 *       configuración, nunca del cuerpo.</li>
 *   <li><b>Valores [DEMO] (D2).</b> {@code esDemo} omitido vale {@code true}. Con {@code esDemo}, la
 *       respuesta lista en {@code camposDemo} los campos que la interfaz rotula «No oficial»
 *       ({@link #CAMPOS_DEMO}); sin él, la lista va vacía. «Oficial» significa que la Liga confirmó
 *       los valores (PEN-021).</li>
 *   <li><b>Juego completo (D3).</b> Cada versión se registra con todos sus valores; nada se hereda
 *       de la anterior. Las versiones no se editan ni se borran (RF-006).</li>
 *   <li><b>Estado (D4).</b> Se registra con la temporada en {@code CONFIGURACION} o {@code ACTIVA};
 *       {@code CERRADA} responde {@code 409 RF-003} (RN-005).</li>
 *   <li><b>Motivo (D5, RN-021).</b> Obligatorio, de 10 a 500 caracteres sin espacios en los extremos:
 *       corto o ausente responde {@code 400 RN-021}; largo, {@code 400 VALIDACION}. Se valida antes
 *       que los demás campos, se guarda en la versión y va en la auditoría.</li>
 *   <li><b>Criterios de desempate (D6, RF-050, DEC-27).</b> De 1 a 8, el primero {@code PUNTOS}, sin
 *       repetidos y {@code SORTEO}, si aparece, al final. Ningún criterio usa el nombre del equipo:
 *       un valor fuera del enum lo rechaza la lectura del JSON.</li>
 *   <li><b>Categorías (D7, DEC-25).</b> Filas propias de duración y bloque, sin repetir categoría y
 *       solo de categorías asignadas a esta temporada ({@code 422 REFERENCIA_INVALIDA}); las demás
 *       usan los valores de la temporada. Solo se insertan, nunca se borran.</li>
 *   <li><b>Consultas (D8).</b> El {@code GET} devuelve la vigente, el historial con
 *       {@code camposCambiados} y, si no hay versiones, los {@code DEFAULT} de la tabla.</li>
 *   <li><b>Concurrencia (D9).</b> La temporada se bloquea con {@code FOR UPDATE} antes de leer la
 *       versión anterior e insertar la nueva.</li>
 *   <li><b>Auditoría (D10, RF-093).</b> {@code REGISTRAR_PARAMETROS} sobre
 *       {@code parametros_temporada} con el motivo y, en el detalle, la temporada, la versión, la
 *       anterior, {@code esDemo} y los campos cambiados; sin valores ni datos personales.</li>
 *   <li><b>Pruebas (D11).</b> Las pruebas de integración preparan sus temporadas con este servicio,
 *       no con {@code INSERT} directos.</li>
 * </ul>
 *
 * <p>Los rangos de los campos replican los {@code CHECK} de la tabla para responder {@code 400} con
 * un mensaje claro; los topes superiores que la tabla no tiene son [PROPUESTA].
 *
 * <p><b>Qué es retroactivo.</b> La tabla de posiciones se calcula con la versión vigente, así que un
 * cambio de puntos o criterios recalcula todas las tablas de la temporada. No cambian
 * {@code fin_bloque} de partidos ya programados, {@code plazo_vence_en} de cédulas ya enviadas ni
 * las fases y propuestas que guardaron su {@code parametros_id}.
 *
 * <p>Refs: H-05.2, RF-003, RF-006, RF-050, RF-093, RF-100, RN-021, DEC-25, DEC-26, DEC-27, DEC-28,
 * DEC-29, ADR-0007
 */
@Service
public class ParametrosService {

    private static final String CONSULTAR = "temporadas.temporada:CONSULTAR";
    private static final String EDITAR = "temporadas.parametros:EDITAR";
    private static final String MODULO = "M-02";
    private static final String ENTIDAD = "parametros_temporada";
    private static final String ACCION = "REGISTRAR_PARAMETROS";

    /** [PROPUESTA] Convenciones de API §5: mínimo del motivo (RN-021). */
    private static final int MINIMO_MOTIVO = 10;
    /** [PROPUESTA] Máximo del motivo de una versión (D5). */
    private static final int MAXIMO_MOTIVO = 500;
    /** [PROPUESTA] Tope de puntos por resultado; la tabla solo exige que no sean negativos. */
    private static final int MAXIMO_PUNTOS = 100;
    /** [PROPUESTA] Tope de goles del marcador por default. */
    private static final int MAXIMO_GOLES = 99;
    /** [PROPUESTA] Plazo de validación de la cédula: de 1 hora a 30 días. */
    private static final int MAXIMO_PLAZO_HORAS = 720;
    /** [PROPUESTA] Tope de duración de juego y bloque de cancha, en minutos. */
    private static final int MAXIMO_MINUTOS = 600;
    /** [PROPUESTA] Tope de equipos que ascienden o descienden. */
    private static final int MAXIMO_MOVIMIENTOS = 32;
    /** {@code ck_parametros_liguilla}. */
    private static final Set<Integer> CLASIFICADOS_VALIDOS = Set.of(2, 4, 8, 16, 32);
    private static final int MAXIMO_CRITERIOS = CriterioDesempate.values().length;

    /**
     * Campos [DEMO] que la interfaz rotula «No oficial» mientras {@code esDemo} sea {@code true}
     * (D2): puntos (DEC-26), desempates (DEC-27), marcador por default (DEC-24), plazo de la cédula
     * (DEC-20), bloque de cancha (DEC-25), liguilla (DEC-28) y ascensos y descensos (DEC-29). No
     * incluye {@code duracionJuegoMin}, que DEC-25 adopta como estándar, ni
     * {@code permiteCambioClub}, que decide DEC-13.
     */
    static final List<String> CAMPOS_DEMO = List.of(
            "puntosVictoria", "puntosEmpate", "puntosDerrota", "criteriosDesempate",
            "golesDefaultGanador", "golesDefaultPerdedor", "plazoValidacionCedulaHoras", "bloqueCanchaMin",
            "clasificadosLiguilla", "formatoLiguilla", "empateLiguillaPorPenales",
            "equiposAscienden", "equiposDescienden");

    /** Campos de una versión en el orden del contrato, para {@code camposCambiados}. */
    private static final Map<String, Function<ValoresParametros, Object>> CAMPOS = campos();

    private static final String CAMPO_CATEGORIAS = "categorias";

    private final ParametrosRepository parametros;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    ParametrosService(ParametrosRepository parametros, Autorizacion autorizacion, AuditoriaService auditoria,
            SiglimPropiedades propiedades) {
        this.parametros = parametros;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    // ── Consultas ──────────────────────────────────────────────────────────────────────────────

    /**
     * Vigente, historial de la mayor a la menor y, si no hay versiones, los valores predeterminados
     * (RF-006, D8).
     */
    @Transactional(readOnly = true)
    public ParametrosTemporadaDto consultar(UUID temporadaId) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        buscarTemporada(temporadaId);
        List<FilaVersion> versiones = parametros.listarVersiones(temporadaId);
        if (versiones.isEmpty()) {
            return new ParametrosTemporadaDto(null, List.of(), ParametrosPredeterminadosDto.DE_LA_TABLA);
        }
        Map<UUID, List<FilaCategoria>> porVersion = parametros.categoriasDeTemporada(temporadaId).stream()
                .collect(Collectors.groupingBy(FilaCategoria::parametrosId, LinkedHashMap::new, Collectors.toList()));

        List<VersionParametrosResumenDto> historial = new ArrayList<>();
        ValoresParametros anterior = null;
        for (FilaVersion fila : versiones) {
            ValoresParametros valores = completos(fila, porVersion.getOrDefault(fila.id(), List.of()));
            historial.add(new VersionParametrosResumenDto(fila.id(), fila.version(), valores.esDemo(), fila.motivo(),
                    creadoPor(fila), fila.creadoEn(), camposCambiados(anterior, valores)));
            anterior = valores;
        }
        Collections.reverse(historial);
        FilaVersion vigente = versiones.getLast();
        return new ParametrosTemporadaDto(aDto(vigente, porVersion.getOrDefault(vigente.id(), List.of())),
                historial, null);
    }

    /** Una versión de la temporada; inexistente responde {@code 404} (RF-006, D8). */
    @Transactional(readOnly = true)
    public ParametrosDto consultarVersion(UUID temporadaId, int version) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        buscarTemporada(temporadaId);
        return version(temporadaId, version);
    }

    // ── Registro ───────────────────────────────────────────────────────────────────────────────

    /**
     * Registra una versión nueva con el juego completo de valores (RF-006, RF-100).
     *
     * <p>Orden: permiso ({@code 403}) → motivo ({@code 400 RN-021} o {@code VALIDACION}) → campos
     * ({@code 400 VALIDACION} con la extensión {@code errores}) → temporada, con candado
     * ({@code 404}) → categorías asignadas ({@code 422 REFERENCIA_INVALIDA}) → temporada cerrada
     * ({@code 409 RF-003}).
     */
    @Transactional
    public ParametrosDto registrar(UUID temporadaId, RegistrarParametrosRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (peticion == null) {
            throw invalida("Envía los parámetros deportivos de la temporada.");
        }
        String motivo = motivoValido(peticion.motivo());
        ValoresParametros valores = validar(peticion);

        TemporadaEstado temporada = parametros.bloquearTemporada(ligaId, temporadaId)
                .orElseThrow(() -> temporadaNoExiste(temporadaId));
        validarCategoriasAsignadas(temporada, valores.categorias());
        if (temporada.estado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + temporada.nombre() + "» está cerrada: sus parámetros son de solo lectura.");
        }

        FilaVersion vigente = parametros.buscarVigente(temporada.id()).orElse(null);
        ValoresParametros anterior = vigente == null ? null
                : completos(vigente, parametros.categoriasDeVersion(vigente.id()));

        VersionCreada creada = parametros.insertar(temporada.id(), valores, motivo, UsuarioActual.idRequerido());
        valores.categorias().forEach(categoria -> parametros.insertarCategoria(creada.id(), categoria));

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("temporadaId", temporada.id().toString());
        detalle.put("version", creada.version());
        detalle.put("versionAnterior", vigente == null ? null : vigente.version());
        detalle.put("esDemo", valores.esDemo());
        detalle.put("campos", camposCambiados(anterior, valores));
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, ACCION, ENTIDAD, creada.id().toString(), motivo,
                detalle));
        return version(temporada.id(), creada.version());
    }

    // ── Validación del cuerpo ──────────────────────────────────────────────────────────────────

    private static String motivoValido(String motivo) {
        String texto = motivo == null ? "" : motivo.strip();
        if (texto.length() < MINIMO_MOTIVO) {
            throw ReglaNegocioException.invalida("RN-021",
                    "Escribe un motivo de al menos " + MINIMO_MOTIVO
                            + " caracteres para registrar los parámetros deportivos.");
        }
        if (texto.length() > MAXIMO_MOTIVO) {
            throw invalida("El motivo admite hasta " + MAXIMO_MOTIVO + " caracteres.");
        }
        return texto;
    }

    /** Valida todos los campos y responde todos los errores juntos, como {@code @Valid}. */
    private static ValoresParametros validar(RegistrarParametrosRequest p) {
        Errores errores = new Errores();

        int victoria = errores.entero("puntosVictoria", p.puntosVictoria(), 0, MAXIMO_PUNTOS, "los puntos por victoria");
        int empate = errores.entero("puntosEmpate", p.puntosEmpate(), 0, MAXIMO_PUNTOS, "los puntos por empate");
        int derrota = errores.entero("puntosDerrota", p.puntosDerrota(), 0, MAXIMO_PUNTOS, "los puntos por derrota");
        if (errores.validos("puntosVictoria", "puntosEmpate", "puntosDerrota")
                && !(victoria >= empate && empate >= derrota)) {
            errores.agregar("puntosVictoria",
                    "Los puntos deben cumplir victoria ≥ empate ≥ derrota (recibidos " + victoria + ", " + empate
                            + " y " + derrota + ").");
        }

        List<CriterioDesempate> criterios = criterios(p.criteriosDesempate(), errores);

        int ganador = errores.entero("golesDefaultGanador", p.golesDefaultGanador(), 0, MAXIMO_GOLES,
                "los goles del equipo presente en el marcador por default");
        int perdedor = errores.entero("golesDefaultPerdedor", p.golesDefaultPerdedor(), 0, MAXIMO_GOLES,
                "los goles del equipo ausente en el marcador por default");
        if (errores.validos("golesDefaultGanador", "golesDefaultPerdedor") && ganador <= perdedor) {
            errores.agregar("golesDefaultGanador",
                    "En el marcador por default el equipo presente debe tener más goles que el ausente.");
        }

        int plazo = errores.entero("plazoValidacionCedulaHoras", p.plazoValidacionCedulaHoras(), 1,
                MAXIMO_PLAZO_HORAS, "el plazo para validar la cédula (horas)");
        int duracion = errores.entero("duracionJuegoMin", p.duracionJuegoMin(), 1, MAXIMO_MINUTOS,
                "la duración de juego (minutos)");
        int bloque = errores.entero("bloqueCanchaMin", p.bloqueCanchaMin(), 1, MAXIMO_MINUTOS,
                "el bloque de cancha (minutos)");
        if (errores.validos("duracionJuegoMin", "bloqueCanchaMin") && bloque < duracion) {
            errores.agregar("bloqueCanchaMin", "El bloque de cancha (" + bloque
                    + " min) no puede ser menor que la duración de juego (" + duracion + " min).");
        }

        int clasificados = 0;
        if (p.clasificadosLiguilla() == null) {
            errores.agregar("clasificadosLiguilla", "Falta el número de clasificados a la liguilla.");
        } else if (!CLASIFICADOS_VALIDOS.contains(p.clasificadosLiguilla())) {
            errores.agregar("clasificadosLiguilla", "Los clasificados a la liguilla deben ser 2, 4, 8, 16 o 32.");
        } else {
            clasificados = p.clasificadosLiguilla();
        }
        errores.obligatorio("formatoLiguilla", p.formatoLiguilla(), "el formato de la liguilla");
        errores.obligatorio("empateLiguillaPorPenales", p.empateLiguillaPorPenales(),
                "indicar si el empate en liguilla se define por penales");
        int ascienden = errores.entero("equiposAscienden", p.equiposAscienden(), 0, MAXIMO_MOVIMIENTOS,
                "los equipos que ascienden");
        int descienden = errores.entero("equiposDescienden", p.equiposDescienden(), 0, MAXIMO_MOVIMIENTOS,
                "los equipos que descienden");
        errores.obligatorio("permiteCambioClub", p.permiteCambioClub(), "indicar si se permite el cambio de club");

        List<ValorCategoria> categorias = categorias(p.categorias(), errores);

        errores.lanzarSiHay();
        return new ValoresParametros(victoria, empate, derrota, criterios, ganador, perdedor, plazo, duracion, bloque,
                clasificados, p.formatoLiguilla(), p.empateLiguillaPorPenales(), ascienden, descienden,
                p.permiteCambioClub(), p.esDemo() == null || p.esDemo(), categorias);
    }

    /** D6: de 1 a 8, el primero PUNTOS, sin repetidos y SORTEO solo al final (RF-050, DEC-27). */
    private static List<CriterioDesempate> criterios(List<CriterioDesempate> criterios, Errores errores) {
        String campo = "criteriosDesempate";
        if (criterios == null || criterios.isEmpty()) {
            errores.agregar(campo, "Indica al menos un criterio de desempate; el primero es PUNTOS.");
            return List.of();
        }
        if (criterios.size() > MAXIMO_CRITERIOS) {
            errores.agregar(campo, "Hay a lo más " + MAXIMO_CRITERIOS + " criterios de desempate.");
            return List.of();
        }
        if (criterios.stream().anyMatch(Objects::isNull)) {
            errores.agregar(campo, "Un criterio de desempate está vacío.");
            return List.of();
        }
        if (criterios.getFirst() != CriterioDesempate.PUNTOS) {
            errores.agregar(campo, "El primer criterio de desempate debe ser PUNTOS.");
        }
        Set<CriterioDesempate> vistos = new HashSet<>();
        for (CriterioDesempate criterio : criterios) {
            if (!vistos.add(criterio)) {
                errores.agregar(campo, "El criterio de desempate " + criterio + " está repetido.");
                break;
            }
        }
        int sorteo = criterios.indexOf(CriterioDesempate.SORTEO);
        if (sorteo >= 0 && sorteo != criterios.size() - 1) {
            errores.agregar(campo, "El SORTEO solo puede ser el último criterio de desempate.");
        }
        return criterios;
    }

    /** D7: sin categorías repetidas y con bloque ≥ duración en cada una. */
    private static List<ValorCategoria> categorias(List<CategoriaRequest> categorias, Errores errores) {
        if (categorias == null || categorias.isEmpty()) {
            return List.of();
        }
        List<ValorCategoria> valores = new ArrayList<>();
        Set<UUID> vistas = new HashSet<>();
        for (int indice = 0; indice < categorias.size(); indice++) {
            String prefijo = "categorias[" + indice + "]";
            CategoriaRequest categoria = categorias.get(indice);
            if (categoria == null) {
                errores.agregar(prefijo, "Una fila de categoría está vacía.");
                continue;
            }
            if (categoria.categoriaId() == null) {
                errores.agregar(prefijo + ".categoriaId", "La categoría es obligatoria.");
            } else if (!vistas.add(categoria.categoriaId())) {
                errores.agregar(prefijo + ".categoriaId", "La categoría está repetida en los parámetros.");
            }
            int duracion = errores.entero(prefijo + ".duracionJuegoMin", categoria.duracionJuegoMin(), 1,
                    MAXIMO_MINUTOS, "la duración de juego de la categoría (minutos)");
            int bloque = errores.entero(prefijo + ".bloqueCanchaMin", categoria.bloqueCanchaMin(), 1,
                    MAXIMO_MINUTOS, "el bloque de cancha de la categoría (minutos)");
            if (errores.validos(prefijo + ".duracionJuegoMin", prefijo + ".bloqueCanchaMin") && bloque < duracion) {
                errores.agregar(prefijo + ".bloqueCanchaMin", "El bloque de cancha de la categoría (" + bloque
                        + " min) no puede ser menor que su duración de juego (" + duracion + " min).");
            }
            valores.add(new ValorCategoria(categoria.categoriaId(), duracion, bloque));
        }
        return valores;
    }

    /** D7: cada categoría debe estar asignada a esta temporada; si no, {@code 422}. */
    private void validarCategoriasAsignadas(TemporadaEstado temporada, List<ValorCategoria> categorias) {
        if (categorias.isEmpty()) {
            return;
        }
        Set<UUID> asignadas = parametros.categoriasAsignadas(temporada.id());
        List<String> ajenas = categorias.stream()
                .map(ValorCategoria::categoriaId)
                .filter(id -> !asignadas.contains(id))
                .map(UUID::toString)
                .toList();
        if (!ajenas.isEmpty()) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                    "Solo se configuran categorías asignadas a la temporada «" + temporada.nombre()
                            + "»; revisa las categorías indicadas.")
                    .con("categoriasInvalidas", ajenas);
        }
    }

    // ── Lectura y comparación ──────────────────────────────────────────────────────────────────

    private TemporadaEstado buscarTemporada(UUID temporadaId) {
        return parametros.buscarTemporada(ligaId, temporadaId).orElseThrow(() -> temporadaNoExiste(temporadaId));
    }

    private static RecursoNoEncontradoException temporadaNoExiste(UUID temporadaId) {
        return new RecursoNoEncontradoException("Temporada " + temporadaId);
    }

    private ParametrosDto version(UUID temporadaId, int version) {
        FilaVersion fila = parametros.buscarVersion(temporadaId, version)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Versión " + version + " de parámetros de la temporada " + temporadaId));
        return aDto(fila, parametros.categoriasDeVersion(fila.id()));
    }

    private static ValoresParametros completos(FilaVersion fila, List<FilaCategoria> categorias) {
        return fila.valores().conCategorias(categorias.stream()
                .map(c -> new ValorCategoria(c.categoria().id(), c.duracionJuegoMin(), c.bloqueCanchaMin()))
                .toList());
    }

    private static ParametrosDto aDto(FilaVersion fila, List<FilaCategoria> categorias) {
        ValoresParametros v = fila.valores();
        return new ParametrosDto(fila.id(), fila.temporadaId(), fila.version(), v.puntosVictoria(), v.puntosEmpate(),
                v.puntosDerrota(), v.criteriosDesempate(), v.golesDefaultGanador(), v.golesDefaultPerdedor(),
                v.plazoValidacionCedulaHoras(), v.duracionJuegoMin(), v.bloqueCanchaMin(), v.clasificadosLiguilla(),
                v.formatoLiguilla(), v.empateLiguillaPorPenales(), v.equiposAscienden(), v.equiposDescienden(),
                v.permiteCambioClub(),
                categorias.stream()
                        .map(c -> new ParametrosCategoriaDto(c.categoria(), c.duracionJuegoMin(), c.bloqueCanchaMin()))
                        .toList(),
                v.esDemo(), v.esDemo() ? CAMPOS_DEMO : List.of(), fila.motivo(), creadoPor(fila), fila.creadoEn());
    }

    private static ReferenciaDto creadoPor(FilaVersion fila) {
        return fila.creadoPorId() == null ? null : new ReferenciaDto(fila.creadoPorId(), fila.creadoPorNombre());
    }

    /**
     * Campos que difieren de la versión anterior, en el orden del contrato. Sin anterior (versión 1)
     * van todos; {@code categorias} solo si la versión tiene filas por categoría, porque se compara
     * el conjunto de filas y una versión sin filas no cambia nada respecto de «ninguna».
     */
    static List<String> camposCambiados(ValoresParametros anterior, ValoresParametros nuevo) {
        List<String> campos = new ArrayList<>();
        CAMPOS.forEach((campo, valor) -> {
            if (anterior == null || !Objects.equals(valor.apply(anterior), valor.apply(nuevo))) {
                campos.add(campo);
            }
        });
        Set<ValorCategoria> antes = anterior == null ? Set.of() : Set.copyOf(anterior.categorias());
        if (!antes.equals(Set.copyOf(nuevo.categorias()))) {
            campos.add(CAMPO_CATEGORIAS);
        }
        return campos;
    }

    private static Map<String, Function<ValoresParametros, Object>> campos() {
        Map<String, Function<ValoresParametros, Object>> campos = new LinkedHashMap<>();
        campos.put("puntosVictoria", ValoresParametros::puntosVictoria);
        campos.put("puntosEmpate", ValoresParametros::puntosEmpate);
        campos.put("puntosDerrota", ValoresParametros::puntosDerrota);
        campos.put("criteriosDesempate", ValoresParametros::criteriosDesempate);
        campos.put("golesDefaultGanador", ValoresParametros::golesDefaultGanador);
        campos.put("golesDefaultPerdedor", ValoresParametros::golesDefaultPerdedor);
        campos.put("plazoValidacionCedulaHoras", ValoresParametros::plazoValidacionCedulaHoras);
        campos.put("duracionJuegoMin", ValoresParametros::duracionJuegoMin);
        campos.put("bloqueCanchaMin", ValoresParametros::bloqueCanchaMin);
        campos.put("clasificadosLiguilla", ValoresParametros::clasificadosLiguilla);
        campos.put("formatoLiguilla", ValoresParametros::formatoLiguilla);
        campos.put("empateLiguillaPorPenales", ValoresParametros::empateLiguillaPorPenales);
        campos.put("equiposAscienden", ValoresParametros::equiposAscienden);
        campos.put("equiposDescienden", ValoresParametros::equiposDescienden);
        campos.put("permiteCambioClub", ValoresParametros::permiteCambioClub);
        campos.put("esDemo", ValoresParametros::esDemo);
        return Collections.unmodifiableMap(campos);
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }

    /** Acumula los errores por campo para responderlos juntos en la extensión {@code errores}. */
    private static final class Errores {

        private final List<Map<String, String>> lista = new ArrayList<>();
        private final Set<String> invalidos = new HashSet<>();

        void agregar(String campo, String mensaje) {
            lista.add(Map.of("campo", campo, "mensaje", mensaje));
            invalidos.add(campo);
        }

        /** Entero obligatorio dentro de {@code [minimo, maximo]}; 0 si falta o no es válido. */
        int entero(String campo, Integer valor, int minimo, int maximo, String nombre) {
            if (valor == null) {
                agregar(campo, "Falta " + nombre + ".");
                return 0;
            }
            if (valor < minimo || valor > maximo) {
                agregar(campo, "El valor de " + nombre + " debe estar entre " + minimo + " y " + maximo + ".");
                return 0;
            }
            return valor;
        }

        void obligatorio(String campo, Object valor, String nombre) {
            if (valor == null) {
                agregar(campo, "Falta " + nombre + ".");
            }
        }

        boolean validos(String... campos) {
            for (String campo : campos) {
                if (invalidos.contains(campo)) {
                    return false;
                }
            }
            return true;
        }

        void lanzarSiHay() {
            if (!lista.isEmpty()) {
                throw invalida(lista.getFirst().get("mensaje")).con("errores", List.copyOf(lista));
            }
        }
    }
}

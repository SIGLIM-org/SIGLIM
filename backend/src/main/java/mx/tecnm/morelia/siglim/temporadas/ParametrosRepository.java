package mx.tecnm.morelia.siglim.temporadas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.TemporadaEstado;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code parametros_temporada} y {@code parametros_categoria}. Las versiones solo se
 * insertan: el rol {@code siglim_app} no tiene {@code UPDATE} sobre la tabla y
 * {@code tg_parametros_inmutable} rechaza cambios y borrados; {@code tg_parametros_version} asigna
 * el número de versión consecutivo.
 *
 * <p><b>Candado (D9).</b> {@link #bloquearTemporada} lee la temporada con {@code FOR UPDATE}, el
 * mismo candado que activación y cierre en {@link TemporadaRepository}: dos versiones simultáneas
 * de la misma temporada se serializan y el trigger no calcula dos veces la misma versión; un cierre
 * en curso termina antes de que se evalúe el estado.
 *
 * <p>Refs: H-05.2, RF-006, RF-100, DEC-25 a DEC-29
 */
@Repository
class ParametrosRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION_VERSION = """
            SELECT p.id, p.temporada_id, p.version, p.puntos_victoria, p.puntos_empate, p.puntos_derrota,
                   array_to_string(p.criterios_desempate, ',') AS criterios_desempate,
                   p.goles_default_ganador, p.goles_default_perdedor,
                   (extract(epoch FROM p.plazo_validacion_cedula) / 3600)::int AS plazo_horas,
                   p.duracion_juego_min, p.bloque_cancha_min, p.clasificados_liguilla,
                   p.formato_liguilla::text AS formato_liguilla, p.empate_liguilla_por_penales,
                   p.equipos_ascienden, p.equipos_descienden, p.permite_cambio_club, p.es_demo,
                   p.motivo, p.creado_por, pf.nombre_mostrado AS creado_por_nombre, p.creado_en
              FROM parametros_temporada p
              LEFT JOIN perfiles pf ON pf.id = p.creado_por
            """;

    private static final String SELECCION_CATEGORIA = """
            SELECT pc.parametros_id, c.id AS categoria_id, c.nombre AS categoria_nombre,
                   pc.duracion_juego_min, pc.bloque_cancha_min
              FROM parametros_categoria pc
              JOIN parametros_temporada p ON p.id = pc.parametros_id
              JOIN categorias c ON c.id = pc.categoria_id
            """;

    private final JdbcClient jdbc;

    ParametrosRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── Temporada ───────────────────────────────────────────────────────────────────────────

    Optional<TemporadaEstado> buscarTemporada(UUID ligaId, UUID temporadaId) {
        return temporada(ligaId, temporadaId, "");
    }

    /** La temporada con candado exclusivo, como activación y cierre (D9). */
    Optional<TemporadaEstado> bloquearTemporada(UUID ligaId, UUID temporadaId) {
        return temporada(ligaId, temporadaId, " FOR UPDATE");
    }

    private Optional<TemporadaEstado> temporada(UUID ligaId, UUID temporadaId, String candado) {
        return jdbc.sql("SELECT id, nombre, estado FROM temporadas WHERE id = :id AND liga_id = :ligaId" + candado)
                .param("id", temporadaId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new TemporadaEstado(fila.getObject("id", UUID.class),
                        fila.getString("nombre"), EstadoTemporada.valueOf(fila.getString("estado"))))
                .optional();
    }

    /** Categorías del catálogo asignadas a la temporada ({@code categorias_temporada}). */
    Set<UUID> categoriasAsignadas(UUID temporadaId) {
        return new HashSet<>(jdbc.sql("SELECT categoria_id FROM categorias_temporada WHERE temporada_id = :t")
                .param("t", temporadaId)
                .query(UUID.class)
                .list());
    }

    // ── Versiones ───────────────────────────────────────────────────────────────────────────

    /** Todas las versiones de la temporada, de la menor a la mayor, sin sus filas por categoría. */
    List<FilaVersion> listarVersiones(UUID temporadaId) {
        return jdbc.sql(SELECCION_VERSION + " WHERE p.temporada_id = :t ORDER BY p.version")
                .param("t", temporadaId)
                .query(ParametrosRepository::aFilaVersion)
                .list();
    }

    Optional<FilaVersion> buscarVersion(UUID temporadaId, int version) {
        return jdbc.sql(SELECCION_VERSION + " WHERE p.temporada_id = :t AND p.version = :version")
                .param("t", temporadaId)
                .param("version", version)
                .query(ParametrosRepository::aFilaVersion)
                .optional();
    }

    /** La versión mayor, igual que {@code parametros_vigentes()}. */
    Optional<FilaVersion> buscarVigente(UUID temporadaId) {
        return jdbc.sql(SELECCION_VERSION + " WHERE p.temporada_id = :t ORDER BY p.version DESC LIMIT 1")
                .param("t", temporadaId)
                .query(ParametrosRepository::aFilaVersion)
                .optional();
    }

    /** Filas por categoría de una versión, por el orden del catálogo. */
    List<FilaCategoria> categoriasDeVersion(UUID parametrosId) {
        return jdbc.sql(SELECCION_CATEGORIA + " WHERE pc.parametros_id = :id ORDER BY c.orden, c.nombre, c.id")
                .param("id", parametrosId)
                .query(ParametrosRepository::aFilaCategoria)
                .list();
    }

    /** Filas por categoría de todas las versiones de la temporada, en una sola consulta (§9.1). */
    List<FilaCategoria> categoriasDeTemporada(UUID temporadaId) {
        return jdbc.sql(SELECCION_CATEGORIA + " WHERE p.temporada_id = :t ORDER BY c.orden, c.nombre, c.id")
                .param("t", temporadaId)
                .query(ParametrosRepository::aFilaCategoria)
                .list();
    }

    /**
     * Inserta la versión; el número lo pone {@code tg_parametros_version}. Los criterios viajan como
     * literal de arreglo armado con los nombres del enum, nunca con texto del usuario.
     */
    VersionCreada insertar(UUID temporadaId, ValoresParametros valores, String motivo, UUID creadoPor) {
        String criterios = valores.criteriosDesempate().stream()
                .map(Enum::name)
                .collect(Collectors.joining(",", "{", "}"));
        return jdbc.sql("""
                        INSERT INTO parametros_temporada (
                               temporada_id, puntos_victoria, puntos_empate, puntos_derrota, criterios_desempate,
                               goles_default_ganador, goles_default_perdedor, plazo_validacion_cedula,
                               duracion_juego_min, bloque_cancha_min, clasificados_liguilla, formato_liguilla,
                               empate_liguilla_por_penales, equipos_ascienden, equipos_descienden,
                               permite_cambio_club, es_demo, motivo, creado_por)
                        VALUES (:temporadaId, :puntosVictoria, :puntosEmpate, :puntosDerrota,
                                CAST(:criterios AS criterio_desempate[]),
                                :golesGanador, :golesPerdedor, make_interval(hours => :plazoHoras),
                                :duracion, :bloque, :clasificados, CAST(:formato AS formato_eliminatoria),
                                :penales, :ascienden, :descienden, :cambioClub, :esDemo, :motivo, :creadoPor)
                        RETURNING id, version
                        """)
                .param("temporadaId", temporadaId)
                .param("puntosVictoria", valores.puntosVictoria())
                .param("puntosEmpate", valores.puntosEmpate())
                .param("puntosDerrota", valores.puntosDerrota())
                .param("criterios", criterios)
                .param("golesGanador", valores.golesDefaultGanador())
                .param("golesPerdedor", valores.golesDefaultPerdedor())
                .param("plazoHoras", valores.plazoValidacionCedulaHoras())
                .param("duracion", valores.duracionJuegoMin())
                .param("bloque", valores.bloqueCanchaMin())
                .param("clasificados", valores.clasificadosLiguilla())
                .param("formato", valores.formatoLiguilla().name())
                .param("penales", valores.empateLiguillaPorPenales())
                .param("ascienden", valores.equiposAscienden())
                .param("descienden", valores.equiposDescienden())
                .param("cambioClub", valores.permiteCambioClub())
                .param("esDemo", valores.esDemo())
                .param("motivo", motivo)
                .param("creadoPor", creadoPor)
                .query((fila, numero) -> new VersionCreada(fila.getObject("id", UUID.class), fila.getInt("version")))
                .single();
    }

    void insertarCategoria(UUID parametrosId, ValoresParametros.ValorCategoria categoria) {
        jdbc.sql("""
                        INSERT INTO parametros_categoria (parametros_id, categoria_id, duracion_juego_min, bloque_cancha_min)
                        VALUES (:parametrosId, :categoriaId, :duracion, :bloque)
                        """)
                .param("parametrosId", parametrosId)
                .param("categoriaId", categoria.categoriaId())
                .param("duracion", categoria.duracionJuegoMin())
                .param("bloque", categoria.bloqueCanchaMin())
                .update();
    }

    // ── Filas ───────────────────────────────────────────────────────────────────────────────

    /** Fila de {@code parametros_temporada}; {@code valores} llega sin filas por categoría. */
    record FilaVersion(UUID id, UUID temporadaId, int version, ValoresParametros valores, String motivo,
                       UUID creadoPorId, String creadoPorNombre, OffsetDateTime creadoEn) {
    }

    /** Fila de {@code parametros_categoria} con el nombre de la categoría. */
    record FilaCategoria(UUID parametrosId, ReferenciaDto categoria, int duracionJuegoMin, int bloqueCanchaMin) {
    }

    /** Identificador y número de la versión recién insertada. */
    record VersionCreada(UUID id, int version) {
    }

    private static FilaVersion aFilaVersion(ResultSet fila, int numero) throws SQLException {
        List<CriterioDesempate> criterios = Arrays.stream(fila.getString("criterios_desempate").split(","))
                .map(CriterioDesempate::valueOf)
                .toList();
        ValoresParametros valores = new ValoresParametros(
                fila.getInt("puntos_victoria"),
                fila.getInt("puntos_empate"),
                fila.getInt("puntos_derrota"),
                criterios,
                fila.getInt("goles_default_ganador"),
                fila.getInt("goles_default_perdedor"),
                fila.getInt("plazo_horas"),
                fila.getInt("duracion_juego_min"),
                fila.getInt("bloque_cancha_min"),
                fila.getInt("clasificados_liguilla"),
                FormatoLiguilla.valueOf(fila.getString("formato_liguilla")),
                fila.getBoolean("empate_liguilla_por_penales"),
                fila.getInt("equipos_ascienden"),
                fila.getInt("equipos_descienden"),
                fila.getBoolean("permite_cambio_club"),
                fila.getBoolean("es_demo"),
                List.of());
        return new FilaVersion(
                fila.getObject("id", UUID.class),
                fila.getObject("temporada_id", UUID.class),
                fila.getInt("version"),
                valores,
                fila.getString("motivo"),
                fila.getObject("creado_por", UUID.class),
                fila.getString("creado_por_nombre"),
                fila.getObject("creado_en", OffsetDateTime.class).atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime());
    }

    private static FilaCategoria aFilaCategoria(ResultSet fila, int numero) throws SQLException {
        return new FilaCategoria(
                fila.getObject("parametros_id", UUID.class),
                new ReferenciaDto(fila.getObject("categoria_id", UUID.class), fila.getString("categoria_nombre")),
                fila.getInt("duracion_juego_min"),
                fila.getInt("bloque_cancha_min"));
    }
}

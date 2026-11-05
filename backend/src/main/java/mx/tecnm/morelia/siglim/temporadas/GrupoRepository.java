package mx.tecnm.morelia.siglim.temporadas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.temporadas.CategoriaTemporadaDto.CategoriaResumenDto;
import mx.tecnm.morelia.siglim.temporadas.CategoriaTemporadaDto.GrupoResumenDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code categorias_temporada} y {@code grupos}. Los grupos se leen con el conteo de sus
 * equipos en una subconsulta, para no hacer una petición por fila (convenciones de API §9.1).
 *
 * <p><b>Candados.</b> Las escrituras leen la temporada con {@code FOR SHARE}: la activación y el
 * cierre la leen con {@code FOR UPDATE}, así que una asignación o un grupo nuevo no se cruza con un
 * cambio de estado de la temporada (RF-004, RF-005).
 *
 * <p>Refs: H-05.1, RF-004, RF-005, ADR-0011
 */
@Repository
class GrupoRepository {

    private static final String CONTEXTO_CATEGORIA_TEMPORADA = """
            SELECT ct.id, ct.temporada_id, t.nombre AS temporada_nombre, t.estado AS temporada_estado,
                   c.id AS categoria_id, c.nombre AS categoria_nombre
              FROM categorias_temporada ct
              JOIN temporadas t ON t.id = ct.temporada_id
              JOIN categorias c ON c.id = ct.categoria_id
             WHERE ct.id = :id AND t.liga_id = :ligaId
            """;

    private static final String SELECCION_GRUPO = """
            SELECT g.id, g.nombre, g.categoria_temporada_id, ct.temporada_id,
                   c.id AS categoria_id, c.nombre AS categoria_nombre,
                   t.nombre AS temporada_nombre, t.estado AS temporada_estado,
                   (SELECT count(*) FROM equipos e WHERE e.grupo_id = g.id) AS equipos
              FROM grupos g
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN categorias c ON c.id = ct.categoria_id
              JOIN temporadas t ON t.id = ct.temporada_id
             WHERE g.id = :id AND t.liga_id = :ligaId
            """;

    private final JdbcClient jdbc;

    GrupoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── Temporada ───────────────────────────────────────────────────────────────────────────

    Optional<TemporadaEstado> buscarTemporada(UUID ligaId, UUID temporadaId) {
        return temporada(ligaId, temporadaId, "");
    }

    /** La temporada con candado compartido: espera a una activación o un cierre en curso. */
    Optional<TemporadaEstado> bloquearTemporada(UUID ligaId, UUID temporadaId) {
        return temporada(ligaId, temporadaId, " FOR SHARE");
    }

    private Optional<TemporadaEstado> temporada(UUID ligaId, UUID temporadaId, String candado) {
        return jdbc.sql("SELECT id, nombre, estado FROM temporadas WHERE id = :id AND liga_id = :ligaId" + candado)
                .param("id", temporadaId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new TemporadaEstado(fila.getObject("id", UUID.class),
                        fila.getString("nombre"), EstadoTemporada.valueOf(fila.getString("estado"))))
                .optional();
    }

    // ── Categorías de la temporada ──────────────────────────────────────────────────────────

    /**
     * Categorías de la temporada por el orden del catálogo, sin grupos; con
     * {@code categoriaTemporadaId}, solo esa.
     */
    List<CategoriaAsignada> listarCategorias(UUID temporadaId, UUID categoriaTemporadaId) {
        return jdbc.sql("""
                        SELECT ct.id, c.id AS categoria_id, c.nombre, c.tipo, c.orden,
                               s.id AS superior_id, s.nombre AS superior_nombre
                          FROM categorias_temporada ct
                          JOIN categorias c ON c.id = ct.categoria_id
                          LEFT JOIN categorias s ON s.id = c.categoria_superior_id
                         WHERE ct.temporada_id = :temporadaId
                           AND (CAST(:ct AS uuid) IS NULL OR ct.id = CAST(:ct AS uuid))
                         ORDER BY c.orden, c.nombre, ct.id
                        """)
                .param("temporadaId", temporadaId)
                .param("ct", categoriaTemporadaId)
                .query(GrupoRepository::aCategoriaAsignada)
                .list();
    }

    /** Grupos de las categorías de la temporada, por nombre, con su conteo de equipos. */
    List<GrupoDeCategoria> listarGruposDeTemporada(UUID temporadaId, UUID categoriaTemporadaId) {
        return jdbc.sql("""
                        SELECT g.id, g.nombre, g.categoria_temporada_id,
                               (SELECT count(*) FROM equipos e WHERE e.grupo_id = g.id) AS equipos
                          FROM grupos g
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                         WHERE ct.temporada_id = :temporadaId
                           AND (CAST(:ct AS uuid) IS NULL OR ct.id = CAST(:ct AS uuid))
                         ORDER BY g.nombre, g.id
                        """)
                .param("temporadaId", temporadaId)
                .param("ct", categoriaTemporadaId)
                .query((fila, numero) -> new GrupoDeCategoria(
                        fila.getObject("categoria_temporada_id", UUID.class),
                        new GrupoResumenDto(fila.getObject("id", UUID.class), fila.getString("nombre"),
                                fila.getInt("equipos"))))
                .list();
    }

    boolean categoriaAsignada(UUID temporadaId, UUID categoriaId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM categorias_temporada
                                        WHERE temporada_id = :temporadaId AND categoria_id = :categoriaId)
                        """)
                .param("temporadaId", temporadaId)
                .param("categoriaId", categoriaId)
                .query(Boolean.class)
                .single();
    }

    UUID asignarCategoria(UUID temporadaId, UUID categoriaId) {
        return jdbc.sql("""
                        INSERT INTO categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporadaId, :categoriaId)
                        RETURNING id
                        """)
                .param("temporadaId", temporadaId)
                .param("categoriaId", categoriaId)
                .query(UUID.class)
                .single();
    }

    /** La categoría de la temporada con su temporada y categoría, de la liga. */
    Optional<ContextoCategoriaTemporada> buscarCategoriaTemporada(UUID ligaId, UUID id) {
        return contexto(CONTEXTO_CATEGORIA_TEMPORADA, ligaId, id);
    }

    /** Como {@link #buscarCategoriaTemporada}, con candado compartido sobre la temporada. */
    Optional<ContextoCategoriaTemporada> bloquearCategoriaTemporada(UUID ligaId, UUID id) {
        return contexto(CONTEXTO_CATEGORIA_TEMPORADA + " FOR SHARE OF t", ligaId, id);
    }

    private Optional<ContextoCategoriaTemporada> contexto(String sql, UUID ligaId, UUID id) {
        return jdbc.sql(sql)
                .param("id", id)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new ContextoCategoriaTemporada(
                        fila.getObject("id", UUID.class),
                        new TemporadaEstado(fila.getObject("temporada_id", UUID.class),
                                fila.getString("temporada_nombre"),
                                EstadoTemporada.valueOf(fila.getString("temporada_estado"))),
                        new ReferenciaDto(fila.getObject("categoria_id", UUID.class),
                                fila.getString("categoria_nombre"))))
                .optional();
    }

    // ── Grupos ──────────────────────────────────────────────────────────────────────────────

    /** Grupos de la categoría de la temporada por nombre, con la forma corta del listado. */
    List<GrupoDto> listarGrupos(UUID categoriaTemporadaId) {
        return jdbc.sql("""
                        SELECT g.id, g.nombre, g.categoria_temporada_id,
                               (SELECT count(*) FROM equipos e WHERE e.grupo_id = g.id) AS equipos
                          FROM grupos g
                         WHERE g.categoria_temporada_id = :ct
                         ORDER BY g.nombre, g.id
                        """)
                .param("ct", categoriaTemporadaId)
                .query((fila, numero) -> new GrupoDto(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getObject("categoria_temporada_id", UUID.class),
                        null,
                        null,
                        fila.getInt("equipos")))
                .list();
    }

    Optional<GrupoTemporada> buscarGrupo(UUID ligaId, UUID id) {
        return grupo(SELECCION_GRUPO, ligaId, id);
    }

    /** Lee el grupo con candado y la temporada con candado compartido (RF-003, RF-005). */
    Optional<GrupoTemporada> buscarGrupoParaActualizar(UUID ligaId, UUID id) {
        return grupo(SELECCION_GRUPO + " FOR UPDATE OF g FOR SHARE OF t", ligaId, id);
    }

    private Optional<GrupoTemporada> grupo(String sql, UUID ligaId, UUID id) {
        return jdbc.sql(sql)
                .param("id", id)
                .param("ligaId", ligaId)
                .query((fila, numero) -> {
                    UUID temporadaId = fila.getObject("temporada_id", UUID.class);
                    return new GrupoTemporada(
                            new GrupoDto(
                                    fila.getObject("id", UUID.class),
                                    fila.getString("nombre"),
                                    fila.getObject("categoria_temporada_id", UUID.class),
                                    temporadaId,
                                    new ReferenciaDto(fila.getObject("categoria_id", UUID.class),
                                            fila.getString("categoria_nombre")),
                                    fila.getInt("equipos")),
                            new TemporadaEstado(temporadaId, fila.getString("temporada_nombre"),
                                    EstadoTemporada.valueOf(fila.getString("temporada_estado"))));
                })
                .optional();
    }

    boolean existeNombreGrupo(UUID categoriaTemporadaId, String nombre, UUID excepto) {
        return jdbc.sql("""
                        SELECT EXISTS (
                                 SELECT 1 FROM grupos
                                  WHERE categoria_temporada_id = :ct AND nombre = :nombre
                                    AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid)))
                        """)
                .param("ct", categoriaTemporadaId)
                .param("nombre", nombre)
                .param("excepto", excepto)
                .query(Boolean.class)
                .single();
    }

    UUID insertarGrupo(UUID categoriaTemporadaId, String nombre) {
        return jdbc.sql("INSERT INTO grupos (categoria_temporada_id, nombre) VALUES (:ct, :nombre) RETURNING id")
                .param("ct", categoriaTemporadaId)
                .param("nombre", nombre)
                .query(UUID.class)
                .single();
    }

    void renombrarGrupo(UUID id, String nombre) {
        jdbc.sql("UPDATE grupos SET nombre = :nombre WHERE id = :id")
                .param("id", id)
                .param("nombre", nombre)
                .update();
    }

    // ── Filas ───────────────────────────────────────────────────────────────────────────────

    /** Temporada con lo necesario para validar su estado y nombrarla en el error. */
    record TemporadaEstado(UUID id, String nombre, EstadoTemporada estado) {
    }

    /** Fila de {@code categorias_temporada} con su categoría del catálogo, sin grupos. */
    record CategoriaAsignada(UUID id, CategoriaResumenDto categoria) {
    }

    /** Grupo de una categoría de la temporada, para anidarlo en ella. */
    record GrupoDeCategoria(UUID categoriaTemporadaId, GrupoResumenDto grupo) {
    }

    /** Categoría de la temporada con la temporada y la categoría, para validar y auditar. */
    record ContextoCategoriaTemporada(UUID id, TemporadaEstado temporada, ReferenciaDto categoria) {
    }

    /** Detalle del grupo con el estado de su temporada (RF-003, RF-005). */
    record GrupoTemporada(GrupoDto grupo, TemporadaEstado temporada) {
    }

    private static CategoriaAsignada aCategoriaAsignada(ResultSet fila, int numero) throws SQLException {
        UUID superiorId = fila.getObject("superior_id", UUID.class);
        return new CategoriaAsignada(
                fila.getObject("id", UUID.class),
                new CategoriaResumenDto(
                        fila.getObject("categoria_id", UUID.class),
                        fila.getString("nombre"),
                        TipoCategoria.valueOf(fila.getString("tipo")),
                        fila.getInt("orden"),
                        superiorId == null ? null
                                : new ReferenciaDto(superiorId, fila.getString("superior_nombre"))));
    }
}

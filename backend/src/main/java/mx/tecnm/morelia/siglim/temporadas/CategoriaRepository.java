package mx.tecnm.morelia.siglim.temporadas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL del catálogo {@code categorias}. La superior se lee con {@code LEFT JOIN} para devolverla
 * anidada (convenciones de API §9.1).
 *
 * <p><b>Candado del catálogo.</b> {@link #bloquearCatalogo} toma {@code FOR UPDATE} de todas las
 * categorías de la liga, en orden de id para no interbloquearse: así dos {@code PATCH} concurrentes
 * no forman un ciclo en la jerarquía (RF-099) ni dos reordenamientos se mezclan.
 *
 * <p><b>Reordenamiento.</b> El índice {@code ux_categorias_orden} es único sobre
 * {@code (liga_id, orden)} de las activas y PostgreSQL lo verifica fila por fila; por eso
 * {@link #reordenar} escribe primero órdenes negativos y después los invierte, en la misma
 * transacción.
 *
 * <p>Refs: H-05.1, RF-004, RF-099, RF-110, ADR-0011
 */
@Repository
class CategoriaRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT c.id, c.nombre, c.tipo, c.orden, c.activa, c.creado_en,
                   s.id AS superior_id, s.nombre AS superior_nombre
              FROM categorias c
              LEFT JOIN categorias s ON s.id = c.categoria_superior_id
            """;

    private final JdbcClient jdbc;

    CategoriaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Catálogo de la liga por orden y nombre; {@code activa} nulo no filtra. */
    List<CategoriaDto> listar(UUID ligaId, Boolean activa) {
        return jdbc.sql(SELECCION + """
                         WHERE c.liga_id = :ligaId
                           AND (CAST(:activa AS boolean) IS NULL OR c.activa = CAST(:activa AS boolean))
                         ORDER BY c.orden, c.nombre, c.id
                        """)
                .param("ligaId", ligaId)
                .param("activa", activa)
                .query(CategoriaRepository::aDto)
                .list();
    }

    Optional<CategoriaDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE c.id = :id AND c.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(CategoriaRepository::aDto)
                .optional();
    }

    /** Todas las categorías de la liga con candado hasta el fin de la transacción (RF-099). */
    List<FilaCategoria> bloquearCatalogo(UUID ligaId) {
        return jdbc.sql("""
                        SELECT id, nombre, orden, categoria_superior_id, activa
                          FROM categorias
                         WHERE liga_id = :ligaId
                         ORDER BY id
                           FOR UPDATE
                        """)
                .param("ligaId", ligaId)
                .query(CategoriaRepository::aFila)
                .list();
    }

    /**
     * Lee la categoría con candado compartido para asignarla a una temporada: una desactivación
     * concurrente espera y después ve la asignación (RF-004, RF-099).
     */
    Optional<FilaCategoria> buscarParaAsignar(UUID ligaId, UUID id) {
        return jdbc.sql("""
                        SELECT id, nombre, orden, categoria_superior_id, activa
                          FROM categorias
                         WHERE id = :id AND liga_id = :ligaId
                           FOR SHARE
                        """)
                .param("id", id)
                .param("ligaId", ligaId)
                .query(CategoriaRepository::aFila)
                .optional();
    }

    /** Nombres de las temporadas, en esos estados, en que participa la categoría. */
    List<String> temporadasConCategoria(UUID categoriaId, Collection<EstadoTemporada> estados) {
        return jdbc.sql("""
                        SELECT t.nombre
                          FROM categorias_temporada ct
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE ct.categoria_id = :categoriaId
                           AND CAST(t.estado AS text) = ANY(:estados)
                         ORDER BY t.fecha_inicio, t.nombre
                        """)
                .param("categoriaId", categoriaId)
                .param("estados", new SqlArrayValue("text",
                        estados.stream().map(EstadoTemporada::name).toArray()))
                .query(String.class)
                .list();
    }

    boolean existeNombre(UUID ligaId, String nombre, UUID excepto) {
        return jdbc.sql("""
                        SELECT EXISTS (
                                 SELECT 1 FROM categorias
                                  WHERE liga_id = :ligaId AND nombre = :nombre
                                    AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid)))
                        """)
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .param("excepto", excepto)
                .query(Boolean.class)
                .single();
    }

    /** Inserta la categoría activa al final del catálogo: orden = máximo actual + 1. */
    UUID insertar(UUID ligaId, String nombre, TipoCategoria tipo, UUID categoriaSuperiorId) {
        return jdbc.sql("""
                        INSERT INTO categorias (liga_id, nombre, tipo, orden, categoria_superior_id)
                        SELECT :ligaId, :nombre, CAST(:tipo AS tipo_categoria),
                               coalesce(max(orden), 0) + 1, :superiorId
                          FROM categorias
                         WHERE liga_id = :ligaId
                        RETURNING id
                        """)
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .param("tipo", tipo.name())
                .param("superiorId", categoriaSuperiorId)
                .query(UUID.class)
                .single();
    }

    void actualizar(UUID id, String nombre, TipoCategoria tipo, UUID categoriaSuperiorId, boolean activa) {
        jdbc.sql("""
                        UPDATE categorias
                           SET nombre = :nombre,
                               tipo = CAST(:tipo AS tipo_categoria),
                               categoria_superior_id = :superiorId,
                               activa = :activa
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("nombre", nombre)
                .param("tipo", tipo.name())
                .param("superiorId", categoriaSuperiorId)
                .param("activa", activa)
                .update();
    }

    /** Asigna el orden 1..n en el orden de {@code ids}, en dos pasos por el índice único. */
    void reordenar(UUID ligaId, List<UUID> ids) {
        jdbc.sql("""
                        UPDATE categorias c
                           SET orden = -CAST(n.posicion AS smallint)
                          FROM unnest(:ids) WITH ORDINALITY AS n(id, posicion)
                         WHERE c.id = n.id AND c.liga_id = :ligaId
                        """)
                .param("ids", new SqlArrayValue("uuid", ids.toArray()))
                .param("ligaId", ligaId)
                .update();
        jdbc.sql("UPDATE categorias SET orden = -orden WHERE liga_id = :ligaId AND orden < 0")
                .param("ligaId", ligaId)
                .update();
    }

    /** Lo necesario para validar jerarquía, nombre y orden sin otra consulta por fila. */
    record FilaCategoria(UUID id, String nombre, int orden, UUID categoriaSuperiorId, boolean activa) {
    }

    private static FilaCategoria aFila(ResultSet fila, int numero) throws SQLException {
        return new FilaCategoria(
                fila.getObject("id", UUID.class),
                fila.getString("nombre"),
                fila.getInt("orden"),
                fila.getObject("categoria_superior_id", UUID.class),
                fila.getBoolean("activa"));
    }

    private static CategoriaDto aDto(ResultSet fila, int numero) throws SQLException {
        UUID superiorId = fila.getObject("superior_id", UUID.class);
        return new CategoriaDto(
                fila.getObject("id", UUID.class),
                fila.getString("nombre"),
                TipoCategoria.valueOf(fila.getString("tipo")),
                fila.getInt("orden"),
                superiorId == null ? null : new ReferenciaDto(superiorId, fila.getString("superior_nombre")),
                fila.getBoolean("activa"),
                fila.getObject("creado_en", OffsetDateTime.class)
                        .atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime());
    }
}

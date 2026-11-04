package mx.tecnm.morelia.siglim.disciplina;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code tipos_incidencia}: un catálogo pequeño de la liga, sin paginación (convenciones de
 * API §4). No tiene creado por ni fechas; su trazabilidad queda en la auditoría (RF-093).
 *
 * <p>Refs: H-26.1, RF-128, ADR-0011
 */
@Repository
class TiposIncidenciaRepository {

    private static final String SELECCION = """
            SELECT id, codigo, nombre, abre_expediente, activo, es_demo
              FROM tipos_incidencia
            """;

    private final JdbcClient jdbc;

    TiposIncidenciaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<TipoIncidenciaDto> listar(UUID ligaId, Boolean activo) {
        return jdbc.sql(SELECCION + """
                         WHERE liga_id = :ligaId
                           AND (CAST(:activo AS boolean) IS NULL OR activo = CAST(:activo AS boolean))
                         ORDER BY codigo
                        """)
                .param("ligaId", ligaId)
                .param("activo", activo)
                .query(TiposIncidenciaRepository::aDto)
                .list();
    }

    Optional<TipoIncidenciaDto> buscar(UUID ligaId, long id) {
        return jdbc.sql(SELECCION + " WHERE id = :id AND liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(TiposIncidenciaRepository::aDto)
                .optional();
    }

    /** Lee el tipo y toma su candado: serializa las escrituras sobre él hasta el fin de la transacción. */
    Optional<TipoIncidenciaDto> buscarParaActualizar(UUID ligaId, long id) {
        return jdbc.sql(SELECCION + " WHERE id = :id AND liga_id = :ligaId FOR UPDATE")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(TiposIncidenciaRepository::aDto)
                .optional();
    }

    boolean existeCodigo(UUID ligaId, String codigo, Long excepto) {
        return jdbc.sql("""
                        SELECT EXISTS (
                                 SELECT 1 FROM tipos_incidencia
                                  WHERE liga_id = :ligaId AND codigo = :codigo
                                    AND (CAST(:excepto AS bigint) IS NULL OR id <> :excepto))
                        """)
                .param("ligaId", ligaId)
                .param("codigo", codigo)
                .param("excepto", excepto)
                .query(Boolean.class)
                .single();
    }

    long insertar(UUID ligaId, String codigo, String nombre, boolean abreExpediente) {
        return jdbc.sql("""
                        INSERT INTO tipos_incidencia (liga_id, codigo, nombre, abre_expediente)
                        VALUES (:ligaId, :codigo, :nombre, :abreExpediente)
                        RETURNING id
                        """)
                .param("ligaId", ligaId)
                .param("codigo", codigo)
                .param("nombre", nombre)
                .param("abreExpediente", abreExpediente)
                .query(Long.class)
                .single();
    }

    void actualizar(long id, String codigo, String nombre, boolean abreExpediente, boolean activo) {
        jdbc.sql("""
                        UPDATE tipos_incidencia
                           SET codigo = :codigo,
                               nombre = :nombre,
                               abre_expediente = :abreExpediente,
                               activo = :activo
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("codigo", codigo)
                .param("nombre", nombre)
                .param("abreExpediente", abreExpediente)
                .param("activo", activo)
                .update();
    }

    private static TipoIncidenciaDto aDto(ResultSet rs, int fila) throws SQLException {
        return new TipoIncidenciaDto(rs.getLong("id"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getBoolean("abre_expediente"), rs.getBoolean("activo"), rs.getBoolean("es_demo"));
    }
}

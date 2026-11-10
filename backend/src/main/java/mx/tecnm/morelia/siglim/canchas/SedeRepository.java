package mx.tecnm.morelia.siglim.canchas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code sedes}: un catálogo pequeño de la liga, sin paginación (convenciones de API §4).
 * Toda consulta filtra por {@code liga_id} (ADR-0011).
 *
 * <p>Refs: B-11.1, RF-028, ADR-0011
 */
@Repository
class SedeRepository {

    private static final String SELECCION = "SELECT id, nombre, direccion FROM sedes";

    private final JdbcClient jdbc;

    SedeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Sedes de la liga por nombre; el id da un orden estable (D5). */
    List<SedeDto> listar(UUID ligaId) {
        return jdbc.sql(SELECCION + " WHERE liga_id = :ligaId ORDER BY nombre ASC, id ASC")
                .param("ligaId", ligaId)
                .query(SedeRepository::aDto)
                .list();
    }

    Optional<SedeDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE id = :id AND liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(SedeRepository::aDto)
                .optional();
    }

    /** Lee la sede y toma su candado: serializa las escrituras sobre ella hasta el fin de la transacción. */
    Optional<SedeDto> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE id = :id AND liga_id = :ligaId FOR UPDATE")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(SedeRepository::aDto)
                .optional();
    }

    boolean existeNombre(UUID ligaId, String nombre, UUID excepto) {
        return jdbc.sql("""
                        SELECT EXISTS (
                                 SELECT 1 FROM sedes
                                  WHERE liga_id = :ligaId AND nombre = :nombre
                                    AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid)))
                        """)
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .param("excepto", excepto)
                .query(Boolean.class)
                .single();
    }

    UUID insertar(UUID ligaId, String nombre, String direccion) {
        return jdbc.sql("""
                        INSERT INTO sedes (liga_id, nombre, direccion)
                        VALUES (:ligaId, :nombre, :direccion)
                        RETURNING id
                        """)
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .param("direccion", direccion)
                .query(UUID.class)
                .single();
    }

    void actualizar(UUID id, String nombre, String direccion) {
        jdbc.sql("UPDATE sedes SET nombre = :nombre, direccion = :direccion WHERE id = :id")
                .param("id", id)
                .param("nombre", nombre)
                .param("direccion", direccion)
                .update();
    }

    private static SedeDto aDto(ResultSet fila, int numero) throws SQLException {
        return new SedeDto(fila.getObject("id", UUID.class), fila.getString("nombre"), fila.getString("direccion"));
    }
}

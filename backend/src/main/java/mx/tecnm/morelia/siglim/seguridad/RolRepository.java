package mx.tecnm.morelia.siglim.seguridad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL del catálogo de roles de la liga (RF-094, RF-095).
 *
 * <p>Corre dentro de la transacción del servicio, con {@code siglim_app} y el {@code search_path}
 * que fija {@code GestorTransaccionesSiglim}. La liga se filtra siempre: un rol de otra liga no
 * existe para esta instalación (ADR-0011).
 *
 * <p>Refs: H-03.1, RF-094, RF-095, ADR-0011
 */
@Repository
class RolRepository {

    /** Rol tal como sale de la base; el servicio lo convierte al DTO. */
    record FilaRol(UUID id, String codigo, String nombre, String descripcion,
                   String ambitoPredeterminado, boolean asignable) {
    }

    private final JdbcClient jdbc;

    RolRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<FilaRol> buscar(UUID ligaId, UUID id) {
        return jdbc.sql("""
                        SELECT r.id, r.codigo, r.nombre, r.descripcion,
                               CAST(r.ambito_predeterminado AS text) AS ambito_predeterminado,
                               r.asignable
                          FROM roles r
                         WHERE r.id = :id AND r.liga_id = :ligaId
                        """)
                .param("id", id)
                .param("ligaId", ligaId)
                .query(FilaRol.class)
                .optional();
    }

    /** Todos los roles de la liga, en el orden en que se muestran en la pantalla de usuarios. */
    List<FilaRol> listar(UUID ligaId) {
        return jdbc.sql("""
                        SELECT r.id, r.codigo, r.nombre, r.descripcion,
                               CAST(r.ambito_predeterminado AS text) AS ambito_predeterminado,
                               r.asignable
                          FROM roles r
                         WHERE r.liga_id = :ligaId
                         ORDER BY r.nombre, r.codigo
                        """)
                .param("ligaId", ligaId)
                .query(FilaRol.class)
                .list();
    }
}

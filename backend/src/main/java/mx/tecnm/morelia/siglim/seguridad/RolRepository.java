package mx.tecnm.morelia.siglim.seguridad;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL del catálogo de roles, del catálogo de permisos y de la matriz {@code rol_permisos}
 * (RF-094, RF-095, RF-096).
 *
 * <p>Corre dentro de la transacción del servicio, con {@code siglim_app} y el {@code search_path}
 * que fija {@code GestorTransaccionesSiglim}. La liga se filtra siempre: un rol de otra liga no
 * existe para esta instalación (ADR-0011).
 *
 * <p>Los permisos se manejan por su identificador numérico dentro del módulo y por su código en la
 * API (convenciones de API §1). El catálogo y los pares incompatibles son pequeños: no se paginan.
 *
 * <p>Refs: H-03.1, H-20.1, RF-094, RF-095, RF-096, ADR-0007, ADR-0011
 */
@Repository
class RolRepository {

    /** Rol tal como sale de la base; el servicio lo convierte al DTO. */
    record FilaRol(UUID id, String codigo, String nombre, String descripcion,
                   String ambitoPredeterminado, boolean asignable) {
    }

    /** Permiso con su identificador interno, que solo se usa para escribir {@code rol_permisos}. */
    record FilaPermiso(long id, String codigo, String modulo, String descripcion) {
    }

    /** Par incompatible con los códigos ya resueltos para la respuesta. */
    record FilaParIncompatible(String permisoA, String permisoB, String motivo) {
    }

    private static final String SELECCION_ROL = """
            SELECT r.id, r.codigo, r.nombre, r.descripcion,
                   CAST(r.ambito_predeterminado AS text) AS ambito_predeterminado,
                   r.asignable
              FROM roles r
            """;

    private static final String SELECCION_PERMISO = """
            SELECT p.id, p.codigo, trim(p.modulo) AS modulo, p.descripcion
              FROM permisos p
            """;

    private final JdbcClient jdbc;

    RolRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<FilaRol> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION_ROL + " WHERE r.id = :id AND r.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(FilaRol.class)
                .optional();
    }

    /** Toma el candado del rol para serializar dos cambios de su matriz de permisos (RF-095). */
    Optional<FilaRol> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION_ROL + " WHERE r.id = :id AND r.liga_id = :ligaId FOR UPDATE")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(FilaRol.class)
                .optional();
    }

    /** Todos los roles de la liga, en el orden en que se muestran en la pantalla de usuarios. */
    List<FilaRol> listar(UUID ligaId) {
        return jdbc.sql(SELECCION_ROL + " WHERE r.liga_id = :ligaId ORDER BY r.nombre, r.codigo")
                .param("ligaId", ligaId)
                .query(FilaRol.class)
                .list();
    }

    /** Catálogo completo, agrupable por módulo en F-13 (RF-095). */
    List<FilaPermiso> listarPermisos() {
        return jdbc.sql(SELECCION_PERMISO + " ORDER BY p.modulo, p.codigo")
                .query(FilaPermiso.class)
                .list();
    }

    /** Permisos existentes para los códigos indicados; un código desconocido no aparece. */
    List<FilaPermiso> listarPermisosPorCodigos(Collection<String> codigos) {
        if (codigos.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECCION_PERMISO + " WHERE p.codigo = ANY(:codigos)")
                .param("codigos", new SqlArrayValue("text", codigos.toArray()))
                .query(FilaPermiso.class)
                .list();
    }

    /** Permisos efectivos del rol, ordenados por módulo y código. */
    List<FilaPermiso> listarPermisosDeRol(UUID rolId) {
        return jdbc.sql(SELECCION_PERMISO + """
                         JOIN rol_permisos rp ON rp.permiso_id = p.id
                        WHERE rp.rol_id = :rolId
                        ORDER BY p.modulo, p.codigo
                        """)
                .param("rolId", rolId)
                .query(FilaPermiso.class)
                .list();
    }

    /** Pares declarados incompatibles, con los códigos resueltos (RF-096, RN-053). */
    List<FilaParIncompatible> listarParesIncompatibles() {
        return jdbc.sql("""
                        SELECT a.codigo AS permiso_a,
                               b.codigo AS permiso_b,
                               pi.motivo
                          FROM pares_incompatibles pi
                          JOIN permisos a ON a.id = pi.permiso_a_id
                          JOIN permisos b ON b.id = pi.permiso_b_id
                         ORDER BY a.codigo, b.codigo
                        """)
                .query(FilaParIncompatible.class)
                .list();
    }

    /**
     * Verdadero si la cuenta tiene una asignación activa del rol, con cualquier ámbito. Es la misma
     * condición del trigger {@code tg_rol_permisos_propio} (VAL-022).
     */
    boolean tieneAsignacionActiva(UUID perfilId, UUID rolId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1
                              FROM asignaciones_rol
                             WHERE perfil_id = :perfilId AND rol_id = :rolId AND activa
                        )
                        """)
                .param("perfilId", perfilId)
                .param("rolId", rolId)
                .query(Boolean.class)
                .single();
    }

    /**
     * Aplica la diferencia de la matriz: quita los permisos retirados y agrega los nuevos. Los
     * permisos que no cambian no se tocan, para no generar disparos innecesarios del trigger de
     * VAL-022 ni reescribir filas idénticas.
     */
    void aplicarCambios(UUID rolId, Collection<Long> agregados, Collection<Long> quitados) {
        if (!quitados.isEmpty()) {
            jdbc.sql("DELETE FROM rol_permisos WHERE rol_id = :rolId AND permiso_id = ANY(:quitados)")
                    .param("rolId", rolId)
                    .param("quitados", new SqlArrayValue("bigint", quitados.toArray()))
                    .update();
        }
        if (!agregados.isEmpty()) {
            jdbc.sql("""
                            INSERT INTO rol_permisos (rol_id, permiso_id)
                            SELECT :rolId, unnest(CAST(:agregados AS bigint[]))
                            ON CONFLICT (rol_id, permiso_id) DO NOTHING
                            """)
                    .param("rolId", rolId)
                    .param("agregados", new SqlArrayValue("bigint", agregados.toArray()))
                    .update();
        }
    }
}

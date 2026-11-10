package mx.tecnm.morelia.siglim.seguridad;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * Consultas de {@link Autorizacion}: qué asignaciones activas otorgan un permiso y de qué personas
 * es titular el usuario. Corren dentro de la transacción del servicio, con {@code siglim_app} y el
 * {@code search_path} que fija {@code GestorTransaccionesSiglim}.
 *
 * <p>Refs: H-02.1, RF-094, RF-144, RN-054, ADR-0007
 */
@Repository
class AutorizacionRepositorio {

    /** Asignación de rol activa que otorga el permiso; {@code clubId} solo con ámbito {@code CLUB}. */
    record AsignacionPermiso(String ambito, UUID clubId) {
    }

    private final JdbcClient jdbc;

    AutorizacionRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Asignaciones activas del perfil cuyo rol otorga ese permiso, con su ámbito y su club. */
    List<AsignacionPermiso> listarAsignacionesDelPermiso(UUID perfilId, String permiso) {
        return jdbc.sql("""
                        SELECT a.ambito, a.club_id
                          FROM asignaciones_rol a
                          JOIN rol_permisos rp ON rp.rol_id = a.rol_id
                          JOIN permisos p ON p.id = rp.permiso_id
                         WHERE a.perfil_id = :perfilId
                           AND a.activa
                           AND p.codigo = :permiso
                        """)
                .param("perfilId", perfilId)
                .param("permiso", permiso)
                .query(AsignacionPermiso.class)
                .list();
    }

    /**
     * Verdadero si el usuario de la transacción es titular de alguna de esas personas: él mismo o
     * el menor bajo su tutela vigente (RF-144).
     *
     * <p>Usa {@code es_titular}, la misma función que las políticas RLS: así la regla del servicio
     * y la de la base no pueden divergir (ADR-0007 §4). Toma al usuario de
     * {@code siglim.usuario_actual()}, el claim que fija la transacción.
     *
     * <p>Las personas van como un arreglo {@code uuid[]} ({@code SqlArrayValue}), no como texto
     * unido por comas: así el conjunto vacío es un arreglo vacío y un UUID no depende de cómo se
     * separe.
     */
    boolean esTitularDeAlguna(Collection<UUID> personas) {
        if (personas.isEmpty()) {
            return false;
        }
        return jdbc.sql("""
                        SELECT EXISTS (
                                 SELECT 1
                                   FROM unnest(CAST(:personas AS uuid[])) AS t (persona)
                                  WHERE es_titular(t.persona))
                        """)
                .param("personas", new SqlArrayValue("uuid", personas.toArray()))
                .query(Boolean.class)
                .single();
    }
}

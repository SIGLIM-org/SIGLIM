package mx.tecnm.morelia.siglim.seguridad;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de cuentas ({@code perfiles}) y sus asignaciones de rol (RF-094).
 *
 * <p>El listado filtra por ámbito en el {@code WHERE} y en el {@code COUNT} (ADR-0007): una cuenta
 * solo la ve quien tiene el permiso en toda la liga. El perfil técnico {@code SISTEMA} no se lista
 * ni se opera.
 *
 * <p>Refs: H-03.1, RF-094, RN-005, RN-054, ADR-0007
 */
@Repository
class UsuarioRepository {

    /** Cuenta tal como sale de la base; el servicio la convierte al DTO. */
    record FilaPerfil(UUID id, String nombreMostrado, String correo, boolean activo,
                      OffsetDateTime creadoEn) {
    }

    /** Asignación con su rol y su club, activa o revocada. */
    record FilaAsignacion(UUID id, UUID perfilId, UUID rolId, String rolCodigo, String rolNombre,
                          String ambito, UUID clubId, String clubNombre, boolean activa,
                          OffsetDateTime creadoEn, OffsetDateTime revocadaEn) {
    }

    private static final String SELECCION_PERFIL = """
            SELECT p.id, p.nombre_mostrado, p.correo, p.activo, p.creado_en
              FROM perfiles p
            """;

    private static final String CONDICIONES_LISTADO = """
             WHERE NOT p.es_sistema
               AND (:todaLaLiga)
               AND (CAST(:activo AS boolean) IS NULL OR p.activo = CAST(:activo AS boolean))
               AND (CAST(:q AS text) IS NULL
                    OR strpos(lower(p.nombre_mostrado), lower(CAST(:q AS text))) > 0
                    OR strpos(lower(coalesce(p.correo, '')), lower(CAST(:q AS text))) > 0)
               AND ((CAST(:rolId AS uuid) IS NULL AND CAST(:ambito AS text) IS NULL
                     AND CAST(:clubId AS uuid) IS NULL)
                    OR EXISTS (
                      SELECT 1 FROM asignaciones_rol a
                       WHERE a.perfil_id = p.id AND a.activa
                         AND (CAST(:rolId AS uuid) IS NULL OR a.rol_id = CAST(:rolId AS uuid))
                         AND (CAST(:ambito AS text) IS NULL
                              OR CAST(a.ambito AS text) = CAST(:ambito AS text))
                         AND (CAST(:clubId AS uuid) IS NULL OR a.club_id = CAST(:clubId AS uuid))))
            """;

    private static final String SELECCION_ASIGNACION = """
            SELECT a.id, a.perfil_id, r.id AS rol_id, r.codigo AS rol_codigo, r.nombre AS rol_nombre,
                   CAST(a.ambito AS text) AS ambito, c.id AS club_id, c.nombre AS club_nombre,
                   a.activa, a.creado_en, a.revocada_en
              FROM asignaciones_rol a
              JOIN roles r ON r.id = a.rol_id
              JOIN perfiles p ON p.id = a.perfil_id AND NOT p.es_sistema
              LEFT JOIN clubes c ON c.id = a.club_id
            """;

    private final JdbcClient jdbc;

    UsuarioRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<FilaPerfil> buscarPerfil(UUID id) {
        return jdbc.sql(SELECCION_PERFIL + " WHERE p.id = :id AND NOT p.es_sistema")
                .param("id", id)
                .query(FilaPerfil.class)
                .optional();
    }

    List<FilaPerfil> listar(FiltroUsuarios filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql(SELECCION_PERFIL + CONDICIONES_LISTADO
                        + " ORDER BY p.nombre_mostrado, p.id LIMIT :limite OFFSET :desplazamiento"), filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query(FilaPerfil.class)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata cuentas fuera de ámbito. */
    long contar(FiltroUsuarios filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM perfiles p" + CONDICIONES_LISTADO), filtro, alcance)
                .query(Long.class)
                .single();
    }

    /** Asignaciones de varias cuentas; {@code soloActivas} para los chips del listado. */
    List<FilaAsignacion> listarAsignaciones(Collection<UUID> perfiles, boolean soloActivas) {
        if (perfiles.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECCION_ASIGNACION + """
                         WHERE a.perfil_id = ANY(:perfiles)
                           AND (NOT CAST(:soloActivas AS boolean) OR a.activa)
                         ORDER BY a.perfil_id, a.activa DESC, a.ambito, r.nombre, a.id
                        """)
                .param("perfiles", new SqlArrayValue("uuid", perfiles.toArray()))
                .param("soloActivas", soloActivas)
                .query(FilaAsignacion.class)
                .list();
    }

    List<FilaAsignacion> listarAsignaciones(UUID perfilId, boolean soloActivas) {
        return listarAsignaciones(List.of(perfilId), soloActivas);
    }

    Optional<FilaAsignacion> buscarAsignacion(UUID id) {
        return jdbc.sql(SELECCION_ASIGNACION + " WHERE a.id = :id")
                .param("id", id)
                .query(FilaAsignacion.class)
                .optional();
    }

    boolean existeCorreo(String correo) {
        return jdbc.sql("""
                        SELECT EXISTS (
                          SELECT 1 FROM perfiles
                           WHERE NOT es_sistema AND lower(correo) = lower(:correo))
                        """)
                .param("correo", correo)
                .query(Boolean.class)
                .single();
    }

    UUID insertarPerfil(UUID usuarioAuthId, String nombreMostrado, String correo) {
        return jdbc.sql("""
                        INSERT INTO perfiles (usuario_auth_id, nombre_mostrado, correo)
                        VALUES (:usuarioAuthId, :nombreMostrado, :correo)
                        RETURNING id
                        """)
                .param("usuarioAuthId", usuarioAuthId)
                .param("nombreMostrado", nombreMostrado)
                .param("correo", correo)
                .query(UUID.class)
                .single();
    }

    void actualizarActivo(UUID perfilId, boolean activo) {
        jdbc.sql("UPDATE perfiles SET activo = :activo WHERE id = :id AND NOT es_sistema")
                .param("activo", activo)
                .param("id", perfilId)
                .update();
    }

    UUID insertarAsignacion(UUID perfilId, UUID rolId, UUID ligaId, String ambito, UUID clubId,
            UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO asignaciones_rol
                               (perfil_id, rol_id, liga_id, ambito, club_id, creado_por)
                        VALUES (:perfilId, :rolId, :ligaId, CAST(:ambito AS siglim.ambito), :clubId, :creadoPor)
                        RETURNING id
                        """)
                .param("perfilId", perfilId)
                .param("rolId", rolId)
                .param("ligaId", ligaId)
                .param("ambito", ambito)
                .param("clubId", clubId)
                .param("creadoPor", creadoPor)
                .query(UUID.class)
                .single();
    }

    boolean existeAsignacionActiva(UUID perfilId, UUID rolId, UUID clubId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                          SELECT 1 FROM asignaciones_rol
                           WHERE perfil_id = :perfilId AND rol_id = :rolId AND activa
                             AND club_id IS NOT DISTINCT FROM CAST(:clubId AS uuid))
                        """)
                .param("perfilId", perfilId)
                .param("rolId", rolId)
                .param("clubId", clubId)
                .query(Boolean.class)
                .single();
    }

    /** Revoca una asignación activa; devuelve las filas cambiadas. */
    int revocarAsignacion(UUID asignacionId) {
        return jdbc.sql("""
                        UPDATE asignaciones_rol
                           SET activa = false, revocada_en = now()
                         WHERE id = :id AND activa
                        """)
                .param("id", asignacionId)
                .update();
    }

    boolean existeClub(UUID ligaId, UUID clubId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM clubes WHERE id = :clubId AND liga_id = :ligaId)")
                .param("clubId", clubId)
                .param("ligaId", ligaId)
                .query(Boolean.class)
                .single();
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, FiltroUsuarios filtro,
            AlcanceAutorizacion alcance) {
        return sentencia
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("activo", filtro.activo())
                .param("q", filtro.texto())
                .param("rolId", filtro.rolId())
                .param("ambito", filtro.ambito() == null ? null : filtro.ambito().name())
                .param("clubId", filtro.clubId());
    }
}

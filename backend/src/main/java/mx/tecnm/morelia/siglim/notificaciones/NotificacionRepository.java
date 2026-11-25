package mx.tecnm.morelia.siglim.notificaciones;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * Acceso a {@code siglim.notificaciones} y a los datos que arman y dirigen los avisos (M-14.1).
 *
 * <ul>
 *   <li><b>Lectura y lectura masiva.</b> Siempre filtran por {@code perfil_id} del usuario de la
 *       sesión, además de la política RLS {@code notificaciones_leer}/{@code notificaciones_editar}
 *       (defensa en profundidad, ADR-0014).</li>
 *   <li><b>Alta sin {@code RETURNING}.</b> El aviso es de otro perfil: la política de lectura no lo
 *       deja ver a quien dispara el evento, y {@code INSERT ... RETURNING} exige pasarla. El
 *       identificador se genera en la base y no se devuelve.</li>
 *   <li><b>Destinatarios por permiso.</b> Nunca por nombre de rol (DP-008): un perfil activo recibe el
 *       aviso si una asignación activa le otorga el permiso del trámite y, además,
 *       {@value #PERMISO_AVISOS} para poder leerlo.</li>
 * </ul>
 *
 * <p>Refs: H-25.1, RF-142, ADR-0007, ADR-0010, ADR-0014
 */
@Repository
class NotificacionRepository {

    static final String PERMISO_AVISOS = "notificaciones.aviso:CONSULTAR";

    private static final String COLUMNAS = """
            SELECT id, evento, titulo, cuerpo, enlace, creada_en, leida_en
              FROM notificaciones
            """;

    /** Condición de las asignaciones que otorgan {@code :permiso} a {@code a.perfil_id}. */
    private static final String OTORGA_AVISOS = """
            EXISTS (SELECT 1
                      FROM asignaciones_rol av
                      JOIN rol_permisos rpv ON rpv.rol_id = av.rol_id
                      JOIN permisos pv ON pv.id = rpv.permiso_id
                     WHERE av.perfil_id = a.perfil_id AND av.activa AND av.liga_id = :liga
                       AND pv.codigo = '%s')
            """.formatted(PERMISO_AVISOS);

    private final JdbcClient jdbc;

    NotificacionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── Consulta del usuario ──────────────────────────────────────────────────────────────────

    List<NotificacionDto> listar(UUID perfilId, Boolean leida, int pagina, int tamano) {
        return jdbc.sql(COLUMNAS + """
                         WHERE perfil_id = :perfil
                           AND (CAST(:leida AS boolean) IS NULL OR (leida_en IS NOT NULL) = CAST(:leida AS boolean))
                         ORDER BY creada_en DESC, id DESC
                         LIMIT :limite OFFSET :desplazamiento
                        """)
                .param("perfil", perfilId)
                .param("leida", leida)
                .param("limite", tamano)
                .param("desplazamiento", (long) pagina * tamano)
                .query(NotificacionRepository::aDto)
                .list();
    }

    long contar(UUID perfilId, Boolean leida) {
        return jdbc.sql("""
                        SELECT count(*) FROM notificaciones
                         WHERE perfil_id = :perfil
                           AND (CAST(:leida AS boolean) IS NULL OR (leida_en IS NOT NULL) = CAST(:leida AS boolean))
                        """)
                .param("perfil", perfilId)
                .param("leida", leida)
                .query(Long.class)
                .single();
    }

    /**
     * Marca como leído un aviso del perfil; si ya lo estaba, conserva la primera lectura.
     *
     * @return el aviso, o vacío si no existe o es de otro perfil
     */
    Optional<NotificacionDto> marcarLeida(UUID perfilId, UUID id) {
        return jdbc.sql("""
                        UPDATE notificaciones
                           SET leida_en = coalesce(leida_en, now())
                         WHERE id = :id AND perfil_id = :perfil
                        RETURNING id, evento, titulo, cuerpo, enlace, creada_en, leida_en
                        """)
                .param("id", id)
                .param("perfil", perfilId)
                .query(NotificacionRepository::aDto)
                .optional();
    }

    /** @return cuántos avisos sin leer del perfil quedaron leídos */
    int marcarTodas(UUID perfilId) {
        return jdbc.sql("UPDATE notificaciones SET leida_en = now() WHERE perfil_id = :perfil AND leida_en IS NULL")
                .param("perfil", perfilId)
                .update();
    }

    // ── Alta desde los oyentes ────────────────────────────────────────────────────────────────

    /** Un aviso igual para cada destinatario, en la transacción del evento. */
    void insertar(Collection<UUID> destinatarios, EventoAviso evento, String titulo, String cuerpo, String enlace) {
        if (destinatarios.isEmpty()) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO notificaciones (perfil_id, evento, titulo, cuerpo, enlace)
                        SELECT d, :evento, :titulo, :cuerpo, :enlace FROM unnest(:destinatarios) AS d
                        """)
                .param("destinatarios", new SqlArrayValue("uuid", destinatarios.toArray()))
                .param("evento", evento.name())
                .param("titulo", titulo)
                .param("cuerpo", cuerpo)
                .param("enlace", enlace)
                .update();
    }

    /**
     * Perfiles activos del árbitro central vigente del partido que pueden aprobar la cédula
     * ({@code partidos.cedula:APROBAR}, H-17) y leer avisos.
     */
    List<UUID> arbitrosCentrales(UUID ligaId, UUID partidoId) {
        return jdbc.sql("""
                        SELECT DISTINCT a.perfil_id
                          FROM designaciones d
                          JOIN arbitros ar ON ar.id = d.arbitro_id
                          JOIN perfiles pf ON pf.persona_id = ar.persona_id AND pf.activo
                          JOIN asignaciones_rol a ON a.perfil_id = pf.id AND a.activa AND a.liga_id = :liga
                          JOIN rol_permisos rp ON rp.rol_id = a.rol_id
                          JOIN permisos p ON p.id = rp.permiso_id AND p.codigo = 'partidos.cedula:APROBAR'
                         WHERE d.partido_id = :partido AND d.vigente AND d.funcion = 'CENTRAL'
                           AND\s""" + OTORGA_AVISOS)
                .param("liga", ligaId)
                .param("partido", partidoId)
                .query(UUID.class)
                .list();
    }

    /**
     * Perfiles activos con una asignación de ámbito {@code CLUB} sobre alguno de los clubes que les
     * otorga {@code permiso}, y que pueden leer avisos. [PROPUESTA] Los usuarios de la liga no reciben
     * los avisos de un club: verían los de todos.
     */
    List<UUID> directivasDeClubes(UUID ligaId, Set<UUID> clubes, String permiso) {
        if (clubes.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT DISTINCT a.perfil_id
                          FROM asignaciones_rol a
                          JOIN perfiles pf ON pf.id = a.perfil_id AND pf.activo
                          JOIN rol_permisos rp ON rp.rol_id = a.rol_id
                          JOIN permisos p ON p.id = rp.permiso_id AND p.codigo = :permiso
                         WHERE a.liga_id = :liga AND a.activa AND a.ambito = 'CLUB' AND a.club_id = ANY(:clubes)
                           AND\s""" + OTORGA_AVISOS)
                .param("liga", ligaId)
                .param("clubes", new SqlArrayValue("uuid", clubes.toArray()))
                .param("permiso", permiso)
                .query(UUID.class)
                .list();
    }

    /** Equipos y jornada del partido, para el texto del aviso de la cédula. */
    Optional<DatosPartido> datosPartido(UUID ligaId, UUID partidoId) {
        return jdbc.sql("""
                        SELECT el.nombre AS local, ev.nombre AS visitante, j.numero AS jornada
                          FROM partidos p
                          JOIN jornadas j ON j.id = p.jornada_id
                          JOIN grupos g ON g.id = j.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                          JOIN equipos el ON el.id = p.equipo_local_id
                          JOIN equipos ev ON ev.id = p.equipo_visitante_id
                         WHERE p.id = :partido AND t.liga_id = :liga
                        """)
                .param("partido", partidoId)
                .param("liga", ligaId)
                .query((fila, numero) -> new DatosPartido(fila.getString("local"), fila.getString("visitante"),
                        fila.getInt("jornada")))
                .optional();
    }

    /** Número, categoría, grupo y clubes que juegan la jornada (sin partidos cancelados). */
    Optional<DatosJornada> datosJornada(UUID ligaId, UUID jornadaId) {
        Optional<DatosJornada> jornada = jdbc.sql("""
                        SELECT j.numero, c.nombre AS categoria, g.nombre AS grupo
                          FROM jornadas j
                          JOIN grupos g ON g.id = j.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN categorias c ON c.id = ct.categoria_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE j.id = :jornada AND t.liga_id = :liga
                        """)
                .param("jornada", jornadaId)
                .param("liga", ligaId)
                .query((fila, numero) -> new DatosJornada(fila.getInt("numero"), fila.getString("categoria"),
                        fila.getString("grupo"), Set.of()))
                .optional();
        return jornada.map(datos -> new DatosJornada(datos.numero(), datos.categoria(), datos.grupo(),
                Set.copyOf(jdbc.sql("""
                                SELECT e.club_id
                                  FROM partidos p
                                  JOIN equipos e ON e.id IN (p.equipo_local_id, p.equipo_visitante_id)
                                 WHERE p.jornada_id = :jornada AND p.estado <> 'CANCELADO'
                                """)
                        .param("jornada", jornadaId)
                        .query(UUID.class)
                        .list())));
    }

    private static NotificacionDto aDto(ResultSet fila, int numero) throws SQLException {
        return new NotificacionDto(
                fila.getObject("id", UUID.class),
                fila.getString("evento"),
                fila.getString("titulo"),
                fila.getString("cuerpo"),
                fila.getString("enlace"),
                fila.getObject("creada_en", OffsetDateTime.class),
                fila.getObject("leida_en", OffsetDateTime.class));
    }

    record DatosPartido(String local, String visitante, int jornada) {
    }

    record DatosJornada(int numero, String categoria, String grupo, Set<UUID> clubes) {
    }
}

package mx.tecnm.morelia.siglim.publicacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.publicacion.PublicacionPendienteDto.Perfil;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de la bandeja de pendientes del Presidente: jornadas y comunicados en {@code REVISION}
 * (RF-090, RF-121).
 *
 * <p>Une ambas fuentes con {@code UNION ALL} para paginar en la base. Cada rama se activa con un
 * indicador que el servicio calcula con los permisos de la sesión; el {@code COUNT} usa las mismas
 * condiciones para que el total no cuente filas que la sesión no puede ver (ADR-0007 §2). La fecha
 * de envío del comunicado sale de su último paso a {@code REVISION} en {@code historial_publicacion}.
 *
 * <p><b>Fechas de programación (H-35.3, DEC-175).</b> Las jornadas de una fecha no se listan solas: la
 * fecha aparece una vez, como {@code FECHA}, cuando su estado derivado es {@code REVISION} (alguna
 * jornada en revisión y ninguna en borrador, igual que {@code FechaRepository}). Su contexto lleva la
 * temporada y cuántas jornadas tiene; quién la envió y cuándo salen de la jornada enviada al último.
 * Usa el mismo indicador que las jornadas: se ven con el permiso de aprobarlas.
 *
 * <p>Refs: H-13.1, H-35.3, RF-090, RF-121, DEC-175, ADR-0007
 */
@Repository
class PublicacionPendienteRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String PENDIENTES = """
            SELECT 'JORNADA' AS tipo, j.id, 'Jornada ' || j.numero AS titulo,
                   t.nombre || ' · ' || cat.nombre || ' · ' || g.nombre AS contexto,
                   (SELECT count(*) FROM partidos p
                     WHERE p.jornada_id = j.id AND p.estado <> 'CANCELADO') AS partidos,
                   j.enviada_por AS enviado_por, pe.nombre_mostrado AS enviado_por_nombre,
                   coalesce(j.enviada_en, j.actualizado_en) AS enviado_en
              FROM jornadas j
              JOIN grupos g ON g.id = j.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN categorias cat ON cat.id = ct.categoria_id
              JOIN temporadas t ON t.id = ct.temporada_id
              LEFT JOIN perfiles pe ON pe.id = j.enviada_por
             WHERE :jornadas AND t.liga_id = :ligaId AND j.estado_publicacion = 'REVISION'
               AND j.fecha_id IS NULL
            UNION ALL
            SELECT 'FECHA', f.id, 'Fecha ' || f.numero,
                   t.nombre || ' · ' || jj.jornadas
                       || CASE WHEN jj.jornadas = 1 THEN ' jornada' ELSE ' jornadas' END,
                   (SELECT count(*) FROM partidos p
                      JOIN jornadas j ON j.id = p.jornada_id
                     WHERE j.fecha_id = f.id AND p.estado <> 'CANCELADO'),
                   jj.enviada_por, pe.nombre_mostrado, jj.enviada_en
              FROM fechas_programacion f
              JOIN temporadas t ON t.id = f.temporada_id
              JOIN LATERAL (
                     SELECT count(*) AS jornadas,
                            count(*) FILTER (WHERE j.estado_publicacion = 'BORRADOR') AS en_borrador,
                            count(*) FILTER (WHERE j.estado_publicacion = 'REVISION') AS en_revision,
                            (array_agg(j.enviada_por ORDER BY j.enviada_en DESC NULLS LAST))[1] AS enviada_por,
                            max(coalesce(j.enviada_en, j.actualizado_en)) AS enviada_en
                       FROM jornadas j
                      WHERE j.fecha_id = f.id) jj ON true
              LEFT JOIN perfiles pe ON pe.id = jj.enviada_por
             WHERE :jornadas AND t.liga_id = :ligaId AND jj.en_revision > 0 AND jj.en_borrador = 0
            UNION ALL
            SELECT 'COMUNICADO', c.id, c.titulo, NULL, NULL,
                   c.redactado_por, pe.nombre_mostrado,
                   coalesce((SELECT max(h.cambiado_en) FROM historial_publicacion h
                              WHERE h.entidad = 'COMUNICADO' AND h.entidad_id = c.id
                                AND h.estado_nuevo = 'REVISION'), c.actualizado_en)
              FROM comunicados c
              LEFT JOIN perfiles pe ON pe.id = c.redactado_por
             WHERE :comunicados AND c.liga_id = :ligaId AND c.estado_publicacion = 'REVISION'
            """;

    private final JdbcClient jdbc;

    PublicacionPendienteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Pendientes visibles, del envío más antiguo al más reciente (primero lo que lleva más esperando). */
    List<PublicacionPendienteDto> listar(UUID ligaId, boolean jornadas, boolean comunicados, int limite,
            long desplazamiento) {
        return jdbc.sql("SELECT * FROM (" + PENDIENTES + ") x ORDER BY enviado_en, id"
                        + " LIMIT :limite OFFSET :desplazamiento")
                .param("ligaId", ligaId)
                .param("jornadas", jornadas)
                .param("comunicados", comunicados)
                .param("limite", limite)
                .param("desplazamiento", desplazamiento)
                .query(PublicacionPendienteRepository::aFila)
                .list();
    }

    /** Con las mismas condiciones del listado. */
    long contar(UUID ligaId, boolean jornadas, boolean comunicados) {
        return jdbc.sql("SELECT count(*) FROM (" + PENDIENTES + ") x")
                .param("ligaId", ligaId)
                .param("jornadas", jornadas)
                .param("comunicados", comunicados)
                .query(Long.class)
                .single();
    }

    private static PublicacionPendienteDto aFila(ResultSet fila, int numero) throws SQLException {
        UUID enviadoPor = fila.getObject("enviado_por", UUID.class);
        long partidos = fila.getLong("partidos");
        Long partidosOpcional = fila.wasNull() ? null : partidos;
        OffsetDateTime enviadoEn = fila.getObject("enviado_en", OffsetDateTime.class);
        return new PublicacionPendienteDto(
                TipoPublicacion.valueOf(fila.getString("tipo")),
                fila.getObject("id", UUID.class),
                fila.getString("titulo"),
                fila.getString("contexto"),
                partidosOpcional,
                enviadoPor == null ? null : new Perfil(enviadoPor, fila.getString("enviado_por_nombre")),
                enviadoEn == null ? null : enviadoEn.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime());
    }
}

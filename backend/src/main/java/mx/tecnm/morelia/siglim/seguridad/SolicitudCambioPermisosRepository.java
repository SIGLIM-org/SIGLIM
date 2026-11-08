package mx.tecnm.morelia.siglim.seguridad;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code solicitudes_cambio_permisos}: el trámite que deja pendiente un cambio de permisos
 * que crea un par incompatible (RF-096, VAL-023).
 *
 * <p>La solicitud guarda la diferencia como arreglos de identificadores de permiso; la API los
 * vuelve a exponer como códigos. La liga se filtra por el rol, porque la tabla no guarda
 * {@code liga_id} (ADR-0011).
 *
 * <p>Refs: H-20.1, RF-096, VAL-023, ADR-0011
 */
@Repository
class SolicitudCambioPermisosRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT s.id, s.rol_id,
                   r.codigo AS rol_codigo, r.nombre AS rol_nombre, r.descripcion AS rol_descripcion,
                   CAST(r.ambito_predeterminado AS text) AS rol_ambito_predeterminado,
                   r.asignable AS rol_asignable,
                   s.permisos_agregados AS permisos_agregados_ids,
                   s.permisos_quitados AS permisos_quitados_ids,
                   (SELECT coalesce(array_agg(p.codigo ORDER BY p.codigo), ARRAY[]::text[])
                      FROM permisos p
                     WHERE p.id = ANY(s.permisos_agregados)) AS permisos_agregados,
                   (SELECT coalesce(array_agg(p.codigo ORDER BY p.codigo), ARRAY[]::text[])
                      FROM permisos p
                     WHERE p.id = ANY(s.permisos_quitados)) AS permisos_quitados,
                   s.motivo, CAST(s.estado AS text) AS estado,
                   s.solicitado_por,
                   (SELECT nombre_mostrado FROM perfiles WHERE id = s.solicitado_por) AS solicitante_nombre,
                   s.solicitado_en,
                   s.resuelto_por,
                   (SELECT nombre_mostrado FROM perfiles WHERE id = s.resuelto_por) AS resolutor_nombre,
                   s.resuelto_en, s.observaciones
              FROM solicitudes_cambio_permisos s
              JOIN roles r ON r.id = s.rol_id
            """;

    /** Condición compartida por el listado y su {@code COUNT}. */
    private static final String FILTRO_LISTADO = """
             WHERE r.liga_id = :ligaId
               AND (CAST(:estado AS text) IS NULL OR s.estado::text = CAST(:estado AS text))
               AND (CAST(:rolId AS text) IS NULL OR s.rol_id = CAST(:rolId AS uuid))
            """;

    private final JdbcClient jdbc;

    SolicitudCambioPermisosRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<FilaSolicitud> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE s.id = :id AND r.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(SolicitudCambioPermisosRepository::aFila)
                .optional();
    }

    /** Toma el candado de la solicitud: dos dictámenes simultáneos no se pisan (RF-096). */
    Optional<FilaSolicitud> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE s.id = :id AND r.liga_id = :ligaId FOR UPDATE OF s")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(SolicitudCambioPermisosRepository::aFila)
                .optional();
    }

    List<FilaSolicitud> listar(UUID ligaId, EstadoSolicitudPermisos estado, UUID rolId, int limite,
            long desplazamiento) {
        return conFiltro(jdbc.sql(SELECCION + FILTRO_LISTADO + """
                         ORDER BY s.solicitado_en DESC, s.id
                         LIMIT :limite OFFSET :desplazamiento
                        """), ligaId, estado, rolId)
                .param("limite", limite)
                .param("desplazamiento", desplazamiento)
                .query(SolicitudCambioPermisosRepository::aFila)
                .list();
    }

    /** Con la misma condición del listado, para que el total no delate solicitudes de otra liga. */
    long contar(UUID ligaId, EstadoSolicitudPermisos estado, UUID rolId) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM solicitudes_cambio_permisos s"
                + " JOIN roles r ON r.id = s.rol_id" + FILTRO_LISTADO), ligaId, estado, rolId)
                .query(Long.class)
                .single();
    }

    /** Registra el trámite en {@code SOLICITADO}; el cambio no se aplica todavía (VAL-023). */
    UUID insertar(UUID rolId, Collection<Long> agregados, Collection<Long> quitados, String motivo,
            UUID solicitadoPor) {
        return jdbc.sql("""
                        INSERT INTO solicitudes_cambio_permisos
                               (rol_id, permisos_agregados, permisos_quitados, motivo, solicitado_por)
                        VALUES (:rolId, :agregados, :quitados, :motivo, :solicitadoPor)
                        RETURNING id
                        """)
                .param("rolId", rolId)
                .param("agregados", new SqlArrayValue("bigint", agregados.toArray()))
                .param("quitados", new SqlArrayValue("bigint", quitados.toArray()))
                .param("motivo", motivo)
                .param("solicitadoPor", solicitadoPor)
                .query(UUID.class)
                .single();
    }

    /** Cierra el trámite con el dictamen del Presidente; la resolución no se reabre (RF-096). */
    void resolver(UUID id, EstadoSolicitudPermisos estado, UUID resueltoPor, String observaciones) {
        jdbc.sql("""
                        UPDATE solicitudes_cambio_permisos
                           SET estado = CAST(:estado AS estado_solicitud_permisos),
                               resuelto_por = :resueltoPor,
                               resuelto_en = now(),
                               observaciones = :observaciones
                         WHERE id = :id AND estado = 'SOLICITADO'
                        """)
                .param("id", id)
                .param("estado", estado.name())
                .param("resueltoPor", resueltoPor)
                .param("observaciones", observaciones)
                .update();
    }

    /** Solicitud con el rol, los códigos de sus cambios y los perfiles ya resueltos. */
    record FilaSolicitud(
            UUID id,
            UUID rolId,
            String rolCodigo,
            String rolNombre,
            String rolDescripcion,
            String rolAmbitoPredeterminado,
            boolean rolAsignable,
            List<Long> permisosAgregadosIds,
            List<Long> permisosQuitadosIds,
            List<String> permisosAgregados,
            List<String> permisosQuitados,
            String motivo,
            EstadoSolicitudPermisos estado,
            UUID solicitadoPor,
            String solicitadoPorNombre,
            OffsetDateTime solicitadoEn,
            UUID resueltoPor,
            String resueltoPorNombre,
            OffsetDateTime resueltoEn,
            String observaciones) {
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec consulta, UUID ligaId,
            EstadoSolicitudPermisos estado, UUID rolId) {
        return consulta
                .param("ligaId", ligaId)
                .param("estado", estado == null ? null : estado.name())
                .param("rolId", rolId == null ? null : rolId.toString());
    }

    private static FilaSolicitud aFila(ResultSet fila, int numero) throws SQLException {
        return new FilaSolicitud(
                fila.getObject("id", UUID.class),
                fila.getObject("rol_id", UUID.class),
                fila.getString("rol_codigo"),
                fila.getString("rol_nombre"),
                fila.getString("rol_descripcion"),
                fila.getString("rol_ambito_predeterminado"),
                fila.getBoolean("rol_asignable"),
                leerIds(fila, "permisos_agregados_ids"),
                leerIds(fila, "permisos_quitados_ids"),
                leerCodigos(fila, "permisos_agregados"),
                leerCodigos(fila, "permisos_quitados"),
                fila.getString("motivo"),
                EstadoSolicitudPermisos.valueOf(fila.getString("estado")),
                fila.getObject("solicitado_por", UUID.class),
                fila.getString("solicitante_nombre"),
                aZona(fila.getObject("solicitado_en", OffsetDateTime.class)),
                fila.getObject("resuelto_por", UUID.class),
                fila.getString("resolutor_nombre"),
                aZona(fila.getObject("resuelto_en", OffsetDateTime.class)),
                fila.getString("observaciones"));
    }

    private static List<Long> leerIds(ResultSet fila, String columna) throws SQLException {
        Array arreglo = fila.getArray(columna);
        if (arreglo == null) {
            return List.of();
        }
        try {
            Object[] valores = (Object[]) arreglo.getArray();
            return Arrays.stream(valores).map(valor -> ((Number) valor).longValue()).toList();
        } finally {
            arreglo.free();
        }
    }

    private static List<String> leerCodigos(ResultSet fila, String columna) throws SQLException {
        Array arreglo = fila.getArray(columna);
        if (arreglo == null) {
            return List.of();
        }
        try {
            Object[] valores = (Object[]) arreglo.getArray();
            return Arrays.stream(valores).map(String::valueOf).toList();
        } finally {
            arreglo.free();
        }
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }
}

package mx.tecnm.morelia.siglim.seguridad;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code solicitudes_arco} (M-01).
 *
 * <p>El nombre del titular y el del perfil que atendió se unen con {@code LEFT JOIN}: si RLS no deja
 * ver a la persona, la solicitud se sigue listando, sin nombre.
 *
 * <p>Refs: B-01, RF-098, ADR-0007
 */
@Repository
class SolicitudArcoRepository {

    private static final String SELECCION = """
            SELECT s.id, s.persona_id, s.tipo, s.descripcion, s.estado, s.recibida_en,
                   s.atendida_por, s.atendida_en, s.respuesta,
                   concat_ws(' ', pe.nombre, pe.apellido_paterno, pe.apellido_materno) AS persona_nombre,
                   pf.nombre_mostrado AS atendida_por_nombre
              FROM solicitudes_arco s
              LEFT JOIN personas pe ON pe.id = s.persona_id
              LEFT JOIN perfiles pf ON pf.id = s.atendida_por
            """;

    /** Misma condición en el listado y en el conteo (ADR-0007 §2). */
    private static final String CONDICIONES_LISTADO = """
             WHERE s.liga_id = :liga
               AND (:todaLaLiga OR (:propio AND siglim.es_titular(s.persona_id)))
               AND (CAST(:estado AS text) IS NULL OR s.estado = CAST(:estado AS estado_solicitud_arco))
               AND (CAST(:tipo AS text) IS NULL OR s.tipo = CAST(:tipo AS tipo_solicitud_arco))
               AND (CAST(:persona AS uuid) IS NULL OR s.persona_id = :persona)
            """;

    private final JdbcClient jdbc;

    SolicitudArcoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<SolicitudArcoDto> listar(UUID ligaId, FiltroSolicitudesArco filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql(SELECCION + CONDICIONES_LISTADO
                        + " ORDER BY s.recibida_en DESC, s.id LIMIT :limite OFFSET :desplazamiento"),
                ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query((fila, numero) -> aDto(fila))
                .list();
    }

    long contar(UUID ligaId, FiltroSolicitudesArco filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM solicitudes_arco s" + CONDICIONES_LISTADO),
                ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    Optional<SolicitudArcoDto> buscar(UUID id) {
        return jdbc.sql(SELECCION + " WHERE s.id = :id")
                .param("id", id)
                .query((fila, numero) -> aDto(fila))
                .optional();
    }

    /** Estado actual con candado de fila: dos cambios simultáneos no se pisan. */
    Optional<EstadoSolicitudArco> bloquearEstado(UUID ligaId, UUID id) {
        return jdbc.sql("SELECT estado FROM solicitudes_arco WHERE id = :id AND liga_id = :liga FOR UPDATE")
                .param("id", id)
                .param("liga", ligaId)
                .query((fila, numero) -> EstadoSolicitudArco.valueOf(fila.getString("estado")))
                .optional();
    }

    /** Si la persona existe y la sesión puede verla (RLS {@code personas_leer}). */
    boolean existePersona(UUID personaId) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM personas WHERE id = :persona)")
                .param("persona", personaId)
                .query(Boolean.class)
                .single());
    }

    UUID insertar(UUID ligaId, UUID personaId, TipoSolicitudArco tipo, String descripcion) {
        return jdbc.sql("""
                        INSERT INTO solicitudes_arco (liga_id, persona_id, tipo, descripcion)
                        VALUES (:liga, :persona, CAST(:tipo AS tipo_solicitud_arco), :descripcion)
                        RETURNING id
                        """)
                .param("liga", ligaId)
                .param("persona", personaId)
                .param("tipo", tipo.name())
                .param("descripcion", descripcion)
                .query(UUID.class)
                .single();
    }

    /** Pone el estado; al cerrar guarda además la respuesta, el perfil y la fecha de atención. */
    void actualizarEstado(UUID id, EstadoSolicitudArco estado, String respuesta, UUID atendidaPor) {
        jdbc.sql("""
                        UPDATE solicitudes_arco
                           SET estado = CAST(:estado AS estado_solicitud_arco),
                               respuesta = CASE WHEN :cierra THEN :respuesta ELSE respuesta END,
                               atendida_por = CASE WHEN :cierra THEN CAST(:atendidaPor AS uuid) ELSE atendida_por END,
                               atendida_en = CASE WHEN :cierra THEN now() ELSE atendida_en END
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("estado", estado.name())
                .param("cierra", estado.esFinal())
                .param("respuesta", respuesta)
                .param("atendidaPor", atendidaPor)
                .update();
    }

    private static StatementSpec conFiltro(StatementSpec consulta, UUID ligaId, FiltroSolicitudesArco filtro,
            AlcanceAutorizacion alcance) {
        return consulta
                .param("liga", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("propio", alcance.propio())
                .param("estado", filtro.estado() == null ? null : filtro.estado().name())
                .param("tipo", filtro.tipo() == null ? null : filtro.tipo().name())
                .param("persona", filtro.personaId());
    }

    private static SolicitudArcoDto aDto(ResultSet fila) throws SQLException {
        String nombre = fila.getString("persona_nombre");
        return new SolicitudArcoDto(
                fila.getObject("id", UUID.class),
                fila.getObject("persona_id", UUID.class),
                nombre == null || nombre.isBlank() ? null : nombre,
                TipoSolicitudArco.valueOf(fila.getString("tipo")),
                fila.getString("descripcion"),
                EstadoSolicitudArco.valueOf(fila.getString("estado")),
                fila.getObject("recibida_en", OffsetDateTime.class),
                fila.getObject("atendida_por", UUID.class),
                fila.getString("atendida_por_nombre"),
                fila.getObject("atendida_en", OffsetDateTime.class),
                fila.getString("respuesta"));
    }
}

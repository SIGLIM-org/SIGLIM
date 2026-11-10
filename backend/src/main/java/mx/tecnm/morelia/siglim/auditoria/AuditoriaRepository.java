package mx.tecnm.morelia.siglim.auditoria;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * SQL de la consulta de {@code auditoria}: solo {@code SELECT}; la escritura está únicamente en
 * {@link AuditoriaServiceJdbc}. El nombre del perfil se lee con un {@code LEFT JOIN} de solo lectura
 * a {@code perfiles} porque {@code perfil_id} admite nulos y este paquete no puede usar
 * {@code seguridad} (ADR-0006); de ese join solo sale {@code nombre_mostrado}.
 *
 * <p>Refs: H-21.1, RF-134, RNF-012, RNF-015, ADR-0011
 */
@Repository
class AuditoriaRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String CONDICIONES_LISTADO = """
             WHERE a.liga_id = :ligaId
               AND :todaLaLiga
               AND (CAST(:perfilId AS uuid) IS NULL OR a.perfil_id = CAST(:perfilId AS uuid))
               AND (cardinality(CAST(:modulos AS text[])) = 0 OR CAST(a.modulo AS text) = ANY(CAST(:modulos AS text[])))
               AND (cardinality(CAST(:acciones AS text[])) = 0 OR a.accion = ANY(CAST(:acciones AS text[])))
               AND (CAST(:desde AS timestamptz) IS NULL OR a.ocurrido_en >= CAST(:desde AS timestamptz))
               AND (CAST(:hasta AS timestamptz) IS NULL OR a.ocurrido_en <  CAST(:hasta AS timestamptz))
            """;

    private static final String CONDICIONES_OPCIONES = " WHERE a.liga_id = :ligaId AND :todaLaLiga";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditoriaRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    List<RegistroAuditoriaDto> listar(UUID ligaId, FiltroAuditoria filtro, boolean todaLaLiga) {
        return conFiltro(jdbc.sql("""
                        SELECT a.id, a.ocurrido_en, a.perfil_id, p.nombre_mostrado, a.modulo, a.accion, a.entidad,
                               a.entidad_id, a.motivo, a.detalle::text AS detalle
                          FROM auditoria a
                          LEFT JOIN perfiles p ON p.id = a.perfil_id
                        """ + CONDICIONES_LISTADO + " ORDER BY " + filtro.ordenSql()
                        + " LIMIT :limite OFFSET :desplazamiento"), ligaId, filtro, todaLaLiga)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query(this::aDto)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, FiltroAuditoria filtro, boolean todaLaLiga) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM auditoria a" + CONDICIONES_LISTADO), ligaId, filtro, todaLaLiga)
                .query(Long.class)
                .single();
    }

    OpcionesFiltroAuditoriaDto opcionesFiltro(UUID ligaId, boolean todaLaLiga) {
        List<PerfilResumenDto> perfiles = conOpciones("""
                        SELECT DISTINCT p.id, p.nombre_mostrado
                          FROM auditoria a
                          JOIN perfiles p ON p.id = a.perfil_id
                        """ + CONDICIONES_OPCIONES + " ORDER BY p.nombre_mostrado, p.id", ligaId, todaLaLiga)
                .query((fila, numero) -> new PerfilResumenDto(fila.getObject("id", UUID.class),
                        fila.getString("nombre_mostrado")))
                .list();
        List<String> modulos = conOpciones("SELECT DISTINCT CAST(a.modulo AS text) AS modulo FROM auditoria a"
                + CONDICIONES_OPCIONES + " ORDER BY modulo", ligaId, todaLaLiga)
                .query(String.class)
                .list();
        List<String> acciones = conOpciones("SELECT DISTINCT a.accion FROM auditoria a"
                + CONDICIONES_OPCIONES + " ORDER BY a.accion", ligaId, todaLaLiga)
                .query(String.class)
                .list();
        return new OpcionesFiltroAuditoriaDto(perfiles, modulos, acciones);
    }

    private JdbcClient.StatementSpec conOpciones(String sql, UUID ligaId, boolean todaLaLiga) {
        return jdbc.sql(sql)
                .param("ligaId", ligaId)
                .param("todaLaLiga", todaLaLiga);
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, UUID ligaId,
            FiltroAuditoria filtro, boolean todaLaLiga) {
        return sentencia
                .param("ligaId", ligaId)
                .param("todaLaLiga", todaLaLiga)
                .param("perfilId", filtro.perfilId())
                .param("modulos", new SqlArrayValue("text", filtro.modulos().toArray()))
                .param("acciones", new SqlArrayValue("text", filtro.acciones().toArray()))
                .param("desde", filtro.desde())
                .param("hasta", filtro.hasta());
    }

    private RegistroAuditoriaDto aDto(ResultSet fila, int numero) throws SQLException {
        UUID perfilId = fila.getObject("perfil_id", UUID.class);
        String detalle = fila.getString("detalle");
        return new RegistroAuditoriaDto(
                fila.getLong("id"),
                fila.getObject("ocurrido_en", OffsetDateTime.class)
                        .atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime(),
                perfilId == null ? null : new PerfilResumenDto(perfilId, fila.getString("nombre_mostrado")),
                fila.getString("modulo"),
                fila.getString("accion"),
                fila.getString("entidad"),
                fila.getString("entidad_id"),
                fila.getString("motivo"),
                detalle == null ? null : json.readTree(detalle));
    }
}

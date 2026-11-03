package mx.tecnm.morelia.siglim.organizacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code clubes}. El listado filtra por ámbito con la misma condición en el {@code COUNT}
 * (ADR-0007): un ámbito de club solo alcanza su propio club.
 *
 * <p>Refs: H-06.1, RF-008, ADR-0007, ADR-0011
 */
@Repository
class ClubRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT c.id, c.nombre, c.estado, c.creado_en, c.cerrado_en, c.motivo_cierre
              FROM clubes c
            """;

    private static final String CONDICIONES_LISTADO = """
             WHERE c.liga_id = :ligaId
               AND (:todaLaLiga OR c.id = ANY(:clubes))
               AND (CAST(:estado AS text) IS NULL OR CAST(c.estado AS text) = CAST(:estado AS text))
               AND (CAST(:q AS text) IS NULL OR strpos(lower(c.nombre), lower(CAST(:q AS text))) > 0)
            """;

    private final JdbcClient jdbc;

    ClubRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ClubDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE c.id = :id AND c.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(ClubRepository::aDto)
                .optional();
    }

    List<ClubDto> listar(UUID ligaId, FiltroClubes filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql(SELECCION + CONDICIONES_LISTADO
                        + " ORDER BY c.nombre, c.id LIMIT :limite OFFSET :desplazamiento"), ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query(ClubRepository::aDto)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, FiltroClubes filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM clubes c" + CONDICIONES_LISTADO), ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    boolean existeNombre(UUID ligaId, String nombre) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM clubes WHERE liga_id = :ligaId AND nombre = :nombre)")
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .query(Boolean.class)
                .single();
    }

    UUID insertar(UUID ligaId, String nombre) {
        return jdbc.sql("INSERT INTO clubes (liga_id, nombre) VALUES (:ligaId, :nombre) RETURNING id")
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .query(UUID.class)
                .single();
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, UUID ligaId,
            FiltroClubes filtro, AlcanceAutorizacion alcance) {
        return sentencia
                .param("ligaId", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("estado", filtro.estado() == null ? null : filtro.estado().name())
                .param("q", filtro.texto());
    }

    private static ClubDto aDto(ResultSet fila, int numero) throws SQLException {
        return new ClubDto(
                fila.getObject("id", UUID.class),
                fila.getString("nombre"),
                EstadoClub.valueOf(fila.getString("estado")),
                fila.getObject("creado_en", OffsetDateTime.class)
                        .atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime(),
                fila.getObject("cerrado_en", LocalDate.class),
                fila.getString("motivo_cierre"));
    }
}

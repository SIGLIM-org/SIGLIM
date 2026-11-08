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
 * <p>El cambio de nombre (B-04) lee el club con {@code FOR UPDATE}, inserta el club nuevo con
 * {@code club_anterior_id} y cierra el anterior; nunca se borra ni se renombra una fila (RN-005).
 *
 * <p>Refs: H-06.1, B-04, RF-008, RF-105, RN-041, DEC-49, DEC-61, ADR-0007, ADR-0011
 */
@Repository
class ClubRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT c.id, c.nombre, c.estado, c.creado_en, c.cerrado_en, c.motivo_cierre,
                   ant.id AS anterior_id, ant.nombre AS anterior_nombre
              FROM clubes c
              LEFT JOIN clubes ant ON ant.id = c.club_anterior_id
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

    /** Club de la liga con candado de fila: serializa el cambio de nombre con el alta de equipos. */
    Optional<ClubDto> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE c.id = :id AND c.liga_id = :ligaId FOR UPDATE OF c")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(ClubRepository::aDto)
                .optional();
    }

    /**
     * Club de la liga con candado compartido: un alta de equipo no se cuela mientras otro proceso
     * cierra el club por cambio de nombre, y dos altas no se bloquean entre sí.
     */
    Optional<ClubDto> buscarParaCompartir(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE c.id = :id AND c.liga_id = :ligaId FOR SHARE OF c")
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

    /**
     * Equipos inscritos del club (DEC-49, DEC-61): no dados de baja, en un grupo de una temporada de
     * la liga en {@code ACTIVA} o {@code CONFIGURACION}.
     */
    long contarEquiposInscritos(UUID ligaId, UUID clubId) {
        return jdbc.sql("""
                        SELECT count(*)
                          FROM equipos e
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE e.club_id = :clubId
                           AND e.estado <> 'BAJA'
                           AND t.liga_id = :ligaId
                           AND t.estado IN ('ACTIVA', 'CONFIGURACION')
                        """)
                .param("clubId", clubId)
                .param("ligaId", ligaId)
                .query(Long.class)
                .single();
    }

    UUID insertar(UUID ligaId, String nombre) {
        return jdbc.sql("INSERT INTO clubes (liga_id, nombre) VALUES (:ligaId, :nombre) RETURNING id")
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .query(UUID.class)
                .single();
    }

    /** Club nuevo {@code ACTIVO} que proviene de otro por cambio de nombre (DEC-15). */
    UUID insertarPorCambioNombre(UUID ligaId, String nombre, UUID clubAnteriorId) {
        return jdbc.sql("""
                        INSERT INTO clubes (liga_id, nombre, club_anterior_id)
                        VALUES (:ligaId, :nombre, :clubAnteriorId)
                        RETURNING id
                        """)
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .param("clubAnteriorId", clubAnteriorId)
                .query(UUID.class)
                .single();
    }

    /** {@code ACTIVO → CERRADO} con fecha y motivo (RN-041, {@code ck_clubes_cierre}). */
    void cerrar(UUID id, LocalDate cerradoEn, String motivo) {
        jdbc.sql("""
                        UPDATE clubes
                           SET estado = 'CERRADO', cerrado_en = :cerradoEn, motivo_cierre = :motivo
                         WHERE id = :id AND estado = 'ACTIVO'
                        """)
                .param("id", id)
                .param("cerradoEn", cerradoEn)
                .param("motivo", motivo)
                .update();
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
        UUID anteriorId = fila.getObject("anterior_id", UUID.class);
        return new ClubDto(
                fila.getObject("id", UUID.class),
                fila.getString("nombre"),
                EstadoClub.valueOf(fila.getString("estado")),
                fila.getObject("creado_en", OffsetDateTime.class)
                        .atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime(),
                fila.getObject("cerrado_en", LocalDate.class),
                fila.getString("motivo_cierre"),
                anteriorId == null ? null : new ClubDto.ClubAnteriorDto(anteriorId, fila.getString("anterior_nombre")));
    }
}

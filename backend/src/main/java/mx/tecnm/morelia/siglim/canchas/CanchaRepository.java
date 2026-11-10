package mx.tecnm.morelia.siglim.canchas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code canchas}. El nombre del club se lee con un {@code LEFT JOIN} de solo lectura porque
 * el módulo de clubes aún no existe (D10). El nombre de la sede se lee también con un
 * {@code LEFT JOIN}: una cancha puede no tener sede (RF-028).
 *
 * <p>Refs: H-07.1, B-11.1, RF-027, RF-028, ADR-0011
 */
@Repository
class CanchaRepository {

    private static final String SELECCION = """
            SELECT c.id, c.nombre, c.ubicacion, c.sede_id, s.nombre AS sede_nombre, c.direccion, c.propietario,
                   c.club_id, cl.nombre AS club_nombre, c.propietario_externo, c.caracteristicas, c.activa,
                   c.creado_en
              FROM canchas c
              LEFT JOIN clubes cl ON cl.id = c.club_id
              LEFT JOIN sedes s ON s.id = c.sede_id
            """;

    private static final String CONDICIONES_LISTADO = """
             WHERE c.liga_id = :ligaId
               AND (:todaLaLiga OR c.club_id = ANY(:clubes))
               AND (CAST(:activa AS boolean) IS NULL OR c.activa = CAST(:activa AS boolean))
               AND (CAST(:ubicacion AS text) IS NULL OR CAST(c.ubicacion AS text) = CAST(:ubicacion AS text))
               AND (CAST(:propietario AS text) IS NULL OR CAST(c.propietario AS text) = CAST(:propietario AS text))
               AND (CAST(:sedeId AS uuid) IS NULL OR c.sede_id = CAST(:sedeId AS uuid))
               AND (CAST(:q AS text) IS NULL OR strpos(lower(c.nombre), lower(CAST(:q AS text))) > 0)
            """;

    private final JdbcClient jdbc;

    CanchaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<CanchaDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE c.id = :id AND c.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(CanchaRepository::aDto)
                .optional();
    }

    /** Lee la cancha y toma su candado: serializa las escrituras sobre ella hasta el fin de la transacción. */
    Optional<CanchaDto> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE c.id = :id AND c.liga_id = :ligaId FOR UPDATE OF c")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(CanchaRepository::aDto)
                .optional();
    }

    List<CanchaDto> listar(UUID ligaId, FiltroCanchas filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql(SELECCION + CONDICIONES_LISTADO + " ORDER BY " + filtro.ordenSql()
                        + " LIMIT :limite OFFSET :desplazamiento"), ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query(CanchaRepository::aDto)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, FiltroCanchas filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM canchas c" + CONDICIONES_LISTADO), ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    boolean existeNombre(UUID ligaId, String nombre, UUID excepto) {
        return jdbc.sql("""
                        SELECT EXISTS (
                                 SELECT 1 FROM canchas
                                  WHERE liga_id = :ligaId AND nombre = :nombre
                                    AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid)))
                        """)
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .param("excepto", excepto)
                .query(Boolean.class)
                .single();
    }

    UUID insertar(UUID ligaId, DatosCancha datos) {
        return conDatos(jdbc.sql("""
                        INSERT INTO canchas (liga_id, nombre, ubicacion, direccion, propietario, club_id,
                                             propietario_externo, caracteristicas, sede_id)
                        VALUES (:ligaId, :nombre, CAST(:ubicacion AS ubicacion_cancha), :direccion,
                                CAST(:propietario AS propietario_cancha), :clubId, :propietarioExterno,
                                :caracteristicas, :sedeId)
                        RETURNING id
                        """), datos)
                .param("ligaId", ligaId)
                .query(UUID.class)
                .single();
    }

    void actualizar(UUID id, DatosCancha datos, boolean activa) {
        conDatos(jdbc.sql("""
                        UPDATE canchas
                           SET nombre = :nombre,
                               ubicacion = CAST(:ubicacion AS ubicacion_cancha),
                               direccion = :direccion,
                               propietario = CAST(:propietario AS propietario_cancha),
                               club_id = :clubId,
                               propietario_externo = :propietarioExterno,
                               caracteristicas = :caracteristicas,
                               sede_id = :sedeId,
                               activa = :activa
                         WHERE id = :id
                        """), datos)
                .param("id", id)
                .param("activa", activa)
                .update();
    }

    /** Valores que se escriben en una cancha. */
    record DatosCancha(String nombre, UbicacionCancha ubicacion, String direccion, PropietarioCancha propietario,
                       UUID clubId, String propietarioExterno, String caracteristicas, UUID sedeId) {
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, UUID ligaId,
            FiltroCanchas filtro, AlcanceAutorizacion alcance) {
        return sentencia
                .param("ligaId", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("activa", filtro.activa())
                .param("ubicacion", filtro.ubicacion() == null ? null : filtro.ubicacion().name())
                .param("propietario", filtro.propietario() == null ? null : filtro.propietario().name())
                .param("sedeId", filtro.sedeId())
                .param("q", filtro.texto());
    }

    private static JdbcClient.StatementSpec conDatos(JdbcClient.StatementSpec sentencia, DatosCancha datos) {
        return sentencia
                .param("nombre", datos.nombre())
                .param("ubicacion", datos.ubicacion().name())
                .param("direccion", datos.direccion())
                .param("propietario", datos.propietario().name())
                .param("clubId", datos.clubId())
                .param("propietarioExterno", datos.propietarioExterno())
                .param("caracteristicas", datos.caracteristicas())
                .param("sedeId", datos.sedeId());
    }

    private static CanchaDto aDto(ResultSet fila, int numero) throws SQLException {
        UUID clubId = fila.getObject("club_id", UUID.class);
        UUID sedeId = fila.getObject("sede_id", UUID.class);
        return new CanchaDto(
                fila.getObject("id", UUID.class),
                fila.getString("nombre"),
                UbicacionCancha.valueOf(fila.getString("ubicacion")),
                sedeId == null ? null : new CanchaDto.SedeResumenDto(sedeId, fila.getString("sede_nombre")),
                fila.getString("direccion"),
                PropietarioCancha.valueOf(fila.getString("propietario")),
                clubId == null ? null : new CanchaDto.ClubDto(clubId, fila.getString("club_nombre")),
                fila.getString("propietario_externo"),
                fila.getString("caracteristicas"),
                fila.getBoolean("activa"),
                Intervalo.enZonaDeNegocio(fila.getObject("creado_en", OffsetDateTime.class)));
    }
}

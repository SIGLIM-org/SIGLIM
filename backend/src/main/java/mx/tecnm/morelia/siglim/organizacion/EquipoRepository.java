package mx.tecnm.morelia.siglim.organizacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.organizacion.EquipoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.organizacion.EquipoDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code equipos}. El club, el grupo, la categoría y la temporada se leen con {@code JOIN}
 * para devolver las referencias anidadas sin una petición por fila (convenciones de API §9.1).
 *
 * <p>El grupo y su temporada se leen aquí, solo para validar (grupos → categorías de la temporada →
 * temporadas): el CRUD de grupos es de M-02 y este repositorio no lo reimplementa.
 *
 * <p>Refs: H-06.1, RF-009, RN-007, ADR-0007, ADR-0011
 */
@Repository
class EquipoRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String ORIGEN = """
              FROM equipos e
              JOIN clubes cl ON cl.id = e.club_id
              JOIN grupos g ON g.id = e.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN categorias c ON c.id = ct.categoria_id
              JOIN temporadas t ON t.id = ct.temporada_id
            """;

    private static final String SELECCION = """
            SELECT e.id, e.nombre, e.estado, e.creado_en, e.baja_en, e.motivo_baja,
                   cl.id AS club_id, cl.nombre AS club_nombre,
                   g.id AS grupo_id, g.nombre AS grupo_nombre,
                   c.id AS categoria_id, c.nombre AS categoria_nombre,
                   t.id AS temporada_id, t.nombre AS temporada_nombre, t.estado AS temporada_estado
            """ + ORIGEN;

    private static final String CONDICIONES_LISTADO = """
             WHERE cl.liga_id = :ligaId
               AND (:todaLaLiga OR e.club_id = ANY(:clubes))
               AND (CAST(:temporadaId AS uuid) IS NULL OR t.id = CAST(:temporadaId AS uuid))
               AND (CAST(:grupoId AS uuid) IS NULL OR e.grupo_id = CAST(:grupoId AS uuid))
               AND (CAST(:clubId AS uuid) IS NULL OR e.club_id = CAST(:clubId AS uuid))
               AND (CAST(:estado AS text) IS NULL OR CAST(e.estado AS text) = CAST(:estado AS text))
            """;

    private final JdbcClient jdbc;

    EquipoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<EquipoDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE e.id = :id AND cl.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(EquipoRepository::aDto)
                .optional();
    }

    /** Lee el equipo y toma su candado: serializa edición y baja hasta el fin de la transacción. */
    Optional<EquipoDto> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE e.id = :id AND cl.liga_id = :ligaId FOR UPDATE OF e")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(EquipoRepository::aDto)
                .optional();
    }

    List<EquipoDto> listar(UUID ligaId, FiltroEquipos filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql(SELECCION + CONDICIONES_LISTADO
                        + " ORDER BY c.orden, g.nombre, e.nombre, e.id LIMIT :limite OFFSET :desplazamiento"),
                ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query(EquipoRepository::aDto)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, FiltroEquipos filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("SELECT count(*)" + ORIGEN + CONDICIONES_LISTADO), ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    /** El grupo con su temporada, de la liga; vacío si no existe (RN-007). */
    Optional<GrupoTemporada> buscarGrupo(UUID ligaId, UUID grupoId) {
        return jdbc.sql("""
                        SELECT g.id, g.nombre, t.id AS temporada_id, t.nombre AS temporada_nombre,
                               t.estado AS temporada_estado
                          FROM grupos g
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE g.id = :grupoId AND t.liga_id = :ligaId
                        """)
                .param("grupoId", grupoId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new GrupoTemporada(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getObject("temporada_id", UUID.class),
                        fila.getString("temporada_nombre"),
                        EstadoTemporada.valueOf(fila.getString("temporada_estado"))))
                .optional();
    }

    boolean existeNombre(UUID grupoId, String nombre, UUID excepto) {
        return jdbc.sql("""
                        SELECT EXISTS (
                                 SELECT 1 FROM equipos
                                  WHERE grupo_id = :grupoId AND nombre = :nombre
                                    AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid)))
                        """)
                .param("grupoId", grupoId)
                .param("nombre", nombre)
                .param("excepto", excepto)
                .query(Boolean.class)
                .single();
    }

    UUID insertar(UUID clubId, UUID grupoId, String nombre) {
        return jdbc.sql("""
                        INSERT INTO equipos (club_id, grupo_id, nombre)
                        VALUES (:clubId, :grupoId, :nombre)
                        RETURNING id
                        """)
                .param("clubId", clubId)
                .param("grupoId", grupoId)
                .param("nombre", nombre)
                .query(UUID.class)
                .single();
    }

    void actualizar(UUID id, String nombre, UUID grupoId) {
        jdbc.sql("UPDATE equipos SET nombre = :nombre, grupo_id = :grupoId WHERE id = :id")
                .param("id", id)
                .param("nombre", nombre)
                .param("grupoId", grupoId)
                .update();
    }

    /** {@code ACTIVO → BAJA} con motivo y fecha; la fila se conserva (RN-005). */
    void darDeBaja(UUID id, String motivo, LocalDate bajaEn) {
        jdbc.sql("""
                        UPDATE equipos
                           SET estado = 'BAJA', motivo_baja = :motivo, baja_en = :bajaEn
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("motivo", motivo)
                .param("bajaEn", bajaEn)
                .update();
    }

    /** Grupo de una categoría de la temporada, con lo necesario para validar RF-003 y RN-007. */
    record GrupoTemporada(UUID id, String nombre, UUID temporadaId, String temporadaNombre,
                          EstadoTemporada temporadaEstado) {
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, UUID ligaId,
            FiltroEquipos filtro, AlcanceAutorizacion alcance) {
        return sentencia
                .param("ligaId", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("temporadaId", filtro.temporadaId())
                .param("grupoId", filtro.grupoId())
                .param("clubId", filtro.clubId())
                .param("estado", filtro.estado() == null ? null : filtro.estado().name());
    }

    private static EquipoDto aDto(ResultSet fila, int numero) throws SQLException {
        return new EquipoDto(
                fila.getObject("id", UUID.class),
                fila.getString("nombre"),
                EstadoEquipo.valueOf(fila.getString("estado")),
                new ReferenciaDto(fila.getObject("club_id", UUID.class), fila.getString("club_nombre")),
                new ReferenciaDto(fila.getObject("grupo_id", UUID.class), fila.getString("grupo_nombre")),
                new ReferenciaDto(fila.getObject("categoria_id", UUID.class), fila.getString("categoria_nombre")),
                new TemporadaResumenDto(fila.getObject("temporada_id", UUID.class),
                        fila.getString("temporada_nombre"),
                        EstadoTemporada.valueOf(fila.getString("temporada_estado"))),
                fila.getObject("creado_en", OffsetDateTime.class)
                        .atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime(),
                fila.getObject("baja_en", LocalDate.class),
                fila.getString("motivo_baja"));
    }
}

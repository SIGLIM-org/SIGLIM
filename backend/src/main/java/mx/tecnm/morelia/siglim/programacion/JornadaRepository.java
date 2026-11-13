package mx.tecnm.morelia.siglim.programacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code jornadas} y {@code partidos} (RF-033, RF-036, RF-040).
 *
 * <p><b>Candados.</b> La jornada se lee con {@code FOR UPDATE} antes de agregar un partido y el
 * partido con {@code FOR UPDATE} antes de ajustarlo, para que dos escrituras de la misma jornada no
 * se crucen (ADR-0011). La cancha también se bloquea con {@code FOR UPDATE}: así el ajuste no se
 * cruza con un bloqueo o una desactivación de cancha, que toman el mismo candado (H-07.1).
 *
 * <p>El bloque de cancha ({@code fin_bloque}) lo calcula el trigger {@code tg_partidos_programacion};
 * este repositorio nunca lo escribe.
 *
 * <p>Refs: H-12.1, RF-033, RF-036, RF-040, DEC-25, ADR-0011
 */
@Repository
class JornadaRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION_JORNADA = """
            SELECT j.id, j.grupo_id, j.fase_id, j.numero, j.estado_publicacion,
                   j.creado_en, j.actualizado_en
              FROM jornadas j
              JOIN grupos g ON g.id = j.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN temporadas t ON t.id = ct.temporada_id
             WHERE j.id = :id AND t.liga_id = :ligaId
            """;

    private static final String SELECCION_PARTIDO = """
            SELECT p.id, p.jornada_id, j.grupo_id, j.estado_publicacion,
                   p.equipo_local_id, l.nombre AS local_nombre, l.club_id AS local_club_id,
                   p.equipo_visitante_id, v.nombre AS visitante_nombre, v.club_id AS visitante_club_id,
                   p.cancha_id, c.nombre AS cancha_nombre,
                   p.inicio, p.fin_bloque, p.estado, p.motivo_localia,
                   p.creado_en, p.actualizado_en
              FROM partidos p
              JOIN jornadas j ON j.id = p.jornada_id
              JOIN grupos g ON g.id = j.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN temporadas t ON t.id = ct.temporada_id
              JOIN equipos l ON l.id = p.equipo_local_id
              JOIN equipos v ON v.id = p.equipo_visitante_id
              JOIN canchas c ON c.id = p.cancha_id
            """;

    private final JdbcClient jdbc;

    JornadaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── Grupos, equipos y canchas ───────────────────────────────────────────────────────────────

    /** Grupo de la liga con su categoría de temporada; sirve para validar y crear la jornada. */
    Optional<GrupoDeLiga> buscarGrupo(UUID ligaId, UUID grupoId) {
        return jdbc.sql("""
                        SELECT g.id, g.categoria_temporada_id
                          FROM grupos g
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE g.id = :id AND t.liga_id = :ligaId
                        """)
                .param("id", grupoId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new GrupoDeLiga(
                        fila.getObject("id", UUID.class),
                        fila.getObject("categoria_temporada_id", UUID.class)))
                .optional();
    }

    /** Indica si la fase final pertenece al grupo, para no ligar una jornada a otra categoría. */
    boolean faseDelGrupo(UUID grupoId, UUID faseId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM fases WHERE id = :faseId AND grupo_id = :grupoId)")
                .param("faseId", faseId)
                .param("grupoId", grupoId)
                .query(Boolean.class)
                .single();
    }

    /** Equipo de la liga con su grupo y su club, para validar pertenencia y construir el objetivo. */
    Optional<EquipoDeLiga> buscarEquipo(UUID ligaId, UUID equipoId) {
        return jdbc.sql("""
                        SELECT e.id, e.nombre, e.grupo_id, e.club_id, e.estado
                          FROM equipos e
                          JOIN clubes c ON c.id = e.club_id
                         WHERE e.id = :id AND c.liga_id = :ligaId
                        """)
                .param("id", equipoId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new EquipoDeLiga(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getObject("grupo_id", UUID.class),
                        fila.getObject("club_id", UUID.class),
                        fila.getString("estado")))
                .optional();
    }

    /**
     * Bloquea la cancha de la liga y devuelve su nombre y estado, para que el validador de
     * conflictos explique VAL-005. Vacío si no existe en la liga.
     *
     * <p>[PROPUESTA] El ERS no fija si una cancha de otra liga es un 404 o una referencia inválida;
     * se trata como referencia inválida porque el identificador llega en el cuerpo.
     */
    Optional<CanchaDeLiga> bloquearCancha(UUID ligaId, UUID canchaId) {
        return jdbc.sql("""
                        SELECT id, nombre, activa
                          FROM canchas
                         WHERE id = :id AND liga_id = :ligaId
                         FOR UPDATE
                        """)
                .param("id", canchaId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new CanchaDeLiga(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getBoolean("activa")))
                .optional();
    }

    // ── Jornadas ────────────────────────────────────────────────────────────────────────────────

    /** Jornadas de la liga, con filtros opcionales de grupo y estado, ordenadas por número. */
    List<DatosJornada> listarJornadas(UUID ligaId, UUID grupoId, EstadoJornada estado, int limite,
            long desplazamiento) {
        return jdbc.sql("""
                        SELECT j.id, j.grupo_id, j.fase_id, j.numero, j.estado_publicacion,
                               j.creado_en, j.actualizado_en
                          FROM jornadas j
                          JOIN grupos g ON g.id = j.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE t.liga_id = :ligaId
                           AND (CAST(:grupoId AS uuid) IS NULL OR j.grupo_id = CAST(:grupoId AS uuid))
                           AND (CAST(:estado AS text) IS NULL
                                OR j.estado_publicacion = CAST(:estado AS estado_publicacion))
                         ORDER BY j.numero, j.id
                         LIMIT :limite OFFSET :desplazamiento
                        """)
                .param("ligaId", ligaId)
                .param("grupoId", grupoId)
                .param("estado", estado == null ? null : estado.name())
                .param("limite", limite)
                .param("desplazamiento", desplazamiento)
                .query(JornadaRepository::aJornada)
                .list();
    }

    /** Con la misma condición del listado, incluidos los filtros opcionales. */
    long contarJornadas(UUID ligaId, UUID grupoId, EstadoJornada estado) {
        return jdbc.sql("""
                        SELECT count(*)
                          FROM jornadas j
                          JOIN grupos g ON g.id = j.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE t.liga_id = :ligaId
                           AND (CAST(:grupoId AS uuid) IS NULL OR j.grupo_id = CAST(:grupoId AS uuid))
                           AND (CAST(:estado AS text) IS NULL
                                OR j.estado_publicacion = CAST(:estado AS estado_publicacion))
                        """)
                .param("ligaId", ligaId)
                .param("grupoId", grupoId)
                .param("estado", estado == null ? null : estado.name())
                .query(Long.class)
                .single();
    }

    Optional<DatosJornada> buscarJornada(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION_JORNADA)
                .param("id", id)
                .param("ligaId", ligaId)
                .query(JornadaRepository::aJornada)
                .optional();
    }

    /** Lee la jornada con candado: serializa el alta de partidos con el envío a aprobación. */
    Optional<DatosJornada> buscarJornadaParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION_JORNADA + " FOR UPDATE OF j")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(JornadaRepository::aJornada)
                .optional();
    }

    /** {@code true} si el grupo ya tiene ese número de jornada en la fase regular o en la fase. */
    boolean existeNumero(UUID grupoId, UUID faseId, int numero) {
        return jdbc.sql("""
                        SELECT EXISTS (
                                 SELECT 1 FROM jornadas
                                  WHERE grupo_id = :grupoId AND numero = :numero
                                    AND fase_id IS NOT DISTINCT FROM CAST(:faseId AS uuid))
                        """)
                .param("grupoId", grupoId)
                .param("faseId", faseId)
                .param("numero", numero)
                .query(Boolean.class)
                .single();
    }

    UUID insertarJornada(UUID grupoId, UUID faseId, int numero, UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO jornadas (grupo_id, fase_id, numero, creado_por)
                        VALUES (:grupoId, CAST(:faseId AS uuid), :numero, :creadoPor)
                        RETURNING id
                        """)
                .param("grupoId", grupoId)
                .param("faseId", faseId)
                .param("numero", numero)
                .param("creadoPor", creadoPor)
                .query(UUID.class)
                .single();
    }

    // ── Partidos ────────────────────────────────────────────────────────────────────────────────

    /** Partidos de la jornada ordenados por inicio. */
    List<DatosPartido> listarPartidos(UUID jornadaId) {
        return jdbc.sql(SELECCION_PARTIDO + " WHERE p.jornada_id = :jornadaId ORDER BY p.inicio, p.id")
                .param("jornadaId", jornadaId)
                .query(JornadaRepository::aPartido)
                .list();
    }

    Optional<DatosPartido> buscarPartido(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION_PARTIDO + " WHERE p.id = :id AND t.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(JornadaRepository::aPartido)
                .optional();
    }

    /** Lee el partido con candado: serializa el ajuste con otra edición o una transición de estado. */
    Optional<DatosPartido> buscarPartidoParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION_PARTIDO + " WHERE p.id = :id AND t.liga_id = :ligaId FOR UPDATE OF p")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(JornadaRepository::aPartido)
                .optional();
    }

    /**
     * Registra el partido. {@code fin_bloque} se envía igual a {@code inicio} porque el trigger
     * {@code tg_partidos_programacion} lo reemplaza con el bloque de la categoría (DEC-25).
     */
    UUID insertarPartido(UUID jornadaId, UUID equipoLocalId, UUID equipoVisitanteId, UUID canchaId,
            OffsetDateTime inicio, UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id,
                                              inicio, fin_bloque, creado_por)
                        VALUES (:jornadaId, :local, :visitante, :cancha, :inicio, :inicio, :creadoPor)
                        RETURNING id
                        """)
                .param("jornadaId", jornadaId)
                .param("local", equipoLocalId)
                .param("visitante", equipoVisitanteId)
                .param("cancha", canchaId)
                .param("inicio", inicio)
                .param("creadoPor", creadoPor)
                .query(UUID.class)
                .single();
    }

    /** Ajusta equipos, cancha e inicio; el trigger recalcula {@code fin_bloque}. */
    void actualizarPartido(UUID id, UUID equipoLocalId, UUID equipoVisitanteId, UUID canchaId,
            OffsetDateTime inicio) {
        jdbc.sql("""
                        UPDATE partidos
                           SET equipo_local_id = :local,
                               equipo_visitante_id = :visitante,
                               cancha_id = :cancha,
                               inicio = :inicio
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("local", equipoLocalId)
                .param("visitante", equipoVisitanteId)
                .param("cancha", canchaId)
                .param("inicio", inicio)
                .update();
    }

    // ── Filas ───────────────────────────────────────────────────────────────────────────────────

    /** Grupo de la liga con la categoría de temporada a la que pertenece. */
    record GrupoDeLiga(UUID id, UUID categoriaTemporadaId) {
    }

    /** Equipo de la liga con lo necesario para validar grupo, club y si está dado de baja. */
    record EquipoDeLiga(UUID id, String nombre, UUID grupoId, UUID clubId, String estado) {
    }

    /** Cancha de la liga con el nombre y el estado que VAL-005 necesita explicar. */
    record CanchaDeLiga(UUID id, String nombre, boolean activa) {
    }

    /** Fila de {@code jornadas} con las fechas en la zona de negocio. */
    record DatosJornada(UUID id, UUID grupoId, UUID faseId, int numero, EstadoJornada estadoPublicacion,
                        OffsetDateTime creadoEn, OffsetDateTime actualizadoEn) {
    }

    /** Fila de {@code partidos} con los nombres, los clubes y el estado de su jornada. */
    record DatosPartido(UUID id, UUID jornadaId, UUID grupoId, EstadoJornada estadoJornada,
                        UUID equipoLocalId, String equipoLocalNombre, UUID clubLocalId,
                        UUID equipoVisitanteId, String equipoVisitanteNombre, UUID clubVisitanteId,
                        UUID canchaId, String canchaNombre, OffsetDateTime inicio, OffsetDateTime finBloque,
                        EstadoPartido estado, String motivoLocalia, OffsetDateTime creadoEn,
                        OffsetDateTime actualizadoEn) {
    }

    private static DatosJornada aJornada(ResultSet fila, int numero) throws SQLException {
        return new DatosJornada(
                fila.getObject("id", UUID.class),
                fila.getObject("grupo_id", UUID.class),
                fila.getObject("fase_id", UUID.class),
                fila.getInt("numero"),
                EstadoJornada.valueOf(fila.getString("estado_publicacion")),
                aZona(fila.getObject("creado_en", OffsetDateTime.class)),
                aZona(fila.getObject("actualizado_en", OffsetDateTime.class)));
    }

    private static DatosPartido aPartido(ResultSet fila, int numero) throws SQLException {
        return new DatosPartido(
                fila.getObject("id", UUID.class),
                fila.getObject("jornada_id", UUID.class),
                fila.getObject("grupo_id", UUID.class),
                EstadoJornada.valueOf(fila.getString("estado_publicacion")),
                fila.getObject("equipo_local_id", UUID.class),
                fila.getString("local_nombre"),
                fila.getObject("local_club_id", UUID.class),
                fila.getObject("equipo_visitante_id", UUID.class),
                fila.getString("visitante_nombre"),
                fila.getObject("visitante_club_id", UUID.class),
                fila.getObject("cancha_id", UUID.class),
                fila.getString("cancha_nombre"),
                aZona(fila.getObject("inicio", OffsetDateTime.class)),
                aZona(fila.getObject("fin_bloque", OffsetDateTime.class)),
                EstadoPartido.valueOf(fila.getString("estado")),
                fila.getString("motivo_localia"),
                aZona(fila.getObject("creado_en", OffsetDateTime.class)),
                aZona(fila.getObject("actualizado_en", OffsetDateTime.class)));
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }
}

package mx.tecnm.morelia.siglim.partidos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.ArbitroDto;
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.CedulaResumenDto;
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.JornadaDto;
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.ResultadoDto;
import mx.tecnm.morelia.siglim.programacion.EstadoJornada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * Consultas y cambios de estado del partido (M-08.1).
 *
 * <p><b>Visibilidad.</b> Un solo predicado, con el {@link AlcanceAutorizacion} de
 * {@code partidos.partido:CONSULTAR}, acota el listado, su total, el detalle y el historial
 * (ADR-0007): el ámbito {@code LIGA} ve todos los partidos de la liga; {@code CLUB}, los de sus
 * equipos; {@code PROPIO}, los de los equipos donde la persona de la sesión (o un menor bajo su
 * tutela) tiene un registro no rechazado o donde está designada como árbitro. Club y propio solo ven
 * jornadas {@code PUBLICADO} <b>[PROPUESTA]</b> (DEC-161).
 *
 * <p>Las fechas se devuelven en {@code America/Mexico_City}.
 *
 * <p>Refs: H-15.1, RF-043, RF-045, RF-046, ADR-0007, ADR-0014, DEC-161
 */
@Repository
class PartidosRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String DESDE = """
              FROM partidos p
              JOIN jornadas j ON j.id = p.jornada_id
              JOIN grupos g ON g.id = j.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN categorias cat ON cat.id = ct.categoria_id
              JOIN temporadas t ON t.id = ct.temporada_id
              JOIN equipos el ON el.id = p.equipo_local_id
              JOIN equipos ev ON ev.id = p.equipo_visitante_id
              JOIN canchas ca ON ca.id = p.cancha_id
            """;

    private static final String VISIBLE = """
             t.liga_id = :liga
             AND (:todaLaLiga
                  OR (j.estado_publicacion = 'PUBLICADO'
                      AND (el.club_id = ANY(:clubes) OR ev.club_id = ANY(:clubes)
                           OR (:propio AND (
                                 EXISTS (SELECT 1 FROM registros_jugador r
                                          WHERE r.equipo_id IN (p.equipo_local_id, p.equipo_visitante_id)
                                            AND r.estado <> 'RECHAZADO' AND siglim.es_titular(r.persona_id))
                                 OR EXISTS (SELECT 1 FROM designaciones d JOIN arbitros a ON a.id = d.arbitro_id
                                             WHERE d.partido_id = p.id AND d.vigente
                                               AND siglim.es_titular(a.persona_id)))))))
            """;

    private static final String COLUMNAS = """
            SELECT p.id, p.inicio, p.fin_bloque, p.estado,
                   j.id AS jornada_id, j.numero AS jornada_numero, j.estado_publicacion,
                   t.id AS temporada_id, t.nombre AS temporada_nombre,
                   cat.id AS categoria_id, cat.nombre AS categoria_nombre,
                   g.id AS grupo_id, g.nombre AS grupo_nombre,
                   el.id AS local_id, el.nombre AS local_nombre,
                   ev.id AS visitante_id, ev.nombre AS visitante_nombre,
                   ca.id AS cancha_id, ca.nombre AS cancha_nombre
            """;

    private final JdbcClient jdbc;

    PartidosRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Partidos visibles con los filtros, por inicio ascendente. */
    List<DatosPartido> listar(UUID ligaId, FiltroPartidos filtro, AlcanceAutorizacion alcance) {
        return conFiltros(jdbc.sql(COLUMNAS + DESDE + " WHERE " + VISIBLE + FILTROS
                        + " ORDER BY p.inicio, p.id LIMIT :limite OFFSET :desplazamiento"), ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", (long) filtro.pagina() * filtro.tamano())
                .query(PartidosRepository::aDatos)
                .list();
    }

    /** Total de partidos visibles con los filtros: la misma condición que el listado. */
    long contar(UUID ligaId, FiltroPartidos filtro, AlcanceAutorizacion alcance) {
        return conFiltros(jdbc.sql("SELECT count(*)" + DESDE + " WHERE " + VISIBLE + FILTROS), ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    /** El partido, si es visible con ese alcance. */
    Optional<DatosPartido> buscarVisible(UUID ligaId, UUID partidoId, AlcanceAutorizacion alcance) {
        return conAlcance(jdbc.sql(COLUMNAS + DESDE + " WHERE p.id = :partido AND " + VISIBLE), ligaId, alcance)
                .param("partido", partidoId)
                .query(PartidosRepository::aDatos)
                .optional();
    }

    /** El partido de la liga, bloqueado para cambiar su estado. */
    Optional<DatosPartido> bloquear(UUID ligaId, UUID partidoId) {
        return jdbc.sql(COLUMNAS + DESDE + " WHERE p.id = :partido AND t.liga_id = :liga FOR UPDATE OF p")
                .param("partido", partidoId)
                .param("liga", ligaId)
                .query(PartidosRepository::aDatos)
                .optional();
    }

    /** Historial del partido, del más antiguo al más reciente (RF-045). */
    List<CambioEstadoPartidoDto> historial(UUID partidoId) {
        return jdbc.sql("""
                        SELECT h.estado_anterior, h.estado_nuevo, h.motivo, h.cambiado_en,
                               h.cambiado_por, pf.nombre_mostrado
                          FROM partido_estado_historial h
                          LEFT JOIN perfiles pf ON pf.id = h.cambiado_por
                         WHERE h.partido_id = :partido
                         ORDER BY h.cambiado_en, h.id
                        """)
                .param("partido", partidoId)
                .query((fila, numero) -> {
                    String anterior = fila.getString("estado_anterior");
                    UUID perfil = fila.getObject("cambiado_por", UUID.class);
                    return new CambioEstadoPartidoDto(
                            anterior == null ? null : EstadoPartido.valueOf(anterior),
                            EstadoPartido.valueOf(fila.getString("estado_nuevo")),
                            fila.getString("motivo"),
                            perfil == null ? null : new ReferenciaDto(perfil, fila.getString("nombre_mostrado")),
                            enZonaDeNegocio(fila.getObject("cambiado_en", OffsetDateTime.class)));
                })
                .list();
    }

    /** Árbitros con designación vigente, el central primero; el nombre va {@code null} si RLS oculta la persona (DEC-78). */
    List<ArbitroDto> arbitros(UUID partidoId) {
        return jdbc.sql("""
                        SELECT a.id, CASE WHEN pe.id IS NULL THEN NULL
                                    ELSE concat_ws(' ', pe.nombre, pe.apellido_paterno, pe.apellido_materno) END AS nombre,
                               d.funcion
                          FROM designaciones d
                          JOIN arbitros a ON a.id = d.arbitro_id
                          LEFT JOIN personas pe ON pe.id = a.persona_id
                         WHERE d.partido_id = :partido AND d.vigente
                         ORDER BY d.funcion = 'CENTRAL' DESC, nombre, a.id
                        """)
                .param("partido", partidoId)
                .query((fila, numero) -> new ArbitroDto(fila.getObject("id", UUID.class), fila.getString("nombre"),
                        fila.getString("funcion")))
                .list();
    }

    Optional<CedulaResumenDto> cedula(UUID partidoId) {
        return jdbc.sql("SELECT id, estado FROM cedulas WHERE partido_id = :partido")
                .param("partido", partidoId)
                .query((fila, numero) -> new CedulaResumenDto(fila.getObject("id", UUID.class), fila.getString("estado")))
                .optional();
    }

    /** Resultado oficial vigente (RF-048, RF-052). */
    Optional<ResultadoDto> resultadoVigente(UUID partidoId) {
        return jdbc.sql("""
                        SELECT goles_local, goles_visitante, penales_local, penales_visitante, por_default, version
                          FROM resultados
                         WHERE partido_id = :partido AND vigente
                        """)
                .param("partido", partidoId)
                .query((fila, numero) -> new ResultadoDto(
                        fila.getInt("goles_local"),
                        fila.getInt("goles_visitante"),
                        fila.getObject("penales_local", Integer.class),
                        fila.getObject("penales_visitante", Integer.class),
                        fila.getBoolean("por_default"),
                        fila.getInt("version")))
                .optional();
    }

    /**
     * Suspende o cancela: cambia solo el estado. No nombra {@code inicio} ni {@code cancha_id} en el
     * {@code SET} para que no se dispare {@code tg_partidos_programacion} ({@code UPDATE OF}), que
     * rechazaría suspender en una cancha que se acaba de bloquear o con un equipo dado de baja, y que
     * recalcularía {@code fin_bloque}. El motivo lo toma el trigger del historial de
     * {@code siglim.motivo}, que el servicio fija antes.
     */
    void cambiarEstado(UUID partidoId, EstadoPartido estado) {
        jdbc.sql("""
                        UPDATE partidos
                           SET estado = CAST(:estado AS siglim.estado_partido),
                               actualizado_en = now()
                         WHERE id = :id
                        """)
                .param("id", partidoId)
                .param("estado", estado.name())
                .update();
    }

    /**
     * Reprograma: estado {@code REPROGRAMADO} con el nuevo inicio y la cancha. Aquí sí corre
     * {@code tg_partidos_programacion}, que recalcula {@code fin_bloque} (DEC-25) y repite las reglas de
     * conflictos como última garantía (DEC-160).
     */
    void reprogramar(UUID partidoId, OffsetDateTime inicio, UUID canchaId) {
        jdbc.sql("""
                        UPDATE partidos
                           SET estado = 'REPROGRAMADO',
                               inicio = :inicio,
                               cancha_id = :cancha,
                               actualizado_en = now()
                         WHERE id = :id
                        """)
                .param("id", partidoId)
                .param("inicio", inicio, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("cancha", canchaId)
                .update();
    }

    /**
     * Pasa a {@code EN_CURSO} los partidos {@code PROGRAMADO} o {@code REPROGRAMADO} de jornadas
     * publicadas de la liga cuyo inicio ya llegó (RF-127, ADR-0009 §2). Es un solo {@code UPDATE}
     * condicional: repetirlo no encuentra filas, y si otra ejecución ya los inició, la condición del
     * {@code WHERE} se vuelve a evaluar sobre la fila bloqueada y la excluye. Solo cambia
     * {@code estado}, así que no corre {@code tg_partidos_programacion}; el historial lo escribe
     * {@code tg_partidos_historial} con el motivo fijado por el servicio.
     *
     * @return solo los partidos que cambiaron en esta ejecución, con su estado anterior
     */
    List<PartidoIniciado> iniciarLlegados(UUID ligaId, OffsetDateTime ahora) {
        return jdbc.sql("""
                        WITH llegados AS (
                            SELECT p.id, p.estado
                              FROM partidos p
                              JOIN jornadas j ON j.id = p.jornada_id
                              JOIN grupos g ON g.id = j.grupo_id
                              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                              JOIN temporadas t ON t.id = ct.temporada_id
                             WHERE t.liga_id = :liga
                               AND j.estado_publicacion = 'PUBLICADO'
                               AND p.estado IN ('PROGRAMADO', 'REPROGRAMADO')
                               AND p.inicio <= :ahora
                        )
                        UPDATE partidos p
                           SET estado = 'EN_CURSO', actualizado_en = now()
                          FROM llegados l
                         WHERE p.id = l.id AND p.estado IN ('PROGRAMADO', 'REPROGRAMADO')
                        RETURNING p.id, l.estado AS estado_anterior, p.inicio
                        """)
                .param("liga", ligaId)
                .param("ahora", ahora, Types.TIMESTAMP_WITH_TIMEZONE)
                .query((fila, numero) -> new PartidoIniciado(
                        fila.getObject("id", UUID.class),
                        EstadoPartido.valueOf(fila.getString("estado_anterior")),
                        fila.getObject("inicio", OffsetDateTime.class)))
                .list();
    }

    /** Partido que la tarea de inicio pasó a {@code EN_CURSO}. */
    record PartidoIniciado(UUID id, EstadoPartido estadoAnterior, OffsetDateTime inicio) {
    }

    private static final String FILTROS = """
             AND (CAST(:jornada AS uuid) IS NULL OR p.jornada_id = :jornada)
             AND (CAST(:estadoFiltro AS text) IS NULL OR p.estado = CAST(:estadoFiltro AS siglim.estado_partido))
             AND (CAST(:desde AS timestamptz) IS NULL OR p.inicio >= :desde)
             AND (CAST(:hasta AS timestamptz) IS NULL OR p.inicio < :hasta)
            """;

    private static StatementSpec conFiltros(StatementSpec sentencia, UUID ligaId, FiltroPartidos filtro,
            AlcanceAutorizacion alcance) {
        return conAlcance(sentencia, ligaId, alcance)
                .param("jornada", filtro.jornadaId(), Types.OTHER)
                .param("estadoFiltro", filtro.estado() == null ? null : filtro.estado().name(), Types.VARCHAR)
                .param("desde", filtro.desde(), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("hasta", filtro.hasta(), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private static StatementSpec conAlcance(StatementSpec sentencia, UUID ligaId, AlcanceAutorizacion alcance) {
        return sentencia
                .param("liga", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("propio", alcance.propio());
    }

    private static DatosPartido aDatos(ResultSet fila, int numero) throws SQLException {
        return new DatosPartido(
                fila.getObject("id", UUID.class),
                new JornadaDto(fila.getObject("jornada_id", UUID.class), fila.getInt("jornada_numero"),
                        EstadoJornada.valueOf(fila.getString("estado_publicacion"))),
                referencia(fila, "temporada"),
                referencia(fila, "categoria"),
                referencia(fila, "grupo"),
                referencia(fila, "local"),
                referencia(fila, "visitante"),
                referencia(fila, "cancha"),
                enZonaDeNegocio(fila.getObject("inicio", OffsetDateTime.class)),
                enZonaDeNegocio(fila.getObject("fin_bloque", OffsetDateTime.class)),
                EstadoPartido.valueOf(fila.getString("estado")));
    }

    private static ReferenciaDto referencia(ResultSet fila, String prefijo) throws SQLException {
        return new ReferenciaDto(fila.getObject(prefijo + "_id", UUID.class), fila.getString(prefijo + "_nombre"));
    }

    private static OffsetDateTime enZonaDeNegocio(OffsetDateTime instante) {
        return instante == null ? null : instante.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    /** Partido con sus referencias, base del listado y del detalle. */
    record DatosPartido(UUID id, JornadaDto jornada, ReferenciaDto temporada, ReferenciaDto categoria,
                        ReferenciaDto grupo, ReferenciaDto equipoLocal, ReferenciaDto equipoVisitante,
                        ReferenciaDto cancha, OffsetDateTime inicio, OffsetDateTime finBloque, EstadoPartido estado) {
    }
}

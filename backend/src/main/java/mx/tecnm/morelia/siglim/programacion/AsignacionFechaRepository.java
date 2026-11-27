package mx.tecnm.morelia.siglim.programacion;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.Ocupacion;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.TipoPeriodo;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lecturas y escrituras de la asignación automática de una fecha (H-33.2). Las lecturas arman la
 * {@link SolicitudAsignacion} del motor y llevan {@code ORDER BY} explícito para que la misma base dé
 * la misma asignación (ADR-0015). Las escrituras nunca borran: los partidos se cancelan con su
 * historial y las designaciones pierden la vigencia (DEC-181, RF-044).
 *
 * <p>Toda lectura de {@code emparejamientos} usa solo el calendario vigente (DEC-189).
 *
 * <p>Refs: H-33.2, RF-035, RF-041, RF-042, RF-044, DEC-176, DEC-177, DEC-179, DEC-180, DEC-181, DEC-189
 */
@Repository
class AsignacionFechaRepository {

    private final JdbcClient jdbc;

    AsignacionFechaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── Parámetros y calendario ─────────────────────────────────────────────────────────────────

    /** Versión vigente de los parámetros con el horario de juego y RN-049; vacío si no hay (RF-006). */
    Optional<ParametrosAsignacion> parametrosVigentes(UUID temporadaId) {
        return jdbc.sql("""
                        SELECT p.version, p.hora_inicio_juego, p.hora_fin_juego,
                               p.fechas_fase_regular, p.fechas_finales_unidad_deportiva
                          FROM siglim.parametros_vigentes(:temporadaId) p
                         WHERE p.id IS NOT NULL
                        """)
                .param("temporadaId", temporadaId)
                .query((fila, n) -> new ParametrosAsignacion(
                        fila.getInt("version"),
                        fila.getObject("hora_inicio_juego", LocalTime.class),
                        fila.getObject("hora_fin_juego", LocalTime.class),
                        fila.getObject("fechas_fase_regular", Integer.class),
                        fila.getObject("fechas_finales_unidad_deportiva", Integer.class)))
                .optional();
    }

    /** Última fecha del calendario vigente de la temporada; 0 sin calendario (DEC-179, DEC-193). */
    int ultimaFechaDelCalendario(UUID temporadaId) {
        return jdbc.sql("""
                        SELECT coalesce(max(e.numero_fecha), 0)
                          FROM emparejamientos e
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                         WHERE ct.temporada_id = :temporadaId AND e.vigente
                        """)
                .param("temporadaId", temporadaId)
                .query(Integer.class)
                .single();
    }

    /**
     * Grupos con emparejamientos vigentes en la fecha, con su jornada de la fecha si ya existe y si
     * otra jornada de la fase regular del grupo ya usa ese número (índice {@code ux_jornadas_regular}).
     */
    List<GrupoDeLaFecha> gruposDeLaFecha(UUID temporadaId, UUID fechaId, int numero) {
        return jdbc.sql("""
                        SELECT g.id, g.nombre, jf.id AS jornada_id,
                               EXISTS (SELECT 1 FROM jornadas jm
                                        WHERE jm.grupo_id = g.id AND jm.fase_id IS NULL AND jm.numero = :numero
                                          AND jm.fecha_id IS DISTINCT FROM :fechaId) AS numero_ocupado
                          FROM grupos g
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          LEFT JOIN jornadas jf ON jf.fecha_id = :fechaId AND jf.grupo_id = g.id
                         WHERE ct.temporada_id = :temporadaId
                           AND EXISTS (SELECT 1 FROM emparejamientos e
                                        WHERE e.grupo_id = g.id AND e.vigente AND e.numero_fecha = :numero)
                         ORDER BY g.id
                        """)
                .param("temporadaId", temporadaId)
                .param("fechaId", fechaId)
                .param("numero", numero)
                .query((fila, n) -> new GrupoDeLaFecha(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getObject("jornada_id", UUID.class),
                        fila.getBoolean("numero_ocupado")))
                .list();
    }

    /**
     * Emparejamientos vigentes de la fecha que no son descanso y no tienen partido vigente (sin partido
     * o con uno {@code CANCELADO}), con la categoría del grupo y los equipos.
     */
    List<EmparejamientoPendiente> pendientes(UUID temporadaId, int numero) {
        return jdbc.sql("""
                        SELECT e.id, e.grupo_id, c.id AS categoria_id, c.nombre AS categoria_nombre, c.orden,
                               ct.dias_juego,
                               (extract(epoch FROM siglim.bloque_cancha(ct.temporada_id, c.id)) / 60)::int
                                   AS bloque_minutos,
                               l.id AS local_id, l.nombre AS local_nombre, l.club_id AS local_club_id,
                               l.estado AS local_estado,
                               v.id AS visitante_id, v.nombre AS visitante_nombre, v.club_id AS visitante_club_id,
                               v.estado AS visitante_estado
                          FROM emparejamientos e
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN categorias c ON c.id = ct.categoria_id
                          JOIN equipos l ON l.id = e.equipo_local_id
                          JOIN equipos v ON v.id = e.equipo_visitante_id
                          LEFT JOIN partidos p ON p.id = e.partido_id
                         WHERE ct.temporada_id = :temporadaId AND e.vigente AND e.numero_fecha = :numero
                           AND (p.id IS NULL OR p.estado = 'CANCELADO')
                         ORDER BY e.id
                        """)
                .param("temporadaId", temporadaId)
                .param("numero", numero)
                .query((fila, n) -> {
                    UUID grupoId = fila.getObject("grupo_id", UUID.class);
                    return new EmparejamientoPendiente(
                            fila.getObject("id", UUID.class),
                            grupoId,
                            fila.getObject("categoria_id", UUID.class),
                            fila.getString("categoria_nombre"),
                            fila.getInt("orden"),
                            dias(fila.getArray("dias_juego")),
                            fila.getInt("bloque_minutos"),
                            new JornadaRepository.EquipoDeLiga(fila.getObject("local_id", UUID.class),
                                    fila.getString("local_nombre"), grupoId,
                                    fila.getObject("local_club_id", UUID.class), fila.getString("local_estado")),
                            new JornadaRepository.EquipoDeLiga(fila.getObject("visitante_id", UUID.class),
                                    fila.getString("visitante_nombre"), grupoId,
                                    fila.getObject("visitante_club_id", UUID.class),
                                    fila.getString("visitante_estado")));
                })
                .list();
    }

    // ── Canchas y ocupación ─────────────────────────────────────────────────────────────────────

    /** Canchas de la liga, activas o no, en orden de {@code id}. */
    List<CanchaDeAsignacion> canchas(UUID ligaId) {
        return jdbc.sql("""
                        SELECT id, nombre, activa, ubicacion = 'UNIDAD_DEPORTIVA' AS unidad_deportiva
                          FROM canchas
                         WHERE liga_id = :ligaId
                         ORDER BY id
                        """)
                .param("ligaId", ligaId)
                .query((fila, n) -> new CanchaDeAsignacion(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getBoolean("activa"),
                        fila.getBoolean("unidad_deportiva")))
                .list();
    }

    /**
     * Periodos {@code DISPONIBLE}, {@code BLOQUEO} y {@code MANTENIMIENTO} de las canchas de la liga que
     * tocan {@code [desde, hasta)}, recortados a ese rango (un periodo sin límite no tiene fin).
     */
    List<PeriodoDeCancha> periodos(UUID ligaId, OffsetDateTime desde, OffsetDateTime hasta) {
        return jdbc.sql("""
                        SELECT d.cancha_id, d.tipo,
                               greatest(lower(d.periodo), CAST(:desde AS timestamptz)) AS desde,
                               least(upper(d.periodo), CAST(:hasta AS timestamptz)) AS hasta
                          FROM disponibilidad_canchas d
                          JOIN canchas c ON c.id = d.cancha_id
                         WHERE c.liga_id = :ligaId
                           AND d.periodo && tstzrange(CAST(:desde AS timestamptz), CAST(:hasta AS timestamptz))
                         ORDER BY d.cancha_id, lower(d.periodo), d.id
                        """)
                .param("ligaId", ligaId)
                .param("desde", desde, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("hasta", hasta, Types.TIMESTAMP_WITH_TIMEZONE)
                .query((fila, n) -> new PeriodoDeCancha(
                        fila.getObject("cancha_id", UUID.class),
                        TipoPeriodo.valueOf(fila.getString("tipo")),
                        fila.getObject("desde", OffsetDateTime.class),
                        fila.getObject("hasta", OffsetDateTime.class)))
                .list();
    }

    /**
     * Partidos no cancelados de la liga cuyo bloque toca {@code [desde, hasta)}: los ya asignados o
     * ajustados de la fecha y los de otras fechas en esos días. El motor no los mueve (DEC-181).
     */
    List<Ocupacion> ocupacion(UUID ligaId, OffsetDateTime desde, OffsetDateTime hasta) {
        return jdbc.sql("""
                        SELECT p.cancha_id, p.equipo_local_id, p.equipo_visitante_id, p.inicio, p.fin_bloque
                          FROM partidos p
                          JOIN canchas c ON c.id = p.cancha_id
                         WHERE c.liga_id = :ligaId AND p.estado <> 'CANCELADO'
                           AND tstzrange(p.inicio, p.fin_bloque)
                               && tstzrange(CAST(:desde AS timestamptz), CAST(:hasta AS timestamptz))
                         ORDER BY p.inicio, p.id
                        """)
                .param("ligaId", ligaId)
                .param("desde", desde, Types.TIMESTAMP_WITH_TIMEZONE)
                .param("hasta", hasta, Types.TIMESTAMP_WITH_TIMEZONE)
                .query((fila, n) -> new Ocupacion(
                        fila.getObject("cancha_id", UUID.class),
                        fila.getObject("equipo_local_id", UUID.class),
                        fila.getObject("equipo_visitante_id", UUID.class),
                        fila.getObject("inicio", OffsetDateTime.class),
                        fila.getObject("fin_bloque", OffsetDateTime.class)))
                .list();
    }

    // ── Regenerar (DEC-181) ─────────────────────────────────────────────────────────────────────

    /** Partidos no cancelados de las jornadas de la fecha, con lo necesario para nombrarlos. */
    List<PartidoDeLaFecha> partidosDeLaFecha(UUID fechaId) {
        return jdbc.sql("""
                        SELECT p.id, p.estado, l.nombre AS local_nombre, v.nombre AS visitante_nombre
                          FROM partidos p
                          JOIN jornadas j ON j.id = p.jornada_id
                          JOIN equipos l ON l.id = p.equipo_local_id
                          JOIN equipos v ON v.id = p.equipo_visitante_id
                         WHERE j.fecha_id = :fechaId AND p.estado <> 'CANCELADO'
                         ORDER BY p.inicio, p.id
                        """)
                .param("fechaId", fechaId)
                .query((fila, n) -> new PartidoDeLaFecha(
                        fila.getObject("id", UUID.class),
                        EstadoPartido.valueOf(fila.getString("estado")),
                        fila.getString("local_nombre"),
                        fila.getString("visitante_nombre")))
                .list();
    }

    /**
     * Cancela el partido con el mismo SQL de {@code PartidosRepository.cambiarEstado} (H-15.1): solo
     * cambia {@code estado}, así que {@code tg_partidos_programacion} no corre, y
     * {@code tg_partidos_historial} guarda el motivo que el servicio fijó en {@code siglim.motivo}.
     */
    void cancelarPartido(UUID partidoId) {
        jdbc.sql("""
                        UPDATE partidos
                           SET estado = 'CANCELADO',
                               actualizado_en = now()
                         WHERE id = :id
                        """)
                .param("id", partidoId)
                .update();
    }

    /**
     * Deja sin vigencia las designaciones de los partidos con el motivo, como
     * {@code DesignacionRepository} (RN-005): nada se borra.
     */
    int retirarDesignaciones(List<UUID> partidoIds, String motivo) {
        if (partidoIds.isEmpty()) {
            return 0;
        }
        return jdbc.sql("""
                        UPDATE designaciones
                           SET vigente = false, motivo_cambio = :motivo
                         WHERE partido_id IN (:partidos) AND vigente
                        """)
                .param("partidos", partidoIds)
                .param("motivo", motivo)
                .update();
    }

    /**
     * Suelta los emparejamientos vigentes de la fecha cuyo partido quedó cancelado y limpia las causas
     * de todos los que no tienen partido vigente: la asignación los vuelve a procesar (DEC-181).
     */
    void soltarEmparejamientos(UUID temporadaId, int numero) {
        jdbc.sql("""
                        UPDATE emparejamientos e
                           SET partido_id = NULL,
                               codigo_no_asignado = NULL,
                               causa_no_asignado = NULL
                          FROM grupos g
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                         WHERE g.id = e.grupo_id AND ct.temporada_id = :temporadaId
                           AND e.vigente AND e.numero_fecha = :numero AND e.equipo_visitante_id IS NOT NULL
                           AND (e.partido_id IS NULL
                                OR EXISTS (SELECT 1 FROM partidos p
                                            WHERE p.id = e.partido_id AND p.estado = 'CANCELADO'))
                        """)
                .param("temporadaId", temporadaId)
                .param("numero", numero)
                .update();
    }

    // ── Resultado ───────────────────────────────────────────────────────────────────────────────

    /** Guarda por qué el emparejamiento quedó sin partido (DEC-180). */
    void marcarNoAsignado(UUID emparejamientoId, String codigo, String causa) {
        jdbc.sql("""
                        UPDATE emparejamientos
                           SET codigo_no_asignado = :codigo,
                               causa_no_asignado = :causa
                         WHERE id = :id AND vigente
                        """)
                .param("id", emparejamientoId)
                .param("codigo", codigo)
                .param("causa", causa)
                .update();
    }

    /** Quién y cuándo generó la fecha, y con qué parámetros (RF-041). */
    void registrarGeneracion(UUID fechaId, UUID generadaPor, String parametrosJson) {
        jdbc.sql("""
                        UPDATE fechas_programacion
                           SET generada_por = :perfil,
                               generada_en = now(),
                               parametros_generacion = CAST(:parametros AS jsonb),
                               actualizado_en = now()
                         WHERE id = :id
                        """)
                .param("id", fechaId)
                .param("perfil", generadaPor)
                .param("parametros", parametrosJson)
                .update();
    }

    private static Set<DayOfWeek> dias(Array arreglo) throws SQLException {
        Set<DayOfWeek> dias = new LinkedHashSet<>();
        if (arreglo != null) {
            for (Object valor : (Object[]) arreglo.getArray()) {
                dias.add(DayOfWeek.of(((Number) valor).intValue()));
            }
        }
        return dias;
    }

    // ── Filas ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Parámetros vigentes que usa la asignación.
     *
     * @param fechasFaseRegular total de fechas de la fase regular; {@code null} si no se definió
     * @param fechasFinales     últimas fechas en la unidad deportiva (RN-049); {@code null} sin definir
     */
    record ParametrosAsignacion(int version, LocalTime horaInicioJuego, LocalTime horaFinJuego,
                                Integer fechasFaseRegular, Integer fechasFinales) {
    }

    /** Grupo con emparejamientos en la fecha y su jornada de la fecha ({@code null} si falta crearla). */
    record GrupoDeLaFecha(UUID id, String nombre, UUID jornadaId, boolean numeroOcupado) {
    }

    /** Emparejamiento por asignar con su categoría (bloque en minutos) y sus equipos. */
    record EmparejamientoPendiente(UUID id, UUID grupoId, UUID categoriaId, String categoriaNombre, int orden,
                                   Set<DayOfWeek> diasJuego, int bloqueMinutos,
                                   JornadaRepository.EquipoDeLiga local, JornadaRepository.EquipoDeLiga visitante) {
    }

    /** Cancha de la liga para el motor. */
    record CanchaDeAsignacion(UUID id, String nombre, boolean activa, boolean unidadDeportiva) {
    }

    /** Periodo de una cancha ya recortado al rango consultado. */
    record PeriodoDeCancha(UUID canchaId, TipoPeriodo tipo, OffsetDateTime desde, OffsetDateTime hasta) {
    }

    /** Partido vigente de la fecha, para cancelarlo al regenerar. */
    record PartidoDeLaFecha(UUID id, EstadoPartido estado, String localNombre, String visitanteNombre) {
    }
}

package mx.tecnm.morelia.siglim.programacion;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code fechas_programacion} y de la vista por fecha de F-22 (H-35.1, DEC-175, DEC-180).
 *
 * <p>Una fecha agrupa las jornadas de todos los grupos de la misma semana ({@code jornadas.fecha_id})
 * y los emparejamientos del calendario con {@code numero_fecha} igual a su número. Su estado no se
 * guarda: se deriva de sus jornadas. Las jornadas y los partidos se leen y se actualizan con
 * {@link JornadaRepository}; aquí solo vive lo propio de la fecha.
 *
 * <p>Los partidos {@code CANCELADO} no se muestran (DEC-181): un emparejamiento cuyo partido se
 * canceló vuelve a contarse como sin asignar.
 *
 * <p>Refs: H-35.1, RF-033, RF-037, RF-042, RF-090, RF-121, DEC-175, DEC-180, DEC-181
 */
@Repository
class FechaRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    /** Conteos por fecha: jornadas por estado, partidos vigentes y emparejamientos sin asignar. */
    private static final String SELECCION_FECHA = """
            SELECT f.id, f.temporada_id, f.numero, f.dias, f.generada_por, f.generada_en, f.creado_por,
                   jj.jornadas, jj.en_borrador, jj.en_revision, pp.partidos, ee.sin_asignar
              FROM fechas_programacion f
              JOIN temporadas t ON t.id = f.temporada_id
              LEFT JOIN LATERAL (
                     SELECT count(*) AS jornadas,
                            count(*) FILTER (WHERE j.estado_publicacion = 'BORRADOR') AS en_borrador,
                            count(*) FILTER (WHERE j.estado_publicacion = 'REVISION') AS en_revision
                       FROM jornadas j
                      WHERE j.fecha_id = f.id) jj ON true
              LEFT JOIN LATERAL (
                     SELECT count(*) AS partidos
                       FROM partidos p
                       JOIN jornadas j ON j.id = p.jornada_id
                      WHERE j.fecha_id = f.id AND p.estado <> 'CANCELADO') pp ON true
              LEFT JOIN LATERAL (
                     SELECT count(*) AS sin_asignar
                       FROM emparejamientos e
                       JOIN grupos g ON g.id = e.grupo_id
                       JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                       LEFT JOIN partidos p ON p.id = e.partido_id
                      WHERE ct.temporada_id = f.temporada_id AND e.numero_fecha = f.numero
                        AND e.equipo_visitante_id IS NOT NULL
                        AND (p.id IS NULL OR p.estado = 'CANCELADO')) ee ON true
            """;

    private final JdbcClient jdbc;

    FechaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code true} si la temporada existe en la liga. */
    boolean existeTemporada(UUID ligaId, UUID temporadaId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM temporadas WHERE id = :id AND liga_id = :ligaId)")
                .param("id", temporadaId)
                .param("ligaId", ligaId)
                .query(Boolean.class)
                .single();
    }

    /** Fechas de la temporada por número, con sus conteos. */
    List<DatosFecha> listar(UUID ligaId, UUID temporadaId, int limite, long desplazamiento) {
        return jdbc.sql(SELECCION_FECHA + """
                         WHERE t.liga_id = :ligaId AND f.temporada_id = :temporadaId
                         ORDER BY f.numero, f.id
                         LIMIT :limite OFFSET :desplazamiento
                        """)
                .param("ligaId", ligaId)
                .param("temporadaId", temporadaId)
                .param("limite", limite)
                .param("desplazamiento", desplazamiento)
                .query(FechaRepository::aFecha)
                .list();
    }

    /** Con la misma condición del listado. */
    long contar(UUID ligaId, UUID temporadaId) {
        return jdbc.sql("""
                        SELECT count(*)
                          FROM fechas_programacion f
                          JOIN temporadas t ON t.id = f.temporada_id
                         WHERE t.liga_id = :ligaId AND f.temporada_id = :temporadaId
                        """)
                .param("ligaId", ligaId)
                .param("temporadaId", temporadaId)
                .query(Long.class)
                .single();
    }

    Optional<DatosFecha> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION_FECHA + " WHERE f.id = :id AND t.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(FechaRepository::aFecha)
                .optional();
    }

    /**
     * Bloquea la fila de la fecha de la liga: serializa el envío, el dictamen y, después, la
     * asignación automática sobre la misma fecha (RNF-004). Vacío si no existe en la liga.
     */
    boolean bloquear(UUID ligaId, UUID id) {
        return jdbc.sql("""
                        SELECT f.id
                          FROM fechas_programacion f
                          JOIN temporadas t ON t.id = f.temporada_id
                         WHERE f.id = :id AND t.liga_id = :ligaId
                         FOR UPDATE OF f
                        """)
                .param("id", id)
                .param("ligaId", ligaId)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    /**
     * Grupos que participan en la fecha: los que tienen una jornada ligada a ella o un emparejamiento
     * con su número. Ordenados por el primer día de juego de la categoría, el {@code orden} de la
     * categoría y el nombre del grupo (F-22).
     */
    List<GrupoDeFecha> listarGrupos(UUID fechaId, UUID temporadaId, int numero) {
        return jdbc.sql("""
                        SELECT g.id AS grupo_id, g.nombre AS grupo_nombre,
                               c.id AS categoria_id, c.nombre AS categoria_nombre, c.orden,
                               ct.dias_juego, j.id AS jornada_id, j.estado_publicacion
                          FROM grupos g
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN categorias c ON c.id = ct.categoria_id
                          LEFT JOIN jornadas j ON j.grupo_id = g.id AND j.fecha_id = :fechaId
                         WHERE ct.temporada_id = :temporadaId
                           AND (j.id IS NOT NULL
                                OR EXISTS (SELECT 1 FROM emparejamientos e
                                            WHERE e.grupo_id = g.id AND e.numero_fecha = :numero))
                         ORDER BY (SELECT min(d) FROM unnest(ct.dias_juego) AS d), c.orden, g.nombre, g.id
                        """)
                .param("fechaId", fechaId)
                .param("temporadaId", temporadaId)
                .param("numero", numero)
                .query((fila, n) -> new GrupoDeFecha(
                        fila.getObject("grupo_id", UUID.class),
                        fila.getString("grupo_nombre"),
                        fila.getObject("categoria_id", UUID.class),
                        fila.getString("categoria_nombre"),
                        fila.getInt("orden"),
                        enteros(fila.getArray("dias_juego")),
                        fila.getObject("jornada_id", UUID.class),
                        fila.getString("estado_publicacion") == null ? null
                                : EstadoJornada.valueOf(fila.getString("estado_publicacion"))))
                .list();
    }

    /**
     * Partidos vigentes de las jornadas de la fecha con su emparejamiento, su árbitro central vigente
     * y si se ajustaron a mano con motivo (auditoría {@code AJUSTAR_PARTIDO}, RF-040).
     */
    List<PartidoDeFecha> listarPartidos(UUID fechaId) {
        return jdbc.sql("""
                        SELECT p.id, j.id AS jornada_id, j.grupo_id, e.id AS emparejamiento_id,
                               p.equipo_local_id, l.nombre AS local_nombre,
                               p.equipo_visitante_id, v.nombre AS visitante_nombre,
                               p.cancha_id, c.nombre AS cancha_nombre,
                               p.inicio, p.fin_bloque, p.estado, p.motivo_localia, p.asignado_automaticamente,
                               EXISTS (SELECT 1 FROM auditoria a
                                        WHERE a.entidad = 'partidos' AND a.entidad_id = p.id::text
                                          AND a.accion = 'AJUSTAR_PARTIDO') AS ajustado,
                               ar.id AS arbitro_id,
                               concat_ws(' ', pe.nombre, pe.apellido_paterno) AS arbitro_nombre,
                               d.automatica AS designacion_automatica
                          FROM partidos p
                          JOIN jornadas j ON j.id = p.jornada_id
                          JOIN equipos l ON l.id = p.equipo_local_id
                          JOIN equipos v ON v.id = p.equipo_visitante_id
                          JOIN canchas c ON c.id = p.cancha_id
                          LEFT JOIN emparejamientos e ON e.partido_id = p.id
                          LEFT JOIN designaciones d ON d.partido_id = p.id AND d.vigente AND d.funcion = 'CENTRAL'
                          LEFT JOIN arbitros ar ON ar.id = d.arbitro_id
                          LEFT JOIN personas pe ON pe.id = ar.persona_id
                         WHERE j.fecha_id = :fechaId AND p.estado <> 'CANCELADO'
                         ORDER BY p.inicio, c.nombre, p.id
                        """)
                .param("fechaId", fechaId)
                .query((fila, n) -> new PartidoDeFecha(
                        fila.getObject("id", UUID.class),
                        fila.getObject("jornada_id", UUID.class),
                        fila.getObject("grupo_id", UUID.class),
                        fila.getObject("emparejamiento_id", UUID.class),
                        fila.getObject("equipo_local_id", UUID.class),
                        fila.getString("local_nombre"),
                        fila.getObject("equipo_visitante_id", UUID.class),
                        fila.getString("visitante_nombre"),
                        fila.getObject("cancha_id", UUID.class),
                        fila.getString("cancha_nombre"),
                        aZona(fila.getObject("inicio", OffsetDateTime.class)),
                        aZona(fila.getObject("fin_bloque", OffsetDateTime.class)),
                        EstadoPartido.valueOf(fila.getString("estado")),
                        fila.getString("motivo_localia"),
                        fila.getBoolean("asignado_automaticamente"),
                        fila.getBoolean("ajustado"),
                        fila.getObject("arbitro_id", UUID.class),
                        fila.getObject("arbitro_id", UUID.class) == null ? null : fila.getString("arbitro_nombre"),
                        (Boolean) fila.getObject("designacion_automatica")))
                .list();
    }

    /**
     * Emparejamientos de la fecha sin partido vigente: los descansos (visitante nulo, RF-037) y los
     * que no se asignaron, con su código y su causa (DEC-180). Uno cuyo partido se canceló al
     * regenerar (DEC-181) vuelve a quedar sin asignar.
     */
    List<EmparejamientoDeFecha> listarSinPartido(UUID temporadaId, int numero) {
        return jdbc.sql("""
                        SELECT e.id, e.grupo_id,
                               e.equipo_local_id, l.nombre AS local_nombre,
                               e.equipo_visitante_id, v.nombre AS visitante_nombre,
                               e.codigo_no_asignado, e.causa_no_asignado
                          FROM emparejamientos e
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN equipos l ON l.id = e.equipo_local_id
                          LEFT JOIN equipos v ON v.id = e.equipo_visitante_id
                          LEFT JOIN partidos p ON p.id = e.partido_id
                         WHERE ct.temporada_id = :temporadaId AND e.numero_fecha = :numero
                           AND (p.id IS NULL OR p.estado = 'CANCELADO')
                         ORDER BY l.nombre, e.id
                        """)
                .param("temporadaId", temporadaId)
                .param("numero", numero)
                .query((fila, n) -> new EmparejamientoDeFecha(
                        fila.getObject("id", UUID.class),
                        fila.getObject("grupo_id", UUID.class),
                        fila.getObject("equipo_local_id", UUID.class),
                        fila.getString("local_nombre"),
                        fila.getObject("equipo_visitante_id", UUID.class),
                        fila.getString("visitante_nombre"),
                        fila.getString("codigo_no_asignado"),
                        fila.getString("causa_no_asignado")))
                .list();
    }

    // ── Filas ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Fila de {@code fechas_programacion} con los conteos de sus jornadas. {@code generadaPor} y
     * {@code creadoPor} sirven para la separación de funciones del dictamen (RF-121).
     */
    record DatosFecha(UUID id, UUID temporadaId, int numero, List<LocalDate> dias, UUID generadaPor,
                      OffsetDateTime generadaEn, UUID creadoPor, long jornadas, long enBorrador,
                      long enRevision, long partidos, long sinAsignar) {

        /**
         * Estado derivado de sus jornadas (DEC-175): {@code BORRADOR} si no tiene jornadas o alguna
         * sigue en borrador; {@code REVISION} si alguna está en revisión; si no, {@code PUBLICADO}.
         * Como el envío y el dictamen mueven todas juntas, el estado mezclado solo aparece si alguien
         * usó los endpoints por jornada, y entonces se muestra el menos avanzado.
         */
        EstadoJornada estado() {
            if (jornadas == 0 || enBorrador > 0) {
                return EstadoJornada.BORRADOR;
            }
            return enRevision > 0 ? EstadoJornada.REVISION : EstadoJornada.PUBLICADO;
        }
    }

    /** Grupo de la fecha con su categoría y, si existe, su jornada en la fecha. */
    record GrupoDeFecha(UUID grupoId, String grupoNombre, UUID categoriaId, String categoriaNombre, int orden,
                        List<Integer> diasJuego, UUID jornadaId, EstadoJornada estadoJornada) {
    }

    /** Partido vigente de la fecha con lo que muestra la fila de F-22. */
    record PartidoDeFecha(UUID id, UUID jornadaId, UUID grupoId, UUID emparejamientoId,
                          UUID equipoLocalId, String equipoLocalNombre,
                          UUID equipoVisitanteId, String equipoVisitanteNombre,
                          UUID canchaId, String canchaNombre, OffsetDateTime inicio, OffsetDateTime finBloque,
                          EstadoPartido estado, String motivoLocalia, boolean asignadoAutomaticamente,
                          boolean ajustado, UUID arbitroId, String arbitroNombre, Boolean designacionAutomatica) {
    }

    /** Emparejamiento sin partido vigente: descanso si {@code equipoVisitanteId} es nulo. */
    record EmparejamientoDeFecha(UUID id, UUID grupoId, UUID equipoLocalId, String equipoLocalNombre,
                                 UUID equipoVisitanteId, String equipoVisitanteNombre,
                                 String codigoNoAsignado, String causaNoAsignado) {

        boolean esDescanso() {
            return equipoVisitanteId == null;
        }
    }

    private static DatosFecha aFecha(ResultSet fila, int numero) throws SQLException {
        return new DatosFecha(
                fila.getObject("id", UUID.class),
                fila.getObject("temporada_id", UUID.class),
                fila.getInt("numero"),
                fechas(fila.getArray("dias")),
                fila.getObject("generada_por", UUID.class),
                aZona(fila.getObject("generada_en", OffsetDateTime.class)),
                fila.getObject("creado_por", UUID.class),
                fila.getLong("jornadas"),
                fila.getLong("en_borrador"),
                fila.getLong("en_revision"),
                fila.getLong("partidos"),
                fila.getLong("sin_asignar"));
    }

    private static List<LocalDate> fechas(Array arreglo) throws SQLException {
        List<LocalDate> dias = new ArrayList<>();
        if (arreglo != null) {
            for (Object dia : (Object[]) arreglo.getArray()) {
                dias.add(dia instanceof Date fecha ? fecha.toLocalDate() : (LocalDate) dia);
            }
        }
        return List.copyOf(dias);
    }

    private static List<Integer> enteros(Array arreglo) throws SQLException {
        List<Integer> valores = new ArrayList<>();
        if (arreglo != null) {
            for (Object valor : (Object[]) arreglo.getArray()) {
                valores.add(((Number) valor).intValue());
            }
        }
        return List.copyOf(valores);
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }
}

package mx.tecnm.morelia.siglim.programacion;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL del calendario de la temporada: los grupos con sus equipos {@code ACTIVO}, los parámetros
 * vigentes y los emparejamientos (H-32.2, DEC-174, DEC-189). Las fechas de programación las crea y
 * las cambia {@link FechaRepository}.
 *
 * <p>Toda lectura de {@code emparejamientos} es del calendario vigente ({@code vigente}); regenerar
 * marca las filas anteriores como reemplazadas con su motivo y nunca borra (RN-020, DEC-189).
 *
 * <p>Refs: H-32.2, RF-034, RF-037, RN-012, RN-020, DEC-174, DEC-185, DEC-189, DEC-190
 */
@Repository
class CalendarioRepository {

    private final JdbcClient jdbc;

    CalendarioRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── Temporada y parámetros ──────────────────────────────────────────────────────────────────

    /** La temporada de la liga con candado: serializa dos generaciones de la misma temporada (RNF-004). */
    Optional<TemporadaCalendario> bloquearTemporada(UUID ligaId, UUID temporadaId) {
        return temporada(ligaId, temporadaId, " FOR UPDATE");
    }

    Optional<TemporadaCalendario> buscarTemporada(UUID ligaId, UUID temporadaId) {
        return temporada(ligaId, temporadaId, "");
    }

    private Optional<TemporadaCalendario> temporada(UUID ligaId, UUID temporadaId, String candado) {
        return jdbc.sql("""
                        SELECT id, nombre, estado, fecha_inicio, fecha_fin
                          FROM temporadas
                         WHERE id = :id AND liga_id = :ligaId
                        """ + candado)
                .param("id", temporadaId)
                .param("ligaId", ligaId)
                .query((fila, n) -> new TemporadaCalendario(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getString("estado"),
                        fila.getObject("fecha_inicio", LocalDate.class),
                        fila.getObject("fecha_fin", LocalDate.class)))
                .optional();
    }

    /** Versión y vueltas de los parámetros vigentes (DEC-174, DEC-190); vacío si la temporada no tiene. */
    Optional<ParametrosCalendario> parametrosVigentes(UUID temporadaId) {
        return jdbc.sql("""
                        SELECT p.version, p.vueltas
                          FROM siglim.parametros_vigentes(:temporadaId) p
                         WHERE p.id IS NOT NULL
                        """)
                .param("temporadaId", temporadaId)
                .query((fila, n) -> new ParametrosCalendario(fila.getInt("version"), fila.getInt("vueltas")))
                .optional();
    }

    // ── Grupos y equipos ────────────────────────────────────────────────────────────────────────

    /** Grupos de la temporada por el {@code orden} de su categoría y por nombre, y si ya tienen calendario vigente. */
    List<GrupoCalendario> listarGrupos(UUID temporadaId) {
        return jdbc.sql("""
                        SELECT g.id, g.nombre, c.id AS categoria_id, c.nombre AS categoria_nombre,
                               EXISTS (SELECT 1 FROM emparejamientos m
                                        WHERE m.grupo_id = g.id AND m.vigente) AS con_calendario
                          FROM grupos g
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN categorias c ON c.id = ct.categoria_id
                         WHERE ct.temporada_id = :temporadaId
                         ORDER BY c.orden, c.nombre, g.nombre, g.id
                        """)
                .param("temporadaId", temporadaId)
                .query((fila, n) -> new GrupoCalendario(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getObject("categoria_id", UUID.class),
                        fila.getString("categoria_nombre"),
                        fila.getBoolean("con_calendario")))
                .list();
    }

    /** Equipos {@code ACTIVO} del grupo por {@code id}, el mismo orden del generador (DEC-185). */
    List<UUID> equiposActivos(UUID grupoId) {
        return jdbc.sql("SELECT id FROM equipos WHERE grupo_id = :grupoId AND estado = 'ACTIVO' ORDER BY id")
                .param("grupoId", grupoId)
                .query(UUID.class)
                .list();
    }

    /**
     * Grupos que ya no pueden regenerarse (RN-020): con un emparejamiento vigente ligado a un partido o
     * con una jornada ligada a una fecha de programación.
     */
    List<UUID> gruposConProgramacion(Collection<UUID> grupoIds) {
        if (grupoIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT g.id
                          FROM grupos g
                         WHERE g.id = ANY (CAST(:grupos AS uuid[]))
                           AND (EXISTS (SELECT 1 FROM emparejamientos e
                                         WHERE e.grupo_id = g.id AND e.vigente AND e.partido_id IS NOT NULL)
                                OR EXISTS (SELECT 1 FROM jornadas j
                                            WHERE j.grupo_id = g.id AND j.fecha_id IS NOT NULL))
                         ORDER BY g.id
                        """)
                .param("grupos", arreglo(grupoIds))
                .query(UUID.class)
                .list();
    }

    /**
     * Jornadas manuales de la fase regular ({@code fecha_id} y {@code fase_id} nulos) de los grupos:
     * su número puede coincidir con una fecha del calendario (advertencia de DEC-193).
     */
    List<JornadaManual> jornadasManuales(Collection<UUID> grupoIds) {
        if (grupoIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        SELECT grupo_id, numero
                          FROM jornadas
                         WHERE grupo_id = ANY (CAST(:grupos AS uuid[]))
                           AND fecha_id IS NULL AND fase_id IS NULL
                         ORDER BY grupo_id, numero
                        """)
                .param("grupos", arreglo(grupoIds))
                .query((fila, n) -> new JornadaManual(fila.getObject("grupo_id", UUID.class), fila.getInt("numero")))
                .list();
    }

    // ── Emparejamientos ─────────────────────────────────────────────────────────────────────────

    /**
     * Marca como reemplazado el calendario vigente del grupo (DEC-189). El trigger
     * {@code tg_emparejamientos_reemplazo} rechaza con {@code RN-020} una fila con partido.
     *
     * @return filas reemplazadas
     */
    int reemplazar(UUID grupoId, UUID perfil, String motivo) {
        return jdbc.sql("""
                        UPDATE emparejamientos
                           SET vigente = false, reemplazado_por = :perfil, reemplazado_en = now(),
                               motivo_reemplazo = :motivo
                         WHERE grupo_id = :grupoId AND vigente
                        """)
                .param("grupoId", grupoId)
                .param("perfil", perfil)
                .param("motivo", motivo)
                .update();
    }

    /** Inserta el calendario del grupo en una sola sentencia, con su creador (DEC-192). */
    void insertar(UUID grupoId, List<Emparejamiento> calendario, UUID creadoPor) {
        jdbc.sql("""
                        INSERT INTO emparejamientos
                            (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id, creado_por)
                        SELECT :grupoId, x.vuelta, x.numero_fecha, x.local, x.visitante, :creadoPor
                          FROM unnest(CAST(:vueltas AS smallint[]), CAST(:numeros AS smallint[]),
                                      CAST(:locales AS uuid[]), CAST(:visitantes AS uuid[]))
                               AS x(vuelta, numero_fecha, local, visitante)
                        """)
                .param("grupoId", grupoId)
                .param("creadoPor", creadoPor)
                .param("vueltas", literal(calendario.stream().map(e -> String.valueOf(e.vuelta())).toList()))
                .param("numeros", literal(calendario.stream().map(e -> String.valueOf(e.numeroFecha())).toList()))
                .param("locales", literal(calendario.stream().map(e -> e.equipoLocalId().toString()).toList()))
                .param("visitantes", literal(calendario.stream()
                        .map(e -> e.equipoVisitanteId() == null ? "NULL" : e.equipoVisitanteId().toString()).toList()))
                .update();
    }

    /** Mayor número de fecha del calendario vigente de la temporada ({@code F}, DEC-193); 0 sin calendario. */
    int ultimaFechaVigente(UUID temporadaId) {
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

    /** Calendario vigente de la temporada con los equipos, por grupo, fecha y nombre del local. */
    List<FilaCalendario> listarVigentes(UUID temporadaId) {
        return jdbc.sql("""
                        SELECT e.id, e.grupo_id, e.vuelta, e.numero_fecha, e.partido_id,
                               l.id AS local_id, l.nombre AS local_nombre, l.estado AS local_estado,
                               v.id AS visitante_id, v.nombre AS visitante_nombre, v.estado AS visitante_estado
                          FROM emparejamientos e
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN equipos l ON l.id = e.equipo_local_id
                          LEFT JOIN equipos v ON v.id = e.equipo_visitante_id
                         WHERE ct.temporada_id = :temporadaId AND e.vigente
                         ORDER BY e.grupo_id, e.numero_fecha, l.nombre, e.id
                        """)
                .param("temporadaId", temporadaId)
                .query((fila, n) -> new FilaCalendario(
                        fila.getObject("id", UUID.class),
                        fila.getObject("grupo_id", UUID.class),
                        fila.getInt("vuelta"),
                        fila.getInt("numero_fecha"),
                        fila.getObject("partido_id", UUID.class),
                        new CalendarioDto.EquipoDto(fila.getObject("local_id", UUID.class),
                                fila.getString("local_nombre"), fila.getString("local_estado")),
                        fila.getObject("visitante_id", UUID.class) == null ? null
                                : new CalendarioDto.EquipoDto(fila.getObject("visitante_id", UUID.class),
                                        fila.getString("visitante_nombre"), fila.getString("visitante_estado"))))
                .list();
    }

    private static String arreglo(Collection<UUID> ids) {
        return literal(ids.stream().map(UUID::toString).toList());
    }

    /** Literal de arreglo de PostgreSQL; los valores son números, UUID o {@code NULL}, sin comillas. */
    private static String literal(List<String> valores) {
        return valores.stream().collect(Collectors.joining(",", "{", "}"));
    }

    // ── Filas ───────────────────────────────────────────────────────────────────────────────────

    /** Temporada con lo necesario para validar y calcular las fechas. */
    record TemporadaCalendario(UUID id, String nombre, String estado, LocalDate fechaInicio, LocalDate fechaFin) {

        boolean cerrada() {
            return "CERRADA".equals(estado);
        }
    }

    /** Versión vigente de los parámetros y sus vueltas. */
    record ParametrosCalendario(int version, int vueltas) {
    }

    /** Grupo de la temporada con su categoría y si ya tiene calendario vigente. */
    record GrupoCalendario(UUID id, String nombre, UUID categoriaId, String categoriaNombre, boolean conCalendario) {
    }

    /** Jornada manual de un grupo, por número. */
    record JornadaManual(UUID grupoId, int numero) {
    }

    /** Emparejamiento vigente con sus equipos; {@code visitante} nulo es descanso. */
    record FilaCalendario(UUID id, UUID grupoId, int vuelta, int numeroFecha, UUID partidoId,
                          CalendarioDto.EquipoDto local, CalendarioDto.EquipoDto visitante) {
    }
}

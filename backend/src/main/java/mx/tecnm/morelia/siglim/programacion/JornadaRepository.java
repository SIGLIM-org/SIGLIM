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
 * <p><b>Publicación (H-13.1).</b> El envío y el dictamen cambian {@code estado_publicacion} con un
 * {@code UPDATE} condicionado al estado de origen; el trigger {@code tg_jornadas_publicacion}
 * valida la transición, fija {@code publicada_en} y deja el paso en {@code historial_publicacion}.
 *
 * <p>Refs: H-12.1, H-13.1, RF-033, RF-036, RF-040, RF-090, RF-121, DEC-25, ADR-0011
 */
@Repository
class JornadaRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION_JORNADA = """
            SELECT j.id, j.grupo_id, j.fase_id, j.numero, j.estado_publicacion,
                   j.creado_por, j.enviada_por, j.enviada_en, j.aprobada_por, j.aprobada_en,
                   j.publicada_en, j.observaciones, j.creado_en, j.actualizado_en
              FROM jornadas j
              JOIN grupos g ON g.id = j.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN temporadas t ON t.id = ct.temporada_id
             WHERE j.id = :id AND t.liga_id = :ligaId
            """;

    private static final String SELECCION_PARTIDO = """
            SELECT p.id, p.jornada_id, j.grupo_id, j.estado_publicacion,
                   p.equipo_local_id, l.nombre AS local_nombre, l.club_id AS local_club_id,
                   l.estado AS local_estado,
                   p.equipo_visitante_id, v.nombre AS visitante_nombre, v.club_id AS visitante_club_id,
                   v.estado AS visitante_estado,
                   p.cancha_id, c.nombre AS cancha_nombre, c.activa AS cancha_activa,
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
                               j.creado_por, j.enviada_por, j.enviada_en, j.aprobada_por,
                               j.aprobada_en, j.publicada_en, j.observaciones,
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

    /**
     * Jornadas de una fecha de programación con candado, en orden de {@code id} para que dos
     * operaciones sobre la misma fecha tomen los candados en el mismo orden (H-35.1, DEC-175).
     */
    List<DatosJornada> bloquearJornadasDeFecha(UUID fechaId) {
        return jdbc.sql("""
                        SELECT j.id, j.grupo_id, j.fase_id, j.numero, j.estado_publicacion,
                               j.creado_por, j.enviada_por, j.enviada_en, j.aprobada_por, j.aprobada_en,
                               j.publicada_en, j.observaciones, j.creado_en, j.actualizado_en
                          FROM jornadas j
                         WHERE j.fecha_id = :fechaId
                         ORDER BY j.id
                         FOR UPDATE
                        """)
                .param("fechaId", fechaId)
                .query(JornadaRepository::aJornada)
                .list();
    }

    /** Jornadas de una fecha de programación, sin candado, en orden de {@code id} (H-35.1). */
    List<DatosJornada> listarJornadasDeFecha(UUID fechaId) {
        return jdbc.sql("""
                        SELECT j.id, j.grupo_id, j.fase_id, j.numero, j.estado_publicacion,
                               j.creado_por, j.enviada_por, j.enviada_en, j.aprobada_por, j.aprobada_en,
                               j.publicada_en, j.observaciones, j.creado_en, j.actualizado_en
                          FROM jornadas j
                         WHERE j.fecha_id = :fechaId
                         ORDER BY j.id
                        """)
                .param("fechaId", fechaId)
                .query(JornadaRepository::aJornada)
                .list();
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

    /**
     * Pasa la jornada de {@code BORRADOR} a {@code REVISION} con quien la envía y la hora de la base
     * (RF-121). El {@code WHERE} por estado evita sobrescribir un envío concurrente.
     *
     * @return filas actualizadas: 0 si la jornada ya no estaba en borrador
     */
    int enviarAAprobacion(UUID id, UUID enviadaPor) {
        return jdbc.sql("""
                        UPDATE jornadas
                           SET estado_publicacion = 'REVISION',
                               enviada_por = :enviadaPor,
                               enviada_en = now()
                         WHERE id = :id AND estado_publicacion = 'BORRADOR'
                        """)
                .param("id", id)
                .param("enviadaPor", enviadaPor)
                .update();
    }

    /**
     * Aplica el dictamen del Presidente a una jornada en {@code REVISION} (RF-121). Con
     * {@code PUBLICADO} fija {@code aprobada_por} y {@code aprobada_en}; con {@code BORRADOR} los deja
     * nulos. Las observaciones reemplazan las anteriores; el historial conserva cada motivo.
     *
     * @return filas actualizadas: 0 si la jornada ya no estaba en revisión
     */
    int resolver(UUID id, EstadoJornada estado, UUID aprobadaPor, String observaciones) {
        return jdbc.sql("""
                        UPDATE jornadas
                           SET estado_publicacion = CAST(:estado AS estado_publicacion),
                               aprobada_por = CAST(:aprobadaPor AS uuid),
                               aprobada_en = CASE WHEN :estado = 'PUBLICADO' THEN now() END,
                               observaciones = :observaciones
                         WHERE id = :id AND estado_publicacion = 'REVISION'
                        """)
                .param("id", id)
                .param("estado", estado.name())
                .param("aprobadaPor", aprobadaPor)
                .param("observaciones", observaciones)
                .update();
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
            OffsetDateTime inicio, String motivoLocalia, UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id,
                                              inicio, fin_bloque, motivo_localia, creado_por)
                        VALUES (:jornadaId, :local, :visitante, :cancha, :inicio, :inicio, :motivoLocalia,
                                :creadoPor)
                        RETURNING id
                        """)
                .param("jornadaId", jornadaId)
                .param("local", equipoLocalId)
                .param("visitante", equipoVisitanteId)
                .param("cancha", canchaId)
                .param("inicio", inicio)
                .param("motivoLocalia", motivoLocalia, java.sql.Types.VARCHAR)
                .param("creadoPor", creadoPor)
                .query(UUID.class)
                .single();
    }

    /**
     * Ajusta equipos, cancha, inicio y motivo de localía ({@code null} si el partido ya respeta la
     * localía); el trigger recalcula {@code fin_bloque}.
     */
    void actualizarPartido(UUID id, UUID equipoLocalId, UUID equipoVisitanteId, UUID canchaId,
            OffsetDateTime inicio, String motivoLocalia) {
        jdbc.sql("""
                        UPDATE partidos
                           SET equipo_local_id = :local,
                               equipo_visitante_id = :visitante,
                               cancha_id = :cancha,
                               inicio = :inicio,
                               motivo_localia = :motivoLocalia
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("local", equipoLocalId)
                .param("visitante", equipoVisitanteId)
                .param("cancha", canchaId)
                .param("inicio", inicio)
                .param("motivoLocalia", motivoLocalia, java.sql.Types.VARCHAR)
                .update();
    }

    /**
     * Guarda solo el motivo con el que se acepta una advertencia de localía (B-15.2, RF-120): el
     * partido no cambia de equipos, cancha ni horario, así que no pasa por el trigger de programación.
     */
    void actualizarMotivoLocalia(UUID id, String motivoLocalia) {
        jdbc.sql("""
                        UPDATE partidos
                           SET motivo_localia = :motivoLocalia
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("motivoLocalia", motivoLocalia, java.sql.Types.VARCHAR)
                .update();
    }

    // ── Emparejamientos (H-35.1) ────────────────────────────────────────────────────────────────

    /**
     * Emparejamiento del calendario que corresponde a la jornada: mismo grupo y mismo número que la
     * fecha a la que la jornada pertenece (DEC-175, DEC-180). Lo lee con candado para que dos altas
     * manuales no liguen el mismo emparejamiento. Vacío si no existe o es de otro grupo o fecha.
     */
    Optional<EmparejamientoDeJornada> bloquearEmparejamiento(UUID jornadaId, UUID emparejamientoId) {
        return jdbc.sql("""
                        SELECT e.id, e.equipo_local_id, e.equipo_visitante_id, e.partido_id,
                               pa.estado AS partido_estado
                          FROM emparejamientos e
                          JOIN jornadas j ON j.id = :jornadaId AND j.grupo_id = e.grupo_id
                          JOIN fechas_programacion f ON f.id = j.fecha_id AND f.numero = e.numero_fecha
                          LEFT JOIN partidos pa ON pa.id = e.partido_id
                         WHERE e.id = :id
                         FOR UPDATE OF e
                        """)
                .param("jornadaId", jornadaId)
                .param("id", emparejamientoId)
                .query((fila, numero) -> new EmparejamientoDeJornada(
                        fila.getObject("id", UUID.class),
                        fila.getObject("equipo_local_id", UUID.class),
                        fila.getObject("equipo_visitante_id", UUID.class),
                        fila.getObject("partido_id", UUID.class),
                        fila.getString("partido_estado") == null ? null
                                : EstadoPartido.valueOf(fila.getString("partido_estado"))))
                .optional();
    }

    /**
     * Liga el emparejamiento al partido recién registrado y limpia la causa por la que no se asignó
     * (DEC-180): la fila deja de aparecer como «sin asignar».
     */
    void ligarEmparejamiento(UUID emparejamientoId, UUID partidoId) {
        jdbc.sql("""
                        UPDATE emparejamientos
                           SET partido_id = :partidoId,
                               codigo_no_asignado = NULL,
                               causa_no_asignado = NULL
                         WHERE id = :id
                        """)
                .param("id", emparejamientoId)
                .param("partidoId", partidoId)
                .update();
    }

    // ── Filas ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Emparejamiento de la fecha de una jornada. {@code equipoVisitanteId == null} es un descanso
     * (RF-037); {@code partidoEstado} es el del partido ligado, si lo hay.
     */
    record EmparejamientoDeJornada(UUID id, UUID equipoLocalId, UUID equipoVisitanteId, UUID partidoId,
                                   EstadoPartido partidoEstado) {
    }

    /** Grupo de la liga con la categoría de temporada a la que pertenece. */
    record GrupoDeLiga(UUID id, UUID categoriaTemporadaId) {
    }

    /** Equipo de la liga con lo necesario para validar grupo, club y si está dado de baja. */
    record EquipoDeLiga(UUID id, String nombre, UUID grupoId, UUID clubId, String estado) {
    }

    /** Cancha de la liga con el nombre y el estado que VAL-005 necesita explicar. */
    record CanchaDeLiga(UUID id, String nombre, boolean activa) {
    }

    /**
     * Fila de {@code jornadas} con las fechas en la zona de negocio. {@code creadoPor} y
     * {@code enviadaPor} sirven para la separación de funciones del dictamen (RF-121).
     */
    record DatosJornada(UUID id, UUID grupoId, UUID faseId, int numero, EstadoJornada estadoPublicacion,
                        UUID creadoPor, UUID enviadaPor, OffsetDateTime enviadaEn, UUID aprobadaPor,
                        OffsetDateTime aprobadaEn, OffsetDateTime publicadaEn, String observaciones,
                        OffsetDateTime creadoEn, OffsetDateTime actualizadoEn) {
    }

    /**
     * Fila de {@code partidos} con los nombres, los clubes y el estado de su jornada. Los estados de
     * los equipos y de la cancha los usa la verificación de conflictos (H-12.3).
     */
    record DatosPartido(UUID id, UUID jornadaId, UUID grupoId, EstadoJornada estadoJornada,
                        UUID equipoLocalId, String equipoLocalNombre, UUID clubLocalId,
                        UUID equipoVisitanteId, String equipoVisitanteNombre, UUID clubVisitanteId,
                        UUID canchaId, String canchaNombre, OffsetDateTime inicio, OffsetDateTime finBloque,
                        EstadoPartido estado, String motivoLocalia, OffsetDateTime creadoEn,
                        OffsetDateTime actualizadoEn, String equipoLocalEstado,
                        String equipoVisitanteEstado, boolean canchaActiva) {
    }

    private static DatosJornada aJornada(ResultSet fila, int numero) throws SQLException {
        return new DatosJornada(
                fila.getObject("id", UUID.class),
                fila.getObject("grupo_id", UUID.class),
                fila.getObject("fase_id", UUID.class),
                fila.getInt("numero"),
                EstadoJornada.valueOf(fila.getString("estado_publicacion")),
                fila.getObject("creado_por", UUID.class),
                fila.getObject("enviada_por", UUID.class),
                aZona(fila.getObject("enviada_en", OffsetDateTime.class)),
                fila.getObject("aprobada_por", UUID.class),
                aZona(fila.getObject("aprobada_en", OffsetDateTime.class)),
                aZona(fila.getObject("publicada_en", OffsetDateTime.class)),
                fila.getString("observaciones"),
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
                aZona(fila.getObject("actualizado_en", OffsetDateTime.class)),
                fila.getString("local_estado"),
                fila.getString("visitante_estado"),
                fila.getBoolean("cancha_activa"));
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }
}

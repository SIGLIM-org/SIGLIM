package mx.tecnm.morelia.siglim.arbitraje;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionDto.ArbitroDesignadoDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionesJornadaDto.JornadaDto;
import mx.tecnm.morelia.siglim.programacion.EstadoJornada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code designaciones} (RF-023, H-14.1).
 *
 * <ul>
 *   <li><b>Liga.</b> Ni la designación ni el partido tienen {@code liga_id}: se llega a la liga por
 *       jornada → grupo → categoría de la temporada → temporada, como en {@code PartidosRepository}.
 *       Lo de otra liga no se encuentra ({@code 404}).</li>
 *   <li><b>Nada se borra.</b> La tabla no tiene {@code DELETE} para {@code siglim_app}: retirar o
 *       sustituir es {@code vigente = false} con {@code motivo_cambio} (RN-005).</li>
 *   <li><b>Bloqueos.</b> Al designar se bloquea el partido y después el árbitro
 *       ({@code FOR UPDATE}), siempre en ese orden, para que dos designaciones simultáneas del mismo
 *       árbitro o al mismo partido se serialicen y el trigger vea la otra ya confirmada. La
 *       designación automática (DEC-215) sigue el mismo orden: todos sus partidos por {@code id} y
 *       después todos los árbitros validados por {@code id}.</li>
 *   <li><b>Nombre.</b> {@code LEFT JOIN personas}: va {@code null} si RLS la oculta (DEC-78).</li>
 *   <li><b>Fechas</b> en {@code America/Mexico_City}.</li>
 * </ul>
 *
 * <p>Refs: H-14.1, H-34.1, RF-023, RF-039, RF-115, VAL-019, DEC-201 a DEC-205, DEC-215, ADR-0014
 */
@Repository
class DesignacionRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    /** Partido → liga. */
    private static final String PARTIDO_EN_LIGA = """
              JOIN jornadas j ON j.id = p.jornada_id
              JOIN grupos g ON g.id = j.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN temporadas t ON t.id = ct.temporada_id
            """;

    private static final String DESIGNACION = """
            SELECT d.id, d.partido_id, d.funcion, d.vigente, d.automatica, d.designado_en, d.motivo_cambio,
                   a.id AS arbitro_id,
                   CASE WHEN pe.id IS NULL THEN NULL
                        ELSE concat_ws(' ', pe.nombre, pe.apellido_paterno, pe.apellido_materno) END AS arbitro_nombre,
                   cl.id AS club_id, cl.nombre AS club_nombre
              FROM designaciones d
              JOIN arbitros a ON a.id = d.arbitro_id
              JOIN clubes cl ON cl.id = a.club_id
              LEFT JOIN personas pe ON pe.id = a.persona_id
              JOIN partidos p ON p.id = d.partido_id
            """ + PARTIDO_EN_LIGA;

    private static final String MIS_DESIGNACIONES = """
              FROM designaciones d
              JOIN arbitros a ON a.id = d.arbitro_id
              JOIN partidos p ON p.id = d.partido_id
              JOIN jornadas j ON j.id = p.jornada_id
              JOIN grupos g ON g.id = j.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN categorias cat ON cat.id = ct.categoria_id
              JOIN temporadas t ON t.id = ct.temporada_id
              JOIN equipos el ON el.id = p.equipo_local_id
              JOIN equipos ev ON ev.id = p.equipo_visitante_id
              JOIN canchas ca ON ca.id = p.cancha_id
              LEFT JOIN cedulas ce ON ce.partido_id = p.id
             WHERE t.liga_id = :liga
               AND d.vigente
               AND j.estado_publicacion = 'PUBLICADO'
               AND siglim.es_titular(a.persona_id)
               AND (CAST(:desde AS timestamptz) IS NULL OR p.inicio >= :desde)
               AND (CAST(:hasta AS timestamptz) IS NULL OR p.inicio < :hasta)
            """;

    private final JdbcClient jdbc;

    DesignacionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── Vista por jornada ───────────────────────────────────────────────────────────────────────

    Optional<JornadaDto> buscarJornada(UUID ligaId, UUID jornadaId) {
        return jdbc.sql("""
                        SELECT j.id, j.numero, j.estado_publicacion,
                               g.id AS grupo_id, g.nombre AS grupo_nombre,
                               cat.id AS categoria_id, cat.nombre AS categoria_nombre
                          FROM jornadas j
                          JOIN grupos g ON g.id = j.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN categorias cat ON cat.id = ct.categoria_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE j.id = :jornada AND t.liga_id = :liga
                        """)
                .param("jornada", jornadaId)
                .param("liga", ligaId)
                .query((fila, numero) -> new JornadaDto(
                        fila.getObject("id", UUID.class),
                        fila.getInt("numero"),
                        EstadoJornada.valueOf(fila.getString("estado_publicacion")),
                        referencia(fila, "grupo"),
                        referencia(fila, "categoria")))
                .optional();
    }

    /** Partidos no cancelados de la jornada (DEC-181), por inicio e identificador. */
    List<PartidoDeJornada> partidosDeJornada(UUID jornadaId) {
        return jdbc.sql("""
                        SELECT p.id, p.inicio, p.fin_bloque, p.estado,
                               ca.id AS cancha_id, ca.nombre AS cancha_nombre,
                               el.id AS local_id, el.nombre AS local_nombre,
                               ev.id AS visitante_id, ev.nombre AS visitante_nombre
                          FROM partidos p
                          JOIN equipos el ON el.id = p.equipo_local_id
                          JOIN equipos ev ON ev.id = p.equipo_visitante_id
                          JOIN canchas ca ON ca.id = p.cancha_id
                         WHERE p.jornada_id = :jornada AND p.estado <> 'CANCELADO'
                         ORDER BY p.inicio, p.id
                        """)
                .param("jornada", jornadaId)
                .query((fila, numero) -> new PartidoDeJornada(
                        fila.getObject("id", UUID.class),
                        enZonaDeNegocio(fila.getObject("inicio", OffsetDateTime.class)),
                        enZonaDeNegocio(fila.getObject("fin_bloque", OffsetDateTime.class)),
                        EstadoPartido.valueOf(fila.getString("estado")),
                        referencia(fila, "cancha"),
                        referencia(fila, "local"),
                        referencia(fila, "visitante")))
                .list();
    }

    /** Designaciones vigentes de los partidos no cancelados de la jornada, por fecha de designación. */
    List<DesignacionDto> vigentesDeJornada(UUID jornadaId) {
        return jdbc.sql(DESIGNACION + """
                         WHERE p.jornada_id = :jornada AND d.vigente AND p.estado <> 'CANCELADO'
                         ORDER BY d.designado_en, d.id
                        """)
                .param("jornada", jornadaId)
                .query(DesignacionRepository::aDesignacion)
                .list();
    }

    // ── Designar y retirar ──────────────────────────────────────────────────────────────────────

    /** El partido de la liga, sin bloquearlo (consulta de candidatos). */
    Optional<PartidoDesignable> buscarPartido(UUID ligaId, UUID partidoId) {
        return partido(ligaId, partidoId, "");
    }

    /** El partido de la liga, bloqueado: se bloquea antes que el árbitro. */
    Optional<PartidoDesignable> bloquearPartido(UUID ligaId, UUID partidoId) {
        return partido(ligaId, partidoId, " FOR UPDATE OF p");
    }

    private Optional<PartidoDesignable> partido(UUID ligaId, UUID partidoId, String bloqueo) {
        return jdbc.sql("SELECT p.id, p.estado FROM partidos p" + PARTIDO_EN_LIGA
                        + " WHERE p.id = :partido AND t.liga_id = :liga" + bloqueo)
                .param("partido", partidoId)
                .param("liga", ligaId)
                .query((fila, numero) -> new PartidoDesignable(fila.getObject("id", UUID.class),
                        EstadoPartido.valueOf(fila.getString("estado"))))
                .optional();
    }

    /** {@code true} si el árbitro existe en un club de la liga; queda bloqueado hasta el fin. */
    boolean bloquearArbitro(UUID ligaId, UUID arbitroId) {
        return jdbc.sql("""
                        SELECT a.id
                          FROM arbitros a
                          JOIN clubes cl ON cl.id = a.club_id
                         WHERE a.id = :arbitro AND cl.liga_id = :liga
                           FOR UPDATE OF a
                        """)
                .param("arbitro", arbitroId)
                .param("liga", ligaId)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    /** Designaciones vigentes del partido. */
    List<DesignacionVigente> vigentesDelPartido(UUID partidoId) {
        return jdbc.sql("""
                        SELECT id, arbitro_id, funcion
                          FROM designaciones
                         WHERE partido_id = :partido AND vigente
                         ORDER BY designado_en, id
                        """)
                .param("partido", partidoId)
                .query((fila, numero) -> new DesignacionVigente(
                        fila.getObject("id", UUID.class),
                        fila.getObject("arbitro_id", UUID.class),
                        FuncionArbitral.valueOf(fila.getString("funcion"))))
                .list();
    }

    /**
     * Deja la designación sin vigencia con su motivo. Con {@code vigente = false} el trigger
     * {@code tg_designaciones_validar} no repite sus reglas.
     */
    void retirar(UUID designacionId, String motivo) {
        jdbc.sql("""
                        UPDATE designaciones
                           SET vigente = false, motivo_cambio = :motivo
                         WHERE id = :id AND vigente
                        """)
                .param("id", designacionId)
                .param("motivo", motivo)
                .update();
    }

    /**
     * Designación vigente, manual ({@code automatica = false}, DEC-201) o de la asignación automática
     * del central ({@code true}, DEC-182). El trigger aplica RF-113, VAL-019 y RF-039 como última
     * garantía; sus errores los traduce {@code TraductorErroresBd}.
     */
    UUID insertar(UUID partidoId, UUID arbitroId, FuncionArbitral funcion, UUID designadoPor, boolean automatica) {
        return jdbc.sql("""
                        INSERT INTO designaciones (partido_id, arbitro_id, funcion, designado_por, automatica)
                        VALUES (:partido, :arbitro, CAST(:funcion AS siglim.funcion_arbitral), :perfil, :automatica)
                        RETURNING id
                        """)
                .param("partido", partidoId)
                .param("arbitro", arbitroId)
                .param("funcion", funcion.name())
                .param("perfil", designadoPor)
                .param("automatica", automatica)
                .query(UUID.class)
                .single();
    }

    // ── Designación automática del central (H-34.1) ─────────────────────────────────────────────

    /**
     * Bloquea, en una sola sentencia y por {@code id}, los partidos de la liga que están en la lista;
     * los que no son de la liga se ignoran (DEC-215). Se bloquean antes que los árbitros, en el mismo
     * orden que la designación manual.
     */
    List<PartidoBloqueado> bloquearPartidos(UUID ligaId, Collection<UUID> partidoIds) {
        return jdbc.sql("SELECT p.id, p.estado, p.inicio FROM partidos p" + PARTIDO_EN_LIGA + """
                         WHERE p.id = ANY(:partidos) AND t.liga_id = :liga
                         ORDER BY p.id
                           FOR UPDATE OF p
                        """)
                .param("partidos", new SqlArrayValue("uuid", partidoIds.toArray()))
                .param("liga", ligaId)
                .query((fila, numero) -> new PartidoBloqueado(
                        fila.getObject("id", UUID.class),
                        EstadoPartido.valueOf(fila.getString("estado")),
                        fila.getObject("inicio", OffsetDateTime.class)))
                .list();
    }

    /**
     * Bloquea, por {@code id}, a todos los árbitros {@code VALIDADO} de la liga (DEC-215): son los
     * candidatos de cualquier partido, y así una designación manual simultánea espera a que termine la
     * asignación en lugar de cruzarse con ella.
     *
     * @return los identificadores bloqueados
     */
    List<UUID> bloquearArbitrosValidados(UUID ligaId) {
        return jdbc.sql("""
                        SELECT a.id
                          FROM arbitros a
                          JOIN clubes cl ON cl.id = a.club_id
                         WHERE a.estado = 'VALIDADO' AND cl.liga_id = :liga
                         ORDER BY a.id
                           FOR UPDATE OF a
                        """)
                .param("liga", ligaId)
                .query(UUID.class)
                .list();
    }

    /**
     * Inicio del último partido de cada árbitro en la temporada del partido: el mayor {@code inicio} de
     * sus designaciones vigentes, con cualquier función, en partidos no {@code CANCELADO}. Mismo
     * alcance que la {@code carga} de {@link ElegibilidadArbitral}. Un árbitro sin designaciones no
     * aparece.
     */
    Map<UUID, OffsetDateTime> ultimoPartidoPorArbitro(UUID partidoId) {
        Map<UUID, OffsetDateTime> ultimos = new HashMap<>();
        jdbc.sql("""
                        WITH partido AS (
                            SELECT ct.temporada_id
                              FROM partidos p
                              JOIN jornadas j ON j.id = p.jornada_id
                              JOIN grupos g ON g.id = j.grupo_id
                              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                             WHERE p.id = :partido
                        )
                        SELECT dc.arbitro_id, max(pc.inicio) AS ultimo
                          FROM partido pa
                          JOIN categorias_temporada ctc ON ctc.temporada_id = pa.temporada_id
                          JOIN grupos gc ON gc.categoria_temporada_id = ctc.id
                          JOIN jornadas jc ON jc.grupo_id = gc.id
                          JOIN partidos pc ON pc.jornada_id = jc.id
                          JOIN designaciones dc ON dc.partido_id = pc.id
                         WHERE dc.vigente AND pc.estado <> 'CANCELADO'
                         GROUP BY dc.arbitro_id
                        """)
                .param("partido", partidoId)
                .query((fila, numero) -> ultimos.put(fila.getObject("arbitro_id", UUID.class),
                        fila.getObject("ultimo", OffsetDateTime.class)))
                .list();
        return ultimos;
    }

    /** La designación de la liga, vigente o no. */
    Optional<DesignacionDto> buscar(UUID ligaId, UUID designacionId) {
        return designacion(ligaId, designacionId, "");
    }

    /** La designación de la liga, bloqueada (después del partido). */
    Optional<DesignacionDto> bloquear(UUID ligaId, UUID designacionId) {
        return designacion(ligaId, designacionId, " FOR UPDATE OF d");
    }

    private Optional<DesignacionDto> designacion(UUID ligaId, UUID designacionId, String bloqueo) {
        return jdbc.sql(DESIGNACION + " WHERE d.id = :id AND t.liga_id = :liga" + bloqueo)
                .param("id", designacionId)
                .param("liga", ligaId)
                .query(DesignacionRepository::aDesignacion)
                .optional();
    }

    // ── Mis designaciones ───────────────────────────────────────────────────────────────────────

    /** Designaciones vigentes del titular de la sesión en jornadas publicadas, por inicio. */
    List<MiDesignacionDto> misDesignaciones(UUID ligaId, FiltroMisDesignaciones filtro) {
        return conFiltro(jdbc.sql("""
                        SELECT d.id AS designacion_id, d.funcion,
                               p.id, p.inicio, p.fin_bloque, p.estado,
                               ca.id AS cancha_id, ca.nombre AS cancha_nombre,
                               el.id AS local_id, el.nombre AS local_nombre,
                               ev.id AS visitante_id, ev.nombre AS visitante_nombre,
                               cat.id AS categoria_id, cat.nombre AS categoria_nombre,
                               g.id AS grupo_id, g.nombre AS grupo_nombre,
                               j.id AS jornada_id, j.numero AS jornada_numero,
                               ce.id AS cedula_id, ce.estado AS cedula_estado
                        """ + MIS_DESIGNACIONES + """
                         ORDER BY p.inicio, d.id
                         LIMIT :limite OFFSET :desplazamiento
                        """), ligaId, filtro)
                .param("limite", filtro.tamano())
                .param("desplazamiento", (long) filtro.pagina() * filtro.tamano())
                .query(DesignacionRepository::aMiDesignacion)
                .list();
    }

    /** Total con la misma condición que el listado. */
    long contarMisDesignaciones(UUID ligaId, FiltroMisDesignaciones filtro) {
        return conFiltro(jdbc.sql("SELECT count(*)" + MIS_DESIGNACIONES), ligaId, filtro)
                .query(Long.class)
                .single();
    }

    private static StatementSpec conFiltro(StatementSpec sentencia, UUID ligaId, FiltroMisDesignaciones filtro) {
        return sentencia
                .param("liga", ligaId)
                .param("desde", filtro.desde(), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("hasta", filtro.hasta(), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    // ── Mapeo ───────────────────────────────────────────────────────────────────────────────────

    private static DesignacionDto aDesignacion(ResultSet fila, int numero) throws SQLException {
        return new DesignacionDto(
                fila.getObject("id", UUID.class),
                fila.getObject("partido_id", UUID.class),
                new ArbitroDesignadoDto(fila.getObject("arbitro_id", UUID.class), fila.getString("arbitro_nombre"),
                        referencia(fila, "club")),
                FuncionArbitral.valueOf(fila.getString("funcion")),
                fila.getBoolean("vigente"),
                fila.getBoolean("automatica"),
                enZonaDeNegocio(fila.getObject("designado_en", OffsetDateTime.class)),
                fila.getString("motivo_cambio"));
    }

    private static MiDesignacionDto aMiDesignacion(ResultSet fila, int numero) throws SQLException {
        UUID cedula = fila.getObject("cedula_id", UUID.class);
        return new MiDesignacionDto(
                fila.getObject("designacion_id", UUID.class),
                FuncionArbitral.valueOf(fila.getString("funcion")),
                new MiDesignacionDto.PartidoDto(
                        fila.getObject("id", UUID.class),
                        enZonaDeNegocio(fila.getObject("inicio", OffsetDateTime.class)),
                        enZonaDeNegocio(fila.getObject("fin_bloque", OffsetDateTime.class)),
                        EstadoPartido.valueOf(fila.getString("estado")),
                        referencia(fila, "cancha"),
                        referencia(fila, "local"),
                        referencia(fila, "visitante"),
                        referencia(fila, "categoria"),
                        referencia(fila, "grupo"),
                        new MiDesignacionDto.JornadaDto(fila.getObject("jornada_id", UUID.class),
                                fila.getInt("jornada_numero"))),
                cedula == null ? null : new MiDesignacionDto.CedulaDto(cedula, fila.getString("cedula_estado")));
    }

    private static ReferenciaDto referencia(ResultSet fila, String prefijo) throws SQLException {
        return new ReferenciaDto(fila.getObject(prefijo + "_id", UUID.class), fila.getString(prefijo + "_nombre"));
    }

    private static OffsetDateTime enZonaDeNegocio(OffsetDateTime instante) {
        return instante == null ? null : instante.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    /** Partido de la jornada con sus referencias. */
    record PartidoDeJornada(UUID id, OffsetDateTime inicio, OffsetDateTime finBloque, EstadoPartido estado,
                            ReferenciaDto cancha, ReferenciaDto equipoLocal, ReferenciaDto equipoVisitante) {
    }

    /** Lo que la designación necesita del partido. */
    record PartidoDesignable(UUID id, EstadoPartido estado) {
    }

    /** Partido bloqueado por la designación automática del central. */
    record PartidoBloqueado(UUID id, EstadoPartido estado, OffsetDateTime inicio) {
    }

    /** Designación vigente de un partido, sin datos personales. */
    record DesignacionVigente(UUID id, UUID arbitroId, FuncionArbitral funcion) {
    }
}

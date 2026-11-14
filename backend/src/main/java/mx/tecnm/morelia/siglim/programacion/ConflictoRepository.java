package mx.tecnm.morelia.siglim.programacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consultas de los conflictos duros de la programación (RF-039, VAL-003 a VAL-006).
 *
 * <p><b>Bloque de cancha.</b> No se fija en Java: se calcula con {@code siglim.bloque_cancha(...)}
 * a partir de los parámetros vigentes de la temporada y de la categoría del equipo local, igual que
 * el trigger {@code tg_partidos_programacion} (RN-050, DEC-25). Los traslapes usan el mismo
 * intervalo semiabierto {@code [inicio, fin)} que la restricción GiST y el trigger, de modo que
 * 10:00–12:00 y 12:00–14:00 no chocan.
 *
 * <p><b>Disponibilidad.</b> Solo {@code BLOQUEO} y {@code MANTENIMIENTO} restringen el uso de la
 * cancha; {@code DISPONIBLE} es informativo (decisión D3 de H-07.1, aplicada también por el
 * trigger). La cancha inactiva y las restricciones forman VAL-005.
 *
 * <p><b>Sanciones.</b> VAL-006 considera una sanción vigente del equipo de tipo
 * {@code SUSPENSION_PARTIDOS} o {@code BAJA}, que es la que impide jugar (RN-057). Una suspensión
 * con todos sus partidos cumplidos no bloquea aunque su {@code estado} siga en {@code VIGENTE},
 * por si la máquina de estados no se actualizó. Las multas y las pérdidas de puntos o de partido no
 * bloquean la programación.
 *
 * <p>[PENDIENTE] Las sanciones con {@code sujeto = 'CLUB'} no bloquean a los equipos del club:
 * RN-057 solo describe el alcance de jugador, equipo y pérdida de partidos, y ni el ERS ni DEC-30
 * precisan si una sanción de club alcanza a todos sus equipos. Se decide con M-09; H-12.3 no lo
 * implementa.
 *
 * <p>Las mismas consultas alimentan el alta, el ajuste y la verificación de la jornada (H-12.2,
 * H-12.3), para que las tres operaciones expliquen el mismo conflicto. Las consultas de sanciones y
 * de equipos ocupados devuelven <b>todas</b> las filas que coinciden: si los dos equipos del partido
 * están sancionados u ocupados, la verificación explica cada uno por separado (H-12.4).
 *
 * <p>Refs: H-12.2, H-12.3, H-12.4, RF-039, VAL-003, VAL-004, VAL-005, VAL-006, RN-050, RN-057, DEC-25
 */
@Repository
class ConflictoRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String PARTIDO = """
            SELECT p.id, p.inicio, p.fin_bloque, c.nombre AS cancha_nombre,
                   l.id AS local_id, l.nombre AS local_nombre,
                   v.id AS visitante_id, v.nombre AS visitante_nombre
              FROM partidos p
              JOIN canchas c ON c.id = p.cancha_id
              JOIN equipos l ON l.id = p.equipo_local_id
              JOIN equipos v ON v.id = p.equipo_visitante_id
            """;

    private static final String TRASLAPE = """
              AND p.estado <> 'CANCELADO'
              AND p.id IS DISTINCT FROM CAST(:partidoId AS uuid)
              AND tstzrange(p.inicio, p.fin_bloque)
                  && tstzrange(CAST(:inicio AS timestamptz), CAST(:fin AS timestamptz), '[)')
            """;

    private final JdbcClient jdbc;

    ConflictoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Bloque de cancha de la categoría del equipo local según los parámetros vigentes (DEC-25).
     *
     * @param grupoId        grupo de la jornada, para ubicar la temporada
     * @param equipoLocalId  equipo local, para ubicar la categoría
     * @param inicio         inicio del partido
     * @return el fin del bloqueo calculado, o {@code definido = false} si la temporada no tiene
     *         parámetros (en ese caso el servicio responde {@code 409 RF-100})
     */
    BloqueCancha calcularBloque(UUID grupoId, UUID equipoLocalId, OffsetDateTime inicio) {
        return jdbc.sql("""
                        WITH bloque AS (
                            SELECT siglim.bloque_cancha(
                                       siglim.temporada_de_grupo(:grupoId),
                                       siglim.categoria_de_equipo(:equipoLocalId)) AS duracion
                        )
                        SELECT duracion IS NOT NULL AS definido,
                               CAST(:inicio AS timestamptz) + duracion AS fin_bloque
                          FROM bloque
                        """)
                .param("grupoId", grupoId)
                .param("equipoLocalId", equipoLocalId)
                .param("inicio", inicio)
                .query(ConflictoRepository::aBloque)
                .single();
    }

    /** Primer partido no cancelado de la cancha cuyo bloque se traslapa, sin contar el que se ajusta. */
    Optional<PartidoEnConflicto> buscarCanchaOcupada(UUID canchaId, OffsetDateTime inicio,
            OffsetDateTime finBloque, UUID partidoId) {
        return jdbc.sql(PARTIDO + " WHERE p.cancha_id = :canchaId" + TRASLAPE
                        + " ORDER BY p.inicio, p.id LIMIT 1")
                .param("canchaId", canchaId)
                .param("inicio", inicio)
                .param("fin", finBloque)
                .param("partidoId", partidoId)
                .query(ConflictoRepository::aPartido)
                .optional();
    }

    /**
     * Todos los partidos no cancelados de cualquiera de los dos equipos que se traslapan, con el
     * equipo que choca en cada fila. Sin {@code LIMIT}: si ambos equipos ya tienen partido, se
     * devuelven las dos filas para explicar cada choque (H-12.4).
     */
    List<EquipoEnConflicto> buscarEquipoOcupado(UUID equipoLocalId, UUID equipoVisitanteId,
            OffsetDateTime inicio, OffsetDateTime finBloque, UUID partidoId) {
        return jdbc.sql("""
                        SELECT e.id AS equipo_id, e.nombre AS equipo_nombre,
                               p.id, p.inicio, p.fin_bloque, c.nombre AS cancha_nombre,
                               l.id AS local_id, l.nombre AS local_nombre,
                               v.id AS visitante_id, v.nombre AS visitante_nombre
                          FROM partidos p
                          JOIN equipos e ON e.id IN (p.equipo_local_id, p.equipo_visitante_id)
                          JOIN canchas c ON c.id = p.cancha_id
                          JOIN equipos l ON l.id = p.equipo_local_id
                          JOIN equipos v ON v.id = p.equipo_visitante_id
                         WHERE e.id IN (:localId, :visitanteId)
                        """ + TRASLAPE + " ORDER BY p.inicio, e.nombre, e.id")
                .param("localId", equipoLocalId)
                .param("visitanteId", equipoVisitanteId)
                .param("inicio", inicio)
                .param("fin", finBloque)
                .param("partidoId", partidoId)
                .query((fila, numero) -> new EquipoEnConflicto(
                        fila.getObject("equipo_id", UUID.class),
                        fila.getString("equipo_nombre"),
                        aPartido(fila, numero)))
                .list();
    }

    /** Primer bloqueo o mantenimiento de la cancha que se traslapa con el bloque del partido. */
    Optional<RestriccionCancha> buscarRestriccion(UUID canchaId, OffsetDateTime inicio,
            OffsetDateTime finBloque) {
        return jdbc.sql("""
                        SELECT d.tipo, d.motivo, lower(d.periodo) AS desde, upper(d.periodo) AS hasta
                          FROM disponibilidad_canchas d
                         WHERE d.cancha_id = :canchaId
                           AND d.tipo IN ('BLOQUEO', 'MANTENIMIENTO')
                           AND d.periodo && tstzrange(CAST(:inicio AS timestamptz),
                                                      CAST(:fin AS timestamptz), '[)')
                         ORDER BY lower(d.periodo), d.id
                         LIMIT 1
                        """)
                .param("canchaId", canchaId)
                .param("inicio", inicio)
                .param("fin", finBloque)
                .query((fila, numero) -> new RestriccionCancha(
                        fila.getString("tipo"),
                        fila.getString("motivo"),
                        aZona(fila.getObject("desde", OffsetDateTime.class)),
                        aZona(fila.getObject("hasta", OffsetDateTime.class))))
                .optional();
    }

    /**
     * Todas las sanciones vigentes de los equipos que impiden jugar: suspensión de partidos o baja
     * (RN-057). La vigencia por fechas se compara con el día del partido en la zona de negocio.
     * Una suspensión con todos sus partidos cumplidos se excluye aunque el estado siga en
     * {@code VIGENTE}, por si la máquina de estados no se actualizó. Sin {@code LIMIT}: si ambos
     * equipos están sancionados, se devuelven las dos sanciones (H-12.4).
     */
    List<SancionDeEquipo> buscarSancionVigente(UUID grupoId, UUID equipoLocalId,
            UUID equipoVisitanteId, OffsetDateTime inicio) {
        return jdbc.sql("""
                        SELECT s.equipo_id, e.nombre AS equipo_nombre, s.tipo,
                               s.partidos, s.partidos_cumplidos, s.vigente_desde, s.vigente_hasta
                          FROM sanciones s
                          JOIN equipos e ON e.id = s.equipo_id
                         WHERE s.equipo_id IN (:localId, :visitanteId)
                           AND s.sujeto = 'EQUIPO'
                           AND s.estado = 'VIGENTE'
                           AND s.tipo IN ('SUSPENSION_PARTIDOS', 'BAJA')
                           AND (s.partidos IS NULL OR s.partidos_cumplidos < s.partidos)
                           AND s.temporada_id = siglim.temporada_de_grupo(:grupoId)
                           AND s.vigente_desde <= (CAST(:inicio AS timestamptz)
                                                   AT TIME ZONE 'America/Mexico_City')::date
                           AND (s.vigente_hasta IS NULL
                                OR s.vigente_hasta >= (CAST(:inicio AS timestamptz)
                                                       AT TIME ZONE 'America/Mexico_City')::date)
                         ORDER BY s.vigente_desde, e.nombre, s.id
                        """)
                .param("grupoId", grupoId)
                .param("localId", equipoLocalId)
                .param("visitanteId", equipoVisitanteId)
                .param("inicio", inicio)
                .query((fila, numero) -> new SancionDeEquipo(
                        fila.getObject("equipo_id", UUID.class),
                        fila.getString("equipo_nombre"),
                        fila.getString("tipo"),
                        fila.getObject("partidos", Short.class),
                        fila.getObject("partidos_cumplidos", Short.class),
                        fila.getObject("vigente_desde", LocalDate.class),
                        fila.getObject("vigente_hasta", LocalDate.class)))
                .list();
    }

    // ── Filas ───────────────────────────────────────────────────────────────────────────────────

    /** Fin del bloque de cancha y si la temporada tiene parámetros. */
    record BloqueCancha(boolean definido, OffsetDateTime finBloque) {
    }

    /** Partido ya programado que ocupa el bloque de una cancha. */
    record PartidoEnConflicto(UUID id, String canchaNombre, UUID localId, String localNombre,
                              UUID visitanteId, String visitanteNombre, OffsetDateTime inicio,
                              OffsetDateTime finBloque) {
    }

    /** Equipo que ya tiene otro partido en el bloque y el partido que lo ocupa. */
    record EquipoEnConflicto(UUID equipoId, String equipoNombre, PartidoEnConflicto partido) {
    }

    /** Bloqueo o mantenimiento de la cancha que impide programar. */
    record RestriccionCancha(String tipo, String motivo, OffsetDateTime desde, OffsetDateTime hasta) {
    }

    /** Sanción vigente del equipo que impide jugar. */
    record SancionDeEquipo(UUID equipoId, String equipoNombre, String tipo, Short partidos,
                           Short partidosCumplidos, LocalDate vigenteDesde, LocalDate vigenteHasta) {
    }

    private static BloqueCancha aBloque(ResultSet fila, int numero) throws SQLException {
        return new BloqueCancha(
                fila.getBoolean("definido"),
                aZona(fila.getObject("fin_bloque", OffsetDateTime.class)));
    }

    private static PartidoEnConflicto aPartido(ResultSet fila, int numero) throws SQLException {
        return new PartidoEnConflicto(
                fila.getObject("id", UUID.class),
                fila.getString("cancha_nombre"),
                fila.getObject("local_id", UUID.class),
                fila.getString("local_nombre"),
                fila.getObject("visitante_id", UUID.class),
                fila.getString("visitante_nombre"),
                aZona(fila.getObject("inicio", OffsetDateTime.class)),
                aZona(fila.getObject("fin_bloque", OffsetDateTime.class)));
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }
}

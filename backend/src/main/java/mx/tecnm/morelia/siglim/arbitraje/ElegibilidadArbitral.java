package mx.tecnm.morelia.siglim.arbitraje;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.arbitraje.CandidatoArbitralDto.CausaDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionDto.ArbitroDesignadoDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Elegibilidad y carga de los árbitros para un partido (DEC-204), en <b>una sola consulta</b>.
 *
 * <ul>
 *   <li><b>Candidatos.</b> Los árbitros {@code VALIDADO} de los clubes de la liga, por nombre. Un
 *       árbitro propuesto, rechazado o dado de baja no aparece: el trigger lo rechazaría con
 *       {@code RF-113}.</li>
 *   <li><b>Causas (bloquean).</b> Las mismas condiciones que {@code tg_designaciones_validar}, para
 *       que {@code elegible} coincida con lo que acepta el {@code POST}:
 *       {@code VAL-019} si su club es el de uno de los equipos (RF-115); {@code RF-039} si tiene una
 *       designación vigente en <b>otro</b> partido no {@code CANCELADO} cuyo
 *       {@code tstzrange(inicio, fin_bloque)} se traslapa con el de este (se nombra el primero por
 *       inicio); {@code YA_DESIGNADO} si ya tiene una designación vigente en este partido, con
 *       cualquier función (DEC-201).</li>
 *   <li><b>Advertencias (no bloquean).</b> {@code RF-022} si declaró un periodo
 *       {@code disponible = false} de {@code disponibilidad_arbitral} que se traslapa con el bloque del
 *       partido. La disponibilidad solo informa: DEC-204 no la vuelve regla de rechazo.</li>
 *   <li><b>Carga.</b> Designaciones vigentes del árbitro en partidos no {@code CANCELADO} de la
 *       temporada del partido, con cualquier función.</li>
 *   <li><b>Nombre.</b> Se lee de {@code personas} con un {@code LEFT JOIN}: va {@code null} si la
 *       política RLS no deja verla (DEC-78).</li>
 * </ul>
 *
 * <p>Es público para que la asignación automática del árbitro central (H-34.1, DEC-182) use la
 * misma regla y la misma carga sin duplicarlas: elegir entre los {@code elegible} el de menor
 * {@code carga}. Un módulo de nivel inferior (ADR-0006) lo consumiría a través de una interfaz propia
 * que este paquete implemente. Se llama dentro de la transacción del servicio
 * ({@code Propagation.MANDATORY}): fuera de ella la consulta correría sin el contexto de la sesión
 * (ADR-0014).
 *
 * <p>Refs: H-14.1, RF-022, RF-023, RF-039, RF-115, VAL-019, DEC-78, DEC-182, DEC-204, ADR-0006
 */
@Component
public class ElegibilidadArbitral {

    /** Causa que no viene del ERS: el árbitro ya está en este partido. */
    public static final String YA_DESIGNADO = "YA_DESIGNADO";

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final String CONSULTA = """
            WITH partido AS (
                SELECT p.id, p.inicio, p.fin_bloque, el.club_id AS club_local, ev.club_id AS club_visitante,
                       ct.temporada_id
                  FROM partidos p
                  JOIN jornadas j ON j.id = p.jornada_id
                  JOIN grupos g ON g.id = j.grupo_id
                  JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                  JOIN temporadas t ON t.id = ct.temporada_id
                  JOIN equipos el ON el.id = p.equipo_local_id
                  JOIN equipos ev ON ev.id = p.equipo_visitante_id
                 WHERE p.id = :partido AND t.liga_id = :liga
            )
            SELECT a.id,
                   CASE WHEN pe.id IS NULL THEN NULL
                        ELSE concat_ws(' ', pe.nombre, pe.apellido_paterno, pe.apellido_materno) END AS nombre,
                   cl.id AS club_id, cl.nombre AS club_nombre,
                   cl.id IN (pa.club_local, pa.club_visitante) AS mismo_club,
                   (SELECT CAST(d.funcion AS text) FROM designaciones d
                     WHERE d.partido_id = pa.id AND d.arbitro_id = a.id AND d.vigente
                     LIMIT 1) AS funcion_actual,
                   choque.local_nombre AS choque_local, choque.visitante_nombre AS choque_visitante,
                   choque.inicio AS choque_inicio, choque.cancha_nombre AS choque_cancha,
                   EXISTS (SELECT 1 FROM disponibilidad_arbitral da
                            WHERE da.arbitro_id = a.id AND NOT da.disponible
                              AND da.periodo && tstzrange(pa.inicio, pa.fin_bloque)) AS no_disponible,
                   (SELECT count(*) FROM designaciones dc
                      JOIN partidos pc ON pc.id = dc.partido_id
                      JOIN jornadas jc ON jc.id = pc.jornada_id
                      JOIN grupos gc ON gc.id = jc.grupo_id
                      JOIN categorias_temporada ctc ON ctc.id = gc.categoria_temporada_id
                     WHERE dc.arbitro_id = a.id AND dc.vigente AND pc.estado <> 'CANCELADO'
                       AND ctc.temporada_id = pa.temporada_id) AS carga
              FROM partido pa
              CROSS JOIN arbitros a
              JOIN clubes cl ON cl.id = a.club_id
              LEFT JOIN personas pe ON pe.id = a.persona_id
              LEFT JOIN LATERAL (
                  SELECT elx.nombre AS local_nombre, evx.nombre AS visitante_nombre, px.inicio,
                         cax.nombre AS cancha_nombre
                    FROM designaciones dx
                    JOIN partidos px ON px.id = dx.partido_id
                    JOIN equipos elx ON elx.id = px.equipo_local_id
                    JOIN equipos evx ON evx.id = px.equipo_visitante_id
                    JOIN canchas cax ON cax.id = px.cancha_id
                   WHERE dx.arbitro_id = a.id AND dx.vigente AND px.id <> pa.id AND px.estado <> 'CANCELADO'
                     AND tstzrange(px.inicio, px.fin_bloque) && tstzrange(pa.inicio, pa.fin_bloque)
                   ORDER BY px.inicio, px.id
                   LIMIT 1) choque ON true
             WHERE a.estado = 'VALIDADO' AND cl.liga_id = :liga
             ORDER BY nombre NULLS LAST, a.id
            """;

    private final JdbcClient jdbc;

    ElegibilidadArbitral(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Árbitros validados de la liga con su elegibilidad, advertencias y carga para el partido.
     *
     * @param ligaId    liga activa
     * @param partidoId partido de la liga; si no existe o es de otra liga, la lista va vacía (el
     *                  servicio responde antes el {@code 404})
     * @return candidatos por nombre (los que RLS oculta, al final) e identificador
     */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<CandidatoArbitralDto> candidatos(UUID ligaId, UUID partidoId) {
        return jdbc.sql(CONSULTA)
                .param("partido", partidoId)
                .param("liga", ligaId)
                .query(ElegibilidadArbitral::aCandidato)
                .list();
    }

    private static CandidatoArbitralDto aCandidato(ResultSet fila, int numero) throws SQLException {
        ReferenciaDto club = new ReferenciaDto(fila.getObject("club_id", UUID.class), fila.getString("club_nombre"));
        List<CausaDto> causas = new ArrayList<>();
        if (fila.getBoolean("mismo_club")) {
            causas.add(new CausaDto("VAL-019", "Pertenece a " + club.nombre() + ", que juega este partido."));
        }
        OffsetDateTime choque = fila.getObject("choque_inicio", OffsetDateTime.class);
        if (choque != null) {
            causas.add(new CausaDto("RF-039", "Ya está designado en " + fila.getString("choque_local") + " vs "
                    + fila.getString("choque_visitante") + " a las "
                    + HORA.format(choque.atZoneSameInstant(ZONA_DE_NEGOCIO)) + " ("
                    + fila.getString("choque_cancha") + ")."));
        }
        String funcionActual = fila.getString("funcion_actual");
        if (funcionActual != null) {
            causas.add(new CausaDto(YA_DESIGNADO, "Ya está designado en este partido como "
                    + FuncionArbitral.valueOf(funcionActual).texto() + "."));
        }
        List<CausaDto> advertencias = fila.getBoolean("no_disponible")
                ? List.of(new CausaDto("RF-022", "Declaró que no está disponible en ese horario."))
                : List.of();
        ArbitroDesignadoDto arbitro = new ArbitroDesignadoDto(fila.getObject("id", UUID.class),
                fila.getString("nombre"), club);
        return new CandidatoArbitralDto(arbitro, causas.isEmpty(), causas, advertencias, fila.getLong("carga"));
    }
}

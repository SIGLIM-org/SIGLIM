package mx.tecnm.morelia.siglim.tesoreria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de los cobros automáticos por partido (B-30, RF-136). Los partidos, equipos, canchas y la
 * categoría de la temporada se leen con {@code JOIN} de solo lectura, sin depender del repositorio de
 * {@code programacion} (ADR-0006).
 *
 * <p>Refs: B-30, RF-135, RF-136, ADR-0006
 */
@Repository
class CobroAutomaticoRepository {

    private final JdbcClient jdbc;

    CobroAutomaticoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Partidos de la jornada que se cobran: todos menos los {@code CANCELADO}, con su temporada y
     * categoría (para la tarifa), los dos equipos con su club y el club dueño de la cancha, si es de
     * un club (DEC-33).
     */
    List<PartidoCobrable> partidosDeJornada(UUID ligaId, UUID jornadaId) {
        return jdbc.sql("""
                        SELECT p.id, p.inicio, j.numero AS jornada_numero,
                               ct.temporada_id, ct.categoria_id,
                               el.id AS local_id, el.club_id AS local_club_id, el.nombre AS local_nombre,
                               ev.id AS visitante_id, ev.club_id AS visitante_club_id,
                               ev.nombre AS visitante_nombre,
                               CASE WHEN ca.propietario = 'CLUB' THEN ca.club_id END AS cancha_club_id
                          FROM partidos p
                          JOIN jornadas j ON j.id = p.jornada_id
                          JOIN grupos g ON g.id = j.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN temporadas t ON t.id = ct.temporada_id AND t.liga_id = :ligaId
                          JOIN equipos el ON el.id = p.equipo_local_id
                          JOIN equipos ev ON ev.id = p.equipo_visitante_id
                          JOIN canchas ca ON ca.id = p.cancha_id
                         WHERE p.jornada_id = :jornadaId AND p.estado <> 'CANCELADO'
                         ORDER BY p.inicio, p.id
                        """)
                .param("ligaId", ligaId)
                .param("jornadaId", jornadaId)
                .query((fila, numero) -> new PartidoCobrable(
                        fila.getObject("id", UUID.class),
                        fila.getObject("inicio", OffsetDateTime.class),
                        fila.getShort("jornada_numero"),
                        fila.getObject("temporada_id", UUID.class),
                        fila.getObject("categoria_id", UUID.class),
                        new EquipoCobrable(fila.getObject("local_id", UUID.class),
                                fila.getObject("local_club_id", UUID.class), fila.getString("local_nombre")),
                        new EquipoCobrable(fila.getObject("visitante_id", UUID.class),
                                fila.getObject("visitante_club_id", UUID.class), fila.getString("visitante_nombre")),
                        fila.getObject("cancha_club_id", UUID.class)))
                .list();
    }

    /** Tarifas de arbitraje y cancha de la categoría en la temporada (RF-135); vacío si no hay. */
    Map<ConceptoTarifa, BigDecimal> tarifas(UUID temporadaId, UUID categoriaId) {
        Map<ConceptoTarifa, BigDecimal> tarifas = new EnumMap<>(ConceptoTarifa.class);
        jdbc.sql("""
                        SELECT concepto, monto FROM tarifas
                         WHERE temporada_id = :temporadaId AND categoria_id = :categoriaId
                        """)
                .param("temporadaId", temporadaId)
                .param("categoriaId", categoriaId)
                .query((fila, numero) -> Map.entry(ConceptoTarifa.valueOf(fila.getString("concepto")),
                        fila.getBigDecimal("monto")))
                .list()
                .forEach(tarifa -> tarifas.put(tarifa.getKey(), tarifa.getValue()));
        return tarifas;
    }

    /**
     * Inserta el cobro automático si no existe ya uno del mismo partido, equipo y tipo
     * ({@code ux_cobros_partido}): repetir la generación no duplica cargos.
     *
     * @return el id del cobro nuevo, o vacío si ya existía
     */
    Optional<UUID> insertarSiNoExiste(UUID ligaId, CobroAutomatico cobro, UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO cobros (liga_id, club_id, equipo_id, tipo, concepto, monto, periodo_inicio,
                                            fecha_limite, estado, partido_id, creado_por)
                        VALUES (:ligaId, :clubId, :equipoId, CAST(:tipo AS tipo_cobro), :concepto, :monto,
                                :periodoInicio, :fechaLimite, 'PENDIENTE', :partidoId, :creadoPor)
                        ON CONFLICT (partido_id, equipo_id, tipo) WHERE partido_id IS NOT NULL DO NOTHING
                        RETURNING id
                        """)
                .param("ligaId", ligaId)
                .param("clubId", cobro.clubId())
                .param("equipoId", cobro.equipoId())
                .param("tipo", cobro.tipo().name())
                .param("concepto", cobro.concepto())
                .param("monto", cobro.monto())
                .param("periodoInicio", cobro.periodoInicio())
                .param("fechaLimite", cobro.fechaLimite())
                .param("partidoId", cobro.partidoId())
                .param("creadoPor", creadoPor)
                .query(UUID.class)
                .optional();
    }

    /** Equipo de un partido con el club al que se carga el cobro. */
    record EquipoCobrable(UUID id, UUID clubId, String nombre) {
    }

    /**
     * Partido de la jornada con lo necesario para cobrarlo.
     *
     * @param canchaClubId club dueño de la cancha; nulo si la cancha es de la liga o de un tercero
     */
    record PartidoCobrable(UUID id, OffsetDateTime inicio, short jornadaNumero, UUID temporadaId, UUID categoriaId,
                           EquipoCobrable local, EquipoCobrable visitante, UUID canchaClubId) {
    }

    /** Cobro de arbitraje o cancha de un equipo por un partido. */
    record CobroAutomatico(UUID partidoId, UUID clubId, UUID equipoId, TipoCobro tipo, String concepto,
                           BigDecimal monto, LocalDate periodoInicio, LocalDate fechaLimite) {
    }
}

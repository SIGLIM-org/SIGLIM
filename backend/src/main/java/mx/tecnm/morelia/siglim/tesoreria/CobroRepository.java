package mx.tecnm.morelia.siglim.tesoreria;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code cobros}. El club y el equipo se leen con {@code JOIN} de solo lectura para mostrar
 * sus nombres y para validar la referencia antes de insertar, sin depender del repositorio de otro
 * módulo (ADR-0006).
 *
 * <p>Los listados filtran por ámbito en el {@code WHERE} y el {@code COUNT} usa la misma condición,
 * para que {@code totalElementos} no delate filas ajenas (ADR-0007).
 *
 * <p>Refs: H-29.1, RF-137, RF-140, ADR-0007
 */
@Repository
class CobroRepository {

    private static final String SELECCION = """
            SELECT c.id, c.tipo, c.concepto, c.monto, c.periodo_inicio, c.fecha_limite, c.estado,
                   c.club_id, cl.nombre AS club_nombre, c.equipo_id, e.nombre AS equipo_nombre, c.creado_en
              FROM cobros c
              JOIN clubes cl ON cl.id = c.club_id
              LEFT JOIN equipos e ON e.id = c.equipo_id
            """;

    private static final String CONDICIONES_LISTADO = """
             WHERE c.liga_id = :ligaId
               AND (:todaLaLiga OR c.club_id = ANY(:clubes))
               AND (CAST(:clubId AS uuid) IS NULL OR c.club_id = CAST(:clubId AS uuid))
               AND (CAST(:equipoId AS uuid) IS NULL OR c.equipo_id = CAST(:equipoId AS uuid))
               AND (CAST(:tipo AS text) IS NULL OR CAST(c.tipo AS text) = CAST(:tipo AS text))
               AND (:sinEstados OR CAST(c.estado AS text) = ANY(:estados))
               AND (CAST(:desde AS date) IS NULL OR c.fecha_limite >= CAST(:desde AS date))
               AND (CAST(:hasta AS date) IS NULL OR c.fecha_limite < CAST(:hasta AS date))
            """;

    private final JdbcClient jdbc;

    CobroRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<CobroDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE c.id = :id AND c.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(CobroRepository::aDto)
                .optional();
    }

    List<CobroDto> listar(UUID ligaId, FiltroCobros filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql(SELECCION + CONDICIONES_LISTADO + " ORDER BY " + filtro.ordenSql()
                        + " LIMIT :limite OFFSET :desplazamiento"), ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query(CobroRepository::aDto)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, FiltroCobros filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM cobros c" + CONDICIONES_LISTADO), ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    /** Club de la liga al que se puede cargar un cobro; uno de otra liga no existe para este módulo. */
    Optional<CobroDto.ReferenciaDto> buscarClub(UUID ligaId, UUID clubId) {
        return jdbc.sql("SELECT id, nombre FROM clubes WHERE id = :id AND liga_id = :ligaId")
                .param("id", clubId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new CobroDto.ReferenciaDto(
                        fila.getObject("id", UUID.class), fila.getString("nombre")))
                .optional();
    }

    /** Equipo de la liga y club al que pertenece, para validar que el cobro sea de ese club. */
    Optional<EquipoDeClub> buscarEquipo(UUID ligaId, UUID equipoId) {
        return jdbc.sql("""
                        SELECT e.id, e.nombre, e.club_id
                          FROM equipos e
                          JOIN clubes c ON c.id = e.club_id
                         WHERE e.id = :id AND c.liga_id = :ligaId
                        """)
                .param("id", equipoId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new EquipoDeClub(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getObject("club_id", UUID.class)))
                .optional();
    }

    UUID insertar(UUID ligaId, DatosCobro datos, UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO cobros (liga_id, club_id, equipo_id, tipo, concepto, monto, periodo_inicio,
                                            fecha_limite, estado, creado_por)
                        VALUES (:ligaId, :clubId, :equipoId, CAST(:tipo AS tipo_cobro), :concepto, :monto,
                                :periodoInicio, :fechaLimite, 'PENDIENTE', :creadoPor)
                        RETURNING id
                        """)
                .param("ligaId", ligaId)
                .param("clubId", datos.clubId())
                .param("equipoId", datos.equipoId())
                .param("tipo", datos.tipo().name())
                .param("concepto", datos.concepto())
                .param("monto", datos.monto())
                .param("periodoInicio", datos.periodoInicio())
                .param("fechaLimite", datos.fechaLimite())
                .param("creadoPor", creadoPor)
                .query(UUID.class)
                .single();
    }

    /**
     * Adeudos visibles agrupados por club y equipo: solo {@code PENDIENTE} y {@code VENCIDO}, con sus
     * sumas y el número de cobros. El filtro de ámbito se aplica igual que en el listado.
     */
    List<FilaAdeudo> adeudos(UUID ligaId, UUID clubId, AlcanceAutorizacion alcance) {
        return jdbc.sql("""
                        SELECT c.club_id, cl.nombre AS club_nombre, c.equipo_id, e.nombre AS equipo_nombre,
                               coalesce(sum(c.monto) FILTER (WHERE c.estado = 'PENDIENTE'), 0.00) AS pendiente,
                               coalesce(sum(c.monto) FILTER (WHERE c.estado = 'VENCIDO'), 0.00) AS vencido,
                               coalesce(sum(c.monto), 0.00) AS total,
                               count(*) AS cobros
                          FROM cobros c
                          JOIN clubes cl ON cl.id = c.club_id
                          LEFT JOIN equipos e ON e.id = c.equipo_id
                         WHERE c.liga_id = :ligaId
                           AND c.estado IN ('PENDIENTE', 'VENCIDO')
                           AND (:todaLaLiga OR c.club_id = ANY(:clubes))
                           AND (CAST(:clubId AS uuid) IS NULL OR c.club_id = CAST(:clubId AS uuid))
                         GROUP BY c.club_id, cl.nombre, c.equipo_id, e.nombre
                         ORDER BY cl.nombre, e.nombre NULLS FIRST
                        """)
                .param("ligaId", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("clubId", clubId)
                .query(CobroRepository::aFilaAdeudo)
                .list();
    }

    /** Valores que se escriben en un cobro. */
    record DatosCobro(UUID clubId, UUID equipoId, TipoCobro tipo, String concepto, BigDecimal monto,
                      LocalDate periodoInicio, LocalDate fechaLimite) {
    }

    /** Equipo y club al que pertenece. */
    record EquipoDeClub(UUID id, String nombre, UUID clubId) {
    }

    /** Una fila del agregado de adeudos; {@code equipoId} es nulo en los cobros al club. */
    record FilaAdeudo(UUID clubId, String clubNombre, UUID equipoId, String equipoNombre,
                      BigDecimal pendiente, BigDecimal vencido, BigDecimal total, long cobros) {
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, UUID ligaId,
            FiltroCobros filtro, AlcanceAutorizacion alcance) {
        List<String> estados = filtro.estados().stream().map(EstadoCobro::name).toList();
        return sentencia
                .param("ligaId", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("clubId", filtro.clubId())
                .param("equipoId", filtro.equipoId())
                .param("tipo", filtro.tipo() == null ? null : filtro.tipo().name())
                .param("sinEstados", estados.isEmpty())
                .param("estados", new SqlArrayValue("text", estados.toArray()))
                .param("desde", filtro.desde())
                .param("hasta", filtro.hasta());
    }

    private static CobroDto aDto(ResultSet fila, int numero) throws SQLException {
        UUID clubId = fila.getObject("club_id", UUID.class);
        UUID equipoId = fila.getObject("equipo_id", UUID.class);
        return new CobroDto(
                fila.getObject("id", UUID.class),
                TipoCobro.valueOf(fila.getString("tipo")),
                fila.getString("concepto"),
                fila.getBigDecimal("monto"),
                fila.getObject("periodo_inicio", LocalDate.class),
                fila.getObject("fecha_limite", LocalDate.class),
                EstadoCobro.valueOf(fila.getString("estado")),
                new CobroDto.ReferenciaDto(clubId, fila.getString("club_nombre")),
                equipoId == null ? null
                        : new CobroDto.ReferenciaDto(equipoId, fila.getString("equipo_nombre")),
                fila.getObject("creado_en", OffsetDateTime.class));
    }

    private static FilaAdeudo aFilaAdeudo(ResultSet fila, int numero) throws SQLException {
        return new FilaAdeudo(
                fila.getObject("club_id", UUID.class),
                fila.getString("club_nombre"),
                fila.getObject("equipo_id", UUID.class),
                fila.getString("equipo_nombre"),
                fila.getBigDecimal("pendiente"),
                fila.getBigDecimal("vencido"),
                fila.getBigDecimal("total"),
                fila.getLong("cobros"));
    }
}

package mx.tecnm.morelia.siglim.canchas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code disponibilidad_canchas}. Los periodos {@code tstzrange} se escriben como
 * {@code [desde, hasta)} y se leen con {@code lower} y {@code upper}.
 *
 * <p>Refs: H-07.1, RF-029, RF-030
 */
@Repository
class DisponibilidadCanchaRepository {

    private static final String SELECCION = """
            SELECT id, tipo, lower(periodo) AS desde, upper(periodo) AS hasta, motivo, creado_en
              FROM disponibilidad_canchas
            """;

    private final JdbcClient jdbc;

    DisponibilidadCanchaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Registros que se traslapan con el rango, ordenados por inicio. */
    List<DisponibilidadDto> listar(UUID canchaId, Intervalo rango) {
        return jdbc.sql(SELECCION + """
                         WHERE cancha_id = :canchaId
                           AND periodo && tstzrange(CAST(:desde AS timestamptz), CAST(:hasta AS timestamptz), '[)')
                         ORDER BY lower(periodo), id
                        """)
                .param("canchaId", canchaId)
                .param("desde", rango.desde())
                .param("hasta", rango.hasta())
                .query(DisponibilidadCanchaRepository::aDto)
                .list();
    }

    /** Un {@code DISPONIBLE} de la cancha que se traslape con el periodo (D4), si existe. */
    Optional<DisponibilidadDto> buscarDisponibleTraslapado(UUID canchaId, Intervalo periodo) {
        return jdbc.sql(SELECCION + """
                         WHERE cancha_id = :canchaId
                           AND tipo = 'DISPONIBLE'
                           AND periodo && tstzrange(CAST(:desde AS timestamptz), CAST(:hasta AS timestamptz), '[)')
                         ORDER BY lower(periodo), id
                         LIMIT 1
                        """)
                .param("canchaId", canchaId)
                .param("desde", periodo.desde())
                .param("hasta", periodo.hasta())
                .query(DisponibilidadCanchaRepository::aDto)
                .optional();
    }

    DisponibilidadDto insertar(UUID canchaId, TipoDisponibilidad tipo, Intervalo periodo, String motivo,
            UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO disponibilidad_canchas (cancha_id, tipo, periodo, motivo, creado_por)
                        VALUES (:canchaId, CAST(:tipo AS tipo_disponibilidad),
                                tstzrange(CAST(:desde AS timestamptz), CAST(:hasta AS timestamptz), '[)'),
                                :motivo, :creadoPor)
                        RETURNING id, tipo, lower(periodo) AS desde, upper(periodo) AS hasta, motivo, creado_en
                        """)
                .param("canchaId", canchaId)
                .param("tipo", tipo.name())
                .param("desde", periodo.desde())
                .param("hasta", periodo.hasta())
                .param("motivo", motivo)
                .param("creadoPor", creadoPor)
                .query(DisponibilidadCanchaRepository::aDto)
                .single();
    }

    private static DisponibilidadDto aDto(ResultSet fila, int numero) throws SQLException {
        return new DisponibilidadDto(
                fila.getObject("id", UUID.class),
                TipoDisponibilidad.valueOf(fila.getString("tipo")),
                Intervalo.enZonaDeNegocio(fila.getObject("desde", OffsetDateTime.class),
                        fila.getObject("hasta", OffsetDateTime.class)),
                fila.getString("motivo"),
                Intervalo.enZonaDeNegocio(fila.getObject("creado_en", OffsetDateTime.class)));
    }
}

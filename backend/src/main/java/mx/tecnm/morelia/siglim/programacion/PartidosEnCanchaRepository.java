package mx.tecnm.morelia.siglim.programacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.canchas.Intervalo;
import mx.tecnm.morelia.siglim.canchas.PartidoEnCanchaDto;
import mx.tecnm.morelia.siglim.canchas.PartidoEnCanchaDto.EquipoDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lectura de los partidos de una cancha. Solo consulta: nunca modifica {@code partidos}.
 *
 * <p>Refs: H-07.1, RF-030, PEN-007
 */
@Repository
class PartidosEnCanchaRepository {

    private static final String SELECCION = """
            SELECT p.id, p.estado, p.inicio, p.fin_bloque,
                   l.id AS local_id, l.nombre AS local_nombre,
                   v.id AS visitante_id, v.nombre AS visitante_nombre
              FROM partidos p
              JOIN equipos l ON l.id = p.equipo_local_id
              JOIN equipos v ON v.id = p.equipo_visitante_id
             WHERE p.cancha_id = :canchaId
            """;
    private static final String PROGRAMADOS = " AND p.estado IN ('PROGRAMADO', 'REPROGRAMADO')";
    private static final String TRASLAPADOS = """
             AND tstzrange(p.inicio, p.fin_bloque)
                 && tstzrange(CAST(:desde AS timestamptz), CAST(:hasta AS timestamptz), '[)')
            """;
    private static final String ORDEN = " ORDER BY p.inicio, p.id";

    private final JdbcClient jdbc;

    PartidosEnCanchaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<PartidoEnCanchaDto> listarProgramados(UUID canchaId, Intervalo periodo) {
        return traslapados(SELECCION + PROGRAMADOS + TRASLAPADOS + ORDEN, canchaId, periodo);
    }

    List<PartidoEnCanchaDto> listarNoCancelados(UUID canchaId, Intervalo periodo) {
        return traslapados(SELECCION + " AND p.estado <> 'CANCELADO'" + TRASLAPADOS + ORDEN, canchaId, periodo);
    }

    List<PartidoEnCanchaDto> listarProgramadosDesde(UUID canchaId, OffsetDateTime desde) {
        return jdbc.sql(SELECCION + PROGRAMADOS + " AND p.fin_bloque > CAST(:desde AS timestamptz)" + ORDEN)
                .param("canchaId", canchaId)
                .param("desde", desde)
                .query(PartidosEnCanchaRepository::aDto)
                .list();
    }

    private List<PartidoEnCanchaDto> traslapados(String sql, UUID canchaId, Intervalo periodo) {
        return jdbc.sql(sql)
                .param("canchaId", canchaId)
                .param("desde", periodo.desde())
                .param("hasta", periodo.hasta())
                .query(PartidosEnCanchaRepository::aDto)
                .list();
    }

    private static PartidoEnCanchaDto aDto(ResultSet fila, int numero) throws SQLException {
        return new PartidoEnCanchaDto(
                fila.getObject("id", UUID.class),
                fila.getString("estado"),
                Intervalo.enZonaDeNegocio(fila.getObject("inicio", OffsetDateTime.class),
                        fila.getObject("fin_bloque", OffsetDateTime.class)),
                new EquipoDto(fila.getObject("local_id", UUID.class), fila.getString("local_nombre")),
                new EquipoDto(fila.getObject("visitante_id", UUID.class), fila.getString("visitante_nombre")));
    }
}

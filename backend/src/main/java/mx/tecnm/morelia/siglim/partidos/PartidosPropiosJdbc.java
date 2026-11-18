package mx.tecnm.morelia.siglim.partidos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.ConsultaPartidosPropios;
import mx.tecnm.morelia.siglim.registros.PartidoPropio;
import mx.tecnm.morelia.siglim.registros.PartidoPropio.ResultadoDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Le da a {@code registros} los partidos del equipo de un registro para la consulta propia (H-24.1,
 * RF-144) sin que ese módulo, de un nivel inferior, dependa de este (ADR-0006, como
 * el ejemplo {@code partidos.ConsultaSanciones} de ese ADR).
 *
 * <p>SQL de solo lectura con {@code JdbcClient} que corre en la transacción del servicio que llama, con
 * su contexto de usuario y RLS (ADR-0014). El resultado es la versión vigente de {@code resultados}
 * (RF-052); el inicio se devuelve en {@code America/Mexico_City}.
 *
 * <p><b>[PROPUESTA]</b> DEC-144: próximos son los de jornadas {@code PUBLICADO} que no estén
 * {@code FINALIZADO} ni {@code CANCELADO}; jugados, los que tienen resultado oficial vigente.
 *
 * <p>Refs: H-24.1, RF-052, RF-144, ADR-0006, ADR-0014, DEC-143, DEC-144
 */
@Component
class PartidosPropiosJdbc implements ConsultaPartidosPropios {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String PARTIDOS_DEL_EQUIPO = """
            SELECT p.id, j.numero AS jornada, p.inicio, ca.nombre AS cancha,
                   el.id AS local_id, el.nombre AS local_nombre,
                   ev.id AS visitante_id, ev.nombre AS visitante_nombre,
                   p.equipo_local_id = :equipo AS es_local, p.estado,
                   r.id AS resultado_id, r.goles_local, r.goles_visitante, r.penales_local, r.penales_visitante
              FROM partidos p
              JOIN jornadas j ON j.id = p.jornada_id
              JOIN canchas ca ON ca.id = p.cancha_id
              JOIN equipos el ON el.id = p.equipo_local_id
              JOIN equipos ev ON ev.id = p.equipo_visitante_id
              LEFT JOIN resultados r ON r.partido_id = p.id AND r.vigente
             WHERE (p.equipo_local_id = :equipo OR p.equipo_visitante_id = :equipo)
            """;

    private final JdbcClient jdbc;

    PartidosPropiosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PartidoPropio> proximos(UUID equipoId, int limite) {
        return jdbc.sql(PARTIDOS_DEL_EQUIPO + """
                           AND j.estado_publicacion = 'PUBLICADO'
                           AND p.estado NOT IN ('FINALIZADO', 'CANCELADO')
                         ORDER BY p.inicio, p.id
                         LIMIT :limite
                        """)
                .param("equipo", equipoId)
                .param("limite", limite)
                .query(PartidosPropiosJdbc::aPartido)
                .list();
    }

    @Override
    public List<PartidoPropio> jugados(UUID equipoId, int limite) {
        return jdbc.sql(PARTIDOS_DEL_EQUIPO + """
                           AND r.id IS NOT NULL
                         ORDER BY p.inicio DESC, p.id DESC
                         LIMIT :limite
                        """)
                .param("equipo", equipoId)
                .param("limite", limite)
                .query(PartidosPropiosJdbc::aPartido)
                .list();
    }

    private static PartidoPropio aPartido(ResultSet fila, int numero) throws SQLException {
        ResultadoDto resultado = fila.getObject("resultado_id", UUID.class) == null ? null
                : new ResultadoDto(fila.getInt("goles_local"), fila.getInt("goles_visitante"),
                        fila.getObject("penales_local", Integer.class),
                        fila.getObject("penales_visitante", Integer.class));
        return new PartidoPropio(
                fila.getObject("id", UUID.class),
                fila.getInt("jornada"),
                fila.getObject("inicio", OffsetDateTime.class).atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime(),
                fila.getString("cancha"),
                new ReferenciaDto(fila.getObject("local_id", UUID.class), fila.getString("local_nombre")),
                new ReferenciaDto(fila.getObject("visitante_id", UUID.class), fila.getString("visitante_nombre")),
                fila.getBoolean("es_local"),
                fila.getString("estado"),
                resultado);
    }
}

package mx.tecnm.morelia.siglim.disciplina;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.registros.ConsultaSancionesPropias;
import mx.tecnm.morelia.siglim.registros.SancionPropia;
import mx.tecnm.morelia.siglim.registros.SancionPropia.ArticuloDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Le da a {@code registros} las sanciones de una persona para la consulta propia y la elegibilidad
 * (H-24.1, RF-020, RF-144) sin que ese módulo, de un nivel inferior, dependa de este (ADR-0006, como
 * el ejemplo {@code partidos.ConsultaSanciones} de ese ADR).
 *
 * <p>SQL de solo lectura con {@code JdbcClient} que corre en la transacción del servicio que llama, con
 * su contexto de usuario y RLS (ADR-0014): el titular ve sus sanciones y sus expedientes por
 * {@code persona_visible} ({@code sanciones_leer}, {@code expediente_visible}). El artículo es el de la
 * resolución y el folio, el del expediente ({@code sanciones → resoluciones → expedientes/articulos}).
 *
 * <p>Refs: H-24.1, RF-020, RF-144, ADR-0006, ADR-0014, DEC-141, DEC-143
 */
@Component
class SancionesPropiasJdbc implements ConsultaSancionesPropias {

    private final JdbcClient jdbc;
    private final UUID ligaId;

    SancionesPropiasJdbc(JdbcClient jdbc, SiglimPropiedades propiedades) {
        this.jdbc = jdbc;
        this.ligaId = propiedades.ligaId();
    }

    @Override
    public List<SancionPropia> dePersona(UUID personaId) {
        return jdbc.sql("""
                        SELECT s.id, s.tipo, s.estado, s.partidos, s.partidos_cumplidos, s.vigente_desde,
                               s.vigente_hasta, a.numero AS articulo_numero, a.descripcion AS articulo_descripcion,
                               x.folio
                          FROM sanciones s
                          JOIN resoluciones r ON r.id = s.resolucion_id
                          JOIN expedientes x ON x.id = r.expediente_id
                          JOIN temporadas t ON t.id = s.temporada_id
                          LEFT JOIN articulos a ON a.id = r.articulo_id
                         WHERE s.sujeto = 'JUGADOR' AND s.persona_id = :persona AND x.liga_id = :ligaId
                           AND (s.estado = 'VIGENTE' OR t.estado <> 'CERRADA')
                         ORDER BY s.vigente_desde DESC, s.creado_en DESC, s.id
                        """)
                .param("persona", personaId)
                .param("ligaId", ligaId)
                .query(SancionesPropiasJdbc::aSancion)
                .list();
    }

    private static SancionPropia aSancion(ResultSet fila, int numero) throws SQLException {
        String articulo = fila.getString("articulo_numero");
        return new SancionPropia(
                fila.getObject("id", UUID.class),
                fila.getString("tipo"),
                fila.getString("estado"),
                fila.getObject("partidos", Integer.class),
                fila.getInt("partidos_cumplidos"),
                fila.getObject("vigente_desde", LocalDate.class),
                fila.getObject("vigente_hasta", LocalDate.class),
                articulo == null ? null : new ArticuloDto(articulo, fila.getString("articulo_descripcion")),
                fila.getString("folio"));
    }
}

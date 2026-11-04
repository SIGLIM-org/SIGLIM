package mx.tecnm.morelia.siglim.disciplina;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code articulos}: catálogo pequeño de la liga, sin paginación (convenciones de API §4).
 * Los enums se leen con {@code CAST(... AS text)} para que Spring los reciba como texto y el
 * mapeador los convierta a los enums del módulo.
 *
 * <p>Refs: H-26.1, RF-129, RF-060, ADR-0011
 */
@Repository
class ArticulosRepository {

    private static final String SELECCION = """
            SELECT id, numero, descripcion, CAST(tipo_sancion AS text) AS tipo_sancion,
                   CAST(sujeto AS text) AS sujeto, CAST(efecto AS text) AS efecto,
                   aplicacion_automatica, partidos_suspension, puntos, monto_multa,
                   otorga_default_restantes, activo, es_demo
              FROM articulos
            """;

    private final JdbcClient jdbc;

    ArticulosRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<ArticuloDto> listar(UUID ligaId, Boolean activo) {
        return jdbc.sql(SELECCION + """
                         WHERE liga_id = :ligaId
                           AND (CAST(:activo AS boolean) IS NULL OR activo = CAST(:activo AS boolean))
                         ORDER BY numero
                        """)
                .param("ligaId", ligaId)
                .param("activo", activo)
                .query(ArticulosRepository::aDto)
                .list();
    }

    Optional<ArticuloDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE id = :id AND liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(ArticulosRepository::aDto)
                .optional();
    }

    /** Lee el artículo y toma su candado: serializa las escrituras sobre él hasta el fin de la transacción. */
    Optional<ArticuloDto> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE id = :id AND liga_id = :ligaId FOR UPDATE")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(ArticulosRepository::aDto)
                .optional();
    }

    boolean existeNumero(UUID ligaId, String numero, UUID excepto) {
        return jdbc.sql("""
                        SELECT EXISTS (
                                 SELECT 1 FROM articulos
                                  WHERE liga_id = :ligaId AND numero = :numero
                                    AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid)))
                        """)
                .param("ligaId", ligaId)
                .param("numero", numero)
                .param("excepto", excepto)
                .query(Boolean.class)
                .single();
    }

    UUID insertar(UUID ligaId, DatosArticulo datos) {
        return conDatos(jdbc.sql("""
                        INSERT INTO articulos (liga_id, numero, descripcion, tipo_sancion, sujeto, efecto,
                                               aplicacion_automatica, partidos_suspension, puntos, monto_multa,
                                               otorga_default_restantes)
                        VALUES (:ligaId, :numero, :descripcion, CAST(:tipoSancion AS tipo_sancion),
                                CAST(:sujeto AS sujeto_sancion), CAST(:efecto AS efecto_sancion),
                                :aplicacionAutomatica, :partidosSuspension, :puntos, :montoMulta,
                                :otorgaDefaultRestantes)
                        RETURNING id
                        """), datos)
                .param("ligaId", ligaId)
                .query(UUID.class)
                .single();
    }

    void actualizar(UUID id, DatosArticulo datos, boolean activo) {
        conDatos(jdbc.sql("""
                        UPDATE articulos
                           SET numero = :numero,
                               descripcion = :descripcion,
                               tipo_sancion = CAST(:tipoSancion AS tipo_sancion),
                               sujeto = CAST(:sujeto AS sujeto_sancion),
                               efecto = CAST(:efecto AS efecto_sancion),
                               aplicacion_automatica = :aplicacionAutomatica,
                               partidos_suspension = :partidosSuspension,
                               puntos = :puntos,
                               monto_multa = :montoMulta,
                               otorga_default_restantes = :otorgaDefaultRestantes,
                               activo = :activo
                         WHERE id = :id
                        """), datos)
                .param("id", id)
                .param("activo", activo)
                .update();
    }

    private org.springframework.jdbc.core.simple.JdbcClient.StatementSpec conDatos(
            org.springframework.jdbc.core.simple.JdbcClient.StatementSpec sql, DatosArticulo datos) {
        return sql.param("numero", datos.numero())
                .param("descripcion", datos.descripcion())
                .param("tipoSancion", datos.tipoSancion().name())
                .param("sujeto", datos.sujeto().name())
                .param("efecto", datos.efecto().name())
                .param("aplicacionAutomatica", datos.aplicacionAutomatica())
                .param("partidosSuspension", datos.partidosSuspension())
                .param("puntos", datos.puntos())
                .param("montoMulta", datos.montoMulta())
                .param("otorgaDefaultRestantes", datos.otorgaDefaultRestantes());
    }

    private static ArticuloDto aDto(ResultSet rs, int fila) throws SQLException {
        return new ArticuloDto(
                rs.getObject("id", UUID.class),
                rs.getString("numero"),
                rs.getString("descripcion"),
                TipoSancion.valueOf(rs.getString("tipo_sancion")),
                SujetoSancion.valueOf(rs.getString("sujeto")),
                EfectoSancion.valueOf(rs.getString("efecto")),
                rs.getBoolean("aplicacion_automatica"),
                rs.getObject("partidos_suspension") == null ? null : rs.getInt("partidos_suspension"),
                rs.getObject("puntos") == null ? null : rs.getInt("puntos"),
                rs.getBigDecimal("monto_multa"),
                rs.getBoolean("otorga_default_restantes"),
                rs.getBoolean("activo"),
                rs.getBoolean("es_demo"));
    }

    /** Estado completo de un artículo para escribir en la tabla. */
    record DatosArticulo(String numero, String descripcion, TipoSancion tipoSancion, SujetoSancion sujeto,
                         EfectoSancion efecto, boolean aplicacionAutomatica, Integer partidosSuspension,
                         Integer puntos, BigDecimal montoMulta, boolean otorgaDefaultRestantes) {
    }
}

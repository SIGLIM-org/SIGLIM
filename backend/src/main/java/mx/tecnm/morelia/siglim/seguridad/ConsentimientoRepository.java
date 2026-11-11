package mx.tecnm.morelia.siglim.seguridad;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code avisos_privacidad} y {@code consentimientos} (M-01).
 *
 * <p>La persona y la tutela se consultan con las funciones {@code SECURITY DEFINER}
 * {@code es_menor} y {@code es_tutor_vigente} (DEC-78): el consentimiento puede registrarse antes de
 * que la persona pertenezca al club, y la tutela pudo registrarla otro club. Así M-01 no depende del
 * paquete {@code organizacion} (ADR-0006).
 *
 * <p>Refs: H-09.1, RF-097, VAL-025, DEC-78, DEC-80, DEC-81
 */
@Repository
class ConsentimientoRepository {

    private static final String COLUMNAS_AVISO =
            "id, tipo, version, contenido, finalidades, vigente_desde, es_demo";

    private final JdbcClient jdbc;

    ConsentimientoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Versión vigente del tipo en la liga: la de mayor {@code vigente_desde} que no sea posterior a
     * {@code hoy}; ante un empate, la creada al último <b>[PROPUESTA]</b>.
     */
    Optional<AvisoPrivacidadDto> buscarAvisoVigente(UUID ligaId, TipoAvisoPrivacidad tipo, LocalDate hoy) {
        return jdbc.sql("SELECT " + COLUMNAS_AVISO + """
                         FROM avisos_privacidad
                        WHERE liga_id = :liga
                          AND tipo = CAST(:tipo AS tipo_aviso_privacidad)
                          AND vigente_desde <= :hoy
                        ORDER BY vigente_desde DESC, creado_en DESC
                        LIMIT 1
                        """)
                .param("liga", ligaId)
                .param("tipo", tipo.name())
                .param("hoy", hoy)
                .query((fila, numero) -> aviso(fila))
                .optional();
    }

    Optional<AvisoPrivacidadDto> buscarAviso(UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS_AVISO + " FROM avisos_privacidad WHERE id = :id")
                .param("id", id)
                .query((fila, numero) -> aviso(fila))
                .optional();
    }

    /** Si la persona es menor en esa fecha; vacío si no existe (DEC-78). */
    Optional<Boolean> esMenor(UUID personaId, LocalDate fecha) {
        return jdbc.sql("SELECT siglim.es_menor(:persona, :fecha) AS menor")
                .param("persona", personaId)
                .param("fecha", fecha)
                .query((fila, numero) -> {
                    boolean menor = fila.getBoolean("menor");
                    return fila.wasNull() ? Optional.<Boolean>empty() : Optional.of(menor);
                })
                .single();
    }

    boolean esTutorVigente(UUID menorId, UUID tutorId) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT siglim.es_tutor_vigente(:menor, :tutor)")
                .param("menor", menorId)
                .param("tutor", tutorId)
                .query(Boolean.class)
                .single());
    }

    ConsentimientoDto insertar(UUID personaId, UUID otorgadoPorId, UUID avisoId, List<String> finalidades,
            UUID registradoPor) {
        return jdbc.sql("""
                        INSERT INTO consentimientos (persona_id, otorgado_por_id, aviso_id, finalidades, registrado_por)
                        VALUES (:persona, :otorgadoPor, :aviso, :finalidades, :registradoPor)
                        RETURNING id, persona_id, otorgado_por_id, aviso_id, finalidades, otorgado_en
                        """)
                .param("persona", personaId)
                .param("otorgadoPor", otorgadoPorId)
                .param("aviso", avisoId)
                .param("finalidades", new SqlArrayValue("text", finalidades.toArray()))
                .param("registradoPor", registradoPor)
                .query((fila, numero) -> new ConsentimientoDto(
                        fila.getObject("id", UUID.class),
                        fila.getObject("persona_id", UUID.class),
                        fila.getObject("otorgado_por_id", UUID.class),
                        fila.getObject("aviso_id", UUID.class),
                        textos(fila, "finalidades"),
                        fila.getObject("otorgado_en", OffsetDateTime.class)))
                .single();
    }

    private static AvisoPrivacidadDto aviso(ResultSet fila) throws SQLException {
        return new AvisoPrivacidadDto(
                fila.getObject("id", UUID.class),
                TipoAvisoPrivacidad.valueOf(fila.getString("tipo")),
                fila.getString("version"),
                fila.getString("contenido"),
                textos(fila, "finalidades"),
                fila.getObject("vigente_desde", LocalDate.class),
                fila.getBoolean("es_demo"));
    }

    private static List<String> textos(ResultSet fila, String columna) throws SQLException {
        return Arrays.asList((String[]) fila.getArray(columna).getArray());
    }
}

package mx.tecnm.morelia.siglim.temporadas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code convocatorias}: una fila por temporada ({@code UNIQUE (temporada_id)}), así que
 * guardarla es un {@code INSERT ... ON CONFLICT} y no hay historial que conservar.
 *
 * <p>La liga no está en la tabla: se alcanza por la temporada, y la condición de liga evita leer o
 * escribir la convocatoria de otra liga (ADR-0011).
 *
 * <p>Refs: B-02.1, RF-002, ADR-0011
 */
@Repository
class ConvocatoriaRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT c.id, c.temporada_id, c.referencia, c.version, c.documento_url, c.creado_en
              FROM convocatorias c
              JOIN temporadas t ON t.id = c.temporada_id
            """;

    private final JdbcClient jdbc;

    ConvocatoriaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<DatosConvocatoria> buscar(UUID ligaId, UUID temporadaId) {
        return jdbc.sql(SELECCION + " WHERE c.temporada_id = :temporadaId AND t.liga_id = :ligaId")
                .param("temporadaId", temporadaId)
                .param("ligaId", ligaId)
                .query(ConvocatoriaRepository::aDatos)
                .optional();
    }

    /**
     * Crea la convocatoria de la temporada o reemplaza la existente (RF-002).
     *
     * <p>Es un {@code PUT}: el cuerpo representa la referencia completa, así que un campo omitido
     * queda en nulo en lugar de conservar el valor anterior.
     */
    UUID guardar(UUID temporadaId, String referencia, String version, String documentoUrl) {
        return jdbc.sql("""
                        INSERT INTO convocatorias (temporada_id, referencia, version, documento_url)
                        VALUES (:temporadaId, :referencia, :version, :documentoUrl)
                        ON CONFLICT (temporada_id) DO UPDATE
                           SET referencia = EXCLUDED.referencia,
                               version = EXCLUDED.version,
                               documento_url = EXCLUDED.documento_url
                        RETURNING id
                        """)
                .param("temporadaId", temporadaId)
                .param("referencia", referencia)
                .param("version", version)
                .param("documentoUrl", documentoUrl)
                .query(UUID.class)
                .single();
    }

    /** Fila de {@code convocatorias} con la fecha ya convertida a la zona de negocio. */
    record DatosConvocatoria(UUID id, UUID temporadaId, String referencia, String version,
                             String documentoUrl, OffsetDateTime creadoEn) {
    }

    private static DatosConvocatoria aDatos(ResultSet fila, int numero) throws SQLException {
        return new DatosConvocatoria(
                fila.getObject("id", UUID.class),
                fila.getObject("temporada_id", UUID.class),
                fila.getString("referencia"),
                fila.getString("version"),
                fila.getString("documento_url"),
                aZona(fila.getObject("creado_en", OffsetDateTime.class)));
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }
}

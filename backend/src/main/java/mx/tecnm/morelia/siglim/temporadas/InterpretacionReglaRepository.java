package mx.tecnm.morelia.siglim.temporadas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code interpretaciones_regla}. La tabla no guarda la liga: se alcanza por la temporada, y
 * la condición de liga evita leer o escribir las interpretaciones de otra liga (ADR-0011).
 *
 * <p>El alta siempre escribe {@code PENDIENTE}: el estado no viene del cuerpo de la petición
 * (RF-007). La resolución sobrescribe la fila y conserva la anterior en la auditoría, porque la
 * tabla no tiene historial.
 *
 * <p>Refs: B-03.1, RF-007, ADR-0011
 */
@Repository
class InterpretacionReglaRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT i.id, i.temporada_id, i.descripcion, i.estado, i.registrada_por, i.registrada_en,
                   i.resuelta_por, i.resuelta_en, i.observaciones
              FROM interpretaciones_regla i
              JOIN temporadas t ON t.id = i.temporada_id
            """;

    /** Condición del listado, compartida con el {@code COUNT} para no delatar filas fuera de ámbito. */
    private static final String FILTRO_LISTADO = """
             WHERE t.liga_id = :ligaId
               AND :todaLaLiga
               AND (CAST(:temporadaId AS text) IS NULL OR i.temporada_id = CAST(:temporadaId AS uuid))
               AND (CAST(:estado AS text) IS NULL OR i.estado::text = CAST(:estado AS text))
            """;

    private final JdbcClient jdbc;

    InterpretacionReglaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<DatosInterpretacion> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE i.id = :id AND t.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(InterpretacionReglaRepository::aDatos)
                .optional();
    }

    /** Lee la interpretación y toma su candado: serializa dos resoluciones simultáneas (RF-007). */
    Optional<DatosInterpretacion> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE i.id = :id AND t.liga_id = :ligaId FOR UPDATE OF i")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(InterpretacionReglaRepository::aDatos)
                .optional();
    }

    List<DatosInterpretacion> listar(UUID ligaId, UUID temporadaId, EstadoInterpretacion estado, int limite,
            long desplazamiento, boolean todaLaLiga) {
        return conFiltro(jdbc.sql(SELECCION + FILTRO_LISTADO + """
                         ORDER BY i.registrada_en DESC, i.id
                         LIMIT :limite OFFSET :desplazamiento
                        """), ligaId, temporadaId, estado, todaLaLiga)
                .param("limite", limite)
                .param("desplazamiento", desplazamiento)
                .query(InterpretacionReglaRepository::aDatos)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, UUID temporadaId, EstadoInterpretacion estado, boolean todaLaLiga) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM interpretaciones_regla i"
                + " JOIN temporadas t ON t.id = i.temporada_id" + FILTRO_LISTADO),
                ligaId, temporadaId, estado, todaLaLiga)
                .query(Long.class)
                .single();
    }

    /** Registra la interpretación en {@code PENDIENTE} con la autoría de la sesión (RF-007). */
    UUID insertar(UUID temporadaId, String descripcion, UUID registradaPor) {
        return jdbc.sql("""
                        INSERT INTO interpretaciones_regla (temporada_id, descripcion, estado, registrada_por)
                        VALUES (:temporadaId, :descripcion, 'PENDIENTE', :registradaPor)
                        RETURNING id
                        """)
                .param("temporadaId", temporadaId)
                .param("descripcion", descripcion)
                .param("registradaPor", registradaPor)
                .query(UUID.class)
                .single();
    }

    /** Guarda la resolución del Presidente; la tabla no tiene trigger de estado (RF-007). */
    void resolver(UUID id, EstadoInterpretacion estado, UUID resueltaPor, OffsetDateTime resueltaEn,
            String observaciones) {
        jdbc.sql("""
                        UPDATE interpretaciones_regla
                           SET estado = CAST(:estado AS estado_interpretacion),
                               resuelta_por = :resueltaPor,
                               resuelta_en = :resueltaEn,
                               observaciones = :observaciones
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("estado", estado.name())
                .param("resueltaPor", resueltaPor)
                .param("resueltaEn", resueltaEn)
                .param("observaciones", observaciones)
                .update();
    }

    /** Fila de {@code interpretaciones_regla} con las fechas en la zona de negocio. */
    record DatosInterpretacion(UUID id, UUID temporadaId, String descripcion, EstadoInterpretacion estado,
                               UUID registradaPor, OffsetDateTime registradaEn, UUID resueltaPor,
                               OffsetDateTime resueltaEn, String observaciones) {
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec consulta, UUID ligaId,
            UUID temporadaId, EstadoInterpretacion estado, boolean todaLaLiga) {
        return consulta
                .param("ligaId", ligaId)
                .param("temporadaId", temporadaId == null ? null : temporadaId.toString())
                .param("estado", estado == null ? null : estado.name())
                .param("todaLaLiga", todaLaLiga);
    }

    private static DatosInterpretacion aDatos(ResultSet fila, int numero) throws SQLException {
        return new DatosInterpretacion(
                fila.getObject("id", UUID.class),
                fila.getObject("temporada_id", UUID.class),
                fila.getString("descripcion"),
                EstadoInterpretacion.valueOf(fila.getString("estado")),
                fila.getObject("registrada_por", UUID.class),
                aZona(fila.getObject("registrada_en", OffsetDateTime.class)),
                fila.getObject("resuelta_por", UUID.class),
                aZona(fila.getObject("resuelta_en", OffsetDateTime.class)),
                fila.getString("observaciones"));
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }
}

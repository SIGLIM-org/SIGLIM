package mx.tecnm.morelia.siglim.temporadas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code temporadas}. La lista de verificación se arma con subconsultas de conteo, para que
 * el listado no haga una petición por fila (convenciones de API §9.1).
 *
 * <p>Refs: H-04.1, B-02.1, RF-001, RF-002, RF-003, RF-004, RF-005, RF-100, RN-058, ADR-0011
 */
@Repository
class TemporadaRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT t.id, t.nombre, t.fecha_inicio, t.fecha_fin, t.estado, t.creado_en,
                   (SELECT count(*) FROM categorias_temporada ct
                     WHERE ct.temporada_id = t.id) AS categorias_asignadas,
                   (SELECT count(*) FROM grupos g
                      JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                     WHERE ct.temporada_id = t.id) AS grupos_creados,
                   (SELECT max(p.version) FROM parametros_temporada p
                     WHERE p.temporada_id = t.id) AS version_parametros,
                   c.id AS convocatoria_id, c.temporada_id AS convocatoria_temporada_id,
                   c.referencia AS convocatoria_referencia, c.version AS convocatoria_version,
                   c.documento_url AS convocatoria_documento_url,
                   c.creado_en AS convocatoria_creado_en
              FROM temporadas t
              LEFT JOIN convocatorias c ON c.temporada_id = t.id
            """;

    private final JdbcClient jdbc;

    TemporadaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<DatosTemporada> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE t.id = :id AND t.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(TemporadaRepository::aDatos)
                .optional();
    }

    /** Lee la temporada y toma su candado: serializa edición, activación y cierre. */
    Optional<DatosTemporada> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE t.id = :id AND t.liga_id = :ligaId FOR UPDATE OF t")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(TemporadaRepository::aDatos)
                .optional();
    }

    /** Todas las temporadas solo las ve el ámbito de liga; un ámbito de club no alcanza ninguna. */
    List<DatosTemporada> listar(UUID ligaId, int limite, long desplazamiento, boolean todaLaLiga) {
        return jdbc.sql(SELECCION + """
                         WHERE t.liga_id = :ligaId
                           AND :todaLaLiga
                         ORDER BY t.fecha_inicio DESC, t.id
                         LIMIT :limite OFFSET :desplazamiento
                        """)
                .param("ligaId", ligaId)
                .param("todaLaLiga", todaLaLiga)
                .param("limite", limite)
                .param("desplazamiento", desplazamiento)
                .query(TemporadaRepository::aDatos)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, boolean todaLaLiga) {
        return jdbc.sql("SELECT count(*) FROM temporadas t WHERE t.liga_id = :ligaId AND :todaLaLiga")
                .param("ligaId", ligaId)
                .param("todaLaLiga", todaLaLiga)
                .query(Long.class)
                .single();
    }

    /** La temporada activa de la liga, sin contar la que se está evaluando (RN-058). */
    Optional<TemporadaActiva> buscarActiva(UUID ligaId, UUID excepto) {
        return jdbc.sql("""
                        SELECT id, nombre
                          FROM temporadas
                         WHERE liga_id = :ligaId
                           AND estado = 'ACTIVA'
                           AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid))
                         ORDER BY id
                         LIMIT 1
                        """)
                .param("ligaId", ligaId)
                .param("excepto", excepto)
                .query((fila, numero) -> new TemporadaActiva(
                        fila.getObject("id", UUID.class), fila.getString("nombre")))
                .optional();
    }

    boolean existeNombre(UUID ligaId, String nombre, UUID excepto) {
        return jdbc.sql("""
                        SELECT EXISTS (
                                 SELECT 1 FROM temporadas
                                  WHERE liga_id = :ligaId AND nombre = :nombre
                                    AND (CAST(:excepto AS uuid) IS NULL OR id <> CAST(:excepto AS uuid)))
                        """)
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .param("excepto", excepto)
                .query(Boolean.class)
                .single();
    }

    UUID insertar(UUID ligaId, String nombre, LocalDate fechaInicio, LocalDate fechaFin, UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO temporadas (liga_id, nombre, fecha_inicio, fecha_fin, creado_por)
                        VALUES (:ligaId, :nombre, :fechaInicio, :fechaFin, :creadoPor)
                        RETURNING id
                        """)
                .param("ligaId", ligaId)
                .param("nombre", nombre)
                .param("fechaInicio", fechaInicio)
                .param("fechaFin", fechaFin)
                .param("creadoPor", creadoPor)
                .query(UUID.class)
                .single();
    }

    void actualizar(UUID id, String nombre, LocalDate fechaInicio, LocalDate fechaFin) {
        jdbc.sql("""
                        UPDATE temporadas
                           SET nombre = :nombre,
                               fecha_inicio = :fechaInicio,
                               fecha_fin = :fechaFin
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("nombre", nombre)
                .param("fechaInicio", fechaInicio)
                .param("fechaFin", fechaFin)
                .update();
    }

    void cambiarEstado(UUID id, EstadoTemporada estado) {
        jdbc.sql("UPDATE temporadas SET estado = CAST(:estado AS estado_temporada) WHERE id = :id")
                .param("id", id)
                .param("estado", estado.name())
                .update();
    }

    /** Fila de {@code temporadas} con los conteos de la lista de verificación y su convocatoria. */
    record DatosTemporada(UUID id, String nombre, LocalDate fechaInicio, LocalDate fechaFin,
                          EstadoTemporada estado, OffsetDateTime creadoEn, int categoriasAsignadas,
                          int gruposCreados, Integer versionParametros,
                          ConvocatoriaRepository.DatosConvocatoria convocatoria) {
    }

    /** Temporada activa de la liga, con lo mínimo para nombrarla en el error de RN-058. */
    record TemporadaActiva(UUID id, String nombre) {
    }

    private static DatosTemporada aDatos(ResultSet fila, int numero) throws SQLException {
        UUID convocatoriaId = fila.getObject("convocatoria_id", UUID.class);
        ConvocatoriaRepository.DatosConvocatoria convocatoria = convocatoriaId == null ? null
                : new ConvocatoriaRepository.DatosConvocatoria(
                        convocatoriaId,
                        fila.getObject("convocatoria_temporada_id", UUID.class),
                        fila.getString("convocatoria_referencia"),
                        fila.getString("convocatoria_version"),
                        fila.getString("convocatoria_documento_url"),
                        fila.getObject("convocatoria_creado_en", OffsetDateTime.class)
                                .atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime());
        return new DatosTemporada(
                fila.getObject("id", UUID.class),
                fila.getString("nombre"),
                fila.getObject("fecha_inicio", LocalDate.class),
                fila.getObject("fecha_fin", LocalDate.class),
                EstadoTemporada.valueOf(fila.getString("estado")),
                fila.getObject("creado_en", OffsetDateTime.class)
                        .atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime(),
                fila.getInt("categorias_asignadas"),
                fila.getInt("grupos_creados"),
                fila.getObject("version_parametros", Integer.class),
                convocatoria);
    }
}

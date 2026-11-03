package mx.tecnm.morelia.siglim.publicacion;

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
 * SQL de {@code comunicados}. El estado y las fechas de aprobación y publicación los escriben el
 * servicio y el trigger {@code tg_comunicados_publicacion}; aquí solo se guardan los cambios y se
 * leen en la zona horaria de negocio.
 *
 * <p>Refs: B-27.1, RF-090, ADR-0011
 */
@Repository
class ComunicadoRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT id, titulo, cuerpo, estado_publicacion, redactado_por, aprobado_por, aprobado_en,
                   publicado_en, observaciones, creado_en, actualizado_en
              FROM comunicados
            """;

    private final JdbcClient jdbc;

    ComunicadoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<DatosComunicado> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE id = :id AND liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(ComunicadoRepository::aDatos)
                .optional();
    }

    /** Lee el comunicado y toma su candado: serializa la edición y las transiciones de estado. */
    Optional<DatosComunicado> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE id = :id AND liga_id = :ligaId FOR UPDATE")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(ComunicadoRepository::aDatos)
                .optional();
    }

    /**
     * Comunicados de la liga, del más reciente al más antiguo.
     *
     * @param estado filtro opcional por estado de publicación
     */
    List<DatosComunicado> listar(UUID ligaId, EstadoPublicacion estado, int limite, long desplazamiento) {
        return jdbc.sql(SELECCION + """
                         WHERE liga_id = :ligaId
                           AND (CAST(:estado AS text) IS NULL
                                OR estado_publicacion = CAST(:estado AS estado_publicacion))
                         ORDER BY creado_en DESC, id
                         LIMIT :limite OFFSET :desplazamiento
                        """)
                .param("ligaId", ligaId)
                .param("estado", estado == null ? null : estado.name())
                .param("limite", limite)
                .param("desplazamiento", desplazamiento)
                .query(ComunicadoRepository::aDatos)
                .list();
    }

    /** Con la misma condición del listado, incluido el filtro opcional. */
    long contar(UUID ligaId, EstadoPublicacion estado) {
        return jdbc.sql("""
                        SELECT count(*)
                          FROM comunicados
                         WHERE liga_id = :ligaId
                           AND (CAST(:estado AS text) IS NULL
                                OR estado_publicacion = CAST(:estado AS estado_publicacion))
                        """)
                .param("ligaId", ligaId)
                .param("estado", estado == null ? null : estado.name())
                .query(Long.class)
                .single();
    }

    UUID insertar(UUID ligaId, String titulo, String cuerpo, UUID redactadoPor) {
        return jdbc.sql("""
                        INSERT INTO comunicados (liga_id, titulo, cuerpo, redactado_por)
                        VALUES (:ligaId, :titulo, :cuerpo, :redactadoPor)
                        RETURNING id
                        """)
                .param("ligaId", ligaId)
                .param("titulo", titulo)
                .param("cuerpo", cuerpo)
                .param("redactadoPor", redactadoPor)
                .query(UUID.class)
                .single();
    }

    void actualizar(UUID id, String titulo, String cuerpo) {
        jdbc.sql("""
                        UPDATE comunicados
                           SET titulo = :titulo,
                               cuerpo = :cuerpo
                         WHERE id = :id
                           AND estado_publicacion = 'BORRADOR'
                        """)
                .param("id", id)
                .param("titulo", titulo)
                .param("cuerpo", cuerpo)
                .update();
    }

    void enviarARevision(UUID id) {
        jdbc.sql("""
                        UPDATE comunicados
                           SET estado_publicacion = 'REVISION'
                         WHERE id = :id
                           AND estado_publicacion = 'BORRADOR'
                        """)
                .param("id", id)
                .update();
    }

    /**
     * Aplica el dictamen del Presidente. El trigger de publicación exige {@code aprobado_por} al
     * publicar y fija {@code publicado_en}; la devolución exige el motivo que fijó el servicio.
     */
    void resolver(UUID id, EstadoPublicacion estado, UUID aprobadoPor, OffsetDateTime aprobadoEn,
            String observaciones) {
        jdbc.sql("""
                        UPDATE comunicados
                           SET estado_publicacion = CAST(:estado AS estado_publicacion),
                               aprobado_por = CAST(:aprobadoPor AS uuid),
                               aprobado_en = CAST(:aprobadoEn AS timestamptz),
                               observaciones = CAST(:observaciones AS text)
                         WHERE id = :id
                           AND estado_publicacion = 'REVISION'
                        """)
                .param("id", id)
                .param("estado", estado.name())
                .param("aprobadoPor", aprobadoPor)
                .param("aprobadoEn", aprobadoEn)
                .param("observaciones", observaciones)
                .update();
    }

    /** Fila de {@code comunicados} con las fechas ya convertidas a la zona de negocio. */
    record DatosComunicado(UUID id, String titulo, String cuerpo, EstadoPublicacion estadoPublicacion,
                           UUID redactadoPor, UUID aprobadoPor, OffsetDateTime aprobadoEn,
                           OffsetDateTime publicadoEn, String observaciones, OffsetDateTime creadoEn,
                           OffsetDateTime actualizadoEn) {
    }

    private static DatosComunicado aDatos(ResultSet fila, int numero) throws SQLException {
        return new DatosComunicado(
                fila.getObject("id", UUID.class),
                fila.getString("titulo"),
                fila.getString("cuerpo"),
                EstadoPublicacion.valueOf(fila.getString("estado_publicacion")),
                fila.getObject("redactado_por", UUID.class),
                fila.getObject("aprobado_por", UUID.class),
                aZona(fila.getObject("aprobado_en", OffsetDateTime.class)),
                aZona(fila.getObject("publicado_en", OffsetDateTime.class)),
                fila.getString("observaciones"),
                aZona(fila.getObject("creado_en", OffsetDateTime.class)),
                aZona(fila.getObject("actualizado_en", OffsetDateTime.class)));
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }
}

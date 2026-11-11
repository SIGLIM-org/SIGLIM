package mx.tecnm.morelia.siglim.arbitraje;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL del catálogo de árbitros (RF-021, RF-112, RF-113).
 *
 * <ul>
 *   <li><b>Lectura.</b> El club y la persona se resuelven con {@code JOIN}; el nombre de quien
 *       propuso o validó se une con {@code LEFT JOIN} porque las políticas RLS de {@code perfiles}
 *       solo lo muestran a quien puede verlo. La CURP no se selecciona: el listado no la necesita
 *       (convenciones de API §9.2). Las fechas se devuelven en la zona de negocio.</li>
 *   <li><b>Ámbito.</b> El listado filtra por club en el {@code WHERE} y en el {@code COUNT} con la
 *       misma condición (ADR-0007).</li>
 *   <li><b>Concurrencia (DEC-90).</b> La validación bloquea el árbitro con {@code FOR UPDATE} para
 *       que dos dictámenes simultáneos no se pisen. El alta usa
 *       {@code INSERT … ON CONFLICT (persona_id) DO NOTHING RETURNING id}: si otra petición presentó
 *       antes a la misma persona, la transacción sigue sana y el servicio consulta el club que ya la
 *       tiene para responder {@code 409}, sin atrapar {@code DuplicateKeyException} (SCRUM-433).</li>
 * </ul>
 *
 * <p>Refs: H-11.1, RF-021, RF-112, RF-113, ADR-0007, ADR-0011
 */
@Repository
class ArbitroRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT a.id, a.persona_id,
                   concat_ws(' ', p.nombre, p.apellido_paterno, p.apellido_materno) AS persona_nombre,
                   cl.id AS club_id, cl.nombre AS club_nombre,
                   a.estado,
                   a.propuesto_por AS propuesto_por_id,
                   pp.nombre_mostrado AS propuesto_por_nombre,
                   a.propuesto_en,
                   a.validado_por AS validado_por_id,
                   vp.nombre_mostrado AS validado_por_nombre,
                   a.validado_en, a.observaciones
              FROM arbitros a
              JOIN personas p ON p.id = a.persona_id
              JOIN clubes cl ON cl.id = a.club_id
              LEFT JOIN perfiles pp ON pp.id = a.propuesto_por
              LEFT JOIN perfiles vp ON vp.id = a.validado_por
            """;

    private static final String CONDICIONES_LISTADO = """
             WHERE cl.liga_id = :ligaId
               AND (:todaLaLiga OR a.club_id = ANY(:clubes))
               AND (CAST(:clubId AS uuid) IS NULL OR a.club_id = CAST(:clubId AS uuid))
               AND (CAST(:estado AS text) IS NULL OR CAST(a.estado AS text) = CAST(:estado AS text))
            """;

    private final JdbcClient jdbc;

    ArbitroRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ArbitroDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE a.id = :id AND cl.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(ArbitroRepository::aDto)
                .optional();
    }

    /** Lee el árbitro y toma su candado: serializa la validación. */
    Optional<ArbitroDto> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE a.id = :id AND cl.liga_id = :ligaId FOR UPDATE OF a")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(ArbitroRepository::aDto)
                .optional();
    }

    List<ArbitroDto> listar(UUID ligaId, FiltroArbitros filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql(SELECCION + CONDICIONES_LISTADO
                        + " ORDER BY a.propuesto_en DESC, a.id LIMIT :limite OFFSET :desplazamiento"),
                ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query(ArbitroRepository::aDto)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, FiltroArbitros filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM arbitros a"
                        + " JOIN clubes cl ON cl.id = a.club_id" + CONDICIONES_LISTADO),
                ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    /** Club de la liga activa; un club de otra liga responde 404. */
    boolean existeClub(UUID ligaId, UUID clubId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM clubes WHERE liga_id = :ligaId AND id = :clubId)")
                .param("ligaId", ligaId)
                .param("clubId", clubId)
                .query(Boolean.class)
                .single();
    }

    /**
     * El club que ya presenta a esa persona. El índice único de {@code persona_id} es global; del
     * club solo se devuelve el nombre, nunca su estado ni identificadores (DEC-88).
     */
    Optional<ResumenArbitro> buscarPorPersona(UUID personaId) {
        return jdbc.sql("""
                        SELECT cl.nombre AS club_nombre
                          FROM arbitros a
                          JOIN clubes cl ON cl.id = a.club_id
                         WHERE a.persona_id = :personaId
                        """)
                .param("personaId", personaId)
                .query((fila, numero) -> new ResumenArbitro(fila.getString("club_nombre")))
                .optional();
    }

    /**
     * Inserta al árbitro {@code PROPUESTO} (lo pone el {@code DEFAULT}). Si otra petición presentó
     * antes a la misma persona, {@code ON CONFLICT … DO NOTHING} no inserta y devuelve vacío sin
     * abortar la transacción (DEC-90; defecto SCRUM-433).
     */
    Optional<UUID> insertar(UUID clubId, UUID personaId, UUID propuestoPor) {
        return jdbc.sql("""
                        INSERT INTO arbitros (club_id, persona_id, propuesto_por)
                        VALUES (:clubId, :personaId, :propuestoPor)
                        ON CONFLICT (persona_id) DO NOTHING
                        RETURNING id
                        """)
                .param("clubId", clubId)
                .param("personaId", personaId)
                .param("propuestoPor", propuestoPor)
                .query(UUID.class)
                .optional();
    }

    /** Aplica el dictamen: estado final, perfil y fecha de validación, y observaciones. */
    void resolver(UUID id, EstadoArbitro estado, UUID validadoPor, OffsetDateTime validadoEn,
            String observaciones) {
        jdbc.sql("""
                        UPDATE arbitros
                           SET estado = CAST(:estado AS estado_arbitro),
                               validado_por = CAST(:validadoPor AS uuid),
                               validado_en = CAST(:validadoEn AS timestamptz),
                               observaciones = CAST(:observaciones AS text)
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("estado", estado.name())
                .param("validadoPor", validadoPor)
                .param("validadoEn", validadoEn)
                .param("observaciones", observaciones)
                .update();
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, UUID ligaId,
            FiltroArbitros filtro, AlcanceAutorizacion alcance) {
        return sentencia
                .param("ligaId", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("clubId", filtro.clubId())
                .param("estado", filtro.estado() == null ? null : filtro.estado().name());
    }

    private static ArbitroDto aDto(ResultSet fila, int numero) throws SQLException {
        UUID validadoPorId = fila.getObject("validado_por_id", UUID.class);
        return new ArbitroDto(
                fila.getObject("id", UUID.class),
                new ArbitroDto.PersonaResumenDto(
                        fila.getObject("persona_id", UUID.class),
                        fila.getString("persona_nombre")),
                new ArbitroDto.ReferenciaDto(
                        fila.getObject("club_id", UUID.class),
                        fila.getString("club_nombre")),
                EstadoArbitro.valueOf(fila.getString("estado")),
                new ArbitroDto.PerfilDto(
                        fila.getObject("propuesto_por_id", UUID.class),
                        fila.getString("propuesto_por_nombre")),
                aZona(fila.getObject("propuesto_en", OffsetDateTime.class)),
                validadoPorId == null ? null : new ArbitroDto.PerfilDto(validadoPorId,
                        fila.getString("validado_por_nombre")),
                aZona(fila.getObject("validado_en", OffsetDateTime.class)),
                fila.getString("observaciones"));
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    /** El club que ya presenta a la persona, solo para explicar el {@code 409} (DEC-88). */
    record ResumenArbitro(String clubNombre) {
    }
}

package mx.tecnm.morelia.siglim.arbitraje;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code disponibilidad_arbitral} (RF-022).
 *
 * <ul>
 *   <li><b>Periodos.</b> {@code periodo} es un {@code tstzrange} que incluye {@code desde} y
 *       excluye {@code hasta}; se escribe con {@code tstzrange(..., '[)')} y se lee con
 *       {@code lower} y {@code upper}, como el resto de los intervalos del API.</li>
 *   <li><b>Liga.</b> La tabla no tiene {@code liga_id}; el árbitro se resuelve con su club y se
 *       filtra por la liga activa, así que un árbitro de otra liga responde {@code 404}.</li>
 *   <li><b>Borrado.</b> La disponibilidad no es historial deportivo: {@code DELETE} quita la fila.
 *       El diccionario no la marca como histórica.</li>
 * </ul>
 *
 * <p>Refs: B-09.1, RF-022, ADR-0011
 */
@Repository
class DisponibilidadArbitralRepository {

    private static final String SELECCION = """
            SELECT id, arbitro_id, lower(periodo) AS desde, upper(periodo) AS hasta, disponible, creado_en
              FROM disponibilidad_arbitral
            """;

    private final JdbcClient jdbc;

    DisponibilidadArbitralRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Árbitro de la liga activa con los datos que necesitan la autorización y el estado. */
    Optional<ArbitroDisponible> buscarArbitro(UUID ligaId, UUID arbitroId) {
        return jdbc.sql("""
                         SELECT a.persona_id, a.estado
                           FROM arbitros a
                           JOIN clubes cl ON cl.id = a.club_id
                          WHERE a.id = :arbitroId AND cl.liga_id = :ligaId
                         """)
                .param("arbitroId", arbitroId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new ArbitroDisponible(
                        fila.getObject("persona_id", UUID.class),
                        EstadoArbitro.valueOf(fila.getString("estado"))))
                .optional();
    }

    /** Periodos del árbitro, por inicio; con rango solo los que se traslapan con él. */
    List<DisponibilidadArbitralDto> listar(UUID arbitroId, PeriodoArbitral rango) {
        JdbcClient.StatementSpec sentencia = jdbc.sql(SELECCION
                        + " WHERE arbitro_id = :arbitroId"
                        + (rango == null ? "" : """
                         AND periodo && tstzrange(CAST(:desde AS timestamptz),
                                                  CAST(:hasta AS timestamptz), '[)')
                        """)
                        + " ORDER BY lower(periodo), id")
                .param("arbitroId", arbitroId);
        if (rango != null) {
            sentencia = sentencia.param("desde", rango.desde()).param("hasta", rango.hasta());
        }
        return sentencia.query(DisponibilidadArbitralRepository::aDto).list();
    }

    Optional<DisponibilidadArbitralDto> buscar(UUID arbitroId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE id = :id AND arbitro_id = :arbitroId")
                .param("id", id)
                .param("arbitroId", arbitroId)
                .query(DisponibilidadArbitralRepository::aDto)
                .optional();
    }

    DisponibilidadArbitralDto insertar(UUID arbitroId, PeriodoArbitral periodo, boolean disponible) {
        return jdbc.sql("""
                         INSERT INTO disponibilidad_arbitral (arbitro_id, periodo, disponible)
                         VALUES (:arbitroId,
                                 tstzrange(CAST(:desde AS timestamptz),
                                           CAST(:hasta AS timestamptz), '[)'),
                                 :disponible)
                         RETURNING id, arbitro_id, lower(periodo) AS desde, upper(periodo) AS hasta,
                                   disponible, creado_en
                         """)
                .param("arbitroId", arbitroId)
                .param("desde", periodo.desde())
                .param("hasta", periodo.hasta())
                .param("disponible", disponible)
                .query(DisponibilidadArbitralRepository::aDto)
                .single();
    }

    /** Borra el periodo del árbitro; {@code false} si ya no existía. */
    boolean eliminar(UUID arbitroId, UUID id) {
        return jdbc.sql("DELETE FROM disponibilidad_arbitral WHERE id = :id AND arbitro_id = :arbitroId")
                .param("id", id)
                .param("arbitroId", arbitroId)
                .update() == 1;
    }

    private static DisponibilidadArbitralDto aDto(ResultSet fila, int numero) throws SQLException {
        return new DisponibilidadArbitralDto(
                fila.getObject("id", UUID.class),
                fila.getObject("arbitro_id", UUID.class),
                PeriodoArbitral.enZonaDeNegocio(
                        fila.getObject("desde", OffsetDateTime.class),
                        fila.getObject("hasta", OffsetDateTime.class)),
                fila.getBoolean("disponible"),
                PeriodoArbitral.enZonaDeNegocio(fila.getObject("creado_en", OffsetDateTime.class)));
    }

    /** Datos del árbitro que la autorización y la regla de estado necesitan (sin datos personales). */
    record ArbitroDisponible(UUID personaId, EstadoArbitro estado) {
    }
}

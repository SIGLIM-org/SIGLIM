package mx.tecnm.morelia.siglim.canchas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.canchas.HorarioCanchaClubDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code horarios_cancha_club} y de las referencias que valida (temporada, categorías de la
 * temporada y clubes de la liga). Los nombres de categoría y club se leen con {@code JOIN} de solo
 * lectura para la respuesta.
 *
 * <p>Refs: B-13.1, RF-119, DEC-33
 */
@Repository
class HorarioCanchaClubRepository {

    /** Temporada de la liga con lo necesario para validar RF-003. */
    record TemporadaHorarios(UUID id, String nombre, EstadoTemporada estado) {
    }

    /** Horario ya validado, listo para insertar. */
    record NuevoHorario(UUID categoriaId, UUID clubId, int diaSemana, LocalTime horaInicio, LocalTime horaFin,
                        boolean esComodato) {
    }

    private final JdbcClient jdbc;

    HorarioCanchaClubRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<TemporadaHorarios> buscarTemporada(UUID ligaId, UUID temporadaId) {
        return temporada(ligaId, temporadaId, "");
    }

    /**
     * Lee la temporada con {@code FOR SHARE}: un cierre simultáneo espera a que terminen de guardarse
     * los horarios, y los horarios no se guardan sobre una temporada que se está cerrando.
     */
    Optional<TemporadaHorarios> bloquearTemporada(UUID ligaId, UUID temporadaId) {
        return temporada(ligaId, temporadaId, " FOR SHARE");
    }

    private Optional<TemporadaHorarios> temporada(UUID ligaId, UUID temporadaId, String candado) {
        return jdbc.sql("SELECT id, nombre, estado FROM temporadas WHERE id = :id AND liga_id = :ligaId" + candado)
                .param("id", temporadaId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new TemporadaHorarios(fila.getObject("id", UUID.class),
                        fila.getString("nombre"), EstadoTemporada.valueOf(fila.getString("estado"))))
                .optional();
    }

    /** Categorías asignadas a la temporada ({@code categorias_temporada}). */
    Set<UUID> categoriasDeTemporada(UUID temporadaId) {
        return new HashSet<>(jdbc.sql("SELECT categoria_id FROM categorias_temporada WHERE temporada_id = :temporadaId")
                .param("temporadaId", temporadaId)
                .query(UUID.class)
                .list());
    }

    /** De los clubes indicados, los que existen en la liga. */
    Set<UUID> clubesDeLiga(UUID ligaId, Collection<UUID> clubes) {
        if (clubes.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("SELECT id FROM clubes WHERE liga_id = :ligaId AND id IN (:clubes)")
                .param("ligaId", ligaId)
                .param("clubes", clubes)
                .query(UUID.class)
                .list());
    }

    /** Horarios de la cancha en la temporada, por día, hora de inicio y orden de la categoría. */
    List<HorarioCanchaClubDto> listar(UUID canchaId, UUID temporadaId) {
        return jdbc.sql("""
                        SELECT h.id, h.cancha_id, h.temporada_id, h.categoria_id, cat.nombre AS categoria_nombre,
                               h.club_id, cl.nombre AS club_nombre, h.dia_semana, h.hora_inicio, h.hora_fin,
                               h.es_comodato
                          FROM horarios_cancha_club h
                          JOIN categorias cat ON cat.id = h.categoria_id
                          JOIN clubes cl ON cl.id = h.club_id
                         WHERE h.cancha_id = :canchaId
                           AND h.temporada_id = :temporadaId
                         ORDER BY h.dia_semana, h.hora_inicio, cat.orden, h.id
                        """)
                .param("canchaId", canchaId)
                .param("temporadaId", temporadaId)
                .query(HorarioCanchaClubRepository::aDto)
                .list();
    }

    /** Borra solo los horarios de esa cancha en esa temporada; los de otras temporadas no se tocan. */
    int eliminar(UUID canchaId, UUID temporadaId) {
        return jdbc.sql("DELETE FROM horarios_cancha_club WHERE cancha_id = :canchaId AND temporada_id = :temporadaId")
                .param("canchaId", canchaId)
                .param("temporadaId", temporadaId)
                .update();
    }

    void insertar(UUID canchaId, UUID temporadaId, NuevoHorario horario) {
        jdbc.sql("""
                        INSERT INTO horarios_cancha_club (cancha_id, club_id, temporada_id, categoria_id, dia_semana,
                                                          hora_inicio, hora_fin, es_comodato)
                        VALUES (:canchaId, :clubId, :temporadaId, :categoriaId, :diaSemana,
                                CAST(:horaInicio AS time), CAST(:horaFin AS time), :esComodato)
                        """)
                .param("canchaId", canchaId)
                .param("clubId", horario.clubId())
                .param("temporadaId", temporadaId)
                .param("categoriaId", horario.categoriaId())
                .param("diaSemana", horario.diaSemana())
                .param("horaInicio", horario.horaInicio().toString())
                .param("horaFin", horario.horaFin().toString())
                .param("esComodato", horario.esComodato())
                .update();
    }

    private static HorarioCanchaClubDto aDto(ResultSet fila, int numero) throws SQLException {
        return new HorarioCanchaClubDto(
                fila.getObject("id", UUID.class),
                fila.getObject("cancha_id", UUID.class),
                fila.getObject("temporada_id", UUID.class),
                new ReferenciaDto(fila.getObject("categoria_id", UUID.class), fila.getString("categoria_nombre")),
                new ReferenciaDto(fila.getObject("club_id", UUID.class), fila.getString("club_nombre")),
                fila.getInt("dia_semana"),
                fila.getObject("hora_inicio", LocalTime.class),
                fila.getObject("hora_fin", LocalTime.class),
                fila.getBoolean("es_comodato"));
    }
}

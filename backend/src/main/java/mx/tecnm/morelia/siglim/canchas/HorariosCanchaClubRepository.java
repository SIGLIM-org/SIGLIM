package mx.tecnm.morelia.siglim.canchas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL de {@code horarios_cancha_club}. */
@Repository
class HorariosCanchaClubRepository {

    private final JdbcClient jdbc;

    HorariosCanchaClubRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<HorariosCanchaClubDto> listarPorCancha(UUID canchaId) {
        return jdbc.sql("""
                        SELECT id, cancha_id, club_id, temporada_id, categoria_id, dia_semana,
                               hora_inicio, hora_fin, es_comodato
                          FROM horarios_cancha_club
                         WHERE cancha_id = :canchaId
                         ORDER BY categoria_id, temporada_id, dia_semana, hora_inicio, id
                        """)
                .param("canchaId", canchaId)
                .query(HorariosCanchaClubRepository::aDto)
                .list();
    }

    boolean existeCategoriaEnTemporada(UUID ligaId, UUID temporadaId, UUID categoriaId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1
                              FROM categorias_temporada ct
                              JOIN temporadas t ON t.id = ct.temporada_id
                              JOIN categorias c ON c.id = ct.categoria_id
                             WHERE t.liga_id = :ligaId
                               AND ct.temporada_id = :temporadaId
                               AND ct.categoria_id = :categoriaId
                               AND c.liga_id = :ligaId)
                        """)
                .param("categoriaId", categoriaId)
                .param("temporadaId", temporadaId)
                .param("ligaId", ligaId)
                .query(Boolean.class)
                .single();
    }

    boolean existeClubDeLiga(UUID ligaId, UUID clubId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM clubes WHERE id = :id AND liga_id = :ligaId)")
                .param("id", clubId)
                .param("ligaId", ligaId)
                .query(Boolean.class)
                .single();
    }

    void eliminarPorCancha(UUID canchaId) {
        jdbc.sql("DELETE FROM horarios_cancha_club WHERE cancha_id = :canchaId")
                .param("canchaId", canchaId)
                .update();
    }

    UUID insertar(UUID canchaId, UUID clubId, UUID temporadaId, UUID categoriaId,
            int diaSemana, LocalTime horaInicio, LocalTime horaFin, boolean esComodato) {
        return jdbc.sql("""
                        INSERT INTO horarios_cancha_club
                            (cancha_id, club_id, temporada_id, categoria_id, dia_semana,
                             hora_inicio, hora_fin, es_comodato)
                        VALUES (:canchaId, :clubId, :temporadaId, :categoriaId, :diaSemana,
                                :horaInicio, :horaFin, :esComodato)
                        RETURNING id
                        """)
                .param("canchaId", canchaId)
                .param("clubId", clubId)
                .param("temporadaId", temporadaId)
                .param("categoriaId", categoriaId)
                .param("diaSemana", diaSemana)
                .param("horaInicio", horaInicio)
                .param("horaFin", horaFin)
                .param("esComodato", esComodato)
                .query(UUID.class)
                .single();
    }

    private static HorariosCanchaClubDto aDto(ResultSet fila, int i) throws SQLException {
        return new HorariosCanchaClubDto(
                fila.getObject("id", UUID.class),
                fila.getObject("cancha_id", UUID.class),
                fila.getObject("club_id", UUID.class),
                fila.getObject("temporada_id", UUID.class),
                fila.getObject("categoria_id", UUID.class),
                fila.getInt("dia_semana"),
                fila.getObject("hora_inicio", LocalTime.class),
                fila.getObject("hora_fin", LocalTime.class),
                fila.getBoolean("es_comodato"));
    }
}

package mx.tecnm.morelia.siglim.canchas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** SQL de {@code horarios_cancha_club}. */

public class HorariosCanchaClubRepository {

    private final JdbcClient jdbc;

    HorariosCanchaClubRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<HorariosCanchaClubDto> buscar(UUID ligaId, UUID canchaId, UUID id) {
        return jdbc.sql("SELECT * FROM horarios_cancha_club WHERE cancha_id = :canchaId AND id = :id")
                .param("canchaId", canchaId)
                .param("id", id)
                .query(HorariosCanchaClubRepository::aDto)
                .optional();
    }

    List<HorariosCanchaClubDto> listarPorCancha(UUID canchaId) {
        return jdbc.sql("SELECT * FROM horarios_cancha_club WHERE cancha_id = :canchaId ORDER BY id")
                .param("canchaId", canchaId)
                .query(HorariosCanchaClubRepository::aDto)
                .list();
    }

    UUID insertar(UUID canchaId, UUID clubId, UUID temporadaId, UUID categoriaId,
            int diaSemana, String horaInicio, String horaFin, boolean esComodato) {
        return jdbc.sql("INSERT INTO horarios_cancha_club (cancha_id, club_id, temporada_id, categoria_id, dia_semana, hora_inicio, hora_fin, es_comodato) VALUES (:canchaId, :clubId, :temporadaId, :categoriaId, :diaSemana, :horaInicio, :horaFin, :esComodato) RETURNING id")
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
                fila.getString("hora_inicio"),
                fila.getString("hora_fin"),
                fila.getBoolean("es_comodato"));
    }
}

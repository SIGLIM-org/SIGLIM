package mx.tecnm.morelia.siglim.organizacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL de la vigencia de cargos de directiva (RF-102). */
@Repository
class CargoDirectivaRepository {

    private static final String SELECT = """
            SELECT cd.id, cd.club_id, cd.persona_id,
                   concat_ws(' ', p.nombre, p.apellido_paterno, p.apellido_materno) AS nombre_persona,
                   cd.cargo, cd.desde, cd.hasta, cd.autorizado_por,
                   CASE WHEN cd.autorizado_por IS NULL THEN 'PENDIENTE' ELSE 'AUTORIZADO' END AS estado
              FROM cargos_directiva cd
              JOIN clubes cl ON cl.id = cd.club_id
              JOIN personas p ON p.id = cd.persona_id
            """;

    private final JdbcClient jdbc;

    CargoDirectivaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean bloquearClub(UUID ligaId, UUID clubId) {
        return jdbc.sql("SELECT id FROM clubes WHERE liga_id = :ligaId AND id = :clubId FOR UPDATE")
                .param("ligaId", ligaId)
                .param("clubId", clubId)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    boolean existeClub(UUID ligaId, UUID clubId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM clubes WHERE liga_id = :ligaId AND id = :clubId)")
                .param("ligaId", ligaId)
                .param("clubId", clubId)
                .query(Boolean.class)
                .single();
    }

    List<CargoDirectivaDto> listar(UUID ligaId, UUID clubId) {
        return jdbc.sql(SELECT + """
                        WHERE cl.liga_id = :ligaId AND cd.club_id = :clubId
                        ORDER BY cd.cargo, (cd.autorizado_por IS NULL) DESC, cd.desde DESC, cd.id
                        """)
                .param("ligaId", ligaId)
                .param("clubId", clubId)
                .query(this::mapear)
                .list();
    }

    UUID insertar(UUID clubId, RegistrarCargoDirectivaRequest peticion) {
        return jdbc.sql("""
                        INSERT INTO cargos_directiva (club_id, persona_id, cargo, desde)
                        VALUES (:clubId, :personaId, CAST(:cargo AS cargo_directiva), :desde)
                        RETURNING id
                        """)
                .param("clubId", clubId)
                .param("personaId", peticion.personaId())
                .param("cargo", peticion.cargo().name())
                .param("desde", peticion.desde())
                .query(UUID.class)
                .single();
    }

    Optional<CargoDirectivaDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECT + " WHERE cl.liga_id = :ligaId AND cd.id = :id")
                .param("ligaId", ligaId)
                .param("id", id)
                .query(this::mapear)
                .optional();
    }

    Optional<CargoDirectivaDto> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECT + """
                        WHERE cl.liga_id = :ligaId AND cd.id = :id
                        FOR UPDATE OF cd
                        """)
                .param("ligaId", ligaId)
                .param("id", id)
                .query(this::mapear)
                .optional();
    }

    Optional<VigenciaCargo> buscarVigente(UUID clubId, CargoDirectiva cargo) {
        return jdbc.sql("""
                        SELECT id, desde FROM cargos_directiva
                         WHERE club_id = :clubId AND cargo = CAST(:cargo AS cargo_directiva)
                           AND autorizado_por IS NOT NULL AND hasta IS NULL
                         FOR UPDATE
                        """)
                .param("clubId", clubId)
                .param("cargo", cargo.name())
                .query((fila, numero) -> new VigenciaCargo(fila.getObject("id", UUID.class),
                        fila.getObject("desde", LocalDate.class)))
                .optional();
    }

    void terminar(UUID id, LocalDate hasta) {
        jdbc.sql("UPDATE cargos_directiva SET hasta = :hasta WHERE id = :id")
                .param("hasta", hasta)
                .param("id", id)
                .update();
    }

    void autorizar(UUID id, UUID perfilId) {
        jdbc.sql("UPDATE cargos_directiva SET autorizado_por = :perfilId WHERE id = :id")
                .param("perfilId", perfilId)
                .param("id", id)
                .update();
    }

    private CargoDirectivaDto mapear(ResultSet fila, int numero) throws SQLException {
        UUID autorizadoPor = fila.getObject("autorizado_por", UUID.class);
        return new CargoDirectivaDto(
                fila.getObject("id", UUID.class),
                fila.getObject("club_id", UUID.class),
                fila.getObject("persona_id", UUID.class),
                fila.getString("nombre_persona"),
                CargoDirectiva.valueOf(fila.getString("cargo")),
                fila.getObject("desde", LocalDate.class),
                fila.getObject("hasta", LocalDate.class),
                EstadoCargoDirectiva.valueOf(fila.getString("estado")),
                autorizadoPor);
    }

    record VigenciaCargo(UUID id, LocalDate desde) {
    }
}

package mx.tecnm.morelia.siglim.organizacion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code personas}: la identidad es única en todo el sistema por CURP, no por liga (RF-010,
 * DEC-10). La tabla no tiene {@code liga_id}, así que la consulta no lo filtra.
 *
 * <p>Refs: H-22.2, RF-010, RF-011
 */
@Repository
class PersonaRepository {

    private final JdbcClient jdbc;

    PersonaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<PersonaDto> buscarPorCurp(String curp) {
        return jdbc.sql("""
                        SELECT id, curp, nombre, apellido_paterno, apellido_materno
                          FROM personas
                         WHERE curp = :curp
                        """)
                .param("curp", curp)
                .query((fila, numero) -> new PersonaDto(
                        fila.getObject("id", UUID.class),
                        fila.getString("curp"),
                        fila.getString("nombre"),
                        fila.getString("apellido_paterno"),
                        fila.getString("apellido_materno")))
                .optional();
    }
}

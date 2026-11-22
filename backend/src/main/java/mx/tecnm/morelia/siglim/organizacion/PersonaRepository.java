package mx.tecnm.morelia.siglim.organizacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code personas} y {@code tutelas}: la identidad es única en todo el sistema por CURP, no
 * por liga (RF-010, DEC-10). La tabla no tiene {@code liga_id}, así que las consultas no lo filtran.
 *
 * <p>RLS solo muestra a un ámbito {@code CLUB} las personas de su club o las que creó. Para saber si
 * una CURP o una persona ya existen en otro club se usan las funciones {@code SECURITY DEFINER}
 * {@code persona_por_curp}, {@code es_menor} y {@code es_tutor_vigente}, que devuelven lo mínimo
 * (DEC-78). El detalle y la edición siguen sujetos a RLS.
 *
 * <p>Refs: H-22.2, H-09.1, RF-010, RF-011, RF-106, DEC-78
 */
@Repository
class PersonaRepository {

    private static final String COLUMNAS_DETALLE = """
            id, curp, nombre, apellido_paterno, apellido_materno, fecha_nacimiento, correo, telefono
            """;

    private final JdbcClient jdbc;

    PersonaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Identidad mínima por CURP, aunque RLS no muestre a la persona (DEC-78). */
    Optional<PersonaDto> buscarPorCurp(String curp) {
        return jdbc.sql("""
                        SELECT id, curp, nombre, apellido_paterno, apellido_materno
                          FROM siglim.persona_por_curp(:curp)
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

    /**
     * Inserta la persona; si la CURP ya existe (también por una carrera) no inserta y devuelve vacío,
     * sin abortar la transacción.
     */
    Optional<UUID> insertar(PersonaDetalleDto datos, UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO personas (curp, nombre, apellido_paterno, apellido_materno,
                                              fecha_nacimiento, correo, telefono, creado_por)
                        VALUES (:curp, :nombre, :apellidoPaterno, :apellidoMaterno,
                                :fechaNacimiento, :correo, :telefono, :creadoPor)
                        ON CONFLICT (curp) DO NOTHING
                        RETURNING id
                        """)
                .param("curp", datos.curp())
                .param("nombre", datos.nombre())
                .param("apellidoPaterno", datos.apellidoPaterno())
                .param("apellidoMaterno", datos.apellidoMaterno())
                .param("fechaNacimiento", datos.fechaNacimiento())
                .param("correo", datos.correo())
                .param("telefono", datos.telefono())
                .param("creadoPor", creadoPor)
                .query(UUID.class)
                .optional();
    }

    /** Detalle visible para el usuario según RLS; vacío si no existe o no la ve. */
    Optional<PersonaDetalleDto> buscarDetalle(UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS_DETALLE + " FROM personas WHERE id = :id")
                .param("id", id)
                .query((fila, numero) -> detalle(fila))
                .optional();
    }

    /** Igual que {@link #buscarDetalle(UUID)}, con la fila bloqueada para editarla. */
    Optional<PersonaDetalleDto> buscarDetalleParaActualizar(UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS_DETALLE + " FROM personas WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query((fila, numero) -> detalle(fila))
                .optional();
    }

    void actualizar(PersonaDetalleDto datos) {
        jdbc.sql("""
                        UPDATE personas
                           SET curp = :curp, nombre = :nombre, apellido_paterno = :apellidoPaterno,
                               apellido_materno = :apellidoMaterno, fecha_nacimiento = :fechaNacimiento,
                               correo = :correo, telefono = :telefono, actualizado_en = now()
                         WHERE id = :id
                        """)
                .param("id", datos.id())
                .param("curp", datos.curp())
                .param("nombre", datos.nombre())
                .param("apellidoPaterno", datos.apellidoPaterno())
                .param("apellidoMaterno", datos.apellidoMaterno())
                .param("fechaNacimiento", datos.fechaNacimiento())
                .param("correo", datos.correo())
                .param("telefono", datos.telefono())
                .update();
    }

    /**
     * Si la persona es menor en esa fecha; vacío si no existe, aunque RLS no la muestre (RN-055,
     * DEC-78).
     */
    Optional<Boolean> esMenor(UUID personaId, LocalDate fecha) {
        return jdbc.sql("SELECT siglim.es_menor(:persona, :fecha) AS menor")
                .param("persona", personaId)
                .param("fecha", fecha)
                .query((fila, numero) -> {
                    boolean menor = fila.getBoolean("menor");
                    return fila.wasNull() ? Optional.<Boolean>empty() : Optional.of(menor);
                })
                .single();
    }

    /** Persona asociada a la cuenta de la sesión; vacío si la cuenta no tiene persona. */
    Optional<UUID> personaDelUsuario() {
        return jdbc.sql("SELECT siglim.persona_usuario() AS persona")
                .query((fila, numero) -> Optional.ofNullable(fila.getObject("persona", UUID.class)))
                .single();
    }

    boolean esTutorVigente(UUID menorId, UUID tutorId) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT siglim.es_tutor_vigente(:menor, :tutor)")
                .param("menor", menorId)
                .param("tutor", tutorId)
                .query(Boolean.class)
                .single());
    }

    /**
     * Inserta la tutela vigente; si ya hay una igual (también por una carrera, {@code ux_tutelas_vigente})
     * no inserta y devuelve vacío.
     */
    Optional<TutelaDto> insertarTutela(UUID menorId, UUID tutorId, String parentesco, UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO tutelas (menor_id, tutor_id, parentesco, creado_por)
                        VALUES (:menor, :tutor, :parentesco, :creadoPor)
                        ON CONFLICT (menor_id, tutor_id) WHERE vigente DO NOTHING
                        RETURNING id, menor_id, tutor_id, parentesco, vigente, creado_en
                        """)
                .param("menor", menorId)
                .param("tutor", tutorId)
                .param("parentesco", parentesco)
                .param("creadoPor", creadoPor)
                .query((fila, numero) -> new TutelaDto(
                        fila.getObject("id", UUID.class),
                        fila.getObject("menor_id", UUID.class),
                        fila.getObject("tutor_id", UUID.class),
                        fila.getString("parentesco"),
                        fila.getBoolean("vigente"),
                        fila.getObject("creado_en", OffsetDateTime.class)))
                .optional();
    }

    private static PersonaDetalleDto detalle(ResultSet fila) throws SQLException {
        return new PersonaDetalleDto(
                fila.getObject("id", UUID.class),
                fila.getString("curp"),
                fila.getString("nombre"),
                fila.getString("apellido_paterno"),
                fila.getString("apellido_materno"),
                fila.getObject("fecha_nacimiento", LocalDate.class),
                fila.getString("correo"),
                fila.getString("telefono"));
    }
}

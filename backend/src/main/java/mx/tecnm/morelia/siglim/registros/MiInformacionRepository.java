package mx.tecnm.morelia.siglim.registros;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.registros.TitularesDto.TitularDto;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import mx.tecnm.morelia.siglim.temporadas.TipoCategoria;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de la consulta propia (H-24.1, RF-144): la persona de la cuenta, los menores bajo su tutela y los
 * registros de una persona. Los documentos se leen con {@link DocumentoRegistroRepository}; los partidos
 * y las sanciones, con las interfaces que implementan {@code partidos} y {@code disciplina} (ADR-0006).
 *
 * <p>Todo corre dentro de la transacción del servicio, sujeto a RLS: el titular ve a su persona, sus
 * tutelas y sus registros por {@code es_titular} ({@code personas_leer}, {@code tutelas_leer},
 * {@code registros_leer}). {@code persona_usuario} y {@code es_titular} son {@code SECURITY DEFINER}.
 *
 * <p>Refs: H-24.1, RF-144, RNF-012, DEC-135 a DEC-137
 */
@Repository
class MiInformacionRepository {

    private static final String NOMBRE_COMPLETO = "concat_ws(' ', p.nombre, p.apellido_paterno, p.apellido_materno)";

    private final JdbcClient jdbc;

    MiInformacionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Persona vinculada a la cuenta de la sesión ({@code persona_usuario()}); vacío si no tiene. */
    Optional<UUID> personaDelUsuario() {
        return jdbc.sql("SELECT siglim.persona_usuario() AS persona")
                .query((fila, numero) -> Optional.ofNullable(fila.getObject("persona", UUID.class)))
                .single();
    }

    /**
     * Si la persona es la de la cuenta o un menor bajo su tutela vigente ({@code es_titular}, RF-144).
     * Sin persona vinculada a la cuenta, {@code es_titular} devuelve {@code NULL}: cuenta como {@code false}.
     */
    boolean esTitular(UUID personaId) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT coalesce(siglim.es_titular(:persona), false)")
                .param("persona", personaId)
                .query(Boolean.class)
                .single());
    }

    /** La persona con su nombre completo, su CURP y si es menor en esa fecha; vacío si no existe o no se ve. */
    Optional<PersonaTitular> persona(UUID personaId, LocalDate hoy) {
        return jdbc.sql("SELECT p.id, " + NOMBRE_COMPLETO + " AS nombre_completo, p.curp, "
                        + "siglim.es_menor(p.id, :hoy) AS es_menor FROM personas p WHERE p.id = :persona")
                .param("persona", personaId)
                .param("hoy", hoy)
                .query((fila, numero) -> new PersonaTitular(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre_completo"),
                        fila.getString("curp"),
                        fila.getBoolean("es_menor")))
                .optional();
    }

    /** Menores con tutela vigente de ese tutor, por nombre completo (DEC-135). */
    List<TitularDto> menoresTutelados(UUID tutorId, LocalDate hoy) {
        return jdbc.sql("SELECT DISTINCT p.id, " + NOMBRE_COMPLETO + " AS nombre_completo, "
                        + """
                        siglim.es_menor(p.id, :hoy) AS es_menor
                          FROM tutelas t
                          JOIN personas p ON p.id = t.menor_id
                         WHERE t.tutor_id = :tutor AND t.vigente
                         ORDER BY nombre_completo, p.id
                        """)
                .param("tutor", tutorId)
                .param("hoy", hoy)
                .query((fila, numero) -> new TitularDto(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre_completo"),
                        false,
                        fila.getBoolean("es_menor")))
                .list();
    }

    /**
     * Registros de la persona en la liga de temporadas que no estén {@code CERRADA}, del más reciente al
     * más antiguo. {@code motivoBaja} se lee del último cambio a {@code BAJA} del historial, como en
     * {@link RegistroJugadorRepository} (DEC-124).
     */
    List<RegistroDePersona> registros(UUID ligaId, UUID personaId) {
        return jdbc.sql("""
                        SELECT r.id, r.estado, r.alta_en, r.baja_en, r.observaciones,
                               t.id AS temporada_id, t.nombre AS temporada_nombre, t.estado AS temporada_estado,
                               cl.id AS club_id, cl.nombre AS club_nombre,
                               e.id AS equipo_id, e.nombre AS equipo_nombre,
                               c.id AS categoria_id, c.nombre AS categoria_nombre, c.tipo AS categoria_tipo,
                               CASE WHEN r.estado = 'BAJA' THEN
                                    (SELECT h.motivo FROM registro_estado_historial h
                                      WHERE h.registro_id = r.id AND h.estado_nuevo = 'BAJA'
                                      ORDER BY h.cambiado_en DESC, h.id DESC LIMIT 1)
                               END AS motivo_baja
                          FROM registros_jugador r
                          JOIN equipos e ON e.id = r.equipo_id
                          JOIN clubes cl ON cl.id = e.club_id
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN categorias c ON c.id = ct.categoria_id
                          JOIN temporadas t ON t.id = r.temporada_id
                         WHERE r.persona_id = :persona AND cl.liga_id = :ligaId AND t.estado <> 'CERRADA'
                         ORDER BY r.creado_en DESC, r.id DESC
                        """)
                .param("persona", personaId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new RegistroDePersona(
                        fila.getObject("id", UUID.class),
                        EstadoRegistro.valueOf(fila.getString("estado")),
                        fila.getObject("alta_en", LocalDate.class),
                        fila.getObject("baja_en", LocalDate.class),
                        fila.getString("motivo_baja"),
                        fila.getString("observaciones"),
                        new TemporadaResumenDto(fila.getObject("temporada_id", UUID.class),
                                fila.getString("temporada_nombre"),
                                EstadoTemporada.valueOf(fila.getString("temporada_estado"))),
                        new ReferenciaDto(fila.getObject("club_id", UUID.class), fila.getString("club_nombre")),
                        new ReferenciaDto(fila.getObject("equipo_id", UUID.class), fila.getString("equipo_nombre")),
                        new ReferenciaDto(fila.getObject("categoria_id", UUID.class), fila.getString("categoria_nombre")),
                        TipoCategoria.valueOf(fila.getString("categoria_tipo"))))
                .list();
    }

    /** La persona consultada con su CURP completa, que el servicio enmascara antes de responder. */
    record PersonaTitular(UUID id, String nombreCompleto, String curp, boolean esMenor) {
    }

    /** Un registro de la persona con el tipo de su categoría, que decide los documentos exigidos. */
    record RegistroDePersona(UUID id, EstadoRegistro estado, LocalDate altaEn, LocalDate bajaEn, String motivoBaja,
                             String observaciones, TemporadaResumenDto temporada, ReferenciaDto club,
                             ReferenciaDto equipo, ReferenciaDto categoria, TipoCategoria categoriaTipo) {
    }
}

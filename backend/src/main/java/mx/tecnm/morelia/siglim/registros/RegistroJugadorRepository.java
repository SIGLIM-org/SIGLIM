package mx.tecnm.morelia.siglim.registros;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDetalleDto.PersonaDetalleDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDetalleDto.TutorDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.PersonaResumenDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorResumenDto.PersonaListadoDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code registros_jugador}. El equipo, su club, su categoría, su temporada y la afiliación
 * del club se leen aquí solo para validar el alta: su CRUD es de M-02 y M-03 y este repositorio no
 * lo reimplementa.
 *
 * <p>RLS solo muestra a un ámbito {@code CLUB} los registros y las personas de su club. Para saber
 * si la persona existe, si ya tiene registro en la temporada (en cualquier club) y si un menor
 * cumple sus requisitos se usan las funciones {@code SECURITY DEFINER} {@code es_menor},
 * {@code registros_en_temporada} y {@code requisitos_menor} (DEC-78, DEC-90). El {@code INSERT} sí
 * está sujeto a RLS ({@code registros_crear}).
 *
 * <p>La validación (H-10.2) cambia el estado con un {@code UPDATE} condicional ({@link #validar}),
 * también sujeto a RLS ({@code registros_editar}) y a {@code tg_registros_validar} (DEC-101).
 *
 * <p>La consulta (H-10.5) filtra por ámbito en el {@code WHERE} y en el {@code COUNT} con la misma
 * condición ({@link #listar}, {@link #contar}), además de RLS ({@code registros_leer},
 * {@code personas_leer}). La CURP se lee para enmascararla ({@link EnmascaradoCurp}); el servicio
 * decide si el detalle la lleva completa (DEC-110).
 *
 * <p>Refs: H-09.2, H-10.2, H-10.5, RF-013, RF-017, RF-107, RF-109, RN-037, VAL-017, VAL-025, VAL-026, DEC-88,
 * DEC-89, DEC-90, DEC-102, DEC-109, DEC-110
 */
@Repository
class RegistroJugadorRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    /** Columnas comunes del listado y del detalle (DEC-109); la CURP se enmascara en Java (DEC-110). */
    private static final String SELECCION_LISTADO = """
            SELECT r.id, r.estado, r.alta_en, r.creado_en,
                   siglim.es_menor(r.persona_id, r.alta_en) AS es_menor,
                   p.id AS persona_id, p.curp,
                   concat_ws(' ', p.nombre, p.apellido_paterno, p.apellido_materno) AS persona_nombre,
                   e.id AS equipo_id, e.nombre AS equipo_nombre,
                   cl.id AS club_id, cl.nombre AS club_nombre,
                   c.id AS categoria_id, c.nombre AS categoria_nombre,
                   t.id AS temporada_id, t.nombre AS temporada_nombre, t.estado AS temporada_estado
            """;

    private static final String DESDE_LISTADO = """
              FROM registros_jugador r
              JOIN personas p ON p.id = r.persona_id
              JOIN equipos e ON e.id = r.equipo_id
              JOIN clubes cl ON cl.id = e.club_id
              JOIN grupos g ON g.id = e.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN categorias c ON c.id = ct.categoria_id
              JOIN temporadas t ON t.id = r.temporada_id
            """;

    /**
     * Ámbito de {@code autorizacion.alcance} y filtros opcionales; la misma condición va en el listado
     * y en el {@code COUNT} (ADR-0007, DEC-109).
     */
    private static final String CONDICIONES_LISTADO = """
             WHERE cl.liga_id = :ligaId
               AND (:todaLaLiga OR e.club_id = ANY(:clubes) OR (:propio AND siglim.es_titular(r.persona_id)))
               AND (cardinality(:estados) = 0 OR CAST(r.estado AS text) = ANY(:estados))
               AND (CAST(:temporadaId AS uuid) IS NULL OR r.temporada_id = CAST(:temporadaId AS uuid))
               AND (CAST(:clubId AS uuid) IS NULL OR e.club_id = CAST(:clubId AS uuid))
               AND (CAST(:equipoId AS uuid) IS NULL OR r.equipo_id = CAST(:equipoId AS uuid))
            """;

    private final JdbcClient jdbc;

    RegistroJugadorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** El equipo con su club y su temporada, de la liga; vacío si no existe. */
    Optional<EquipoDelRegistro> buscarEquipo(UUID ligaId, UUID equipoId) {
        return jdbc.sql("""
                        SELECT e.id, e.nombre, e.estado = 'ACTIVO' AS activo,
                               cl.id AS club_id, cl.nombre AS club_nombre,
                               t.id AS temporada_id, t.nombre AS temporada_nombre, t.estado AS temporada_estado
                          FROM equipos e
                          JOIN clubes cl ON cl.id = e.club_id
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE e.id = :equipoId AND cl.liga_id = :ligaId
                        """)
                .param("equipoId", equipoId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new EquipoDelRegistro(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getBoolean("activo"),
                        fila.getObject("club_id", UUID.class),
                        fila.getString("club_nombre"),
                        fila.getObject("temporada_id", UUID.class),
                        fila.getString("temporada_nombre"),
                        EstadoTemporada.valueOf(fila.getString("temporada_estado"))))
                .optional();
    }

    /** Si el club tiene su tarjetón {@code APROBADA} en la temporada (RF-101, DEC-87). */
    boolean clubAfiliado(UUID clubId, UUID temporadaId) {
        return Boolean.TRUE.equals(jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM afiliaciones
                                        WHERE club_id = :clubId AND temporada_id = :temporadaId
                                          AND estado = 'APROBADA')
                        """)
                .param("clubId", clubId)
                .param("temporadaId", temporadaId)
                .query(Boolean.class)
                .single());
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

    /** Registros no rechazados de la persona en la temporada, de cualquier club (DEC-88). */
    List<RegistroEnTemporada> registrosEnTemporada(UUID personaId, UUID temporadaId) {
        return jdbc.sql("""
                        SELECT club_id, club_nombre, equipo_id, equipo_nombre, estado
                          FROM siglim.registros_en_temporada(:persona, :temporada)
                        """)
                .param("persona", personaId)
                .param("temporada", temporadaId)
                .query((fila, numero) -> new RegistroEnTemporada(
                        fila.getObject("club_id", UUID.class),
                        fila.getString("club_nombre"),
                        fila.getObject("equipo_id", UUID.class),
                        fila.getString("equipo_nombre"),
                        EstadoRegistro.valueOf(fila.getString("estado"))))
                .list();
    }

    /** Tutela vigente y consentimiento del tutor, con la condición del trigger al activar (DEC-89). */
    RequisitosMenor requisitosMenor(UUID personaId) {
        return jdbc.sql("""
                        SELECT tiene_tutor_vigente, tiene_consentimiento
                          FROM siglim.requisitos_menor(:persona)
                        """)
                .param("persona", personaId)
                .query((fila, numero) -> new RequisitosMenor(
                        fila.getBoolean("tiene_tutor_vigente"),
                        fila.getBoolean("tiene_consentimiento")))
                .single();
    }

    /**
     * Inserta el registro {@code PENDIENTE_VALIDACION} (lo pone el {@code DEFAULT}). Si una carrera ya
     * dejó otro registro activo de la persona en la temporada ({@code ux_registros_activo}), no
     * inserta y devuelve vacío sin abortar la transacción (DEC-90). El historial lo escribe
     * {@code tg_registros_historial}.
     */
    Optional<UUID> insertar(UUID personaId, UUID equipoId, UUID temporadaId, LocalDate altaEn, UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO registros_jugador (persona_id, equipo_id, temporada_id, alta_en, creado_por)
                        VALUES (:persona, :equipo, :temporada, :altaEn, :creadoPor)
                        ON CONFLICT (persona_id, temporada_id)
                           WHERE estado IN ('PENDIENTE_VALIDACION', 'ACTIVO', 'SUSPENDIDO')
                        DO NOTHING
                        RETURNING id
                        """)
                .param("persona", personaId)
                .param("equipo", equipoId)
                .param("temporada", temporadaId)
                .param("altaEn", altaEn)
                .param("creadoPor", creadoPor)
                .query(UUID.class)
                .optional();
    }

    /**
     * Guarda el dictamen de la liga si el registro sigue {@code PENDIENTE_VALIDACION}; {@code false} si
     * otra petición lo validó antes (DEC-102). El motivo se fija antes con {@code MotivoOperacion} y el
     * historial lo escribe {@code tg_registros_historial}.
     */
    boolean validar(UUID id, EstadoRegistro estado, UUID validadoPor, OffsetDateTime validadoEn,
            String observaciones) {
        return jdbc.sql("""
                        UPDATE registros_jugador
                           SET estado = CAST(:estado AS siglim.estado_registro), validado_por = :validadoPor,
                               validado_en = :validadoEn, observaciones = :observaciones
                         WHERE id = :id AND estado = 'PENDIENTE_VALIDACION'
                        RETURNING id
                        """)
                .param("id", id)
                .param("estado", estado.name())
                .param("validadoPor", validadoPor)
                .param("validadoEn", validadoEn)
                .param("observaciones", observaciones)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    /** El registro con sus referencias, visible por RLS; vacío si no existe o no se ve. */
    Optional<RegistroJugadorDto> buscar(UUID id) {
        return jdbc.sql("""
                        SELECT r.id, r.estado, r.alta_en, r.creado_en,
                               r.validado_por, pf.nombre_mostrado AS validado_por_nombre, r.validado_en,
                               r.observaciones,
                               p.id AS persona_id,
                               concat_ws(' ', p.nombre, p.apellido_paterno, p.apellido_materno) AS persona_nombre,
                               e.id AS equipo_id, e.nombre AS equipo_nombre,
                               cl.id AS club_id, cl.nombre AS club_nombre,
                               c.id AS categoria_id, c.nombre AS categoria_nombre,
                               t.id AS temporada_id, t.nombre AS temporada_nombre, t.estado AS temporada_estado
                          FROM registros_jugador r
                          JOIN personas p ON p.id = r.persona_id
                          JOIN equipos e ON e.id = r.equipo_id
                          JOIN clubes cl ON cl.id = e.club_id
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN categorias c ON c.id = ct.categoria_id
                          JOIN temporadas t ON t.id = r.temporada_id
                          LEFT JOIN perfiles pf ON pf.id = r.validado_por
                         WHERE r.id = :id
                        """)
                .param("id", id)
                .query((fila, numero) -> {
                    UUID validadoPor = fila.getObject("validado_por", UUID.class);
                    return new RegistroJugadorDto(
                        fila.getObject("id", UUID.class),
                        new PersonaResumenDto(fila.getObject("persona_id", UUID.class), fila.getString("persona_nombre")),
                        new ReferenciaDto(fila.getObject("equipo_id", UUID.class), fila.getString("equipo_nombre")),
                        new ReferenciaDto(fila.getObject("club_id", UUID.class), fila.getString("club_nombre")),
                        new ReferenciaDto(fila.getObject("categoria_id", UUID.class), fila.getString("categoria_nombre")),
                        new TemporadaResumenDto(fila.getObject("temporada_id", UUID.class),
                                fila.getString("temporada_nombre"),
                                EstadoTemporada.valueOf(fila.getString("temporada_estado"))),
                        EstadoRegistro.valueOf(fila.getString("estado")),
                        fila.getObject("alta_en", LocalDate.class),
                        enZonaDeNegocio(fila.getObject("creado_en", OffsetDateTime.class)),
                        validadoPor == null ? null : new ReferenciaDto(validadoPor, fila.getString("validado_por_nombre")),
                        enZonaDeNegocio(fila.getObject("validado_en", OffsetDateTime.class)),
                        fila.getString("observaciones"));
                })
                .optional();
    }

    // ── Consulta (H-10.5, DEC-109) ──────────────────────────────────────────────────────────────

    /** Registros de la liga con su ámbito y sus filtros, en el orden pedido (DEC-109). */
    List<RegistroJugadorResumenDto> listar(UUID ligaId, FiltroRegistros filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql(SELECCION_LISTADO + DESDE_LISTADO + CONDICIONES_LISTADO
                        + " ORDER BY " + orden(filtro.ordenElegido()) + " LIMIT :limite OFFSET :desplazamiento"),
                ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query((fila, numero) -> new RegistroJugadorResumenDto(
                        fila.getObject("id", UUID.class),
                        new PersonaListadoDto(fila.getObject("persona_id", UUID.class), fila.getString("persona_nombre"),
                                EnmascaradoCurp.enmascarar(fila.getString("curp"))),
                        referencia(fila, "equipo"),
                        referencia(fila, "club"),
                        referencia(fila, "categoria"),
                        temporada(fila),
                        EstadoRegistro.valueOf(fila.getString("estado")),
                        fila.getBoolean("es_menor"),
                        fila.getObject("alta_en", LocalDate.class),
                        enZonaDeNegocio(fila.getObject("creado_en", OffsetDateTime.class))))
                .list();
    }

    /**
     * Con los mismos {@code JOIN} y la misma condición del listado: {@code totalElementos} no delata
     * filas fuera de ámbito (ADR-0007).
     */
    long contar(UUID ligaId, FiltroRegistros filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("SELECT count(*)" + DESDE_LISTADO + CONDICIONES_LISTADO), ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    /**
     * El detalle del registro con la CURP completa de la persona y sin tutores; el servicio decide si
     * la CURP se muestra y agrega los tutores (DEC-110). Vacío si no existe o RLS no lo muestra.
     */
    Optional<RegistroJugadorDetalleDto> buscarDetalle(UUID id) {
        return jdbc.sql(SELECCION_LISTADO + """
                        , r.validado_por, pf.nombre_mostrado AS validado_por_nombre, r.validado_en, r.observaciones
                        """ + DESDE_LISTADO + """
                         LEFT JOIN perfiles pf ON pf.id = r.validado_por
                         WHERE r.id = :id
                        """)
                .param("id", id)
                .query((fila, numero) -> {
                    UUID validadoPor = fila.getObject("validado_por", UUID.class);
                    String curp = fila.getString("curp");
                    return new RegistroJugadorDetalleDto(
                            fila.getObject("id", UUID.class),
                            new PersonaDetalleDto(fila.getObject("persona_id", UUID.class),
                                    fila.getString("persona_nombre"), EnmascaradoCurp.enmascarar(curp),
                                    curp == null ? null : curp.strip()),
                            referencia(fila, "equipo"),
                            referencia(fila, "club"),
                            referencia(fila, "categoria"),
                            temporada(fila),
                            EstadoRegistro.valueOf(fila.getString("estado")),
                            fila.getBoolean("es_menor"),
                            fila.getObject("alta_en", LocalDate.class),
                            enZonaDeNegocio(fila.getObject("creado_en", OffsetDateTime.class)),
                            validadoPor == null ? null
                                    : new ReferenciaDto(validadoPor, fila.getString("validado_por_nombre")),
                            enZonaDeNegocio(fila.getObject("validado_en", OffsetDateTime.class)),
                            fila.getString("observaciones"),
                            List.of());
                })
                .optional();
    }

    /**
     * Tutelas vigentes del menor por fecha de creación, con el nombre del tutor y el parentesco; sin
     * CURP ni contacto (RNF-012). Sujeto a RLS: un tutor que la sesión no puede ver no aparece.
     */
    List<TutorDto> tutoresVigentes(UUID menorId) {
        return jdbc.sql("""
                        SELECT concat_ws(' ', p.nombre, p.apellido_paterno, p.apellido_materno) AS nombre_completo,
                               t.parentesco
                          FROM tutelas t
                          JOIN personas p ON p.id = t.tutor_id
                         WHERE t.menor_id = :menor AND t.vigente
                         ORDER BY t.creado_en, t.id
                        """)
                .param("menor", menorId)
                .query((fila, numero) -> new TutorDto(fila.getString("nombre_completo"), fila.getString("parentesco")))
                .list();
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, UUID ligaId,
            FiltroRegistros filtro, AlcanceAutorizacion alcance) {
        return sentencia
                .param("ligaId", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("propio", alcance.propio())
                .param("estados", new SqlArrayValue("text", filtro.estados().stream().map(Enum::name).toArray()))
                .param("temporadaId", filtro.temporadaId())
                .param("clubId", filtro.clubId())
                .param("equipoId", filtro.equipoId());
    }

    /** Lista blanca de {@code ORDER BY}: nunca se concatena texto del usuario (§4). */
    private static String orden(FiltroRegistros.Orden orden) {
        return switch (orden) {
            case CREADO_EN_ASC -> "r.creado_en ASC, r.id";
            case CREADO_EN_DESC -> "r.creado_en DESC, r.id";
            case NOMBRE_ASC -> "p.apellido_paterno, p.apellido_materno, p.nombre, r.id";
        };
    }

    private static ReferenciaDto referencia(ResultSet fila, String prefijo) throws SQLException {
        return new ReferenciaDto(fila.getObject(prefijo + "_id", UUID.class), fila.getString(prefijo + "_nombre"));
    }

    private static TemporadaResumenDto temporada(ResultSet fila) throws SQLException {
        return new TemporadaResumenDto(fila.getObject("temporada_id", UUID.class), fila.getString("temporada_nombre"),
                EstadoTemporada.valueOf(fila.getString("temporada_estado")));
    }

    private static OffsetDateTime enZonaDeNegocio(OffsetDateTime momento) {
        return momento == null ? null : momento.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    /** El equipo del alta con lo necesario para validar RF-003, RF-013 y RF-101. */
    record EquipoDelRegistro(UUID id, String nombre, boolean activo, UUID clubId, String clubNombre,
                             UUID temporadaId, String temporadaNombre, EstadoTemporada temporadaEstado) {
    }

    /** Registro no rechazado de la persona en la temporada, de cualquier club (DEC-88). */
    record RegistroEnTemporada(UUID clubId, String clubNombre, UUID equipoId, String equipoNombre,
                               EstadoRegistro estado) {
    }

    /** Requisitos de un menor para que su registro coincida con la activación (VAL-025, DEC-89). */
    record RequisitosMenor(boolean tieneTutorVigente, boolean tieneConsentimiento) {
    }
}

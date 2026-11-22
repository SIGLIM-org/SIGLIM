package mx.tecnm.morelia.siglim.organizacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.PerfilDto;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code operaciones_equipo} (RF-103, RF-104, RN-040).
 *
 * <ul>
 *   <li><b>Lectura.</b> El equipo, los dos clubes y la temporada se leen con {@code JOIN}, y los
 *       perfiles con {@code LEFT JOIN}: su nombre solo está presente si las políticas RLS de
 *       {@code perfiles} lo permiten (la Directiva no ve el perfil del Presidente). Las fechas se
 *       devuelven en la zona de negocio.</li>
 *   <li><b>Historial de propietarios (RN-040).</b> {@code club_origen_id} no se reescribe nunca: la
 *       fila queda como historial cuando el cierre de la temporada mueva el equipo.</li>
 *   <li><b>Candados.</b> La resolución lee la operación con {@code FOR UPDATE}; el alta lee el
 *       equipo con {@code FOR NO KEY UPDATE} para serializar dos solicitudes del mismo equipo
 *       (DEC-132), y el club destino con {@code FOR SHARE} en {@link ClubRepository} para no
 *       recibir un club que se está cerrando por cambio de nombre (B-04).</li>
 *   <li><b>Observaciones.</b> El dictamen solo reemplaza {@code observaciones} si trae texto:
 *       autorizar sin nota conserva las de la solicitud.</li>
 * </ul>
 *
 * <p>Refs: B-05.1, RF-103, RF-104, RN-040, ADR-0007, ADR-0011
 */
@Repository
class OperacionEquipoRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT o.id, o.tipo, o.estado, o.solicitada_en, o.autorizada_en, o.aplicada_en,
                   o.observaciones,
                   e.id AS equipo_id, e.nombre AS equipo_nombre,
                   co.id AS origen_id, co.nombre AS origen_nombre,
                   cd.id AS destino_id, cd.nombre AS destino_nombre,
                   t.id AS temporada_id, t.nombre AS temporada_nombre, t.estado AS temporada_estado,
                   o.solicitada_por AS solicitada_por_id, sp.nombre_mostrado AS solicitada_por_nombre,
                   o.autorizada_por AS autorizada_por_id, ap.nombre_mostrado AS autorizada_por_nombre
              FROM operaciones_equipo o
              JOIN equipos e ON e.id = o.equipo_id
              JOIN clubes co ON co.id = o.club_origen_id
              LEFT JOIN clubes cd ON cd.id = o.club_destino_id
              JOIN grupos g ON g.id = e.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN temporadas t ON t.id = ct.temporada_id
              LEFT JOIN perfiles sp ON sp.id = o.solicitada_por
              LEFT JOIN perfiles ap ON ap.id = o.autorizada_por
            """;

    private static final String CONDICIONES_LISTADO = """
             WHERE co.liga_id = :ligaId
               AND (:todaLaLiga OR o.club_origen_id = ANY(:clubes))
               AND (CAST(:temporadaId AS uuid) IS NULL
                    OR ct.temporada_id = CAST(:temporadaId AS uuid))
               AND (CAST(:estado AS text) IS NULL OR CAST(o.estado AS text) = CAST(:estado AS text))
            """;

    private final JdbcClient jdbc;

    OperacionEquipoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Equipo de la liga con su club y la temporada de su grupo, para validar la solicitud. Toma el
     * candado del equipo ({@code FOR NO KEY UPDATE}): dos solicitudes simultáneas del mismo equipo
     * se serializan y la segunda ve la primera al revisar duplicados (DEC-132).
     */
    Optional<EquipoOperable> buscarEquipo(UUID ligaId, UUID equipoId) {
        return jdbc.sql("""
                        SELECT e.id, e.nombre, e.estado, e.club_id, cl.nombre AS club_nombre,
                               t.id AS temporada_id, t.nombre AS temporada_nombre,
                               t.estado AS temporada_estado
                          FROM equipos e
                          JOIN clubes cl ON cl.id = e.club_id
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE e.id = :equipoId AND cl.liga_id = :ligaId
                           FOR NO KEY UPDATE OF e
                        """)
                .param("equipoId", equipoId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new EquipoOperable(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getObject("club_id", UUID.class),
                        fila.getString("club_nombre"),
                        EstadoEquipo.valueOf(fila.getString("estado")),
                        fila.getObject("temporada_id", UUID.class),
                        fila.getString("temporada_nombre"),
                        EstadoTemporada.valueOf(fila.getString("temporada_estado"))))
                .optional();
    }

    Optional<OperacionEquipoDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE o.id = :id AND co.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(OperacionEquipoRepository::aDto)
                .optional();
    }

    /** Lee la operación y toma su candado: serializa dos resoluciones simultáneas. */
    Optional<OperacionEquipoDto> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE o.id = :id AND co.liga_id = :ligaId FOR UPDATE OF o")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(OperacionEquipoRepository::aDto)
                .optional();
    }

    /**
     * Bandeja de operaciones visibles para el alcance del permiso, de la solicitud más reciente a
     * la más antigua (B-05.3, RF-103, RF-104, ADR-0007).
     */
    List<OperacionEquipoDto> listar(UUID ligaId, FiltroOperacionesEquipo filtro,
            AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql(SELECCION + CONDICIONES_LISTADO
                        + " ORDER BY o.solicitada_en DESC, o.id LIMIT :limite OFFSET :desplazamiento"),
                ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query(OperacionEquipoRepository::aDto)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, FiltroOperacionesEquipo filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("""
                        SELECT count(*)
                          FROM operaciones_equipo o
                          JOIN clubes co ON co.id = o.club_origen_id
                          JOIN equipos e ON e.id = o.equipo_id
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                        """ + CONDICIONES_LISTADO), ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    /**
     * Estado de la operación vigente ({@code SOLICITADA} o {@code AUTORIZADA}) del mismo tipo para
     * el equipo, si la hay (DEC-132). Las {@code RECHAZADA} y {@code APLICADA} no cuentan.
     */
    Optional<EstadoOperacionEquipo> estadoVigente(UUID equipoId, TipoOperacionEquipo tipo) {
        List<String> estados = jdbc.sql("""
                        SELECT estado
                          FROM operaciones_equipo
                         WHERE equipo_id = :equipoId
                           AND tipo = CAST(:tipo AS tipo_operacion_equipo)
                           AND estado IN ('SOLICITADA', 'AUTORIZADA')
                         ORDER BY solicitada_en
                         LIMIT 1
                        """)
                .param("equipoId", equipoId)
                .param("tipo", tipo.name())
                .query(String.class)
                .list();
        return estados.stream().findFirst().map(EstadoOperacionEquipo::valueOf);
    }

    /** Alta en {@code SOLICITADA}; el estado inicial lo pone el predeterminado de la tabla. */
    UUID insertar(TipoOperacionEquipo tipo, UUID equipoId, UUID clubOrigenId, UUID clubDestinoId,
            UUID solicitadaPor, String observaciones) {
        return jdbc.sql("""
                        INSERT INTO operaciones_equipo
                               (tipo, equipo_id, club_origen_id, club_destino_id, solicitada_por, observaciones)
                        VALUES (CAST(:tipo AS tipo_operacion_equipo), :equipoId, :clubOrigenId,
                                CAST(:clubDestinoId AS uuid), :solicitadaPor,
                                CAST(:observaciones AS text))
                        RETURNING id
                        """)
                .param("tipo", tipo.name())
                .param("equipoId", equipoId)
                .param("clubOrigenId", clubOrigenId)
                .param("clubDestinoId", clubDestinoId)
                .param("solicitadaPor", solicitadaPor)
                .param("observaciones", observaciones)
                .query(UUID.class)
                .single();
    }

    /**
     * Aplica el dictamen: estado final, perfil y fecha. Las observaciones solo se reemplazan si el
     * dictamen trae texto; si no, se conservan las de la solicitud.
     */
    void resolver(UUID id, EstadoOperacionEquipo estado, UUID autorizadaPor,
            OffsetDateTime autorizadaEn, String observaciones) {
        jdbc.sql("""
                        UPDATE operaciones_equipo
                           SET estado = CAST(:estado AS estado_operacion_equipo),
                               autorizada_por = CAST(:autorizadaPor AS uuid),
                               autorizada_en = CAST(:autorizadaEn AS timestamptz),
                               observaciones = COALESCE(CAST(:observaciones AS text), observaciones)
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("estado", estado.name())
                .param("autorizadaPor", autorizadaPor)
                .param("autorizadaEn", autorizadaEn)
                .param("observaciones", observaciones)
                .update();
    }

    /**
     * Operaciones {@code AUTORIZADA} de la temporada, bloqueadas para aplicarlas al cierre
     * (B-05.2, RN-040). El candado serializa el cierre con cualquier otra aplicación de la misma
     * temporada.
     */
    List<OperacionAplicable> listarAutorizadas(UUID temporadaId) {
        return jdbc.sql("""
                        SELECT o.id, o.tipo, o.equipo_id, o.club_origen_id, o.club_destino_id
                          FROM operaciones_equipo o
                          JOIN equipos e ON e.id = o.equipo_id
                          JOIN grupos g ON g.id = e.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                         WHERE ct.temporada_id = :temporadaId
                           AND o.estado = 'AUTORIZADA'
                         ORDER BY o.autorizada_en, o.id
                           FOR UPDATE OF o
                        """)
                .param("temporadaId", temporadaId)
                .query((fila, numero) -> new OperacionAplicable(
                        fila.getObject("id", UUID.class),
                        TipoOperacionEquipo.valueOf(fila.getString("tipo")),
                        fila.getObject("equipo_id", UUID.class),
                        fila.getObject("club_origen_id", UUID.class),
                        fila.getObject("club_destino_id", UUID.class)))
                .list();
    }

    /**
     * Cambia el club del equipo solo si sigue siendo el de origen de la venta. Devuelve 0 si el
     * equipo ya no pertenece a ese club, para que el cierre no aplique una venta inconsistente.
     */
    int cambiarClub(UUID equipoId, UUID clubOrigenId, UUID clubDestinoId) {
        return jdbc.sql("""
                        UPDATE equipos
                           SET club_id = :clubDestinoId
                         WHERE id = :equipoId
                           AND club_id = :clubOrigenId
                        """)
                .param("equipoId", equipoId)
                .param("clubOrigenId", clubOrigenId)
                .param("clubDestinoId", clubDestinoId)
                .update();
    }

    /** Marca la operación {@code APLICADA} con la fecha del cierre; devuelve 0 si ya no lo estaba. */
    int marcarAplicada(UUID id, OffsetDateTime aplicadaEn) {
        return jdbc.sql("""
                        UPDATE operaciones_equipo
                           SET estado = 'APLICADA',
                               aplicada_en = :aplicadaEn
                         WHERE id = :id
                           AND estado = 'AUTORIZADA'
                        """)
                .param("id", id)
                .param("aplicadaEn", aplicadaEn)
                .update();
    }

    private static OperacionEquipoDto aDto(ResultSet fila, int numero) throws SQLException {
        UUID destinoId = fila.getObject("destino_id", UUID.class);
        UUID autorizadaPorId = fila.getObject("autorizada_por_id", UUID.class);
        return new OperacionEquipoDto(
                fila.getObject("id", UUID.class),
                TipoOperacionEquipo.valueOf(fila.getString("tipo")),
                EstadoOperacionEquipo.valueOf(fila.getString("estado")),
                new ReferenciaDto(fila.getObject("equipo_id", UUID.class), fila.getString("equipo_nombre")),
                new ReferenciaDto(fila.getObject("origen_id", UUID.class), fila.getString("origen_nombre")),
                destinoId == null ? null : new ReferenciaDto(destinoId, fila.getString("destino_nombre")),
                new TemporadaResumenDto(fila.getObject("temporada_id", UUID.class),
                        fila.getString("temporada_nombre"),
                        EstadoTemporada.valueOf(fila.getString("temporada_estado"))),
                new PerfilDto(fila.getObject("solicitada_por_id", UUID.class),
                        fila.getString("solicitada_por_nombre")),
                aZona(fila.getObject("solicitada_en", OffsetDateTime.class)),
                autorizadaPorId == null ? null
                        : new PerfilDto(autorizadaPorId, fila.getString("autorizada_por_nombre")),
                aZona(fila.getObject("autorizada_en", OffsetDateTime.class)),
                aZona(fila.getObject("aplicada_en", OffsetDateTime.class)),
                fila.getString("observaciones"));
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, UUID ligaId,
            FiltroOperacionesEquipo filtro, AlcanceAutorizacion alcance) {
        return sentencia
                .param("ligaId", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("temporadaId", filtro.temporadaId())
                .param("estado", filtro.estado() == null ? null : filtro.estado().name());
    }

    /**
     * Operación autorizada lista para aplicarse al cierre: su tipo, equipo y los clubes de la venta.
     * {@code clubDestinoId} es nulo en una conservación de plaza (RF-104).
     */
    record OperacionAplicable(UUID id, TipoOperacionEquipo tipo, UUID equipoId, UUID clubOrigenId,
                              UUID clubDestinoId) {
    }

    /**
     * Equipo con lo necesario para validar la solicitud: su club actual (propietario), su estado y
     * la temporada de su grupo.
     */
    record EquipoOperable(UUID id, String nombre, UUID clubId, String clubNombre, EstadoEquipo estado,
                          UUID temporadaId, String temporadaNombre, EstadoTemporada temporadaEstado) {
    }
}

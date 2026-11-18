package mx.tecnm.morelia.siglim.canchas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.canchas.SolicitudCanchaDto.PerfilDto;
import mx.tecnm.morelia.siglim.canchas.SolicitudCanchaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * SQL de {@code solicitudes_cancha}: Formatos 1 y 2 con número de oficio (RF-116 a RF-118).
 *
 * <ul>
 *   <li><b>Formato.</b> Los campos del formato van en {@code datos_formato} (jsonb) y se leen al
 *       record del tipo: {@link DatosPrestamo} o {@link DatosOferta}.</li>
 *   <li><b>Ámbito.</b> El listado filtra por {@code club_id} con el alcance unificado; una oferta
 *       de un externo ({@code club_id} nulo) solo la ve un ámbito de liga (ADR-0007).</li>
 *   <li><b>Número de oficio.</b> Consecutivo por liga y año con la forma {@code OF-AAAA-NNNN}
 *       [PROPUESTA]. Un candado consultivo de la transacción serializa dos capturas simultáneas;
 *       {@code UNIQUE (liga_id, numero_oficio)} es la última garantía.</li>
 *   <li><b>Candados.</b> La resolución lee el trámite con {@code FOR UPDATE}.</li>
 * </ul>
 *
 * <p>Refs: B-14, RF-116, RF-117, RF-118, ADR-0007, ADR-0011
 */
@Repository
class SolicitudCanchaRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT s.id, s.tipo, s.estado, s.numero_oficio, s.solicitante_externo, s.datos_formato,
                   s.capturada_en, s.resuelta_en, s.observaciones,
                   c.id AS club_id, c.nombre AS club_nombre,
                   ca.id AS cancha_id, ca.nombre AS cancha_nombre,
                   s.capturada_por AS capturada_por_id, cp.nombre_mostrado AS capturada_por_nombre,
                   s.resuelta_por AS resuelta_por_id, rp.nombre_mostrado AS resuelta_por_nombre
              FROM solicitudes_cancha s
              LEFT JOIN clubes c ON c.id = s.club_id
              LEFT JOIN canchas ca ON ca.id = s.cancha_id
              LEFT JOIN perfiles cp ON cp.id = s.capturada_por
              LEFT JOIN perfiles rp ON rp.id = s.resuelta_por
            """;

    private static final String CONDICIONES_LISTADO = """
             WHERE s.liga_id = :ligaId
               AND (:todaLaLiga OR s.club_id = ANY(:clubes))
               AND (cardinality(CAST(:tipos AS text[])) = 0 OR CAST(s.tipo AS text) = ANY(CAST(:tipos AS text[])))
               AND (CAST(:estado AS text) IS NULL OR CAST(s.estado AS text) = CAST(:estado AS text))
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;

    SolicitudCanchaRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    Optional<SolicitudCanchaDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE s.id = :id AND s.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(this::aDto)
                .optional();
    }

    /** Lee el trámite y toma su candado: serializa dos dictámenes simultáneos. */
    Optional<SolicitudCanchaDto> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE s.id = :id AND s.liga_id = :ligaId FOR UPDATE OF s")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(this::aDto)
                .optional();
    }

    /** Trámites visibles para el alcance, de la captura más reciente a la más antigua. */
    List<SolicitudCanchaDto> listar(UUID ligaId, FiltroSolicitudesCancha filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql(SELECCION + CONDICIONES_LISTADO
                        + " ORDER BY s.capturada_en DESC, s.id LIMIT :limite OFFSET :desplazamiento"),
                ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query(this::aDto)
                .list();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, FiltroSolicitudesCancha filtro, AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM solicitudes_cancha s" + CONDICIONES_LISTADO),
                ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    /** Club de la liga con su nombre, si existe y está {@code ACTIVO}. */
    Optional<ReferenciaDto> buscarClubActivo(UUID ligaId, UUID clubId) {
        return jdbc.sql("""
                        SELECT id, nombre FROM clubes
                         WHERE id = :clubId AND liga_id = :ligaId AND estado = 'ACTIVO'
                        """)
                .param("clubId", clubId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new ReferenciaDto(fila.getObject("id", UUID.class),
                        fila.getString("nombre")))
                .optional();
    }

    /** {@code true} si la cancha existe en la liga (activa o no: un trámite puede referirse a ella). */
    boolean existeCancha(UUID ligaId, UUID canchaId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM canchas WHERE id = :canchaId AND liga_id = :ligaId)")
                .param("canchaId", canchaId)
                .param("ligaId", ligaId)
                .query(Boolean.class)
                .single();
    }

    /** Clubes {@code ACTIVO} de la liga por nombre; con {@code todos} falso, solo los indicados. */
    List<ReferenciaDto> listarClubesActivos(UUID ligaId, boolean todos, List<UUID> clubes) {
        return jdbc.sql("""
                        SELECT id, nombre FROM clubes
                         WHERE liga_id = :ligaId AND estado = 'ACTIVO'
                           AND (:todos OR id = ANY(:clubes))
                         ORDER BY nombre, id
                        """)
                .param("ligaId", ligaId)
                .param("todos", todos)
                .param("clubes", new SqlArrayValue("uuid", clubes.toArray()))
                .query((fila, numero) -> new ReferenciaDto(fila.getObject("id", UUID.class),
                        fila.getString("nombre")))
                .list();
    }

    /** Canchas activas de la liga por nombre. */
    List<ReferenciaDto> listarCanchasActivas(UUID ligaId) {
        return jdbc.sql("SELECT id, nombre FROM canchas WHERE liga_id = :ligaId AND activa ORDER BY nombre, id")
                .param("ligaId", ligaId)
                .query((fila, numero) -> new ReferenciaDto(fila.getObject("id", UUID.class),
                        fila.getString("nombre")))
                .list();
    }

    /** Nombre de la liga para el encabezado de la carta. */
    String nombreLiga(UUID ligaId) {
        return jdbc.sql("SELECT nombre FROM ligas WHERE id = :ligaId")
                .param("ligaId", ligaId)
                .query(String.class)
                .single();
    }

    /**
     * Siguiente número de oficio del año, {@code OF-AAAA-NNNN} [PROPUESTA]. Toma un candado
     * consultivo de la transacción por liga y año: dos capturas simultáneas no obtienen el mismo
     * consecutivo. El candado se libera al confirmar o deshacer la transacción.
     */
    String siguienteNumeroOficio(UUID ligaId, int anio) {
        String prefijo = "OF-" + anio + "-";
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(CAST(:clave AS text)))")
                .param("clave", "siglim.oficio." + ligaId + "." + anio)
                .query((fila, numero) -> Boolean.TRUE)
                .single();
        int siguiente = jdbc.sql("""
                        SELECT coalesce(max(CAST(substring(numero_oficio FROM :inicio) AS integer)), 0) + 1
                          FROM solicitudes_cancha
                         WHERE liga_id = :ligaId
                           AND numero_oficio LIKE :patron
                           AND substring(numero_oficio FROM :inicio) ~ '^[0-9]+$'
                        """)
                .param("ligaId", ligaId)
                .param("inicio", prefijo.length() + 1)
                .param("patron", prefijo + "%")
                .query(Integer.class)
                .single();
        return prefijo + String.format("%04d", siguiente);
    }

    UUID insertar(UUID ligaId, TipoSolicitudCancha tipo, String numeroOficio, UUID clubId,
            String solicitanteExterno, UUID canchaId, Object datosFormato, UUID capturadaPor) {
        return jdbc.sql("""
                        INSERT INTO solicitudes_cancha
                               (liga_id, tipo, numero_oficio, club_id, solicitante_externo, cancha_id,
                                datos_formato, estado, capturada_por)
                        VALUES (:ligaId, CAST(:tipo AS tipo_solicitud_cancha), :numeroOficio,
                                CAST(:clubId AS uuid), CAST(:solicitanteExterno AS varchar),
                                CAST(:canchaId AS uuid), CAST(:datos AS jsonb),
                                CAST(:estado AS estado_solicitud_cancha), :capturadaPor)
                        RETURNING id
                        """)
                .param("ligaId", ligaId)
                .param("tipo", tipo.name())
                .param("numeroOficio", numeroOficio)
                .param("clubId", clubId)
                .param("solicitanteExterno", solicitanteExterno)
                .param("canchaId", canchaId)
                .param("datos", json.writeValueAsString(datosFormato))
                .param("estado", tipo.estadoInicial().name())
                .param("capturadaPor", capturadaPor)
                .query(UUID.class)
                .single();
    }

    /**
     * Aplica el dictamen solo si el trámite sigue pendiente; devuelve las filas cambiadas (0 si otro
     * dictamen ganó la carrera, aunque el candado de {@link #buscarParaActualizar} ya lo evita).
     */
    int resolver(UUID id, EstadoSolicitudCancha estado, UUID resueltaPor, OffsetDateTime resueltaEn,
            String observaciones) {
        return jdbc.sql("""
                        UPDATE solicitudes_cancha
                           SET estado = CAST(:estado AS estado_solicitud_cancha),
                               resuelta_por = :resueltaPor,
                               resuelta_en = :resueltaEn,
                               observaciones = CAST(:observaciones AS text)
                         WHERE id = :id
                           AND estado IN ('PENDIENTE', 'PENDIENTE_NEGOCIACION')
                        """)
                .param("id", id)
                .param("estado", estado.name())
                .param("resueltaPor", resueltaPor)
                .param("resueltaEn", resueltaEn)
                .param("observaciones", observaciones)
                .update();
    }

    private SolicitudCanchaDto aDto(ResultSet fila, int numero) throws SQLException {
        TipoSolicitudCancha tipo = TipoSolicitudCancha.valueOf(fila.getString("tipo"));
        String datos = fila.getString("datos_formato");
        UUID clubId = fila.getObject("club_id", UUID.class);
        UUID canchaId = fila.getObject("cancha_id", UUID.class);
        UUID resueltaPorId = fila.getObject("resuelta_por_id", UUID.class);
        return new SolicitudCanchaDto(
                fila.getObject("id", UUID.class),
                tipo,
                tipo.formato(),
                EstadoSolicitudCancha.valueOf(fila.getString("estado")),
                fila.getString("numero_oficio"),
                clubId == null ? null : new ReferenciaDto(clubId, fila.getString("club_nombre")),
                fila.getString("solicitante_externo"),
                canchaId == null ? null : new ReferenciaDto(canchaId, fila.getString("cancha_nombre")),
                tipo.esOferta() ? null : json.readValue(datos, DatosPrestamo.class),
                tipo.esOferta() ? json.readValue(datos, DatosOferta.class) : null,
                new PerfilDto(fila.getObject("capturada_por_id", UUID.class),
                        fila.getString("capturada_por_nombre")),
                aZona(fila.getObject("capturada_en", OffsetDateTime.class)),
                resueltaPorId == null ? null : new PerfilDto(resueltaPorId, fila.getString("resuelta_por_nombre")),
                aZona(fila.getObject("resuelta_en", OffsetDateTime.class)),
                fila.getString("observaciones"));
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, UUID ligaId,
            FiltroSolicitudesCancha filtro, AlcanceAutorizacion alcance) {
        return sentencia
                .param("ligaId", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("tipos", new SqlArrayValue("text",
                        filtro.tipos().stream().map(Enum::name).toArray()))
                .param("estado", filtro.estado() == null ? null : filtro.estado().name());
    }
}

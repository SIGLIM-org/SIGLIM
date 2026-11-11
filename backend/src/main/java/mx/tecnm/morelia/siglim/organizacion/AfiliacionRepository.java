package mx.tecnm.morelia.siglim.organizacion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code afiliaciones} y de sus categorías (RF-101, DEC-09).
 *
 * <ul>
 *   <li><b>Lectura.</b> El club y la temporada se leen con {@code JOIN}, y las categorías con una
 *       segunda consulta para la página completa (nunca una por fila). El perfil se une con
 *       {@code LEFT JOIN}: su nombre solo está presente si las políticas RLS de {@code perfiles} lo
 *       permiten (la Directiva no ve el perfil de la Secretaría), pero el identificador sale de
 *       {@code afiliaciones}. Las fechas se devuelven en la zona de negocio.</li>
 *   <li><b>Ámbito.</b> El listado filtra por el club en el {@code WHERE} y el {@code COUNT} con la
 *       misma condición (ADR-0007).</li>
 *   <li><b>Folio y concurrencia.</b> {@link #bloquearTemporada} lee la temporada con
 *       {@code FOR UPDATE}: dos altas simultáneas de la misma temporada se serializan y el
 *       consecutivo del folio no se repite. La resolución bloquea el tarjetón con
 *       {@code FOR UPDATE}.</li>
 * </ul>
 *
 * <p>Refs: H-08.1, RF-101, CU-018, DEC-09, ADR-0007, ADR-0011
 */
@Repository
class AfiliacionRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String SELECCION = """
            SELECT a.id, a.folio, a.colores_uniforme, a.estado, a.capturada_en, a.resuelta_en,
                   a.observaciones,
                   cl.id AS club_id, cl.nombre AS club_nombre,
                   t.id AS temporada_id, t.nombre AS temporada_nombre, t.estado AS temporada_estado,
                   a.capturada_por AS capturada_por_id, cp.nombre_mostrado AS capturada_por_nombre,
                   a.resuelta_por AS resuelta_por_id, rp.nombre_mostrado AS resuelta_por_nombre
              FROM afiliaciones a
              JOIN clubes cl ON cl.id = a.club_id
              JOIN temporadas t ON t.id = a.temporada_id
              LEFT JOIN perfiles cp ON cp.id = a.capturada_por
              LEFT JOIN perfiles rp ON rp.id = a.resuelta_por
            """;

    private static final String CONDICIONES_LISTADO = """
             WHERE cl.liga_id = :ligaId
               AND (:todaLaLiga OR a.club_id = ANY(:clubes))
               AND (CAST(:temporadaId AS uuid) IS NULL OR a.temporada_id = CAST(:temporadaId AS uuid))
               AND (CAST(:estado AS text) IS NULL OR CAST(a.estado AS text) = CAST(:estado AS text))
            """;

    private final JdbcClient jdbc;

    AfiliacionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<AfiliacionDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE a.id = :id AND cl.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(AfiliacionRepository::aDatos)
                .optional()
                .map(datos -> aDto(datos, categoriasDe(List.of(datos.id()))));
    }

    /** Lee el tarjetón y toma su candado: serializa la edición y la resolución. */
    Optional<AfiliacionDto> buscarParaActualizar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE a.id = :id AND cl.liga_id = :ligaId FOR UPDATE OF a")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(AfiliacionRepository::aDatos)
                .optional()
                .map(datos -> aDto(datos, categoriasDe(List.of(datos.id()))));
    }

    List<AfiliacionDto> listar(UUID ligaId, FiltroAfiliaciones filtro,
            AlcanceAutorizacion alcance) {
        List<DatosAfiliacion> filas = conFiltro(jdbc.sql(SELECCION + CONDICIONES_LISTADO
                        + " ORDER BY t.fecha_inicio DESC, cl.nombre, a.id LIMIT :limite OFFSET :desplazamiento"),
                ligaId, filtro, alcance)
                .param("limite", filtro.tamano())
                .param("desplazamiento", filtro.desplazamiento())
                .query(AfiliacionRepository::aDatos)
                .list();
        Map<UUID, List<ReferenciaDto>> categorias = categoriasDe(
                filas.stream().map(DatosAfiliacion::id).toList());
        return filas.stream()
                .map(fila -> aDto(fila, categorias.getOrDefault(fila.id(), List.of())))
                .toList();
    }

    /** Con la misma condición del listado: {@code totalElementos} no delata filas fuera de ámbito. */
    long contar(UUID ligaId, FiltroAfiliaciones filtro,
            AlcanceAutorizacion alcance) {
        return conFiltro(jdbc.sql("SELECT count(*) FROM afiliaciones a"
                        + " JOIN clubes cl ON cl.id = a.club_id"
                        + " JOIN temporadas t ON t.id = a.temporada_id" + CONDICIONES_LISTADO),
                ligaId, filtro, alcance)
                .query(Long.class)
                .single();
    }

    /** Afiliación del club en la temporada, si ya existe (UNIQUE de la tabla). */
    Optional<ResumenAfiliacion> buscarPorClubTemporada(UUID ligaId, UUID clubId, UUID temporadaId) {
        return jdbc.sql("""
                        SELECT a.id, a.folio, a.estado
                          FROM afiliaciones a
                          JOIN clubes cl ON cl.id = a.club_id
                         WHERE cl.liga_id = :ligaId AND a.club_id = :clubId
                           AND a.temporada_id = :temporadaId
                        """)
                .param("ligaId", ligaId)
                .param("clubId", clubId)
                .param("temporadaId", temporadaId)
                .query((fila, numero) -> new ResumenAfiliacion(
                        fila.getObject("id", UUID.class),
                        fila.getString("folio"),
                        EstadoAfiliacion.valueOf(fila.getString("estado"))))
                .optional();
    }

    /** Cuántos tarjetones tiene la temporada; con la temporada bloqueada da el consecutivo del folio. */
    long contarDeTemporada(UUID temporadaId) {
        return jdbc.sql("SELECT count(*) FROM afiliaciones WHERE temporada_id = :temporadaId")
                .param("temporadaId", temporadaId)
                .query(Long.class)
                .single();
    }

    /**
     * Temporada de la liga con candado exclusivo: serializa el alta de tarjetones de esa temporada
     * con la activación y el cierre, y garantiza un consecutivo de folio sin carreras.
     */
    Optional<TemporadaAfiliacion> bloquearTemporada(UUID ligaId, UUID temporadaId) {
        return jdbc.sql("""
                        SELECT id, nombre, estado, fecha_inicio
                          FROM temporadas
                         WHERE id = :id AND liga_id = :ligaId
                         FOR UPDATE
                        """)
                .param("id", temporadaId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new TemporadaAfiliacion(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        EstadoTemporada.valueOf(fila.getString("estado")),
                        fila.getObject("fecha_inicio", LocalDate.class)))
                .optional();
    }

    /** Categorías que participan en la temporada ({@code categorias_temporada}). */
    Set<UUID> categoriasDeTemporada(UUID temporadaId) {
        return Set.copyOf(jdbc.sql("SELECT categoria_id FROM categorias_temporada WHERE temporada_id = :temporadaId")
                .param("temporadaId", temporadaId)
                .query(UUID.class)
                .list());
    }

    /** Temporadas de la liga, de la más reciente a la más antigua, para el contexto del tarjetón. */
    List<TemporadaContexto> temporadasDeLiga(UUID ligaId) {
        return jdbc.sql("""
                        SELECT id, nombre, estado, fecha_inicio
                          FROM temporadas
                         WHERE liga_id = :ligaId
                         ORDER BY fecha_inicio DESC, id
                        """)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new TemporadaContexto(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        EstadoTemporada.valueOf(fila.getString("estado")),
                        fila.getObject("fecha_inicio", LocalDate.class)))
                .list();
    }

    /**
     * Categorías de las temporadas de la liga con sus grupos y equipos activos, en una sola
     * consulta (sin N+1). Una categoría sin grupos sale con {@code grupo_id} nulo.
     */
    List<CategoriaContexto> categoriasConGruposDeLiga(UUID ligaId) {
        return jdbc.sql("""
                        SELECT ct.temporada_id, c.id AS categoria_id, c.nombre AS categoria_nombre,
                               g.id AS grupo_id, g.nombre AS grupo_nombre,
                               (SELECT count(*) FROM equipos e
                                 WHERE e.grupo_id = g.id AND e.estado <> 'BAJA') AS equipos
                          FROM categorias_temporada ct
                          JOIN categorias c ON c.id = ct.categoria_id AND c.liga_id = :ligaId
                          JOIN temporadas t ON t.id = ct.temporada_id
                          LEFT JOIN grupos g ON g.categoria_temporada_id = ct.id
                         WHERE t.liga_id = :ligaId
                         ORDER BY ct.temporada_id, c.orden, c.nombre, g.nombre
                        """)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new CategoriaContexto(
                        fila.getObject("temporada_id", UUID.class),
                        fila.getObject("categoria_id", UUID.class),
                        fila.getString("categoria_nombre"),
                        fila.getObject("grupo_id", UUID.class),
                        fila.getString("grupo_nombre"),
                        fila.getLong("equipos")))
                .list();
    }

    UUID insertar(UUID clubId, UUID temporadaId, String folio, String coloresUniformes, UUID capturadaPor) {
        return jdbc.sql("""
                        INSERT INTO afiliaciones (club_id, temporada_id, folio, colores_uniforme, capturada_por)
                        VALUES (:clubId, :temporadaId, :folio, :colores, :capturadaPor)
                        RETURNING id
                        """)
                .param("clubId", clubId)
                .param("temporadaId", temporadaId)
                .param("folio", folio)
                .param("colores", coloresUniformes)
                .param("capturadaPor", capturadaPor)
                .query(UUID.class)
                .single();
    }

    /** Agrega las categorías del tarjetón después de insertarlo. */
    void insertarCategorias(UUID afiliacionId, Collection<UUID> categoriaIds) {
        for (UUID categoriaId : categoriaIds) {
            jdbc.sql("""
                            INSERT INTO afiliacion_categorias (afiliacion_id, categoria_id)
                            VALUES (:afiliacionId, :categoriaId)
                            """)
                    .param("afiliacionId", afiliacionId)
                    .param("categoriaId", categoriaId)
                    .update();
        }
    }

    /** Reemplaza el conjunto de categorías del tarjetón en una sola transacción. */
    void reemplazarCategorias(UUID afiliacionId, Collection<UUID> categoriaIds) {
        jdbc.sql("DELETE FROM afiliacion_categorias WHERE afiliacion_id = :afiliacionId")
                .param("afiliacionId", afiliacionId)
                .update();
        insertarCategorias(afiliacionId, categoriaIds);
    }

    void actualizarColores(UUID id, String coloresUniformes) {
        jdbc.sql("UPDATE afiliaciones SET colores_uniforme = :colores WHERE id = :id")
                .param("id", id)
                .param("colores", coloresUniformes)
                .update();
    }

    /** Aplica el dictamen: estado final, perfil y fecha de resolución, y observaciones. */
    void resolver(UUID id, EstadoAfiliacion estado, UUID resueltaPor, OffsetDateTime resueltaEn,
            String observaciones) {
        jdbc.sql("""
                        UPDATE afiliaciones
                           SET estado = CAST(:estado AS estado_afiliacion),
                               resuelta_por = CAST(:resueltaPor AS uuid),
                               resuelta_en = CAST(:resueltaEn AS timestamptz),
                               observaciones = CAST(:observaciones AS text)
                         WHERE id = :id
                        """)
                .param("id", id)
                .param("estado", estado.name())
                .param("resueltaPor", resueltaPor)
                .param("resueltaEn", resueltaEn)
                .param("observaciones", observaciones)
                .update();
    }

    /**
     * Categorías de las afiliaciones indicadas, agrupadas por afiliación y ordenadas por el orden
     * del catálogo y su nombre.
     */
    private Map<UUID, List<ReferenciaDto>> categoriasDe(Collection<UUID> afiliacionIds) {
        if (afiliacionIds.isEmpty()) {
            return Map.of();
        }
        List<CategoriaFila> filas = jdbc.sql("""
                        SELECT ac.afiliacion_id, c.id, c.nombre
                          FROM afiliacion_categorias ac
                          JOIN categorias c ON c.id = ac.categoria_id
                         WHERE ac.afiliacion_id = ANY(:ids)
                         ORDER BY ac.afiliacion_id, c.orden, c.nombre
                        """)
                .param("ids", new SqlArrayValue("uuid", afiliacionIds.toArray()))
                .query((fila, numero) -> new CategoriaFila(
                        fila.getObject("afiliacion_id", UUID.class),
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre")))
                .list();
        Map<UUID, List<ReferenciaDto>> porAfiliacion = new LinkedHashMap<>();
        for (CategoriaFila fila : filas) {
            porAfiliacion.computeIfAbsent(fila.afiliacionId(), clave -> new ArrayList<>())
                    .add(new ReferenciaDto(fila.categoriaId(), fila.categoriaNombre()));
        }
        return porAfiliacion;
    }

    private static JdbcClient.StatementSpec conFiltro(JdbcClient.StatementSpec sentencia, UUID ligaId,
            FiltroAfiliaciones filtro, AlcanceAutorizacion alcance) {
        return sentencia
                .param("ligaId", ligaId)
                .param("todaLaLiga", alcance.todaLaLiga())
                .param("clubes", new SqlArrayValue("uuid", alcance.clubes().toArray()))
                .param("temporadaId", filtro.temporadaId())
                .param("estado", filtro.estado() == null ? null : filtro.estado().name());
    }

    private static DatosAfiliacion aDatos(ResultSet fila, int numero) throws SQLException {
        return new DatosAfiliacion(
                fila.getObject("id", UUID.class),
                fila.getString("folio"),
                fila.getObject("club_id", UUID.class),
                fila.getString("club_nombre"),
                fila.getObject("temporada_id", UUID.class),
                fila.getString("temporada_nombre"),
                EstadoTemporada.valueOf(fila.getString("temporada_estado")),
                fila.getString("colores_uniforme"),
                EstadoAfiliacion.valueOf(fila.getString("estado")),
                fila.getObject("capturada_por_id", UUID.class),
                fila.getString("capturada_por_nombre"),
                aZona(fila.getObject("capturada_en", OffsetDateTime.class)),
                fila.getObject("resuelta_por_id", UUID.class),
                fila.getString("resuelta_por_nombre"),
                aZona(fila.getObject("resuelta_en", OffsetDateTime.class)),
                fila.getString("observaciones"));
    }

    private static AfiliacionDto aDto(DatosAfiliacion datos, Map<UUID, List<ReferenciaDto>> categorias) {
        return aDto(datos, categorias.getOrDefault(datos.id(), List.of()));
    }

    private static AfiliacionDto aDto(DatosAfiliacion datos, List<ReferenciaDto> categorias) {
        return new AfiliacionDto(
                datos.id(),
                datos.folio(),
                new ReferenciaDto(datos.clubId(), datos.clubNombre()),
                new AfiliacionDto.TemporadaResumenDto(datos.temporadaId(), datos.temporadaNombre(),
                        datos.temporadaEstado()),
                datos.coloresUniformes(),
                datos.estado(),
                categorias,
                new AfiliacionDto.PerfilDto(datos.capturadaPorId(), datos.capturadaPorNombre()),
                datos.capturadaEn(),
                datos.resueltaPorId() == null ? null
                        : new AfiliacionDto.PerfilDto(datos.resueltaPorId(), datos.resueltaPorNombre()),
                datos.resueltaEn(),
                datos.observaciones());
    }

    private static OffsetDateTime aZona(OffsetDateTime valor) {
        return valor == null ? null : valor.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    /** Fila cruda del tarjetón con sus referencias ya resueltas. */
    record DatosAfiliacion(
            UUID id,
            String folio,
            UUID clubId,
            String clubNombre,
            UUID temporadaId,
            String temporadaNombre,
            EstadoTemporada temporadaEstado,
            String coloresUniformes,
            EstadoAfiliacion estado,
            UUID capturadaPorId,
            String capturadaPorNombre,
            OffsetDateTime capturadaEn,
            UUID resueltaPorId,
            String resueltaPorNombre,
            OffsetDateTime resueltaEn,
            String observaciones) {
    }

    /** Lo mínimo para explicar que el club ya tiene tarjetón en la temporada. */
    record ResumenAfiliacion(UUID id, String folio, EstadoAfiliacion estado) {
    }

    /** Temporada con lo necesario para validar RF-003 y calcular el folio. */
    record TemporadaAfiliacion(UUID id, String nombre, EstadoTemporada estado, LocalDate fechaInicio) {
    }

    /** Temporada del contexto de captura, con sus categorías. */
    record TemporadaContexto(UUID id, String nombre, EstadoTemporada estado, LocalDate fechaInicio) {
    }

    /** Fila de categoría de una temporada; {@code grupoId} es nulo si la categoría no tiene grupos. */
    record CategoriaContexto(UUID temporadaId, UUID categoriaId, String categoriaNombre, UUID grupoId,
                             String grupoNombre, long equipos) {
    }

    private record CategoriaFila(UUID afiliacionId, UUID categoriaId, String categoriaNombre) {
    }
}

package mx.tecnm.morelia.siglim.partidos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.JugadorAlineadoDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.MarcadorDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.PartidoDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lecturas y escrituras de la captura de la cédula (M-08.2): cédula, alineación y marcador.
 *
 * <p>Todo se acota a la liga por la cadena {@code partido → jornada → grupo → categoría de la
 * temporada → temporada}. Las consultas corren en la transacción del servicio con su contexto de
 * usuario y RLS (ADR-0014). Los triggers {@code tg_cedulas_validar}, {@code tg_alineaciones_validar}
 * y {@code tg_cedula_en_captura} repiten las reglas como última garantía.
 *
 * <p>Refs: H-16.1, RF-019, RF-020, RF-025, RF-047, RF-110, RF-126, ADR-0014
 */
@Repository
class CedulaRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String NOMBRE_COMPLETO = "concat_ws(' ', pe.nombre, pe.apellido_paterno, pe.apellido_materno)";

    private static final String PARTIDO_DE_LA_LIGA = """
              FROM partidos p
              JOIN jornadas j ON j.id = p.jornada_id
              JOIN grupos g ON g.id = j.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN temporadas t ON t.id = ct.temporada_id
            """;

    private final JdbcClient jdbc;

    CedulaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Partido de la liga con sus equipos y su temporada, bloqueado para abrir la cédula. */
    Optional<PartidoCedula> bloquearPartido(UUID ligaId, UUID partidoId) {
        return jdbc.sql("SELECT p.id, p.estado, p.equipo_local_id, p.equipo_visitante_id, ct.temporada_id"
                        + PARTIDO_DE_LA_LIGA
                        + " WHERE p.id = :partido AND t.liga_id = :liga FOR UPDATE OF p")
                .param("partido", partidoId)
                .param("liga", ligaId)
                .query((fila, numero) -> new PartidoCedula(
                        fila.getObject("id", UUID.class),
                        EstadoPartido.valueOf(fila.getString("estado")),
                        fila.getObject("equipo_local_id", UUID.class),
                        fila.getObject("equipo_visitante_id", UUID.class),
                        fila.getObject("temporada_id", UUID.class)))
                .optional();
    }

    /** Si el partido existe en la liga, aunque no se bloquee. */
    boolean existePartido(UUID ligaId, UUID partidoId) {
        return jdbc.sql("SELECT count(*)" + PARTIDO_DE_LA_LIGA + " WHERE p.id = :partido AND t.liga_id = :liga")
                .param("partido", partidoId)
                .param("liga", ligaId)
                .query(Long.class)
                .single() > 0;
    }

    /** Cédula del partido, si ya se abrió. */
    Optional<UUID> cedulaDePartido(UUID partidoId) {
        return jdbc.sql("SELECT id FROM cedulas WHERE partido_id = :partido")
                .param("partido", partidoId)
                .query(UUID.class)
                .optional();
    }

    /**
     * Abre la cédula {@code EN_CAPTURA}. Si otra petición la abrió al mismo tiempo, no inserta nada
     * ({@code UNIQUE (partido_id)}) y devuelve vacío.
     */
    Optional<UUID> insertar(UUID partidoId, UUID capturistaId) {
        return jdbc.sql("""
                        INSERT INTO cedulas (partido_id, capturista_id)
                        VALUES (:partido, :capturista)
                        ON CONFLICT (partido_id) DO NOTHING
                        RETURNING id
                        """)
                .param("partido", partidoId)
                .param("capturista", capturistaId)
                .query(UUID.class)
                .optional();
    }

    /** Cédula de la liga con su estado y los datos de su partido, bloqueada para modificarla. */
    Optional<CedulaBloqueada> bloquear(UUID ligaId, UUID cedulaId) {
        return jdbc.sql("""
                        SELECT c.id, c.estado, p.id AS partido_id, p.equipo_local_id, p.equipo_visitante_id,
                               ct.temporada_id
                          FROM cedulas c
                          JOIN partidos p ON p.id = c.partido_id
                          JOIN jornadas j ON j.id = p.jornada_id
                          JOIN grupos g ON g.id = j.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN temporadas t ON t.id = ct.temporada_id
                         WHERE c.id = :cedula AND t.liga_id = :liga
                         FOR UPDATE OF c
                        """)
                .param("cedula", cedulaId)
                .param("liga", ligaId)
                .query((fila, numero) -> new CedulaBloqueada(
                        fila.getObject("id", UUID.class),
                        EstadoCedula.valueOf(fila.getString("estado")),
                        fila.getObject("partido_id", UUID.class),
                        fila.getObject("equipo_local_id", UUID.class),
                        fila.getObject("equipo_visitante_id", UUID.class),
                        fila.getObject("temporada_id", UUID.class)))
                .optional();
    }

    /** La cédula completa para la respuesta. */
    Optional<CedulaDto> buscar(UUID cedulaId) {
        Optional<CedulaDto> cedula = jdbc.sql("""
                        SELECT c.id, c.estado, c.goles_local, c.goles_visitante, c.penales_local, c.penales_visitante,
                               c.observaciones, c.creado_en, c.actualizado_en,
                               cap.id AS capturista_id, cap.nombre_mostrado AS capturista_nombre,
                               p.id AS partido_id, p.inicio, p.estado AS partido_estado,
                               el.id AS local_id, el.nombre AS local_nombre,
                               ev.id AS visitante_id, ev.nombre AS visitante_nombre,
                               np.id AS no_presentado_id, np.nombre AS no_presentado_nombre,
                               coalesce((siglim.parametros_vigentes(ct.temporada_id)).es_demo, false) AS parametros_demo
                          FROM cedulas c
                          JOIN partidos p ON p.id = c.partido_id
                          JOIN jornadas j ON j.id = p.jornada_id
                          JOIN grupos g ON g.id = j.grupo_id
                          JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN equipos el ON el.id = p.equipo_local_id
                          JOIN equipos ev ON ev.id = p.equipo_visitante_id
                          LEFT JOIN equipos np ON np.id = c.no_presentado_equipo_id
                          LEFT JOIN perfiles cap ON cap.id = c.capturista_id
                         WHERE c.id = :cedula
                        """)
                .param("cedula", cedulaId)
                .query((fila, numero) -> aCedula(fila, List.of()))
                .optional();
        return cedula.map(datos -> new CedulaDto(datos.id(), datos.partido(), datos.estado(), datos.capturista(),
                datos.marcador(), alineaciones(cedulaId), datos.observaciones(), datos.creadoEn(),
                datos.actualizadoEn()));
    }

    /** Jugadores alineados, por equipo, titulares primero y por número de playera. */
    List<JugadorAlineadoDto> alineaciones(UUID cedulaId) {
        return jdbc.sql("SELECT a.id, a.registro_jugador_id, a.equipo_id, a.numero_playera, a.titular,"
                        + " a.categoria_superior, " + NOMBRE_COMPLETO + " AS nombre"
                        + """
                           FROM alineaciones a
                           JOIN cedulas c ON c.id = a.cedula_id
                           JOIN partidos p ON p.id = c.partido_id
                           JOIN registros_jugador r ON r.id = a.registro_jugador_id
                           JOIN personas pe ON pe.id = r.persona_id
                          WHERE a.cedula_id = :cedula
                          ORDER BY (a.equipo_id = p.equipo_local_id) DESC, a.titular DESC,
                                   a.numero_playera NULLS LAST, nombre, a.id
                         """)
                .param("cedula", cedulaId)
                .query((fila, numero) -> new JugadorAlineadoDto(
                        fila.getObject("id", UUID.class),
                        fila.getObject("registro_jugador_id", UUID.class),
                        fila.getString("nombre"),
                        fila.getObject("equipo_id", UUID.class),
                        fila.getObject("numero_playera", Integer.class),
                        fila.getBoolean("titular"),
                        fila.getBoolean("categoria_superior")))
                .list();
    }

    /**
     * Datos para validar la elegibilidad de los registros pedidos (RF-019, RF-020, RF-110): estado,
     * temporada, equipo, club y categoría del registro, nombre del jugador y si tiene una sanción
     * vigente que le impida jugar. Solo registros de la liga; los demás no vienen en el mapa.
     */
    Map<UUID, RegistroAlineable> registros(UUID ligaId, Collection<UUID> registroIds) {
        if (registroIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, RegistroAlineable> resultado = new HashMap<>();
        jdbc.sql("SELECT r.id, r.estado, r.temporada_id, r.equipo_id, e.club_id,"
                        + " siglim.categoria_de_equipo(r.equipo_id) AS categoria_id, cat.categoria_superior_id, "
                        + NOMBRE_COMPLETO + " AS nombre,"
                        + """
                                EXISTS (SELECT 1 FROM sanciones s
                                         WHERE s.persona_id = r.persona_id AND s.estado = 'VIGENTE'
                                           AND s.tipo IN ('SUSPENSION_PARTIDOS', 'BAJA')) AS sancionado
                           FROM registros_jugador r
                           JOIN equipos e ON e.id = r.equipo_id
                           JOIN temporadas t ON t.id = r.temporada_id
                           JOIN personas pe ON pe.id = r.persona_id
                           LEFT JOIN categorias cat ON cat.id = siglim.categoria_de_equipo(r.equipo_id)
                          WHERE r.id IN (:registros) AND t.liga_id = :liga
                         """)
                .param("registros", registroIds)
                .param("liga", ligaId)
                .query((fila, numero) -> new RegistroAlineable(
                        fila.getObject("id", UUID.class),
                        fila.getString("estado"),
                        fila.getObject("temporada_id", UUID.class),
                        fila.getObject("equipo_id", UUID.class),
                        fila.getObject("club_id", UUID.class),
                        fila.getObject("categoria_id", UUID.class),
                        fila.getObject("categoria_superior_id", UUID.class),
                        fila.getString("nombre"),
                        fila.getBoolean("sancionado")))
                .list()
                .forEach(registro -> resultado.put(registro.id(), registro));
        return resultado;
    }

    /** Club y categoría de los equipos indicados. */
    Map<UUID, EquipoAlineacion> equipos(Collection<UUID> equipoIds) {
        if (equipoIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EquipoAlineacion> resultado = new HashMap<>();
        jdbc.sql("""
                        SELECT e.id, e.club_id, siglim.categoria_de_equipo(e.id) AS categoria_id
                          FROM equipos e
                         WHERE e.id IN (:equipos)
                        """)
                .param("equipos", equipoIds)
                .query((fila, numero) -> new EquipoAlineacion(
                        fila.getObject("id", UUID.class),
                        fila.getObject("club_id", UUID.class),
                        fila.getObject("categoria_id", UUID.class)))
                .list()
                .forEach(equipo -> resultado.put(equipo.id(), equipo));
        return resultado;
    }

    /** Alineación actual de la cédula: registro → fila. */
    Map<UUID, UUID> alineacionActual(UUID cedulaId) {
        Map<UUID, UUID> resultado = new HashMap<>();
        jdbc.sql("SELECT id, registro_jugador_id FROM alineaciones WHERE cedula_id = :cedula")
                .param("cedula", cedulaId)
                .query((fila, numero) -> Map.entry(fila.getObject("registro_jugador_id", UUID.class),
                        fila.getObject("id", UUID.class)))
                .list()
                .forEach(par -> resultado.put(par.getKey(), par.getValue()));
        return resultado;
    }

    /** Filas de alineación que ya aparecen en algún evento de la cédula (gol, tarjeta, sustitución). */
    Set<UUID> conEventos(Collection<UUID> alineacionIds) {
        if (alineacionIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("""
                        SELECT alineacion_id FROM eventos_cedula WHERE alineacion_id IN (:filas)
                        UNION
                        SELECT alineacion_entra_id FROM eventos_cedula WHERE alineacion_entra_id IN (:filas)
                        """)
                .param("filas", alineacionIds)
                .query(UUID.class)
                .list());
    }

    void eliminarAlineaciones(Collection<UUID> alineacionIds) {
        if (alineacionIds.isEmpty()) {
            return;
        }
        jdbc.sql("DELETE FROM alineaciones WHERE id IN (:filas)")
                .param("filas", alineacionIds)
                .update();
    }

    void actualizarAlineacion(UUID alineacionId, UUID equipoId, Integer numeroPlayera, boolean titular) {
        jdbc.sql("""
                        UPDATE alineaciones
                           SET equipo_id = :equipo, numero_playera = :playera, titular = :titular
                         WHERE id = :id
                        """)
                .param("id", alineacionId)
                .param("equipo", equipoId)
                .param("playera", numeroPlayera)
                .param("titular", titular)
                .update();
    }

    void insertarAlineacion(UUID cedulaId, UUID registroId, UUID equipoId, Integer numeroPlayera, boolean titular) {
        jdbc.sql("""
                        INSERT INTO alineaciones (cedula_id, registro_jugador_id, equipo_id, numero_playera, titular)
                        VALUES (:cedula, :registro, :equipo, :playera, :titular)
                        """)
                .param("cedula", cedulaId)
                .param("registro", registroId)
                .param("equipo", equipoId)
                .param("playera", numeroPlayera)
                .param("titular", titular)
                .update();
    }

    /**
     * Marcador por default de la versión vigente de los parámetros de la temporada (RF-126, DEC-24).
     * Vacío si la temporada no tiene parámetros.
     */
    Optional<MarcadorDefault> marcadorDefault(UUID temporadaId) {
        return jdbc.sql("""
                        SELECT p.goles_default_ganador, p.goles_default_perdedor, p.es_demo
                          FROM siglim.parametros_vigentes(:temporada) p
                         WHERE p.id IS NOT NULL
                        """)
                .param("temporada", temporadaId)
                .query((fila, numero) -> new MarcadorDefault(
                        fila.getInt("goles_default_ganador"),
                        fila.getInt("goles_default_perdedor"),
                        fila.getBoolean("es_demo")))
                .optional();
    }

    void guardarMarcador(UUID cedulaId, Integer golesLocal, Integer golesVisitante, Integer penalesLocal,
            Integer penalesVisitante, UUID noPresentadoEquipoId) {
        jdbc.sql("""
                        UPDATE cedulas
                           SET goles_local = :golesLocal, goles_visitante = :golesVisitante,
                               penales_local = :penalesLocal, penales_visitante = :penalesVisitante,
                               no_presentado_equipo_id = :noPresentado, actualizado_en = now()
                         WHERE id = :id
                        """)
                .param("id", cedulaId)
                .param("golesLocal", golesLocal)
                .param("golesVisitante", golesVisitante)
                .param("penalesLocal", penalesLocal)
                .param("penalesVisitante", penalesVisitante)
                .param("noPresentado", noPresentadoEquipoId)
                .update();
    }

    /** Marca el cambio de la alineación en la cédula para que {@code actualizadoEn} lo refleje. */
    void tocar(UUID cedulaId) {
        jdbc.sql("UPDATE cedulas SET actualizado_en = now() WHERE id = :id")
                .param("id", cedulaId)
                .update();
    }

    private static CedulaDto aCedula(ResultSet fila, List<JugadorAlineadoDto> alineaciones) throws SQLException {
        UUID noPresentadoId = fila.getObject("no_presentado_id", UUID.class);
        boolean porDefault = noPresentadoId != null;
        MarcadorDto marcador = new MarcadorDto(
                fila.getObject("goles_local", Integer.class),
                fila.getObject("goles_visitante", Integer.class),
                fila.getObject("penales_local", Integer.class),
                fila.getObject("penales_visitante", Integer.class),
                porDefault ? new ReferenciaDto(noPresentadoId, fila.getString("no_presentado_nombre")) : null,
                porDefault,
                porDefault && fila.getBoolean("parametros_demo"));
        UUID capturistaId = fila.getObject("capturista_id", UUID.class);
        return new CedulaDto(
                fila.getObject("id", UUID.class),
                new PartidoDto(
                        fila.getObject("partido_id", UUID.class),
                        new ReferenciaDto(fila.getObject("local_id", UUID.class), fila.getString("local_nombre")),
                        new ReferenciaDto(fila.getObject("visitante_id", UUID.class),
                                fila.getString("visitante_nombre")),
                        enZonaDeNegocio(fila.getObject("inicio", OffsetDateTime.class)),
                        EstadoPartido.valueOf(fila.getString("partido_estado"))),
                EstadoCedula.valueOf(fila.getString("estado")),
                capturistaId == null ? null : new ReferenciaDto(capturistaId, fila.getString("capturista_nombre")),
                marcador,
                alineaciones,
                fila.getString("observaciones"),
                enZonaDeNegocio(fila.getObject("creado_en", OffsetDateTime.class)),
                enZonaDeNegocio(fila.getObject("actualizado_en", OffsetDateTime.class)));
    }

    private static OffsetDateTime enZonaDeNegocio(OffsetDateTime instante) {
        return instante == null ? null : instante.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    /** Partido bloqueado para abrir su cédula. */
    record PartidoCedula(UUID id, EstadoPartido estado, UUID equipoLocalId, UUID equipoVisitanteId,
                         UUID temporadaId) {
    }

    /** Cédula bloqueada con lo necesario para validar la captura. */
    record CedulaBloqueada(UUID id, EstadoCedula estado, UUID partidoId, UUID equipoLocalId,
                           UUID equipoVisitanteId, UUID temporadaId) {

        boolean juega(UUID equipoId) {
            return equipoLocalId.equals(equipoId) || equipoVisitanteId.equals(equipoId);
        }
    }

    /** Registro de jugador con lo que decide su elegibilidad. */
    record RegistroAlineable(UUID id, String estado, UUID temporadaId, UUID equipoId, UUID clubId,
                             UUID categoriaId, UUID categoriaSuperiorId, String nombre, boolean sancionado) {
    }

    /** Club y categoría de un equipo. */
    record EquipoAlineacion(UUID id, UUID clubId, UUID categoriaId) {
    }

    /** Goles del marcador por default y si son valores de demostración. */
    record MarcadorDefault(int golesGanador, int golesPerdedor, boolean esDemo) {
    }
}

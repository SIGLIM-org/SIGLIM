package mx.tecnm.morelia.siglim.programacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Consultas de las reglas de localía (RF-120, VAL-027, RN-049, DEC-33, DEC-34).
 *
 * <p><b>Canchas del club.</b> Un club «tiene cancha» si es propietario de una cancha activa de la
 * liga ({@code propietario = 'CLUB'}) o si tiene horarios en {@code horarios_cancha_club} para la
 * temporada del grupo y la categoría del equipo local, sea cancha ofrecida o en comodato (DEC-33).
 *
 * <p><b>Últimas fechas.</b> Los dos valores salen de la versión vigente de
 * {@code parametros_temporada} con {@code siglim.parametros_vigentes(...)}; nunca se fijan en Java.
 *
 * <p>Refs: B-15.1, RF-120, VAL-027, RN-049, DEC-33, DEC-34
 */
@Repository
class LocaliaRepository {

    private final JdbcClient jdbc;

    LocaliaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Nombre de la cancha y si está en la unidad deportiva ({@code ubicacion}). */
    Optional<CanchaLocalia> buscarCancha(UUID canchaId) {
        return jdbc.sql("""
                        SELECT id, nombre, ubicacion = 'UNIDAD_DEPORTIVA' AS en_unidad_deportiva
                          FROM canchas
                         WHERE id = :id
                        """)
                .param("id", canchaId)
                .query((fila, numero) -> new CanchaLocalia(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getBoolean("en_unidad_deportiva")))
                .optional();
    }

    /**
     * Canchas activas del club local: las suyas y las de sus horarios (oferta o comodato) en la
     * temporada del grupo y la categoría del equipo local (DEC-33). Ordenadas por nombre.
     */
    List<CanchaLocalia> canchasDelClub(UUID clubId, UUID grupoId, UUID equipoLocalId) {
        return jdbc.sql("""
                        SELECT c.id, c.nombre, c.ubicacion = 'UNIDAD_DEPORTIVA' AS en_unidad_deportiva
                          FROM canchas c
                         WHERE c.activa
                           AND ((c.propietario = 'CLUB' AND c.club_id = :clubId)
                                OR EXISTS (SELECT 1
                                             FROM horarios_cancha_club h
                                            WHERE h.cancha_id = c.id
                                              AND h.club_id = :clubId
                                              AND h.temporada_id = siglim.temporada_de_grupo(:grupoId)
                                              AND h.categoria_id = siglim.categoria_de_equipo(:equipoLocalId)))
                         ORDER BY c.nombre, c.id
                        """)
                .param("clubId", clubId)
                .param("grupoId", grupoId)
                .param("equipoLocalId", equipoLocalId)
                .query((fila, numero) -> new CanchaLocalia(
                        fila.getObject("id", UUID.class),
                        fila.getString("nombre"),
                        fila.getBoolean("en_unidad_deportiva")))
                .list();
    }

    /**
     * Número de la jornada, si es de la fase regular, y los parámetros vigentes de las últimas
     * fechas. Sin parámetros en la temporada, los dos valores llegan nulos.
     */
    Optional<FechaDeJornada> buscarFecha(UUID jornadaId) {
        return jdbc.sql("""
                        SELECT j.numero, j.fase_id IS NULL AS fase_regular,
                               p.fechas_fase_regular, p.fechas_finales_unidad_deportiva
                          FROM jornadas j
                          LEFT JOIN LATERAL siglim.parametros_vigentes(siglim.temporada_de_grupo(j.grupo_id)) p
                                 ON true
                         WHERE j.id = :id
                        """)
                .param("id", jornadaId)
                .query((fila, numero) -> new FechaDeJornada(
                        fila.getInt("numero"),
                        fila.getBoolean("fase_regular"),
                        fila.getObject("fechas_fase_regular", Integer.class),
                        fila.getObject("fechas_finales_unidad_deportiva", Integer.class)))
                .optional();
    }

    /** Cancha con su nombre y si está en la unidad deportiva de la liga. */
    record CanchaLocalia(UUID id, String nombre, boolean enUnidadDeportiva) {
    }

    /**
     * Fecha de la jornada y parámetros vigentes de RN-049.
     *
     * @param fechasFaseRegular  total de fechas de la fase regular; {@code null} si no se definió
     * @param fechasFinales      últimas fechas sin canchas externas; {@code null} sin parámetros
     */
    record FechaDeJornada(int numero, boolean faseRegular, Integer fechasFaseRegular, Integer fechasFinales) {
    }
}

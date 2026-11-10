package mx.tecnm.morelia.siglim;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Crea por SQL, como dueño del esquema, lo mínimo para que exista un partido: temporada con
 * parámetros (el trigger calcula {@code fin_bloque} con ellos), categoría, grupo, dos clubes con su
 * equipo y jornada. Cada partido usa datos nuevos, así que solo puede chocar por la cancha.
 *
 * <p>El {@code INSERT} pasa por {@code tg_partidos_programacion}: sirve también para comprobar sus
 * rechazos ({@code VAL-005}).
 */
public final class PartidosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    private final JdbcClient jdbc;

    public PartidosPrueba(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Programa un partido {@code PROGRAMADO} con el bloque de cancha predeterminado (120 min). */
    public UUID programar(UUID canchaId, OffsetDateTime inicio) {
        UUID grupo = crearGrupo();
        UUID jornada = uuid("INSERT INTO siglim.jornadas (grupo_id, numero) VALUES ('%s', 1) RETURNING id"
                .formatted(grupo));
        return jdbc.sql("""
                        INSERT INTO siglim.partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id,
                                                     inicio, fin_bloque)
                        VALUES (:jornada, :local, :visitante, :cancha, :inicio, :inicio)
                        RETURNING id
                        """)
                .param("jornada", jornada)
                .param("local", crearEquipo(grupo, "Local"))
                .param("visitante", crearEquipo(grupo, "Visitante"))
                .param("cancha", canchaId)
                .param("inicio", inicio)
                .query(UUID.class)
                .single();
    }

    private UUID crearGrupo() {
        UUID temporada = uuid("INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio) "
                + "VALUES ('%s', 'Temporada %s', DATE '2030-01-01') RETURNING id".formatted(LIGA, UUID.randomUUID()));
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo) "
                        + "VALUES (:temporada, 1, 'Parámetros de prueba')")
                .param("temporada", temporada)
                .update();
        UUID categoria = uuid("""
                INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                SELECT '%s', 'Categoría %s', 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                RETURNING id
                """.formatted(LIGA, UUID.randomUUID()));
        UUID categoriaTemporada = uuid("INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id) "
                + "VALUES ('%s', '%s') RETURNING id".formatted(temporada, categoria));
        return uuid("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES ('%s', 'A') RETURNING id"
                .formatted(categoriaTemporada));
    }

    private UUID crearEquipo(UUID grupo, String nombre) {
        UUID club = uuid("INSERT INTO siglim.clubes (liga_id, nombre) VALUES ('%s', 'Club %s') RETURNING id"
                .formatted(LIGA, UUID.randomUUID()));
        return uuid("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES ('%s', '%s', '%s') RETURNING id"
                .formatted(club, grupo, nombre));
    }

    /** Solo con valores generados por la propia prueba: no recibe entradas externas. */
    private UUID uuid(String sql) {
        return jdbc.sql(sql).query(UUID.class).single();
    }
}

package mx.tecnm.morelia.siglim.partidos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.EjecutorSistema;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * H-15.2 contra PostgreSQL real: la tarea de inicio con el {@code UPDATE} condicional, RLS y los
 * triggers {@code tg_partidos_estado} y {@code tg_partidos_historial} con el perfil Sistema, y la
 * auditoría.
 *
 * <p>La tarea se construye con los beans reales y un reloj fijo en 2001; los partidos de esta clase
 * empiezan en 2001, así que la tarea no alcanza los que crean las demás pruebas (todos posteriores)
 * en la misma base. Los partidos se preparan por SQL como dueño del esquema, con su estado.
 *
 * <p>Refs: H-15.2, RF-127, RF-045, RN-021, DEC-163, ADR-0009
 */
class InicioPartidosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2001-06-01T12:00:00-06:00");

    @Autowired
    private PartidosRepository partidos;

    @Autowired
    private AuditoriaService auditoria;

    @Autowired
    private MotivoOperacion motivoOperacion;

    @Autowired
    private EjecutorSistema ejecutorSistema;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private SiglimPropiedades propiedades;

    /** Sin la petición simulada de Spring Test, como el hilo del planificador (ver {@link EjecutorSistema}). */
    @BeforeEach
    void comoElHiloDelPlanificador() {
        RequestContextHolder.resetRequestAttributes();
    }

    private InicioPartidosService tarea() {
        return new InicioPartidosService(partidos, auditoria, motivoOperacion, ejecutorSistema, transacciones,
                Clock.fixed(AHORA.toInstant(), ZoneOffset.UTC), propiedades, true);
    }

    @Test
    @DisplayName("RF-127: un partido PROGRAMADO cuya hora llegó pasa a EN_CURSO con historial y auditoría del Sistema")
    void iniciaElLlegado() {
        UUID partido = partido("PUBLICADO", "PROGRAMADO", AHORA.minusMinutes(1));

        tarea().iniciar();

        assertThat(estado(partido)).isEqualTo("EN_CURSO");
        assertThat(historial(partido)).containsExactly(
                "null→PROGRAMADO",
                "PROGRAMADO→EN_CURSO por " + EjecutorSistema.PERFIL_SISTEMA + ": " + InicioPartidosService.MOTIVO);
        assertThat(auditados(partido)).containsExactly(
                "INICIAR_PARTIDO por " + EjecutorSistema.PERFIL_SISTEMA + ": " + InicioPartidosService.MOTIVO);
    }

    @Test
    @DisplayName("ADR-0009 §2: ejecutar dos veces seguidas inicia una sola vez y deja un solo historial y auditoría")
    void dobleEjecucion() {
        UUID partido = partido("PUBLICADO", "PROGRAMADO", AHORA.minusHours(1));
        InicioPartidosService tarea = tarea();

        tarea.iniciar();
        int segunda = tarea.iniciar();

        assertThat(segunda).isZero();
        assertThat(estado(partido)).isEqualTo("EN_CURSO");
        assertThat(historial(partido)).hasSize(2);
        assertThat(auditados(partido)).hasSize(1);
    }

    @Test
    @DisplayName("RF-127: un REPROGRAMADO también inicia a su nueva hora; con la hora exacta ya cuenta como llegado")
    void reprogramadoALaHoraExacta() {
        UUID partido = partido("PUBLICADO", "REPROGRAMADO", AHORA);

        tarea().iniciar();

        assertThat(estado(partido)).isEqualTo("EN_CURSO");
        assertThat(historial(partido)).last().asString().startsWith("REPROGRAMADO→EN_CURSO");
    }

    @Test
    @DisplayName("ADR-0009 §3: un partido de hace semanas se pone al corriente en la primera ejecución")
    void sePoneAlCorriente() {
        UUID partido = partido("PUBLICADO", "PROGRAMADO", AHORA.minusWeeks(3));

        tarea().iniciar();

        assertThat(estado(partido)).isEqualTo("EN_CURSO");
    }

    @Test
    @DisplayName("RF-127: lo que no cumple la regla no cambia ni se audita")
    void noTocaLoDemas() {
        UUID futuro = partido("PUBLICADO", "PROGRAMADO", AHORA.plusMinutes(1));
        UUID borrador = partido("BORRADOR", "PROGRAMADO", AHORA.minusHours(1));
        UUID revision = partido("REVISION", "PROGRAMADO", AHORA.minusHours(1));
        UUID suspendido = partido("PUBLICADO", "SUSPENDIDO", AHORA.minusHours(1));
        UUID cancelado = partido("PUBLICADO", "CANCELADO", AHORA.minusHours(1));

        tarea().iniciar();

        assertThat(List.of(futuro, borrador, revision, suspendido, cancelado)).extracting(this::estado)
                .containsExactly("PROGRAMADO", "PROGRAMADO", "PROGRAMADO", "SUSPENDIDO", "CANCELADO");
        assertThat(List.of(futuro, borrador, revision, suspendido, cancelado)).allSatisfy(
                partido -> assertThat(auditados(partido)).isEmpty());
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    /** Partido nuevo en su propio grupo y cancha, con la jornada y el estado indicados. */
    private UUID partido(String estadoJornada, String estado, OffsetDateTime inicio) {
        UUID grupo = grupo();
        UUID jornada = jdbc.sql("""
                        INSERT INTO siglim.jornadas (grupo_id, numero, estado_publicacion)
                        VALUES (:grupo, 1, CAST(:estado AS siglim.estado_publicacion)) RETURNING id
                        """)
                .param("grupo", grupo).param("estado", estadoJornada).query(UUID.class).single();
        UUID cancha = jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID()).query(UUID.class).single();
        return jdbc.sql("""
                        INSERT INTO siglim.partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id,
                                                     inicio, fin_bloque, estado)
                        VALUES (:jornada, :local, :visitante, :cancha, :inicio, :inicio,
                                CAST(:estado AS siglim.estado_partido))
                        RETURNING id
                        """)
                .param("jornada", jornada)
                .param("local", equipo(grupo))
                .param("visitante", equipo(grupo))
                .param("cancha", cancha)
                .param("inicio", inicio)
                .param("estado", estado)
                .query(UUID.class).single();
    }

    private UUID grupo() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2001-01-01') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID()).query(UUID.class).single();
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo) VALUES (:t, 1, 'Prueba')")
                .param("t", temporada).update();
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Categoría " + UUID.randomUUID()).query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:t, :c) RETURNING id
                        """)
                .param("t", temporada).param("c", categoria).query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'A') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
    }

    private UUID equipo(UUID grupo) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID()).query(UUID.class).single();
        return jdbc.sql("""
                        INSERT INTO siglim.equipos (club_id, grupo_id, nombre)
                        VALUES (:club, :grupo, :nombre) RETURNING id
                        """)
                .param("club", club).param("grupo", grupo).param("nombre", "Equipo " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private String estado(UUID partido) {
        return jdbc.sql("SELECT estado::text FROM siglim.partidos WHERE id = :id")
                .param("id", partido).query(String.class).single();
    }

    /** {@code anterior→nuevo}, y para los cambios con usuario, {@code por <perfil>: <motivo>}. */
    private List<String> historial(UUID partido) {
        return jdbc.sql("""
                        SELECT coalesce(estado_anterior::text, 'null') || '→' || estado_nuevo::text
                               || CASE WHEN cambiado_por IS NULL THEN ''
                                       ELSE ' por ' || cambiado_por || ': ' || motivo END
                          FROM siglim.partido_estado_historial
                         WHERE partido_id = :id
                         ORDER BY id
                        """)
                .param("id", partido).query(String.class).list();
    }

    private List<String> auditados(UUID partido) {
        return jdbc.sql("""
                        SELECT accion || ' por ' || perfil_id || ': ' || motivo
                          FROM siglim.auditoria
                         WHERE modulo = 'M-08' AND entidad = 'partidos' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", partido.toString()).query(String.class).list();
    }
}

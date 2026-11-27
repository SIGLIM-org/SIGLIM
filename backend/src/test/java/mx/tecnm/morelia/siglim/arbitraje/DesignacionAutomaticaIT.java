package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.DesignadorArbitroCentral;
import mx.tecnm.morelia.siglim.programacion.ResultadoCentrales;
import mx.tecnm.morelia.siglim.programacion.ResultadoCentrales.CentralDesignado;
import mx.tecnm.morelia.siglim.programacion.ResultadoCentrales.SinCentral;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H-34.1 contra PostgreSQL real: el puerto {@link DesignadorArbitroCentral} resuelto por Spring
 * ({@link DesignacionAutomatica}), dentro de una transacción como la abrirá la asignación de la fecha
 * (H-34.2), con la matriz de permisos de la migración y {@code tg_designaciones_validar} como garantía
 * final.
 *
 * <p>Para que la elección sea predecible, cada prueba deja como únicos árbitros {@code VALIDADO} de la
 * liga a los que crea: los de otras clases *IT pasan a {@code BAJA} y se restauran al terminar.
 *
 * <p>Refs: H-34.1, RF-023, RF-039, RF-115, VAL-015, VAL-019, DEC-182, DEC-213 a DEC-216
 */
class DesignacionAutomaticaIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime SABADO = OffsetDateTime.parse("2033-03-05T10:00:00-06:00");

    @Autowired
    private DesignadorArbitroCentral designador;

    @Autowired
    private PlatformTransactionManager transacciones;

    private final List<UUID> arbitrosCreados = new ArrayList<>();
    private final List<UUID> clubesCreados = new ArrayList<>();
    private List<UUID> arbitrosAjenos = List.of();

    @BeforeEach
    void aislarArbitros() {
        arbitrosAjenos = jdbc.sql("""
                        UPDATE siglim.arbitros a SET estado = 'BAJA'
                          FROM siglim.clubes cl
                         WHERE cl.id = a.club_id AND cl.liga_id = :liga AND a.estado = 'VALIDADO'
                        RETURNING a.id
                        """).param("liga", LIGA).query(UUID.class).list();
    }

    @AfterEach
    void limpiarDatosCreados() {
        for (UUID arbitro : arbitrosCreados) {
            jdbc.sql("DELETE FROM siglim.designaciones WHERE arbitro_id = :id").param("id", arbitro).update();
            jdbc.sql("DELETE FROM siglim.arbitros WHERE id = :id").param("id", arbitro).update();
        }
        for (UUID club : clubesCreados) {
            jdbc.sql("DELETE FROM siglim.clubes WHERE id = :id").param("id", club).update();
        }
        for (UUID arbitro : arbitrosAjenos) {
            jdbc.sql("UPDATE siglim.arbitros SET estado = 'VALIDADO' WHERE id = :id").param("id", arbitro).update();
        }
        arbitrosCreados.clear();
        clubesCreados.clear();
    }

    @Test
    @DisplayName("DEC-182 · RF-039: cada partido traslapado lleva un central distinto y la carga se reparte (diferencia ≤ 1)")
    void repartoEquitativo() {
        UUID grupo = grupo();
        UUID jornada = jornada(grupo);
        List<UUID> aLaMismaHora = List.of(partido(grupo, jornada, SABADO), partido(grupo, jornada, SABADO),
                partido(grupo, jornada, SABADO));
        List<UUID> despues = List.of(partido(grupo, jornada, SABADO.plusHours(3)),
                partido(grupo, jornada, SABADO.plusHours(3)));
        UUID clubArbitros = club();
        List<UUID> arbitros = List.of(arbitro(clubArbitros), arbitro(clubArbitros), arbitro(clubArbitros));
        UUID perfil = perfilConRol("PROGRAMADOR");
        iniciarSesion(perfil);

        // Desordenados a propósito: el componente los procesa por inicio.
        ResultadoCentrales resultado = designar(List.of(despues.get(1), aLaMismaHora.get(2), despues.get(0),
                aLaMismaHora.get(0), aLaMismaHora.get(1)));

        assertThat(resultado.sinCentral()).isEmpty();
        assertThat(resultado.designados()).hasSize(5);
        assertThat(resultado.designados().subList(0, 3)).extracting(CentralDesignado::partidoId)
                .as("primero los de las 10:00").containsExactlyInAnyOrderElementsOf(aLaMismaHora);
        Map<UUID, UUID> centralPorPartido = resultado.designados().stream()
                .collect(Collectors.toMap(CentralDesignado::partidoId, CentralDesignado::arbitroId));
        assertThat(aLaMismaHora.stream().map(centralPorPartido::get).distinct()).hasSize(3);
        assertThat(despues.stream().map(centralPorPartido::get).distinct()).hasSize(2);
        Map<UUID, Long> carga = centralPorPartido.values().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(carga).containsOnlyKeys(arbitros);
        assertThat(carga.values().stream().mapToLong(Long::longValue).max().orElseThrow()
                - carga.values().stream().mapToLong(Long::longValue).min().orElseThrow()).isLessThanOrEqualTo(1);

        List<Map<String, Object>> filas = jdbc.sql("""
                        SELECT d.id, d.partido_id, d.arbitro_id, d.funcion::text AS funcion, d.automatica,
                               d.designado_por, d.vigente
                          FROM siglim.designaciones d
                         WHERE d.partido_id = ANY(:partidos)
                        """)
                .param("partidos", new SqlArrayValue("uuid", todos(aLaMismaHora, despues).toArray()))
                .query().listOfRows();
        assertThat(filas).hasSize(5).allSatisfy(fila -> {
            assertThat(fila).containsEntry("funcion", "CENTRAL").containsEntry("automatica", true)
                    .containsEntry("designado_por", perfil).containsEntry("vigente", true);
            assertThat(fila.get("arbitro_id")).isEqualTo(centralPorPartido.get((UUID) fila.get("partido_id")));
        });
        for (CentralDesignado designado : resultado.designados()) {
            assertThat(jdbc.sql("""
                            SELECT count(*) FROM siglim.auditoria
                             WHERE modulo = 'M-05' AND accion = 'DESIGNAR_ARBITRO' AND entidad = 'designaciones'
                               AND entidad_id = :id AND perfil_id = :perfil AND detalle ->> 'automatica' = 'true'
                            """)
                    .param("id", designado.designacionId().toString()).param("perfil", perfil)
                    .query(Long.class).single()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("VAL-019 · DEC-216: el árbitro del club local nunca se elige; si es el único, el partido queda sin central")
    void arbitroDelClubLocal() {
        UUID grupo = grupo();
        UUID partido = partido(grupo, jornada(grupo), SABADO.plusDays(1));
        UUID delClubLocal = arbitro(clubLocal(partido));
        iniciarSesion(perfilConRol("PROGRAMADOR"));

        ResultadoCentrales soloEse = designar(List.of(partido));

        assertThat(soloEse.designados()).isEmpty();
        assertThat(soloEse.sinCentral()).containsExactly(new SinCentral(partido, "RF-023",
                "El único árbitro validado no puede pitarlo: 1 es de los clubes del partido (VAL-019)."));
        assertThat(vigentes(partido)).isZero();

        UUID ajeno = arbitro(club());
        ResultadoCentrales conOtro = designar(List.of(partido));

        assertThat(conOtro.sinCentral()).isEmpty();
        assertThat(conOtro.designados()).singleElement().satisfies(d -> {
            assertThat(d.arbitroId()).isEqualTo(ajeno).isNotEqualTo(delClubLocal);
            assertThat(d.advertencias()).isEmpty();
        });
    }

    @Test
    @DisplayName("DEC-213: un partido con central manual se omite y su central sigue vigente")
    void omiteCentralManual() {
        UUID grupo = grupo();
        UUID jornada = jornada(grupo);
        UUID conCentral = partido(grupo, jornada, SABADO.plusDays(2));
        UUID sinCentral = partido(grupo, jornada, SABADO.plusDays(2).plusHours(3));
        UUID clubArbitros = club();
        UUID manual = arbitro(clubArbitros);
        UUID otro = arbitro(clubArbitros);
        UUID designacionManual = jdbc.sql("""
                        INSERT INTO siglim.designaciones (partido_id, arbitro_id, funcion, designado_por, automatica)
                        VALUES (:partido, :arbitro, 'CENTRAL', :perfil, false) RETURNING id
                        """)
                .param("partido", conCentral).param("arbitro", manual).param("perfil", crearPerfil())
                .query(UUID.class).single();
        iniciarSesion(perfilConRol("PROGRAMADOR"));

        ResultadoCentrales resultado = designar(List.of(conCentral, sinCentral));

        assertThat(resultado.sinCentral()).isEmpty();
        assertThat(resultado.designados()).singleElement().satisfies(d -> {
            assertThat(d.partidoId()).isEqualTo(sinCentral);
            assertThat(d.arbitroId()).as("el de la designación manual ya tiene carga 1").isEqualTo(otro);
        });
        assertThat(jdbc.sql("SELECT id FROM siglim.designaciones WHERE partido_id = :id AND vigente")
                .param("id", conCentral).query(UUID.class).list()).containsExactly(designacionManual);
    }

    @Test
    @DisplayName("RF-039 · DEC-214: un choque de la misma corrida gana a la menor carga")
    void choqueDeLaMismaCorrida() {
        UUID grupo = grupo();
        UUID jornada = jornada(grupo);
        // Mismo inicio: el orden de proceso lo decide el id por texto.
        List<UUID> simultaneos = new ArrayList<>(List.of(partido(grupo, jornada, SABADO.plusDays(4)),
                partido(grupo, jornada, SABADO.plusDays(4))));
        simultaneos.sort(Comparator.comparing(UUID::toString));
        UUID clubArbitros = club();
        UUID a = arbitro(clubArbitros);
        UUID b = arbitro(clubArbitros);
        // B con carga 3 en la misma temporada, en partidos que no chocan con los simultáneos.
        for (OffsetDateTime inicio : List.of(SABADO.plusDays(5), SABADO.plusDays(5).plusHours(3),
                SABADO.plusDays(5).plusHours(6))) {
            jdbc.sql("""
                            INSERT INTO siglim.designaciones (partido_id, arbitro_id, funcion, designado_por)
                            VALUES (:partido, :arbitro, 'CENTRAL', :perfil)
                            """)
                    .param("partido", partido(grupo, jornada, inicio)).param("arbitro", b)
                    .param("perfil", crearPerfil()).update();
        }
        iniciarSesion(perfilConRol("PROGRAMADOR"));

        ResultadoCentrales resultado = designar(List.of(simultaneos.get(1), simultaneos.get(0)));

        assertThat(resultado.sinCentral()).isEmpty();
        assertThat(resultado.designados()).extracting(CentralDesignado::partidoId)
                .containsExactlyElementsOf(simultaneos);
        assertThat(resultado.designados().get(0).arbitroId()).as("menor carga").isEqualTo(a);
        assertThat(resultado.designados().get(1).arbitroId())
                .as("A ya pita a esa hora en esta misma corrida (RF-039)").isEqualTo(b);
        assertThat(jdbc.sql("""
                        SELECT automatica FROM siglim.designaciones
                         WHERE partido_id = :partido AND arbitro_id = :arbitro AND vigente AND funcion = 'CENTRAL'
                        """)
                .param("partido", simultaneos.get(1)).param("arbitro", b)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    @DisplayName("DEC-182 · VAL-015: el Programador designa sin designacion:EDITAR y la Comisión, sin AUTOMATICA, recibe 403")
    void permisos() {
        UUID grupo = grupo();
        UUID partido = partido(grupo, jornada(grupo), SABADO.plusDays(3));
        UUID arbitro = arbitro(club());

        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        assertThatThrownBy(() -> designar(List.of(partido)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.codigo()).isEqualTo("VAL-015");
                });
        assertThat(vigentes(partido)).isZero();

        iniciarSesion(perfilConRol("PROGRAMADOR"));
        assertThat(designar(List.of(partido)).designados()).extracting(CentralDesignado::arbitroId)
                .containsExactly(arbitro);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    /** Como lo llamará la asignación de la fecha: dentro de su transacción y con la sesión del hilo. */
    private ResultadoCentrales designar(List<UUID> partidos) {
        return new TransactionTemplate(transacciones).execute(estado -> designador.designarCentrales(partidos));
    }

    @SafeVarargs
    private static List<UUID> todos(Collection<UUID>... grupos) {
        List<UUID> todos = new ArrayList<>();
        for (Collection<UUID> grupo : grupos) {
            todos.addAll(grupo);
        }
        return todos;
    }

    /** Temporada con parámetros (bloque de 120 min), categoría y grupo: la carga se cuenta en ella. */
    private UUID grupo() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2033-01-01') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo) "
                        + "VALUES (:temporada, 1, 'Parámetros de prueba')")
                .param("temporada", temporada).update();
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Categoría " + UUID.randomUUID())
                .query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria) RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria)
                .query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'A') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
    }

    private UUID jornada(UUID grupo) {
        return jdbc.sql("INSERT INTO siglim.jornadas (grupo_id, numero) VALUES (:grupo, 1) RETURNING id")
                .param("grupo", grupo).query(UUID.class).single();
    }

    /** Partido {@code PROGRAMADO} en su propia cancha y con equipos (y clubes) nuevos. */
    private UUID partido(UUID grupo, UUID jornada, OffsetDateTime inicio) {
        UUID cancha = jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID())
                .query(UUID.class).single();
        return jdbc.sql("""
                        INSERT INTO siglim.partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id,
                                                     inicio, fin_bloque)
                        VALUES (:jornada, :local, :visitante, :cancha, :inicio, :inicio)
                        RETURNING id
                        """)
                .param("jornada", jornada)
                .param("local", equipo(grupo, "Local"))
                .param("visitante", equipo(grupo, "Visitante"))
                .param("cancha", cancha)
                .param("inicio", inicio)
                .query(UUID.class).single();
    }

    private UUID equipo(UUID grupo, String papel) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID())
                .query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre) RETURNING id")
                .param("club", club).param("grupo", grupo).param("nombre", papel + " " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID clubLocal(UUID partido) {
        return jdbc.sql("""
                        SELECT e.club_id FROM siglim.partidos p JOIN siglim.equipos e ON e.id = p.equipo_local_id
                         WHERE p.id = :id
                        """).param("id", partido).query(UUID.class).single();
    }

    private long vigentes(UUID partido) {
        return jdbc.sql("SELECT count(*) FROM siglim.designaciones WHERE partido_id = :id AND vigente")
                .param("id", partido).query(Long.class).single();
    }

    /** Árbitro {@code VALIDADO} con una persona nueva. */
    private UUID arbitro(UUID club) {
        UUID id = jdbc.sql("""
                        INSERT INTO siglim.arbitros (persona_id, club_id, estado)
                        VALUES (:persona, :club, 'VALIDADO') RETURNING id
                        """)
                .param("persona", persona()).param("club", club)
                .query(UUID.class).single();
        arbitrosCreados.add(id);
        return id;
    }

    private UUID club() {
        UUID id = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Árbitros " + UUID.randomUUID())
                .query(UUID.class).single();
        clubesCreados.add(id);
        return id;
    }

    /** Persona con una CURP válida y única (RF-010, DEC-10). */
    private UUID persona() {
        String sufijo = "" + letra() + letra() + letra() + letra();
        return jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, fecha_nacimiento)
                        VALUES (:curp, 'Ramón', 'Automático', :nacimiento) RETURNING id
                        """)
                .param("curp", "AURA850505HMN" + sufijo.substring(0, 3) + sufijo.charAt(3) + "7")
                .param("nacimiento", LocalDate.of(1985, 5, 5))
                .query(UUID.class).single();
    }

    private static char letra() {
        return (char) ('A' + ThreadLocalRandom.current().nextInt(26));
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado. */
    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
        return perfil;
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}

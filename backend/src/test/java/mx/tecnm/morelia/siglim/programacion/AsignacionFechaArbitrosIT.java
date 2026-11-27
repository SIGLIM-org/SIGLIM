package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionService;
import mx.tecnm.morelia.siglim.arbitraje.DesignarArbitroRequest;
import mx.tecnm.morelia.siglim.arbitraje.FuncionArbitral;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.programacion.VerificacionFechaDto.Aviso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H-34.2 contra PostgreSQL real: {@code POST /fechas/{id}/asignacion} designa el árbitro central
 * automático con {@code arbitraje.designacion:AUTOMATICA} (DEC-217 a DEC-219) y la verificación de la
 * fecha explica por qué un partido sigue sin central (DEC-220).
 *
 * <p>Escenario: el de {@link AsignacionFechaIT} (temporada que empieza el sábado 3 de marzo de 2035,
 * «Libre» el domingo y «Veteranos» el sábado, un grupo de 4 equipos cada una y dos canchas de la unidad
 * deportiva). Cada prueba deja como únicos árbitros {@code VALIDADO} de la liga a los que crea, como
 * {@code DesignacionAutomaticaIT}.
 *
 * <p>Refs: H-34.2, RF-022, RF-023, RF-039, RF-041, RF-093, VAL-019, DEC-182, DEC-214, DEC-216, DEC-217,
 * DEC-218, DEC-219, DEC-220
 */
class AsignacionFechaArbitrosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final LocalDate INICIO = LocalDate.parse("2035-03-03");
    private static final String MOTIVO = "Se regeneró la fecha por el torneo relámpago";
    private static final AsignarFechaRequest PENDIENTES = new AsignarFechaRequest(ModoAsignacion.PENDIENTES, null);

    @Autowired
    private CalendarioService calendario;

    @Autowired
    private AsignacionFechaService asignacion;

    @Autowired
    private FechaService fechas;

    @Autowired
    private DesignacionService designacionService;

    @Autowired
    private PlatformTransactionManager transacciones;

    private final List<UUID> arbitrosCreados = new ArrayList<>();
    private final List<UUID> clubesCreados = new ArrayList<>();
    private final List<UUID> rolesCreados = new ArrayList<>();
    private List<UUID> arbitrosAjenos = List.of();

    @BeforeEach
    void aislarArbitros() {
        arbitrosAjenos = aislarArbitrosValidados(LIGA);
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
        for (UUID rol : rolesCreados) {
            jdbc.sql("DELETE FROM siglim.asignaciones_rol WHERE rol_id = :id").param("id", rol).update();
            jdbc.sql("DELETE FROM siglim.rol_permisos WHERE rol_id = :id").param("id", rol).update();
            jdbc.sql("DELETE FROM siglim.roles WHERE id = :id").param("id", rol).update();
        }
        restaurarArbitros(arbitrosAjenos);
        arbitrosCreados.clear();
        clubesCreados.clear();
        rolesCreados.clear();
    }

    @Test
    @DisplayName("DEC-217 · VAL-019 · RF-039: PENDIENTES designa un central automático por partido, sin choques y con la carga repartida")
    void designaTodos() {
        Escenario e = escenario(true);
        UUID clubAjeno = club();
        List<UUID> ajenos = List.of(arbitro(clubAjeno), arbitro(clubAjeno), arbitro(clubAjeno));
        UUID clubDeEquipo = jdbc.sql("""
                        SELECT e.club_id FROM siglim.equipos e
                          JOIN siglim.grupos g ON g.id = e.grupo_id
                          JOIN siglim.categorias_temporada ct ON ct.id = g.categoria_temporada_id
                         WHERE ct.temporada_id = :temporada
                         ORDER BY e.id LIMIT 1
                        """).param("temporada", e.temporada()).query(UUID.class).single();
        UUID delClub = arbitro(clubDeEquipo);

        AsignacionFechaDto resultado = asignacion.asignar(e.fecha(), PENDIENTES);

        assertThat(resultado.totales()).isEqualTo(new AsignacionFechaDto.Totales(4, 0, 0, 4, 0));
        assertThat(resultado.sinArbitro()).isEmpty();
        List<FechaDto.FilaPartido> partidos = partidos(resultado.fecha());
        assertThat(partidos).hasSize(4).allSatisfy(partido -> {
            assertThat(partido.arbitro()).as("la respuesta ya trae el central").isNotNull();
            assertThat(partido.designacionAutomatica()).isTrue();
        });
        List<UUID> ids = partidos.stream().map(FechaDto.FilaPartido::id).toList();
        List<Map<String, Object>> designaciones = vigentes(ids);
        assertThat(designaciones).hasSize(4).allSatisfy(fila -> assertThat(fila)
                .containsEntry("funcion", "CENTRAL").containsEntry("automatica", true)
                .containsEntry("designado_por", e.programador()));
        assertThat(designaciones).extracting(fila -> fila.get("partido_id")).containsExactlyInAnyOrderElementsOf(ids);

        assertThat(jdbc.sql("""
                        SELECT count(*) FROM siglim.designaciones d
                          JOIN siglim.partidos p ON p.id = d.partido_id
                          JOIN siglim.equipos l ON l.id = p.equipo_local_id
                          JOIN siglim.equipos v ON v.id = p.equipo_visitante_id
                         WHERE d.arbitro_id = :arbitro AND d.vigente AND :club IN (l.club_id, v.club_id)
                        """).param("arbitro", delClub).param("club", clubDeEquipo).query(Long.class).single())
                .as("VAL-019: nunca pita a su club").isZero();
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM siglim.designaciones a
                          JOIN siglim.partidos pa ON pa.id = a.partido_id
                          JOIN siglim.designaciones b ON b.arbitro_id = a.arbitro_id AND b.id <> a.id AND b.vigente
                          JOIN siglim.partidos pb ON pb.id = b.partido_id
                         WHERE a.vigente AND a.partido_id = ANY(:partidos) AND b.partido_id = ANY(:partidos)
                           AND tstzrange(pa.inicio, pa.fin_bloque) && tstzrange(pb.inicio, pb.fin_bloque)
                        """).param("partidos", new SqlArrayValue("uuid", ids.toArray())).query(Long.class).single())
                .as("RF-039: ningún árbitro en dos partidos traslapados").isZero();
        Map<Object, Long> carga = designaciones.stream()
                .collect(Collectors.groupingBy(fila -> fila.get("arbitro_id"), Collectors.counting()));
        List<Long> cargas = new ArrayList<>();
        for (UUID arbitro : todos(ajenos, List.of(delClub))) {
            cargas.add(carga.getOrDefault(arbitro, 0L));
        }
        assertThat(cargas.stream().mapToLong(Long::longValue).max().orElseThrow()
                - cargas.stream().mapToLong(Long::longValue).min().orElseThrow()).as("DEC-214").isLessThanOrEqualTo(1);

        assertThat(fechas.verificar(e.fecha()).advertencias()).extracting(Aviso::codigo).doesNotContain("RF-023");
        assertThat(jdbc.sql("""
                        SELECT (parametros_generacion ->> 'designacionAutomatica')::boolean
                               AND (parametros_generacion ->> 'centrales')::int = 4
                               AND (parametros_generacion ->> 'sinArbitro')::int = 0
                          FROM siglim.fechas_programacion WHERE id = :fecha
                        """).param("fecha", e.fecha()).query(Boolean.class).single()).as("RF-041, DEC-219").isTrue();
        assertThat(jdbc.sql("""
                        SELECT (detalle ->> 'designacionAutomatica')::boolean AND (detalle ->> 'centrales')::int = 4
                          FROM siglim.auditoria
                         WHERE accion = 'GENERAR_FECHA' AND entidad_id = :fecha
                        """).param("fecha", e.fecha().toString()).query(Boolean.class).single()).isTrue();
        for (Map<String, Object> fila : designaciones) {
            assertThat(jdbc.sql("""
                            SELECT count(*) FROM siglim.auditoria
                             WHERE modulo = 'M-05' AND accion = 'DESIGNAR_ARBITRO' AND entidad_id = :id
                               AND perfil_id = :perfil AND detalle ->> 'automatica' = 'true'
                            """)
                    .param("id", fila.get("id").toString()).param("perfil", e.programador())
                    .query(Long.class).single()).as("RF-093: una auditoría por designación").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("DEC-216 · DEC-220: con un solo árbitro, el partido simultáneo queda sin central con su causa, no bloquea el envío y lo recibe al repetir PENDIENTES")
    void unSoloArbitro() {
        Escenario e = escenario(false, LocalTime.of(10, 0));
        UUID clubAjeno = club();
        UUID unico = arbitro(clubAjeno);

        AsignacionFechaDto primera = asignacion.asignar(e.fecha(), PENDIENTES);

        List<FechaDto.FilaPartido> partidos = partidos(primera.fecha());
        assertThat(partidos).hasSize(2);
        assertThat(partidos.get(0).inicio().toInstant()).as("precondición: los dos a la misma hora")
                .isEqualTo(partidos.get(1).inicio().toInstant());
        String causa = "El único árbitro validado no puede pitarlo: 1 ya pita a esa hora (RF-039).";
        assertThat(primera.totales()).isEqualTo(new AsignacionFechaDto.Totales(2, 0, 0, 1, 1));
        AsignacionFechaDto.SinArbitro sinArbitro = primera.sinArbitro().getFirst();
        assertThat(primera.sinArbitro()).containsExactly(new AsignacionFechaDto.SinArbitro(sinArbitro.partidoId(),
                "RF-023", causa));
        UUID conCentral = partidos.stream().map(FechaDto.FilaPartido::id)
                .filter(id -> !id.equals(sinArbitro.partidoId())).findFirst().orElseThrow();
        assertThat(centralVigente(conCentral)).isEqualTo(unico);

        assertThat(fechas.verificar(e.fecha()).advertencias())
                .filteredOn(aviso -> "RF-023".equals(aviso.codigo()))
                .singleElement().satisfies(aviso -> {
                    assertThat(aviso.partidoId()).isEqualTo(sinArbitro.partidoId());
                    assertThat(aviso.mensaje()).startsWith("Sin árbitro central: ")
                            .contains(". " + causa + " La Comisión de Arbitraje lo designa en F-24.");
                });

        // Criterio 3: «Sin árbitro» no bloquea el envío. Se envía en una transacción que se revierte para
        // seguir asignando la fecha en borrador.
        FechaDto enviada = new TransactionTemplate(transacciones).execute(estado -> {
            FechaDto dto = fechas.enviarAAprobacion(e.fecha());
            estado.setRollbackOnly();
            return dto;
        });
        assertThat(enviada.estadoPublicacion()).isEqualTo(EstadoJornada.REVISION);
        assertThat(fechas.consultar(e.fecha()).estadoPublicacion()).isEqualTo(EstadoJornada.BORRADOR);

        UUID otro = arbitro(clubAjeno);
        AsignacionFechaDto otraVez = asignacion.asignar(e.fecha(), PENDIENTES);

        assertThat(otraVez.totales()).as("DEC-217: los existentes también reciben central")
                .isEqualTo(new AsignacionFechaDto.Totales(0, 0, 0, 1, 0));
        assertThat(otraVez.sinArbitro()).isEmpty();
        assertThat(centralVigente(sinArbitro.partidoId())).isEqualTo(otro);
        assertThat(centralVigente(conCentral)).isEqualTo(unico);
    }

    @Test
    @DisplayName("DEC-181 · DEC-217: REGENERAR retira las designaciones automáticas con el motivo y repite los mismos árbitros por horario")
    void regenerar() {
        Escenario e = escenario(true);
        UUID clubAjeno = club();
        List<UUID> ajenos = List.of(arbitro(clubAjeno), arbitro(clubAjeno), arbitro(clubAjeno));

        AsignacionFechaDto primera = asignacion.asignar(e.fecha(), PENDIENTES);
        assertThat(primera.totales()).isEqualTo(new AsignacionFechaDto.Totales(4, 0, 0, 4, 0));
        List<FechaDto.FilaPartido> antes = partidos(primera.fecha());
        List<Map<String, Object>> designacionesAntes = vigentes(antes.stream().map(FechaDto.FilaPartido::id).toList());

        AsignacionFechaDto regenerada = asignacion.asignar(e.fecha(),
                new AsignarFechaRequest(ModoAsignacion.REGENERAR, MOTIVO));

        assertThat(regenerada.totales()).isEqualTo(new AsignacionFechaDto.Totales(4, 0, 4, 4, 0));
        for (Map<String, Object> anterior : designacionesAntes) {
            assertThat(jdbc.sql("SELECT NOT vigente AND motivo_cambio = :motivo FROM siglim.designaciones WHERE id = :id")
                    .param("id", anterior.get("id")).param("motivo", MOTIVO).query(Boolean.class).single())
                    .as("criterio 6: sin vigencia y con el motivo").isTrue();
        }
        List<FechaDto.FilaPartido> despues = partidos(regenerada.fecha());
        assertThat(vigentes(despues.stream().map(FechaDto.FilaPartido::id).toList())).hasSize(4)
                .allSatisfy(fila -> assertThat(fila).containsEntry("automatica", true));
        assertThat(despues).allSatisfy(partido -> assertThat(ajenos).contains(partido.arbitro().id()));
        // Determinismo (DEC-214): a cada horario le tocan los mismos árbitros. Entre partidos simultáneos
        // el desempate es el id del partido, que es nuevo al regenerar, así que se compara por horario.
        assertThat(arbitrosPorHorario(despues)).isEqualTo(arbitrosPorHorario(antes));
    }

    @Test
    @DisplayName("DEC-218: sin arbitraje.designacion:AUTOMATICA la fecha se asigna sin designaciones y con la advertencia")
    void sinPermisoDeDesignacionAutomatica() {
        Escenario e = escenario(true);
        arbitro(club());
        UUID rol = rolProgramadorSinAutomatica();
        UUID perfil = iniciarSesion(perfilConRol(codigoDe(rol)));

        AsignacionFechaDto resultado = asignacion.asignar(e.fecha(), PENDIENTES);

        assertThat(resultado.totales()).isEqualTo(new AsignacionFechaDto.Totales(4, 0, 0, 0, 0));
        assertThat(resultado.sinArbitro()).isEmpty();
        assertThat(resultado.advertencias()).contains(AsignacionFechaService.SIN_DESIGNACION_AUTOMATICA);
        assertThat(vigentes(partidos(resultado.fecha()).stream().map(FechaDto.FilaPartido::id).toList())).isEmpty();
        assertThat(jdbc.sql("""
                        SELECT NOT (parametros_generacion ->> 'designacionAutomatica')::boolean
                               AND (parametros_generacion ->> 'centrales')::int = 0
                               AND generada_por = :perfil
                          FROM siglim.fechas_programacion WHERE id = :fecha
                        """).param("fecha", e.fecha()).param("perfil", perfil).query(Boolean.class).single()).isTrue();
    }

    @Test
    @DisplayName("DEC-213 · DEC-217: un central manual de la Comisión se respeta al repetir PENDIENTES")
    void respetaCentralManual() {
        Escenario e = escenario(true);
        AsignacionFechaDto sinArbitros = asignacion.asignar(e.fecha(), PENDIENTES);
        assertThat(sinArbitros.totales()).isEqualTo(new AsignacionFechaDto.Totales(4, 0, 0, 0, 4));
        assertThat(sinArbitros.sinArbitro()).allSatisfy(sin -> assertThat(sin.causa())
                .isEqualTo("No hay árbitros validados en la liga."));
        UUID clubAjeno = club();
        UUID manual = arbitro(clubAjeno);
        arbitro(clubAjeno);
        UUID partidoManual = partidos(sinArbitros.fecha()).getFirst().id();
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        UUID designacionManual = designacionService.designar(partidoManual,
                new DesignarArbitroRequest(manual, FuncionArbitral.CENTRAL, null)).id();

        iniciarSesion(e.programador());
        AsignacionFechaDto otraVez = asignacion.asignar(e.fecha(), PENDIENTES);

        assertThat(otraVez.totales()).isEqualTo(new AsignacionFechaDto.Totales(0, 0, 0, 3, 0));
        assertThat(jdbc.sql("""
                        SELECT id FROM siglim.designaciones WHERE partido_id = :partido AND vigente AND funcion = 'CENTRAL'
                        """).param("partido", partidoManual).query(UUID.class).list()).containsExactly(designacionManual);
        assertThat(jdbc.sql("SELECT automatica FROM siglim.designaciones WHERE id = :id")
                .param("id", designacionManual).query(Boolean.class).single()).isFalse();
        assertThat(vigentes(partidos(otraVez.fecha()).stream().map(FechaDto.FilaPartido::id).toList()))
                .hasSize(4)
                .filteredOn(fila -> !partidoManual.equals(fila.get("partido_id")))
                .hasSize(3).allSatisfy(fila -> assertThat(fila).containsEntry("automatica", true));
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private static List<FechaDto.FilaPartido> partidos(FechaDto fecha) {
        return fecha.bloques().stream().flatMap(bloque -> bloque.partidos().stream()).toList();
    }

    /** Por cada inicio, los árbitros centrales ordenados por su id. */
    private static Map<Instant, List<String>> arbitrosPorHorario(List<FechaDto.FilaPartido> partidos) {
        return partidos.stream().collect(Collectors.groupingBy(p -> p.inicio().toInstant(),
                Collectors.mapping(p -> p.arbitro().id().toString(),
                        Collectors.collectingAndThen(Collectors.toList(), lista -> lista.stream().sorted().toList()))));
    }

    @SafeVarargs
    private static List<UUID> todos(List<UUID>... listas) {
        List<UUID> todos = new ArrayList<>();
        for (List<UUID> lista : listas) {
            todos.addAll(lista);
        }
        return todos;
    }

    private List<Map<String, Object>> vigentes(List<UUID> partidos) {
        return jdbc.sql("""
                        SELECT d.id, d.partido_id, d.arbitro_id, d.funcion::text AS funcion, d.automatica,
                               d.designado_por
                          FROM siglim.designaciones d
                         WHERE d.partido_id = ANY(:partidos) AND d.vigente
                        """)
                .param("partidos", new SqlArrayValue("uuid", partidos.toArray()))
                .query().listOfRows();
    }

    private UUID centralVigente(UUID partido) {
        return jdbc.sql("""
                        SELECT arbitro_id FROM siglim.designaciones
                         WHERE partido_id = :partido AND vigente AND funcion = 'CENTRAL'
                        """).param("partido", partido).query(UUID.class).single();
    }

    private record Escenario(UUID programador, UUID temporada, UUID fecha) {
    }

    /**
     * Temporada con parámetros, «Libre» (domingo) y, si se pide, «Veteranos» (sábado), cada una con un
     * grupo de 4 equipos, dos canchas, el calendario generado y la sesión del Programador.
     */
    private Escenario escenario(boolean conVeteranos) {
        return escenario(conVeteranos, LocalTime.of(18, 0));
    }

    /** Con {@code finDeJuego} a las 10:00 solo cabe un bloque por cancha: los partidos del día coinciden. */
    private Escenario escenario(boolean conVeteranos, LocalTime finDeJuego) {
        UUID programador = iniciarSesion(perfilConRol("PROGRAMADOR"));
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, :inicio) RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID()).param("inicio", INICIO)
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo, hora_fin_juego)
                        VALUES (:temporada, 1, 'Parámetros de prueba', :fin)
                        """)
                .param("temporada", temporada).param("fin", finDeJuego).update();
        crearGrupo(crearCategoriaTemporada(temporada, "Libre", "LIBRE"), 4);
        if (conVeteranos) {
            crearGrupo(crearCategoriaTemporada(temporada, "Veteranos", "VETERANOS"), 4);
        }
        for (int i = 0; i < 2; i++) {
            jdbc.sql("""
                            INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                            VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA')
                            """)
                    .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID()).update();
        }
        calendario.generar(temporada, null);
        UUID fecha = fechas.listar(temporada, 0, 20).contenido().getFirst().id();
        return new Escenario(programador, temporada, fecha);
    }

    private UUID crearCategoriaTemporada(UUID temporada, String prefijo, String tipo) {
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, CAST(:tipo AS siglim.tipo_categoria), coalesce(max(orden), 0) + 1
                          FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", prefijo + " " + UUID.randomUUID()).param("tipo", tipo)
                .query(UUID.class).single();
        return jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria) RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria)
                .query(UUID.class).single();
    }

    private void crearGrupo(UUID categoriaTemporada, int equipos) {
        UUID grupo = jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'Grupo 1') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
        for (int i = 0; i < equipos; i++) {
            UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                    .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID())
                    .query(UUID.class).single();
            jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre)")
                    .param("club", club).param("grupo", grupo).param("nombre", "Equipo " + UUID.randomUUID())
                    .update();
        }
    }

    /** Árbitro {@code VALIDADO} con una persona nueva; se borra al terminar. */
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

    /** Club solo con árbitros; se borra al terminar. */
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
                        VALUES (:curp, 'Ramón', 'Asignado', :nacimiento) RETURNING id
                        """)
                .param("curp", "AURA850505HMN" + sufijo.substring(0, 3) + sufijo.charAt(3) + "7")
                .param("nacimiento", LocalDate.of(1985, 5, 5))
                .query(UUID.class).single();
    }

    private static char letra() {
        return (char) ('A' + ThreadLocalRandom.current().nextInt(26));
    }

    /** Rol de prueba con los permisos de PROGRAMADOR menos {@code arbitraje.designacion:AUTOMATICA}. */
    private UUID rolProgramadorSinAutomatica() {
        UUID rol = jdbc.sql("""
                        INSERT INTO siglim.roles (liga_id, codigo, nombre, ambito_predeterminado)
                        VALUES (:liga, :codigo, 'Programador sin designación automática', 'LIGA') RETURNING id
                        """)
                .param("liga", LIGA).param("codigo", "PRUEBA_" + UUID.randomUUID().toString().substring(0, 8))
                .query(UUID.class).single();
        rolesCreados.add(rol);
        jdbc.sql("""
                        INSERT INTO siglim.rol_permisos (rol_id, permiso_id)
                        SELECT :rol, rp.permiso_id
                          FROM siglim.rol_permisos rp
                          JOIN siglim.roles r ON r.id = rp.rol_id
                          JOIN siglim.permisos p ON p.id = rp.permiso_id
                         WHERE r.codigo = 'PROGRAMADOR' AND p.codigo <> 'arbitraje.designacion:AUTOMATICA'
                        """).param("rol", rol).update();
        return rol;
    }

    private String codigoDe(UUID rol) {
        return jdbc.sql("SELECT codigo FROM siglim.roles WHERE id = :id").param("id", rol).query(String.class).single();
    }

    private UUID iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
        return perfil;
    }

    /** Perfil con cuenta y una asignación del rol en ámbito de liga. */
    private UUID perfilConRol(String codigoRol) {
        UUID usuarioAuth = UUID.randomUUID();
        jdbc.sql("INSERT INTO auth.users (id, email, aud, role) VALUES (:id, :correo, 'authenticated', 'authenticated')")
                .param("id", usuarioAuth).param("correo", usuarioAuth + "@prueba.mx").update();
        UUID perfil = jdbc.sql("""
                        INSERT INTO siglim.perfiles (usuario_auth_id, nombre_mostrado, correo)
                        VALUES (:usuario, :nombre, :correo)
                        RETURNING id
                        """)
                .param("usuario", usuarioAuth).param("nombre", codigoRol + " " + usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, r.id, r.liga_id, 'LIGA' FROM siglim.roles r WHERE r.codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", codigoRol).update();
        return perfil;
    }
}

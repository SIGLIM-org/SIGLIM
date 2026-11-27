package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-33.3: la asignación automática de una fecha multicategoría contra PostgreSQL real (CA-002). Varias
 * categorías que juegan sábado y domingo, grupos que comparten canchas, una cancha bloqueada y un
 * equipo sancionado: la asignación termina y la verificación de la fecha no encuentra conflictos duros.
 *
 * <p>Escenario: temporada que empieza el sábado 4 de marzo de 2034, con los parámetros por defecto
 * (08:00 a 18:00 y bloque de 120 min) y 18 fechas de fase regular, así que la fecha 1 no es de las
 * últimas que van a la unidad deportiva. «Libre» juega el domingo con un grupo de 4 y otro de 5 equipos
 * (con descanso); «Veteranos» e «Infantil» juegan el sábado con un grupo de 4 cada una. Dos canchas de
 * la unidad deportiva y, para un local de «Libre», la cancha de su club bloqueada de 08:00 a 12:00 del
 * domingo. Un visitante de «Veteranos» tiene una suspensión vigente. Sin árbitros {@code VALIDADO}
 * (los de otras clases pasan a {@code BAJA} mientras corre): los partidos quedan sin central (H-34.2).
 *
 * <p>Refs: H-33.3, CA-002, RF-032, RF-035, RF-039, VAL-003, VAL-004, VAL-005, VAL-006, DEC-176,
 * DEC-177, DEC-178, DEC-179, DEC-180, DEC-207
 */
class AsignacionMulticategoriaIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDate SABADO = LocalDate.parse("2034-03-04");
    private static final LocalDate DOMINGO = SABADO.plusDays(1);

    @Autowired
    private CalendarioService calendario;

    @Autowired
    private AsignacionFechaService asignacion;

    @Autowired
    private FechaService fechas;

    private List<UUID> arbitrosAjenos = List.of();

    @BeforeEach
    void aislarArbitros() {
        arbitrosAjenos = aislarArbitrosValidados(LIGA);
    }

    @AfterEach
    void devolverArbitros() {
        restaurarArbitros(arbitrosAjenos);
    }

    @Test
    @DisplayName("CA-002: varias categorías con canchas compartidas, una cancha bloqueada y un equipo sancionado se asignan sin conflictos duros")
    void asignarFechaMulticategoria() {
        Escenario e = escenario();
        AsignacionFechaDto resultado = asignacion.asignar(e.fecha(),
                new AsignarFechaRequest(ModoAsignacion.PENDIENTES, null));

        // La asignación termina: 8 partidos en la fecha (2 + 2 + 2 + 2), uno sin asignar por la sanción.
        assertThat(resultado.totales()).isEqualTo(new AsignacionFechaDto.Totales(7, 1, 0, 0, 7));
        assertThat(resultado.fecha().jornadas()).as("una jornada por grupo").isEqualTo(4);
        assertThat(resultado.fecha().descansos()).as("el grupo de 5 descansa a uno").isEqualTo(1);

        // CA-002: la verificación de la fecha, con las reglas de H-12, no encuentra conflictos duros.
        assertThat(fechas.verificar(e.fecha()).conflictos()).isEmpty();

        // VAL-006: el partido del equipo sancionado queda sin asignar con su causa y sin partido.
        assertThat(resultado.noAsignados()).singleElement().satisfies(sinAsignar -> {
            assertThat(sinAsignar.emparejamientoId()).isEqualTo(e.emparejamientoSancionado());
            assertThat(sinAsignar.codigo()).isEqualTo("VAL-006");
            assertThat(sinAsignar.causa()).contains("suspensión vigente");
        });
        assertThat(jdbc.sql("""
                        SELECT codigo_no_asignado FROM siglim.emparejamientos
                         WHERE id = :id AND partido_id IS NULL
                        """)
                .param("id", e.emparejamientoSancionado()).query(String.class).single()).isEqualTo("VAL-006");
        assertThat(partidosDelEquipo(e.equipoSancionado())).as("el sancionado no juega").isZero();

        List<PartidoAsignado> partidos = partidosDeLaFecha(e.fecha());
        assertThat(partidos).hasSize(7);

        // DEC-176, DEC-177: cada categoría en su día y dentro del horario de juego.
        assertThat(partidos).allSatisfy(partido -> {
            LocalDate dia = partido.inicio().atZoneSameInstant(ZONA).toLocalDate();
            assertThat(dia).isEqualTo(partido.categoria().equals("Libre") ? DOMINGO : SABADO);
            assertThat(partido.inicio().atZoneSameInstant(ZONA).toLocalTime()).isAfterOrEqualTo(LocalTime.of(8, 0));
            assertThat(partido.finBloque().atZoneSameInstant(ZONA).toLocalTime()).isBeforeOrEqualTo(LocalTime.of(18, 0));
        });

        // VAL-005, DEC-179: el local con cancha juega en ella, después del bloqueo y sin nada encima.
        PartidoAsignado delClub = partidos.stream()
                .filter(partido -> partido.emparejamientoId().equals(e.emparejamientoConCancha()))
                .findFirst().orElseThrow();
        assertThat(delClub.canchaId()).isEqualTo(e.canchaBloqueada());
        assertThat(delClub.inicio().atZoneSameInstant(ZONA).toLocalDateTime()).isEqualTo(DOMINGO.atTime(12, 0));
        assertThat(partidos).filteredOn(partido -> partido.canchaId().equals(e.canchaBloqueada()))
                .allSatisfy(partido -> assertThat(partido.inicio().isBefore(a(DOMINGO, 12))
                        && a(DOMINGO, 8).isBefore(partido.finBloque())).as("encima del bloqueo").isFalse());

        // Canchas compartidas: al menos una cancha recibe partidos de varios grupos y de varias categorías.
        Map<UUID, Set<UUID>> gruposPorCancha = partidos.stream().collect(Collectors.groupingBy(
                PartidoAsignado::canchaId, Collectors.mapping(PartidoAsignado::grupoId, Collectors.toSet())));
        assertThat(gruposPorCancha.values()).anySatisfy(grupos -> assertThat(grupos).hasSizeGreaterThan(1));
        Map<UUID, Set<String>> categoriasPorCancha = partidos.stream().collect(Collectors.groupingBy(
                PartidoAsignado::canchaId, Collectors.mapping(PartidoAsignado::categoria, Collectors.toSet())));
        UUID compartidaDelSabado = categoriasPorCancha.entrySet().stream()
                .filter(entrada -> entrada.getValue().containsAll(Set.of("Veteranos", "Infantil")))
                .map(Map.Entry::getKey).findFirst().orElseThrow();

        // DEC-178, RF-032: en la cancha compartida del sábado, la categoría de menor orden va primero y
        // los partidos quedan consecutivos.
        List<PartidoAsignado> delSabado = partidos.stream()
                .filter(partido -> partido.canchaId().equals(compartidaDelSabado))
                .filter(partido -> partido.inicio().atZoneSameInstant(ZONA).toLocalDate().equals(SABADO))
                .sorted((uno, otro) -> uno.inicio().compareTo(otro.inicio()))
                .toList();
        assertThat(delSabado).extracting(PartidoAsignado::categoria).containsExactly("Veteranos", "Infantil", "Infantil");
        for (int i = 1; i < delSabado.size(); i++) {
            assertThat(delSabado.get(i).inicio().toInstant()).isEqualTo(delSabado.get(i - 1).finBloque().toInstant());
        }

        // VAL-003, VAL-004 en la base: ningún equipo ni cancha tiene dos partidos traslapados esos días.
        assertThat(traslapes("p.cancha_id = q.cancha_id")).as("cancha ocupada dos veces").isZero();
        assertThat(traslapes("""
                (p.equipo_local_id IN (q.equipo_local_id, q.equipo_visitante_id)
                 OR p.equipo_visitante_id IN (q.equipo_local_id, q.equipo_visitante_id))""")).as("equipo en dos partidos").isZero();
    }

    // ── Consultas de comprobación ───────────────────────────────────────────────────────────────

    /** Partido vigente de la fecha con su grupo, categoría y emparejamiento. */
    private record PartidoAsignado(UUID emparejamientoId, UUID grupoId, String categoria, UUID canchaId,
                                   OffsetDateTime inicio, OffsetDateTime finBloque) {
    }

    private List<PartidoAsignado> partidosDeLaFecha(UUID fecha) {
        return jdbc.sql("""
                        SELECT e.id AS emparejamiento_id, j.grupo_id, split_part(c.nombre, ' ', 1) AS categoria,
                               p.cancha_id, p.inicio, p.fin_bloque
                          FROM siglim.partidos p
                          JOIN siglim.jornadas j ON j.id = p.jornada_id
                          JOIN siglim.emparejamientos e ON e.partido_id = p.id AND e.vigente
                          JOIN siglim.grupos g ON g.id = j.grupo_id
                          JOIN siglim.categorias_temporada ct ON ct.id = g.categoria_temporada_id
                          JOIN siglim.categorias c ON c.id = ct.categoria_id
                         WHERE j.fecha_id = :fecha AND p.estado <> 'CANCELADO'
                         ORDER BY p.inicio, p.id
                        """)
                .param("fecha", fecha)
                .query((fila, n) -> new PartidoAsignado(
                        fila.getObject("emparejamiento_id", UUID.class),
                        fila.getObject("grupo_id", UUID.class),
                        fila.getString("categoria"),
                        fila.getObject("cancha_id", UUID.class),
                        fila.getObject("inicio", OffsetDateTime.class),
                        fila.getObject("fin_bloque", OffsetDateTime.class)))
                .list();
    }

    /** Pares de partidos no cancelados de esos días que cumplen la condición y cuyos bloques se traslapan. */
    private long traslapes(String condicion) {
        return jdbc.sql("""
                        SELECT count(*)
                          FROM siglim.partidos p
                          JOIN siglim.partidos q ON q.id > p.id AND q.estado <> 'CANCELADO'
                         WHERE p.estado <> 'CANCELADO'
                           AND tstzrange(p.inicio, p.fin_bloque)
                               && tstzrange(CAST(:desde AS timestamptz), CAST(:hasta AS timestamptz))
                           AND tstzrange(p.inicio, p.fin_bloque) && tstzrange(q.inicio, q.fin_bloque)
                           AND""" + " " + condicion)
                .param("desde", a(SABADO, 0))
                .param("hasta", a(DOMINGO.plusDays(1), 0))
                .query(Long.class).single();
    }

    private long partidosDelEquipo(UUID equipo) {
        return jdbc.sql("""
                        SELECT count(*) FROM siglim.partidos
                         WHERE :equipo IN (equipo_local_id, equipo_visitante_id) AND estado <> 'CANCELADO'
                        """)
                .param("equipo", equipo).query(Long.class).single();
    }

    private static OffsetDateTime a(LocalDate dia, int hora) {
        return dia.atTime(hora, 0).atZone(ZONA).toOffsetDateTime();
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    private record Escenario(UUID fecha, UUID canchaBloqueada, UUID emparejamientoConCancha,
                             UUID emparejamientoSancionado, UUID equipoSancionado) {
    }

    private Escenario escenario() {
        UUID programador = iniciarSesion(perfilConRol("PROGRAMADOR"));
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, :inicio) RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID()).param("inicio", SABADO)
                .query(UUID.class).single();
        // 18 fechas de fase regular: la fecha 1 no es de las últimas 10 en la unidad deportiva (DEC-179).
        jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo, fechas_fase_regular)
                        VALUES (:temporada, 1, 'Parámetros de prueba', 18)
                        """)
                .param("temporada", temporada).update();
        UUID libre = crearCategoriaTemporada(temporada, "Libre", "LIBRE");
        UUID grupoLibre1 = crearGrupo(libre, "Grupo 1", 4);
        crearGrupo(libre, "Grupo 2", 5);
        UUID grupoVeteranos = crearGrupo(crearCategoriaTemporada(temporada, "Veteranos", "VETERANOS"), "Grupo 1", 4);
        crearGrupo(crearCategoriaTemporada(temporada, "Infantil", "INFANTIL"), "Grupo 1", 4);
        for (int i = 0; i < 2; i++) {
            jdbc.sql("""
                            INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                            VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA')
                            """)
                    .param("liga", LIGA).param("nombre", "Unidad " + UUID.randomUUID()).update();
        }

        calendario.generar(temporada, null);
        UUID fecha = jdbc.sql("SELECT id FROM siglim.fechas_programacion WHERE temporada_id = :t AND numero = 1")
                .param("t", temporada).query(UUID.class).single();

        // La cancha del club de un local de «Libre», bloqueada de 08:00 a 12:00 del domingo.
        UUID[] conCancha = primerPartido(grupoLibre1);
        UUID canchaBloqueada = jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario, club_id)
                        SELECT :liga, :nombre, 'EXTERNA', 'CLUB', club_id FROM siglim.equipos WHERE id = :equipo
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Cancha del club " + UUID.randomUUID()).param("equipo", conCancha[1])
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.disponibilidad_canchas (cancha_id, tipo, periodo, motivo, creado_por)
                        VALUES (:cancha, 'BLOQUEO', tstzrange(:desde, :hasta), 'Reparación del pasto', :perfil)
                        """)
                .param("cancha", canchaBloqueada).param("desde", a(DOMINGO, 8)).param("hasta", a(DOMINGO, 12))
                .param("perfil", programador).update();

        // Un visitante de «Veteranos» con una suspensión vigente.
        UUID[] sancionado = primerPartido(grupoVeteranos);
        sancionarEquipo(temporada, sancionado[2], programador);

        return new Escenario(fecha, canchaBloqueada, conCancha[0], sancionado[0], sancionado[2]);
    }

    /** {emparejamiento, local, visitante} del primer partido (no descanso) del grupo en la fecha 1. */
    private UUID[] primerPartido(UUID grupo) {
        return jdbc.sql("""
                        SELECT id, equipo_local_id, equipo_visitante_id FROM siglim.emparejamientos
                         WHERE grupo_id = :grupo AND numero_fecha = 1 AND vigente AND equipo_visitante_id IS NOT NULL
                         ORDER BY id LIMIT 1
                        """)
                .param("grupo", grupo)
                .query((fila, n) -> new UUID[] {fila.getObject(1, UUID.class), fila.getObject(2, UUID.class),
                        fila.getObject(3, UUID.class)})
                .single();
    }

    /** Suspensión vigente de un partido con su expediente y resolución: lo mínimo que exigen las tablas. */
    private void sancionarEquipo(UUID temporada, UUID equipo, UUID perfil) {
        UUID articulo = jdbc.sql("""
                        INSERT INTO siglim.articulos
                            (liga_id, numero, descripcion, tipo_sancion, sujeto, partidos_suspension)
                        VALUES (:liga, :numero, 'Artículo de prueba', 'SUSPENSION_PARTIDOS', 'EQUIPO', 1)
                        RETURNING id
                        """)
                .param("liga", LIGA).param("numero", "ART-" + UUID.randomUUID().toString().substring(0, 16))
                .query(UUID.class).single();
        UUID expediente = jdbc.sql("""
                        INSERT INTO siglim.expedientes (liga_id, temporada_id, folio, origen, descripcion)
                        VALUES (:liga, :temporada, :folio, 'MANUAL', 'Prueba de sanción de equipo')
                        RETURNING id
                        """)
                .param("liga", LIGA).param("temporada", temporada)
                .param("folio", "PRUEBA-" + UUID.randomUUID().toString().substring(0, 20))
                .query(UUID.class).single();
        UUID resolucion = jdbc.sql("""
                        INSERT INTO siglim.resoluciones (expediente_id, articulo_id, fundamentos, resuelta_por)
                        VALUES (:expediente, :articulo, 'Fundamentos de prueba', :perfil)
                        RETURNING id
                        """)
                .param("expediente", expediente).param("articulo", articulo).param("perfil", perfil)
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.sanciones
                            (resolucion_id, tipo, sujeto, efecto, equipo_id, temporada_id, partidos, vigente_desde)
                        VALUES (:resolucion, 'SUSPENSION_PARTIDOS', 'EQUIPO', 'HACIA_ADELANTE', :equipo, :temporada, 1,
                                :desde)
                        """)
                .param("resolucion", resolucion).param("equipo", equipo).param("temporada", temporada)
                .param("desde", SABADO.minusDays(3)).update();
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

    private UUID crearGrupo(UUID categoriaTemporada, String nombre, int equipos) {
        UUID grupo = jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, :nombre) RETURNING id")
                .param("ct", categoriaTemporada).param("nombre", nombre).query(UUID.class).single();
        for (int i = 0; i < equipos; i++) {
            UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                    .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID())
                    .query(UUID.class).single();
            jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre)")
                    .param("club", club).param("grupo", grupo).param("nombre", "Equipo " + UUID.randomUUID())
                    .update();
        }
        return grupo;
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

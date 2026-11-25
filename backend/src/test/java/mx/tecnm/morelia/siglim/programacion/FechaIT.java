package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.FechaDto.Bloque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-35.1 contra PostgreSQL real: la vista por fecha arma los bloques por categoría y grupo con
 * partidos, descansos, sin asignar y árbitro; el alta manual liga un emparejamiento pendiente; y la
 * verificación, el envío y el dictamen operan sobre todas las jornadas de la fecha juntas, con la
 * separación de funciones de RF-121 y la publicación en {@code v_publica_programacion}.
 *
 * <p>Escenario: fecha 1 con dos grupos de categorías distintas. Grupo A (orden menor): A1 vs A2
 * programado y A3 descansa. Grupo B: B1 vs B2 programado y B3 vs B4 sin asignar (VAL-007).
 *
 * <p>Refs: H-35.1, RF-033, RF-037, RF-039, RF-040, RF-090, RF-121, DEC-175, DEC-180, DEC-181
 */
class FechaIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    /** Domingo de la fecha 1. */
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-09T08:00:00-06:00");
    private static final String OBSERVACIONES = "Mover Lobos vs Osos a la cancha de la unidad";
    private static final String CAUSA = "Sin horario libre el domingo para Libre (bloque de 120 min)";

    @Autowired
    private FechaService fechas;

    @Autowired
    private JornadaService jornadas;

    /** Árbitros creados: se borran al terminar para no aparecer en los listados de otras pruebas. */
    private final List<UUID> arbitrosCreados = new ArrayList<>();

    @AfterEach
    void borrarArbitros() {
        for (UUID arbitro : arbitrosCreados) {
            jdbc.sql("DELETE FROM siglim.designaciones WHERE arbitro_id = :id").param("id", arbitro).update();
            jdbc.sql("DELETE FROM siglim.arbitros WHERE id = :id").param("id", arbitro).update();
        }
        arbitrosCreados.clear();
    }

    // ── Vista por fecha (F-22) ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("UI-009: la fecha se ve en bloques Categoría – Grupo con partidos, descansos, sin asignar y árbitro")
    void vistaPorFecha() {
        Escenario e = escenario();
        UUID arbitro = designarCentral(e.partidoA());
        jornadas.actualizarPartido(e.partidoB(), new ActualizarPartidoRequest(null, null, null,
                INICIO.plusHours(2), "Ajuste pedido por el club local", null));

        FechaDto fecha = fechas.consultar(e.fecha());

        assertThat(fecha.numero()).isEqualTo(1);
        assertThat(fecha.dias()).containsExactly(LocalDate.parse("2031-03-08"), LocalDate.parse("2031-03-09"));
        assertThat(fecha.estadoPublicacion()).isEqualTo(EstadoJornada.BORRADOR);
        assertThat(fecha.jornadas()).isEqualTo(2);
        assertThat(fecha.partidos()).isEqualTo(2);
        assertThat(fecha.sinAsignar()).isEqualTo(1);
        assertThat(fecha.bloques()).extracting(bloque -> bloque.grupo().id())
                .as("por el orden de la categoría").containsExactly(e.grupoA(), e.grupoB());

        Bloque a = fecha.bloques().getFirst();
        assertThat(a.categoria().nombre()).startsWith("Categoría A");
        assertThat(a.grupo().nombre()).isEqualTo("Grupo 1");
        assertThat(a.diasJuego()).as("LIBRE juega en domingo (DEC-177)").containsExactly(7);
        assertThat(a.jornadaId()).isEqualTo(e.jornadaA());
        assertThat(a.partidos()).singleElement().satisfies(fila -> {
            assertThat(fila.id()).isEqualTo(e.partidoA());
            assertThat(fila.emparejamientoId()).isEqualTo(e.emparejamientoA());
            assertThat(fila.equipoLocal().id()).isEqualTo(e.a1());
            assertThat(fila.cancha().id()).isEqualTo(e.cancha1());
            assertThat(fila.inicio().toInstant()).isEqualTo(INICIO.toInstant());
            assertThat(fila.arbitro().id()).isEqualTo(arbitro);
            assertThat(fila.arbitro().nombre()).isEqualTo("Juan Pérez");
            assertThat(fila.designacionAutomatica()).isFalse();
            assertThat(fila.ajustado()).isFalse();
        });
        assertThat(a.descansos()).singleElement().satisfies(descanso ->
                assertThat(descanso.equipo().id()).isEqualTo(e.a3()));
        assertThat(a.sinAsignar()).isEmpty();

        Bloque b = fecha.bloques().get(1);
        assertThat(b.partidos()).singleElement().satisfies(fila -> {
            assertThat(fila.ajustado()).as("ajuste con motivo (RF-040)").isTrue();
            assertThat(fila.arbitro()).as("sin árbitro").isNull();
        });
        assertThat(b.descansos()).isEmpty();
        assertThat(b.sinAsignar()).singleElement().satisfies(fila -> {
            assertThat(fila.emparejamientoId()).isEqualTo(e.emparejamientoB2());
            assertThat(fila.equipoLocal().id()).isEqualTo(e.b3());
            assertThat(fila.equipoVisitante().id()).isEqualTo(e.b4());
            assertThat(fila.codigo()).isEqualTo("VAL-007");
            assertThat(fila.causa()).isEqualTo(CAUSA);
        });

        assertThat(fechas.listar(e.temporada(), 0, 20).contenido()).singleElement().satisfies(resumen -> {
            assertThat(resumen.id()).isEqualTo(e.fecha());
            assertThat(resumen.sinAsignar()).isEqualTo(1);
            assertThat(resumen.bloques()).isNull();
        });
    }

    @Test
    @DisplayName("DEC-181: un partido cancelado no se muestra y su emparejamiento vuelve a quedar sin asignar")
    void canceladoVuelveASinAsignar() {
        Escenario e = escenario();
        // Como «Regenerar» (DEC-181): el partido se cancela con motivo, no se borra.
        jdbc.sql("""
                        WITH motivo AS (SELECT set_config('siglim.motivo', 'Regenerar la fecha de prueba', true))
                        UPDATE siglim.partidos SET estado = 'CANCELADO'
                         WHERE id = :id AND EXISTS (SELECT 1 FROM motivo)
                        """)
                .param("id", e.partidoA()).update();

        Bloque a = fechas.consultar(e.fecha()).bloques().getFirst();

        assertThat(a.partidos()).isEmpty();
        assertThat(a.sinAsignar()).singleElement().satisfies(fila -> {
            assertThat(fila.emparejamientoId()).isEqualTo(e.emparejamientoA());
            assertThat(fila.codigo()).as("aún sin causa").isNull();
        });
    }

    @Test
    @DisplayName("DEC-175: una fecha que no existe en la liga responde 404")
    void fechaInexistente() {
        iniciarSesion(perfilConRol("PROGRAMADOR"));
        assertThatThrownBy(() -> fechas.consultar(UUID.randomUUID()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> fechas.enviarAAprobacion(UUID.randomUUID()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> fechas.listar(UUID.randomUUID(), 0, 20))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Asignar a mano un emparejamiento (DEC-180) ─────────────────────────────────────────────

    @Test
    @DisplayName("DEC-180: «Asignar» registra el partido ligado al emparejamiento y la fila deja de estar sin asignar")
    void asignarEmparejamientoPendiente() {
        Escenario e = escenario();

        PartidoDto partido = jornadas.registrarPartido(e.jornadaB(), new CrearPartidoRequest(
                e.b3(), e.b4(), e.cancha2(), INICIO.plusHours(2), null, e.emparejamientoB2()));

        assertThat(jdbc.sql("""
                        SELECT partido_id FROM siglim.emparejamientos
                         WHERE id = :id AND codigo_no_asignado IS NULL AND causa_no_asignado IS NULL
                        """)
                .param("id", e.emparejamientoB2()).query(UUID.class).single()).isEqualTo(partido.id());
        Bloque b = fechas.consultar(e.fecha()).bloques().get(1);
        assertThat(b.sinAsignar()).isEmpty();
        assertThat(b.partidos()).extracting(FechaDto.FilaPartido::emparejamientoId)
                .contains(e.emparejamientoB2());

        assertThatThrownBy(() -> jornadas.registrarPartido(e.jornadaB(), new CrearPartidoRequest(
                e.b3(), e.b4(), e.cancha1(), INICIO.plusHours(6), null, e.emparejamientoB2())))
                .as("ya tiene partido")
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO));
    }

    @Test
    @DisplayName("DEC-180: el emparejamiento debe ser de la jornada, no un descanso y con los mismos equipos")
    void emparejamientoInvalido() {
        Escenario e = escenario();

        assertThatThrownBy(() -> jornadas.registrarPartido(e.jornadaB(), new CrearPartidoRequest(
                e.b4(), e.b3(), e.cancha2(), INICIO.plusHours(2), null, e.emparejamientoB2())))
                .as("localía invertida")
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
        assertThatThrownBy(() -> jornadas.registrarPartido(e.jornadaA(), new CrearPartidoRequest(
                e.a1(), e.a2(), e.cancha2(), INICIO.plusHours(2), null, e.emparejamientoB2())))
                .as("de otro grupo")
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
        assertThatThrownBy(() -> jornadas.registrarPartido(e.jornadaA(), new CrearPartidoRequest(
                e.a3(), e.a1(), e.cancha2(), INICIO.plusHours(4), null, e.descansoA())))
                .as("descanso")
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "RF-037"));
        assertThat(fechas.consultar(e.fecha()).sinAsignar()).isEqualTo(1);
    }

    // ── Verificación y envío (RF-039, RF-121) ──────────────────────────────────────────────────

    @Test
    @DisplayName("RF-039: con un conflicto duro en una jornada la fecha no se envía y ninguna jornada cambia")
    void envioConConflictos() {
        Escenario e = escenario();
        jdbc.sql("""
                        UPDATE siglim.equipos
                           SET estado = 'BAJA', baja_en = DATE '2031-03-01', motivo_baja = 'Baja de prueba'
                         WHERE id = :id
                        """).param("id", e.b1()).update();

        VerificacionFechaDto verificacion = fechas.verificar(e.fecha());

        assertThat(verificacion.conflictos()).singleElement().satisfies(aviso -> {
            assertThat(aviso.jornadaId()).isEqualTo(e.jornadaB());
            assertThat(aviso.partidoId()).isEqualTo(e.partidoB());
            assertThat(aviso.codigo()).isEqualTo("VAL-006");
        });
        assertThat(verificacion.advertencias()).extracting(VerificacionFechaDto.Aviso::codigo)
                .as("sin árbitro y sin asignar no bloquean").contains(FechaService.CODIGO_SIN_ARBITRO, "VAL-007");
        assertThatThrownBy(() -> fechas.enviarAAprobacion(e.fecha()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-039"));
        assertThat(estados(e.fecha())).containsOnly("BORRADOR");
    }

    @Test
    @DisplayName("RF-090: si una jornada de la fecha ya no está en borrador, la fecha no se envía")
    void envioConJornadaFueraDeBorrador() {
        Escenario e = escenario();
        jornadas.enviarAAprobacion(e.jornadaA());

        assertThatThrownBy(() -> fechas.enviarAAprobacion(e.fecha()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));
        assertThat(fechas.consultar(e.fecha()).estadoPublicacion())
                .as("el estado derivado es el menos avanzado").isEqualTo(EstadoJornada.BORRADOR);
    }

    // ── Dictamen de la fecha completa (RF-121) ─────────────────────────────────────────────────

    @Test
    @DisplayName("RF-121: se envía, se devuelve con observaciones y se aprueba completa; todas se publican")
    void flujoCompleto() {
        Escenario e = escenario();

        FechaDto enviada = fechas.enviarAAprobacion(e.fecha());
        assertThat(enviada.estadoPublicacion()).isEqualTo(EstadoJornada.REVISION);
        assertThat(enviada.enviadaPor()).isEqualTo(e.programador());
        assertThat(estados(e.fecha())).containsOnly("REVISION");
        assertThatThrownBy(() -> fechas.resolver(e.fecha(),
                new ResolverFechaRequest(DecisionJornada.PUBLICADO, null)))
                .as("el Programador no tiene APROBAR")
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));

        UUID presidente = iniciarSesion(perfilConRol("PRESIDENTE"));
        assertThatThrownBy(() -> fechas.resolver(e.fecha(),
                new ResolverFechaRequest(DecisionJornada.BORRADOR, "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RF-121"));

        FechaDto devuelta = fechas.resolver(e.fecha(), new ResolverFechaRequest(DecisionJornada.BORRADOR, OBSERVACIONES));
        assertThat(devuelta.estadoPublicacion()).isEqualTo(EstadoJornada.BORRADOR);
        assertThat(devuelta.observaciones()).isEqualTo(OBSERVACIONES);
        assertThat(estados(e.fecha())).containsOnly("BORRADOR");

        iniciarSesion(e.programador());
        fechas.enviarAAprobacion(e.fecha());
        iniciarSesion(presidente);
        FechaDto publicada = fechas.resolver(e.fecha(), new ResolverFechaRequest(DecisionJornada.PUBLICADO, null));

        assertThat(publicada.estadoPublicacion()).isEqualTo(EstadoJornada.PUBLICADO);
        assertThat(publicada.aprobadaPor()).isEqualTo(presidente);
        assertThat(estados(e.fecha())).containsOnly("PUBLICADO");
        assertThat(jdbc.sql("""
                        SELECT partido_id FROM siglim.v_publica_programacion
                         WHERE jornada_id IN (:a, :b)
                        """)
                .param("a", e.jornadaA()).param("b", e.jornadaB())
                .query(UUID.class).list())
                .as("todas las jornadas en el portal").containsExactlyInAnyOrder(e.partidoA(), e.partidoB());
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM siglim.historial_publicacion
                         WHERE entidad = 'JORNADA' AND entidad_id IN (:a, :b) AND estado_nuevo = 'PUBLICADO'
                        """)
                .param("a", e.jornadaA()).param("b", e.jornadaB()).query(Long.class).single()).isEqualTo(2L);
        assertThat(jdbc.sql("SELECT accion FROM siglim.auditoria WHERE entidad_id = :id ORDER BY id")
                .param("id", e.fecha().toString()).query(String.class).list())
                .containsExactly("ENVIAR_FECHA_A_APROBACION", "DEVOLVER_FECHA",
                        "ENVIAR_FECHA_A_APROBACION", "APROBAR_FECHA");
    }

    @Test
    @DisplayName("RF-121: quien generó la fecha o además puede editarla no la aprueba")
    void separacionDeFunciones() {
        Escenario e = escenario();
        fechas.enviarAAprobacion(e.fecha());

        UUID presidente = perfilConRol("PRESIDENTE");
        jdbc.sql("UPDATE siglim.fechas_programacion SET generada_por = :perfil WHERE id = :id")
                .param("perfil", presidente).param("id", e.fecha()).update();
        iniciarSesion(presidente);
        assertThatThrownBy(() -> fechas.resolver(e.fecha(),
                new ResolverFechaRequest(DecisionJornada.PUBLICADO, null)))
                .as("generó la fecha")
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "RF-121"));

        UUID ambos = perfilConRol("PRESIDENTE");
        asignarRol(ambos, "PROGRAMADOR");
        iniciarSesion(ambos);
        assertThatThrownBy(() -> fechas.resolver(e.fecha(),
                new ResolverFechaRequest(DecisionJornada.PUBLICADO, null)))
                .as("con EDITAR y APROBAR a la vez")
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "RF-121"));

        assertThat(estados(e.fecha())).containsOnly("REVISION");
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    private record Escenario(UUID programador, UUID temporada, UUID fecha, UUID grupoA, UUID grupoB,
                             UUID jornadaA, UUID jornadaB, UUID a1, UUID a2, UUID a3, UUID b1, UUID b2,
                             UUID b3, UUID b4, UUID cancha1, UUID cancha2, UUID emparejamientoA,
                             UUID descansoA, UUID emparejamientoB2, UUID partidoA, UUID partidoB) {
    }

    /** Fecha 1 con dos grupos; el Programador queda con la sesión iniciada. */
    private Escenario escenario() {
        UUID programador = iniciarSesion(perfilConRol("PROGRAMADOR"));
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2031-03-01')
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo)
                        VALUES (:temporada, 1, 'Parámetros de prueba')
                        """)
                .param("temporada", temporada).update();
        UUID fecha = jdbc.sql("""
                        INSERT INTO siglim.fechas_programacion
                            (temporada_id, numero, dias, generada_por, generada_en, creado_por)
                        VALUES (:temporada, 1, ARRAY[DATE '2031-03-08', DATE '2031-03-09'], :perfil, now(), :perfil)
                        RETURNING id
                        """)
                .param("temporada", temporada).param("perfil", programador)
                .query(UUID.class).single();
        UUID grupoA = crearGrupo(temporada, "Categoría A");
        UUID grupoB = crearGrupo(temporada, "Categoría B");
        UUID jornadaA = crearJornada(grupoA, fecha, programador);
        UUID jornadaB = crearJornada(grupoB, fecha, programador);
        UUID a1 = crearEquipo(grupoA, "Halcones");
        UUID a2 = crearEquipo(grupoA, "Toros");
        UUID a3 = crearEquipo(grupoA, "Águilas");
        UUID b1 = crearEquipo(grupoB, "Pumas");
        UUID b2 = crearEquipo(grupoB, "Leones");
        UUID b3 = crearEquipo(grupoB, "Lobos");
        UUID b4 = crearEquipo(grupoB, "Osos");
        UUID cancha1 = crearCancha();
        UUID cancha2 = crearCancha();
        UUID emparejamientoA = crearEmparejamiento(grupoA, a1, a2, null, null);
        UUID descansoA = crearEmparejamiento(grupoA, a3, null, null, null);
        UUID emparejamientoB1 = crearEmparejamiento(grupoB, b1, b2, null, null);
        UUID emparejamientoB2 = crearEmparejamiento(grupoB, b3, b4, "VAL-007", CAUSA);
        UUID partidoA = jornadas.registrarPartido(jornadaA,
                new CrearPartidoRequest(a1, a2, cancha1, INICIO, null, emparejamientoA)).id();
        UUID partidoB = jornadas.registrarPartido(jornadaB,
                new CrearPartidoRequest(b1, b2, cancha2, INICIO, null, emparejamientoB1)).id();
        return new Escenario(programador, temporada, fecha, grupoA, grupoB, jornadaA, jornadaB, a1, a2, a3,
                b1, b2, b3, b4, cancha1, cancha2, emparejamientoA, descansoA, emparejamientoB2, partidoA, partidoB);
    }

    private UUID crearGrupo(UUID temporada, String prefijo) {
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", prefijo + " " + UUID.randomUUID())
                .query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria)
                        RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria)
                .query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'Grupo 1') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
    }

    private UUID crearJornada(UUID grupo, UUID fecha, UUID perfil) {
        return jdbc.sql("""
                        INSERT INTO siglim.jornadas (grupo_id, numero, fecha_id, creado_por)
                        VALUES (:grupo, 1, :fecha, :perfil)
                        RETURNING id
                        """)
                .param("grupo", grupo).param("fecha", fecha).param("perfil", perfil)
                .query(UUID.class).single();
    }

    private UUID crearEquipo(UUID grupo, String nombre) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID())
                .query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre) RETURNING id")
                .param("club", club).param("grupo", grupo).param("nombre", nombre + " " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID crearCancha() {
        return jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA')
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID crearEmparejamiento(UUID grupo, UUID local, UUID visitante, String codigo, String causa) {
        return jdbc.sql("""
                        INSERT INTO siglim.emparejamientos
                            (grupo_id, vuelta, numero_fecha, equipo_local_id, equipo_visitante_id,
                             codigo_no_asignado, causa_no_asignado)
                        VALUES (:grupo, 1, 1, :local, CAST(:visitante AS uuid), :codigo, :causa)
                        RETURNING id
                        """)
                .param("grupo", grupo).param("local", local).param("visitante", visitante)
                .param("codigo", codigo, java.sql.Types.VARCHAR).param("causa", causa, java.sql.Types.VARCHAR)
                .query(UUID.class).single();
    }

    /** Árbitro validado de un club ajeno, designado como central vigente del partido. */
    private UUID designarCentral(UUID partido) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club árbitros " + UUID.randomUUID())
                .query(UUID.class).single();
        String curp = "PEPJ" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000))
                + "HMNRRN0" + ThreadLocalRandom.current().nextInt(10);
        UUID persona = jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, fecha_nacimiento)
                        VALUES (:curp, 'Juan', 'Pérez', DATE '1990-01-01')
                        RETURNING id
                        """)
                .param("curp", curp).query(UUID.class).single();
        UUID arbitro = jdbc.sql("""
                        INSERT INTO siglim.arbitros (persona_id, club_id, estado)
                        VALUES (:persona, :club, 'VALIDADO')
                        RETURNING id
                        """)
                .param("persona", persona).param("club", club).query(UUID.class).single();
        arbitrosCreados.add(arbitro);
        jdbc.sql("INSERT INTO siglim.designaciones (partido_id, arbitro_id) VALUES (:partido, :arbitro)")
                .param("partido", partido).param("arbitro", arbitro).update();
        return arbitro;
    }

    private List<String> estados(UUID fecha) {
        return jdbc.sql("SELECT estado_publicacion::text FROM siglim.jornadas WHERE fecha_id = :id")
                .param("id", fecha).query(String.class).list();
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

    private void asignarRol(UUID perfil, String codigoRol) {
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, r.id, r.liga_id, 'LIGA' FROM siglim.roles r WHERE r.codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", codigoRol).update();
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
        asignarRol(perfil, codigoRol);
        return perfil;
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }
}

package mx.tecnm.morelia.siglim.partidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.partidos.GuardarAlineacionesRequest.JugadorRequest;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H-16.1 contra PostgreSQL real: abrir la cédula de un partido en curso, guardar la alineación con la
 * elegibilidad de cada jugador (RF-019, RF-110, VAL-018) y el marcador con la no presentación por
 * default de los parámetros vigentes (RF-126, DEC-24, DEC-72). H-16.2: eventos, incidencias,
 * observaciones y el envío a validación con VAL-020 y el bloqueo fuera de captura (RF-122). Los triggers
 * {@code tg_cedulas_validar}, {@code tg_alineaciones_validar} y {@code tg_cedula_en_captura} se comprueban
 * como última garantía.
 */
class CedulasIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2036-03-08T10:00:00-06:00");

    @Autowired
    private CedulaService servicio;

    @Autowired
    private MotivoOperacion motivoOperacion;

    @Autowired
    private PlatformTransactionManager transacciones;

    private Escenario escenario;

    @BeforeEach
    void prepararEscenario() {
        escenario = escenario();
    }

    // ── Abrir ───────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-046 · RF-025: la cédula se abre con el partido en curso, una sola vez y con la sesión como capturista")
    void abrirCedula() {
        UUID capturista = iniciarSesion(perfilConRol("CAPTURISTA"));
        esperarError(() -> servicio.abrir(escenario.partido()), "RF-046", HttpStatus.CONFLICT);

        iniciarPartido(escenario.partido());
        CedulaDto cedula = servicio.abrir(escenario.partido());

        assertThat(cedula.estado()).isEqualTo(EstadoCedula.EN_CAPTURA);
        assertThat(cedula.capturista().id()).isEqualTo(capturista);
        assertThat(cedula.partido().estado()).isEqualTo(EstadoPartido.EN_CURSO);
        assertThat(cedula.partido().inicio()).isEqualTo(INICIO);
        assertThat(cedula.marcador().golesLocal()).isNull();
        assertThat(servicio.consultar(escenario.partido()).id()).isEqualTo(cedula.id());
        assertThat(accionesAuditadas(cedula.id())).containsExactly("ABRIR_CEDULA");
        assertThat(jdbc.sql("SELECT estado_nuevo::text FROM siglim.cedula_estado_historial WHERE cedula_id = :id")
                .param("id", cedula.id()).query(String.class).list()).containsExactly("EN_CAPTURA");

        esperarError(() -> servicio.abrir(escenario.partido()), "DUPLICADO", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("VAL-015: una cuenta sin partidos.cedula:CAPTURAR no abre la cédula")
    void abrirSinPermiso() {
        iniciarPartido(escenario.partido());
        iniciarSesion(perfilConRol("PROGRAMADOR"));

        esperarError(() -> servicio.abrir(escenario.partido()), "VAL-015", HttpStatus.FORBIDDEN);
    }

    // ── Alineación ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-019 · RF-110: guarda la alineación y marca al jugador de la categoría inmediata inferior")
    void guardarAlineacion() {
        UUID cedula = abrirCedula(escenario);

        CedulaDto guardada = servicio.guardarAlineaciones(cedula, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(escenario.localPrimera(), escenario.equipoLocal(), 10, true),
                new JugadorRequest(escenario.localSegunda(), escenario.equipoLocal(), 14, false),
                new JugadorRequest(escenario.visitantePrimera(), escenario.equipoVisitante(), 9, true))));

        assertThat(guardada.alineaciones()).hasSize(3);
        assertThat(guardada.alineaciones()).filteredOn(jugador -> jugador.registroJugadorId().equals(escenario.localSegunda()))
                .singleElement().satisfies(jugador -> {
                    assertThat(jugador.categoriaSuperior()).isTrue();
                    assertThat(jugador.titular()).isFalse();
                    assertThat(jugador.nombre()).isEqualTo("Segundo Halcón Prueba");
                });
        assertThat(guardada.alineaciones()).filteredOn(jugador -> !jugador.categoriaSuperior()).hasSize(2);
        assertThat(guardada.alineaciones().getFirst().equipoId()).as("el local va primero")
                .isEqualTo(escenario.equipoLocal());
        assertThat(accionesAuditadas(cedula)).containsExactly("ABRIR_CEDULA", "CAPTURAR_ALINEACION");

        CedulaDto reemplazada = servicio.guardarAlineaciones(cedula, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(escenario.localPrimera(), escenario.equipoLocal(), 7, true))));
        assertThat(reemplazada.alineaciones()).singleElement().satisfies(jugador ->
                assertThat(jugador.numeroPlayera()).isEqualTo(7));
    }

    @Test
    @DisplayName("VAL-002 · RF-019 · VAL-018 · RF-025: un error por jugador no elegible y no se guarda nadie")
    void alineacionNoElegible() {
        UUID cedula = abrirCedula(escenario);

        assertThatThrownBy(() -> servicio.guardarAlineaciones(cedula, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(escenario.localPrimera(), escenario.equipoLocal(), 10, true),
                new JugadorRequest(escenario.pendiente(), escenario.equipoLocal(), 11, true),
                new JugadorRequest(escenario.visitanteSegunda(), escenario.equipoLocal(), 12, true),
                new JugadorRequest(escenario.visitantePrimera(), escenario.equipoAjeno(), 9, true)))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-002");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> errores = (List<Map<String, Object>>) error.propiedades().get("errores");
                    assertThat(errores).extracting(e -> e.get("codigo")).containsExactly("RF-019", "VAL-018", "RF-025");
                    assertThat(errores).extracting(e -> e.get("indice")).containsExactly(1, 2, 3);
                    assertThat(errores.getFirst().get("mensaje")).asString()
                            .contains("«Pendiente Halcón Prueba»").contains("pendiente de validación");
                });
        assertThat(alineados(cedula)).isZero();
    }

    @Test
    @DisplayName("RF-019 · VAL-018: el trigger rechaza una alineación no elegible insertada sin el servicio")
    void triggerDeElegibilidad() {
        UUID cedula = abrirCedula(escenario);

        assertThatThrownBy(() -> insertarAlineacion(cedula, escenario.pendiente(), escenario.equipoLocal()))
                .hasStackTraceContaining("RF-019");
        assertThatThrownBy(() -> insertarAlineacion(cedula, escenario.visitanteSegunda(), escenario.equipoLocal()))
                .hasStackTraceContaining("VAL-018");
        assertThat(alineados(cedula)).isZero();
    }

    // ── Marcador ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-047 · RF-126 · DEC-72: guarda el marcador y la no presentación con el default de la versión vigente")
    void guardarMarcador() {
        UUID cedula = abrirCedula(escenario);

        CedulaDto normal = servicio.guardarMarcador(cedula, new GuardarMarcadorRequest(2, 1, null, null, null));
        assertThat(normal.marcador().golesLocal()).isEqualTo(2);
        assertThat(normal.marcador().golesVisitante()).isEqualTo(1);
        assertThat(normal.marcador().porDefault()).isFalse();

        CedulaDto noPresentado = servicio.guardarMarcador(cedula,
                new GuardarMarcadorRequest(null, null, null, null, escenario.equipoVisitante()));
        assertThat(noPresentado.marcador().golesLocal()).as("3-0 [DEMO] de la primera versión").isEqualTo(3);
        assertThat(noPresentado.marcador().golesVisitante()).isZero();
        assertThat(noPresentado.marcador().porDefault()).isTrue();
        assertThat(noPresentado.marcador().valorDemo()).isTrue();
        assertThat(noPresentado.marcador().noPresentado().id()).isEqualTo(escenario.equipoVisitante());

        jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo, goles_default_ganador,
                                                                 goles_default_perdedor, es_demo)
                        VALUES (:temporada, 2, 'Reglamento de prueba', 2, 0, false)
                        """).param("temporada", escenario.temporada()).update();
        CedulaDto conReglamento = servicio.guardarMarcador(cedula,
                new GuardarMarcadorRequest(null, null, null, null, escenario.equipoLocal()));
        assertThat(conReglamento.marcador().golesLocal()).isZero();
        assertThat(conReglamento.marcador().golesVisitante()).isEqualTo(2);
        assertThat(conReglamento.marcador().valorDemo()).isFalse();

        esperarError(() -> servicio.guardarMarcador(cedula, new GuardarMarcadorRequest(2, 1, 4, 3, null)),
                "RF-047", HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(accionesAuditadas(cedula))
                .containsExactly("ABRIR_CEDULA", "CAPTURAR_MARCADOR", "CAPTURAR_MARCADOR", "CAPTURAR_MARCADOR");
    }

    // ── Contenido solo en captura ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-122: en validación ni el servicio ni la base modifican la alineación ni el marcador")
    void soloEnCaptura() {
        UUID cedula = abrirCedula(escenario);
        servicio.guardarAlineaciones(cedula, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(escenario.localPrimera(), escenario.equipoLocal(), 10, true),
                new JugadorRequest(escenario.visitantePrimera(), escenario.equipoVisitante(), 9, true))));
        servicio.guardarMarcador(cedula, new GuardarMarcadorRequest(1, 0, null, null, null));
        enTransaccion(() -> { // con la sesión del Capturista, como lo hará el envío de H-16.2
            motivoOperacion.fijar("Envío de prueba a validación");
            jdbc.sql("UPDATE siglim.cedulas SET estado = 'PENDIENTE_VALIDACION' WHERE id = :id")
                    .param("id", cedula).update();
        });

        esperarError(() -> servicio.guardarMarcador(cedula, new GuardarMarcadorRequest(3, 0, null, null, null)),
                "RF-122", HttpStatus.CONFLICT);
        esperarError(() -> servicio.guardarAlineaciones(cedula, new GuardarAlineacionesRequest(List.of())),
                "RF-122", HttpStatus.CONFLICT);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM siglim.alineaciones WHERE cedula_id = :id")
                .param("id", cedula).update()).hasStackTraceContaining("RF-122");
        assertThat(alineados(cedula)).isEqualTo(2);
    }

    // ── Jugadores disponibles (H-16.3) ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-172: el Capturista ve los jugadores de los dos equipos y de la categoría inferior, con su elegibilidad")
    void jugadoresDisponibles() {
        UUID cedula = abrirCedula(escenario);

        List<JugadorDisponibleDto> jugadores = servicio.jugadoresDisponibles(cedula);

        assertThat(jugadores).extracting(JugadorDisponibleDto::nombre).containsExactly(
                "Pendiente Halcón Prueba", "Primer Halcón Prueba", "Segundo Halcón Prueba",
                "Primer Toro Prueba", "Segundo Toro Prueba");
        assertThat(jugadores.getFirst()).satisfies(pendiente -> {
            assertThat(pendiente.elegible()).isFalse();
            assertThat(pendiente.causa().codigo()).isEqualTo("RF-019");
        });
        assertThat(jugadores.get(2)).satisfies(deSegunda -> {
            assertThat(deSegunda.categoriaSuperior()).isTrue();
            assertThat(deSegunda.equipoId()).isEqualTo(escenario.equipoLocal());
            assertThat(deSegunda.elegible()).isTrue();
        });
        assertThat(jugadores.get(4).equipoId()).isEqualTo(escenario.equipoVisitante());

        assertThat(servicio.tiposIncidencia(cedula)).extracting(CedulaDto.TipoIncidenciaDto::nombre)
                .contains("Amonestación", "Expulsión", "Riña");

        iniciarSesion(perfilConRol("ARBITRO"));
        esperarError(() -> servicio.jugadoresDisponibles(cedula), "VAL-015", HttpStatus.FORBIDDEN);
        esperarError(() -> servicio.tiposIncidencia(cedula), "VAL-015", HttpStatus.FORBIDDEN);
    }

    // ── Eventos, incidencias y envío (H-16.2) ───────────────────────────────────────────────────

    @Test
    @DisplayName("RF-025 · RF-026: registra gol, tarjeta, sustitución e incidencia con involucrados, y quita un evento")
    void eventosEIncidencias() {
        UUID cedula = abrirCedula(escenario);
        CedulaDto alineada = alinearAmbos(cedula);
        UUID primerHalcon = fila(alineada, escenario.localPrimera());
        UUID segundoHalcon = fila(alineada, escenario.localSegunda());
        UUID primerToro = fila(alineada, escenario.visitantePrimera());

        CedulaDto conGol = servicio.registrarEvento(cedula,
                new RegistrarEventoRequest(TipoEventoCedula.GOL, 12, primerHalcon, null, "Tiro libre"));
        servicio.registrarEvento(cedula, new RegistrarEventoRequest(TipoEventoCedula.TARJETA_AMARILLA, 40, primerToro,
                null, null));
        servicio.registrarEvento(cedula, new RegistrarEventoRequest(TipoEventoCedula.SUSTITUCION, 60, primerHalcon,
                segundoHalcon, null));
        CedulaDto conIncidencia = servicio.registrarIncidencia(cedula, new RegistrarIncidenciaRequest(
                tipoIncidencia("EXPULSION"), 75, "Agresión a un rival después de la jugada",
                List.of(new RegistrarIncidenciaRequest.InvolucradoRequest(persona(escenario.visitantePrimera()), null),
                        new RegistrarIncidenciaRequest.InvolucradoRequest(null, escenario.equipoVisitante()))));

        assertThat(conIncidencia.eventos()).extracting(CedulaDto.EventoDto::tipo).containsExactly(
                TipoEventoCedula.GOL, TipoEventoCedula.TARJETA_AMARILLA, TipoEventoCedula.SUSTITUCION);
        assertThat(conIncidencia.eventos().get(0)).satisfies(gol -> {
            assertThat(gol.equipoId()).as("el equipo sale de la alineación").isEqualTo(escenario.equipoLocal());
            assertThat(gol.jugador().nombre()).isEqualTo("Primer Halcón Prueba");
        });
        assertThat(conIncidencia.eventos().get(2).entra().nombre()).isEqualTo("Segundo Halcón Prueba");
        assertThat(conIncidencia.incidencias()).singleElement().satisfies(incidencia -> {
            assertThat(incidencia.tipo().abreExpediente()).isTrue();
            assertThat(incidencia.involucrados()).extracting(CedulaDto.InvolucradoDto::equipoId)
                    .containsExactly(escenario.equipoVisitante(), null);
            assertThat(incidencia.involucrados()).extracting(CedulaDto.InvolucradoDto::nombre)
                    .satisfiesExactly(equipo -> assertThat(equipo).startsWith("Toros Primera"),
                            jugador -> assertThat(jugador).isEqualTo("Primer Toro Prueba"));
        });

        UUID gol = conGol.eventos().getFirst().id();
        servicio.eliminarEvento(cedula, gol);
        assertThat(servicio.consultar(escenario.partido()).eventos()).extracting(CedulaDto.EventoDto::id)
                .doesNotContain(gol).hasSize(2);
        assertThat(accionesAuditadas(cedula)).containsSubsequence("REGISTRAR_EVENTO_CEDULA",
                "REGISTRAR_INCIDENCIA_CEDULA", "ELIMINAR_EVENTO_CEDULA");
        esperarError(() -> servicio.registrarEvento(cedula,
                new RegistrarEventoRequest(TipoEventoCedula.SUSTITUCION, 70, primerHalcon, primerToro, null)),
                "RF-025", HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("VAL-020 · RF-122: la cédula incompleta no se envía; completa pasa a validación y ya no se edita")
    void enviarCedula() {
        UUID cedula = abrirCedula(escenario);

        assertThatThrownBy(() -> servicio.enviar(cedula)).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.codigo()).isEqualTo("VAL-020");
            assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> faltantes = (List<Map<String, Object>>) error.propiedades().get("faltantes");
            assertThat(faltantes).extracting(falta -> falta.get("campo"))
                    .containsExactly("marcador", "alineacionLocal", "alineacionVisitante");
        });

        CedulaDto alineada = alinearAmbos(cedula);
        UUID primerHalcon = fila(alineada, escenario.localPrimera());
        servicio.guardarMarcador(cedula, new GuardarMarcadorRequest(1, 0, null, null, null));
        servicio.guardarObservaciones(cedula, new GuardarObservacionesRequest("Lluvia en el segundo tiempo"));
        OffsetDateTime antes = OffsetDateTime.now();

        CedulaDto enviada = servicio.enviar(cedula);

        assertThat(enviada.estado()).isEqualTo(EstadoCedula.PENDIENTE_VALIDACION);
        assertThat(enviada.observaciones()).isEqualTo("Lluvia en el segundo tiempo");
        assertThat(enviada.enviadaEn()).isAfterOrEqualTo(antes.minusSeconds(5));
        assertThat(enviada.plazoVenceEn()).as("48 h [DEMO] de los parámetros")
                .isEqualTo(enviada.enviadaEn().plusHours(48));
        assertThat(jdbc.sql("SELECT estado_nuevo::text FROM siglim.cedula_estado_historial WHERE cedula_id = :id ORDER BY id")
                .param("id", cedula).query(String.class).list()).containsExactly("EN_CAPTURA", "PENDIENTE_VALIDACION");
        assertThat(accionesAuditadas(cedula)).last().isEqualTo("ENVIAR_CEDULA");

        esperarError(() -> servicio.registrarEvento(cedula,
                new RegistrarEventoRequest(TipoEventoCedula.GOL, 80, primerHalcon, null, null)), "RF-122", HttpStatus.CONFLICT);
        esperarError(() -> servicio.registrarIncidencia(cedula, new RegistrarIncidenciaRequest(tipoIncidencia("RINA"), 80,
                "Riña en la banca", List.of(new RegistrarIncidenciaRequest.InvolucradoRequest(null, escenario.equipoLocal())))),
                "RF-122", HttpStatus.CONFLICT);
        esperarError(() -> servicio.guardarObservaciones(cedula, new GuardarObservacionesRequest("Otra nota")),
                "RF-122", HttpStatus.CONFLICT);
        esperarError(() -> servicio.enviar(cedula), "RF-122", HttpStatus.CONFLICT);
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO siglim.eventos_cedula (cedula_id, tipo, equipo_id, alineacion_id)
                        VALUES (:cedula, 'GOL', :equipo, :fila)
                        """).param("cedula", cedula).param("equipo", escenario.equipoLocal()).param("fila", primerHalcon)
                .update()).as("el trigger es la última garantía").hasStackTraceContaining("RF-122");
    }

    @Test
    @DisplayName("VAL-020 · RF-126: con un equipo no presentado basta la alineación del que sí se presentó")
    void enviarConNoPresentacion() {
        UUID cedula = abrirCedula(escenario);
        servicio.guardarAlineaciones(cedula, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(escenario.localPrimera(), escenario.equipoLocal(), 10, true))));
        servicio.guardarMarcador(cedula, new GuardarMarcadorRequest(null, null, null, null, escenario.equipoVisitante()));

        assertThat(servicio.enviar(cedula).estado()).isEqualTo(EstadoCedula.PENDIENTE_VALIDACION);
    }

    private CedulaDto alinearAmbos(UUID cedula) {
        return servicio.guardarAlineaciones(cedula, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(escenario.localPrimera(), escenario.equipoLocal(), 10, true),
                new JugadorRequest(escenario.localSegunda(), escenario.equipoLocal(), 14, false),
                new JugadorRequest(escenario.visitantePrimera(), escenario.equipoVisitante(), 9, true))));
    }

    private static UUID fila(CedulaDto cedula, UUID registro) {
        return cedula.alineaciones().stream().filter(jugador -> jugador.registroJugadorId().equals(registro))
                .findFirst().orElseThrow().id();
    }

    private UUID persona(UUID registro) {
        return jdbc.sql("SELECT persona_id FROM siglim.registros_jugador WHERE id = :id")
                .param("id", registro).query(UUID.class).single();
    }

    private long tipoIncidencia(String codigo) {
        return jdbc.sql("SELECT id FROM siglim.tipos_incidencia WHERE liga_id = :liga AND codigo = :codigo")
                .param("liga", LIGA).param("codigo", codigo).query(Long.class).single();
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    /**
     * Temporada con Primera y Segunda (Segunda sube a Primera), clubes Halcones y Toros con un equipo en
     * cada categoría, un partido Halcones Primera vs. Toros Primera programado y registros de jugadores.
     */
    private record Escenario(UUID temporada, UUID partido, UUID equipoLocal, UUID equipoVisitante, UUID equipoAjeno,
                             UUID localPrimera, UUID localSegunda, UUID visitantePrimera, UUID visitanteSegunda,
                             UUID pendiente) {
    }

    private Escenario escenario() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2035-08-01') RETURNING id
                        """).param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo) VALUES (:t, 1, 'Parámetros [DEMO]')")
                .param("t", temporada).update();
        UUID primera = categoria(null);
        UUID segunda = categoria(primera);
        UUID grupoPrimera = grupo(temporada, primera);
        UUID grupoSegunda = grupo(temporada, segunda);
        UUID halcones = club(temporada, "Halcones");
        UUID toros = club(temporada, "Toros");
        UUID halconesPrimera = equipo(halcones, grupoPrimera, "Halcones Primera");
        UUID halconesSegunda = equipo(halcones, grupoSegunda, "Halcones Segunda");
        UUID torosPrimera = equipo(toros, grupoPrimera, "Toros Primera");
        UUID torosSegunda = equipo(toros, grupoSegunda, "Toros Segunda");

        UUID secretario = perfilConRol("SECRETARIO");
        UUID localPrimera = registroActivo(halconesPrimera, temporada, "Primer Halcón", secretario);
        UUID localSegunda = registroActivo(halconesSegunda, temporada, "Segundo Halcón", secretario);
        UUID visitantePrimera = registroActivo(torosPrimera, temporada, "Primer Toro", secretario);
        UUID visitanteSegunda = registroActivo(torosSegunda, temporada, "Segundo Toro", secretario);
        UUID pendiente = registro(halconesPrimera, temporada, "Pendiente Halcón");

        UUID jornada = jdbc.sql("INSERT INTO siglim.jornadas (grupo_id, numero) VALUES (:g, 1) RETURNING id")
                .param("g", grupoPrimera).query(UUID.class).single();
        UUID cancha = jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA') RETURNING id
                        """).param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID()).query(UUID.class).single();
        UUID partido = jdbc.sql("""
                        INSERT INTO siglim.partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id, inicio,
                                                     fin_bloque)
                        VALUES (:jornada, :local, :visitante, :cancha, :inicio, :inicio)
                        RETURNING id
                        """)
                .param("jornada", jornada).param("local", halconesPrimera).param("visitante", torosPrimera)
                .param("cancha", cancha).param("inicio", INICIO).query(UUID.class).single();
        SecurityContextHolder.clearContext();
        return new Escenario(temporada, partido, halconesPrimera, torosPrimera, torosSegunda, localPrimera,
                localSegunda, visitantePrimera, visitanteSegunda, pendiente);
    }

    /**
     * Pone el partido en curso, como lo hará la tarea programada de H-15, con una sesión de la liga
     * (sin sesión la transacción corre como {@code siglim_publico}) y deja la sesión que había.
     */
    private void iniciarPartido(UUID partido) {
        var sesionPrevia = SecurityContextHolder.getContext().getAuthentication();
        iniciarSesionComoAdministrador();
        enTransaccion(() -> {
            motivoOperacion.fijar("Inicio del partido a su hora");
            jdbc.sql("UPDATE siglim.partidos SET estado = 'EN_CURSO' WHERE id = :id").param("id", partido).update();
        });
        SecurityContextHolder.getContext().setAuthentication(sesionPrevia);
    }

    private UUID abrirCedula(Escenario datos) {
        iniciarPartido(datos.partido());
        iniciarSesion(perfilConRol("CAPTURISTA"));
        return servicio.abrir(datos.partido()).id();
    }

    private UUID categoria(UUID superior) {
        return jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden, categoria_superior_id)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1, :superior FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Categoría " + UUID.randomUUID()).param("superior", superior)
                .query(UUID.class).single();
    }

    private UUID grupo(UUID temporada, UUID categoria) {
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:t, :c) RETURNING id
                        """).param("t", temporada).param("c", categoria).query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'A') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
    }

    private UUID club(UUID temporada, String nombre) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + nombre + " " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.afiliaciones (club_id, temporada_id, folio, colores_uniforme, estado, capturada_por)
                        VALUES (:club, :temporada, :folio, 'Azul y blanco', 'APROBADA', :perfil)
                        """)
                .param("club", club).param("temporada", temporada)
                .param("folio", "F-" + ThreadLocalRandom.current().nextInt(1_000_000_000))
                .param("perfil", perfilConRol("SECRETARIO")).update();
        return club;
    }

    private UUID equipo(UUID club, UUID grupo, String nombre) {
        return jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre) RETURNING id")
                .param("club", club).param("grupo", grupo).param("nombre", nombre + " " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID registro(UUID equipo, UUID temporada, String nombre) {
        String curp = "XEXP" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000))
                + "HMNXXX" + String.format("%02d", ThreadLocalRandom.current().nextInt(100));
        UUID persona = jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, apellido_materno, fecha_nacimiento)
                        VALUES (:curp, :nombre, :paterno, 'Prueba', DATE '1995-01-01') RETURNING id
                        """)
                .param("curp", curp).param("nombre", nombre.split(" ")[0]).param("paterno", nombre.split(" ")[1])
                .query(UUID.class).single();
        return jdbc.sql("""
                        INSERT INTO siglim.registros_jugador (persona_id, equipo_id, temporada_id, alta_en)
                        VALUES (:persona, :equipo, :temporada, DATE '2035-08-15') RETURNING id
                        """)
                .param("persona", persona).param("equipo", equipo).param("temporada", temporada)
                .query(UUID.class).single();
    }

    /** Registro con los documentos que exige LIBRE aprobados y validado por el Secretario. */
    private UUID registroActivo(UUID equipo, UUID temporada, String nombre, UUID secretario) {
        UUID registro = registro(equipo, temporada, nombre);
        jdbc.sql("""
                        INSERT INTO siglim.documentos (tipo_documento_id, registro_id, s3_bucket, s3_clave, url, tipo_mime,
                                                       tamano_bytes, sha256, estado_validacion, cargado_por)
                        SELECT rd.tipo_documento_id, :registro, 'siglim-documentos-prueba',
                               'liga/cedula/' || :registro || '/' || rd.tipo_documento_id || '-' || n,
                               'https://example.test/documento', 'image/jpeg', 1024, repeat('a', 64), 'APROBADO', :perfil
                          FROM siglim.requisitos_documento rd CROSS JOIN LATERAL generate_series(1, rd.cantidad) n
                         WHERE rd.liga_id = :liga AND rd.tipo_categoria = 'LIBRE' AND NOT rd.solo_menores
                        """)
                .param("registro", registro).param("perfil", secretario).param("liga", LIGA).update();
        iniciarSesion(secretario);
        enTransaccion(() -> {
            motivoOperacion.fijar("Registro validado para la prueba de la cédula");
            jdbc.sql("""
                            UPDATE siglim.registros_jugador
                               SET estado = 'ACTIVO', validado_por = :perfil, validado_en = now()
                             WHERE id = :id
                            """).param("id", registro).param("perfil", secretario).update();
        });
        return registro;
    }

    private void insertarAlineacion(UUID cedula, UUID registro, UUID equipo) {
        jdbc.sql("""
                        INSERT INTO siglim.alineaciones (cedula_id, registro_jugador_id, equipo_id)
                        VALUES (:cedula, :registro, :equipo)
                        """).param("cedula", cedula).param("registro", registro).param("equipo", equipo).update();
    }

    private int alineados(UUID cedula) {
        return jdbc.sql("SELECT count(*) FROM siglim.alineaciones WHERE cedula_id = :id")
                .param("id", cedula).query(Integer.class).single();
    }

    private List<String> accionesAuditadas(UUID cedula) {
        return jdbc.sql("SELECT accion FROM siglim.auditoria WHERE entidad_id = :id ORDER BY ocurrido_en, id")
                .param("id", cedula.toString()).query(String.class).list();
    }

    private UUID perfilConRol(String codigoRol) {
        UUID usuarioAuth = UUID.randomUUID();
        jdbc.sql("INSERT INTO auth.users (id, email, aud, role) VALUES (:id, :correo, 'authenticated', 'authenticated')")
                .param("id", usuarioAuth).param("correo", usuarioAuth + "@prueba.mx").update();
        UUID perfil = jdbc.sql("""
                        INSERT INTO siglim.perfiles (usuario_auth_id, nombre_mostrado, correo)
                        VALUES (:usuario, :nombre, :correo) RETURNING id
                        """)
                .param("usuario", usuarioAuth).param("nombre", codigoRol + " de prueba")
                .param("correo", usuarioAuth + "@prueba.mx").query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, r.id, r.liga_id, 'LIGA' FROM siglim.roles r WHERE r.codigo = :rol
                        """).param("perfil", perfil).param("rol", codigoRol).update();
        return perfil;
    }

    private UUID iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
        return perfil;
    }

    private void enTransaccion(Runnable accion) {
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> accion.run());
    }

    private static void esperarError(Runnable accion, String codigo, HttpStatus estado) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.codigo()).isEqualTo(codigo);
            assertThat(error.estado()).isEqualTo(estado);
        });
    }
}

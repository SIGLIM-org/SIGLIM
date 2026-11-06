package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-29.1 contra PostgreSQL real, con {@link CobroService} y {@link AdeudoService} completos:
 * autorización con la matriz de la migración, RLS, SQL de sumas y restricciones. Demuestra los tres
 * criterios de aceptación de H-29.
 *
 * <p>Cada prueba crea sus propios clubes y equipos, y filtra por ellos: la base se comparte con las
 * demás pruebas de integración. Los cobros vencidos y pagados se preparan por SQL como dueño del
 * esquema, porque sus transiciones son de H-30 y H-31.
 */
class CobrosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    @Autowired
    private CobroService cobros;

    @Autowired
    private AdeudoService adeudos;

    @Autowired
    private Clock reloj;

    // ── Criterio 1: el Tesorero crea un cobro manual PENDIENTE ───────────────────────────────

    @Test
    @DisplayName("H-29 criterio 1 · RF-137: el TESORERO crea un cobro MANUAL PENDIENTE con monto BigDecimal y lo audita")
    void tesoreroCreaCobroManual() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        LocalDate hoy = hoyDeNegocio();

        CobroDto creado = cobros.crear(new CrearCobroRequest(escenario.clubA(), escenario.equipoA(),
                "  Cuota de inscripción  ", new BigDecimal("1500.00"), null, hoy.plusDays(15)));

        assertThat(creado.tipo()).isEqualTo(TipoCobro.MANUAL);
        assertThat(creado.estado()).isEqualTo(EstadoCobro.PENDIENTE);
        assertThat(creado.concepto()).isEqualTo("Cuota de inscripción");
        assertThat(creado.monto()).isEqualByComparingTo("1500.00").hasScaleOf(2);
        assertThat(creado.periodoInicio()).isEqualTo(hoy);
        assertThat(creado.fechaLimite()).isEqualTo(hoy.plusDays(15));
        assertThat(creado.club().id()).isEqualTo(escenario.clubA());
        assertThat(creado.equipo().id()).isEqualTo(escenario.equipoA());

        FilaCobro fila = jdbc.sql("""
                        SELECT tipo, estado, monto, periodo_inicio, fecha_limite, creado_por
                          FROM siglim.cobros WHERE id = :id
                        """)
                .param("id", creado.id())
                .query((resultado, numero) -> new FilaCobro(
                        TipoCobro.valueOf(resultado.getString("tipo")),
                        EstadoCobro.valueOf(resultado.getString("estado")),
                        resultado.getBigDecimal("monto"),
                        resultado.getObject("periodo_inicio", LocalDate.class),
                        resultado.getObject("fecha_limite", LocalDate.class),
                        resultado.getObject("creado_por", UUID.class)))
                .single();
        assertThat(fila.tipo()).isEqualTo(TipoCobro.MANUAL);
        assertThat(fila.estado()).isEqualTo(EstadoCobro.PENDIENTE);
        assertThat(fila.monto()).isEqualByComparingTo("1500.00").hasScaleOf(2);
        assertThat(fila.periodoInicio()).isEqualTo(hoy);
        assertThat(fila.fechaLimite()).isEqualTo(hoy.plusDays(15));
        assertThat(fila.creadoPor()).isEqualTo(escenario.tesorero());

        assertThat(eventos(creado.id())).extracting(Evento::accion).containsExactly("REGISTRAR_COBRO");
        assertThat(eventos(creado.id()).getFirst().perfilId()).isEqualTo(escenario.tesorero());
    }

    @Test
    @DisplayName("RF-137: rechaza un equipo de otro club y un club inexistente sin escribir")
    void crearRechazaReferenciasInvalidas() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        LocalDate limite = hoyDeNegocio().plusDays(5);
        long antes = contarCobros(escenario.clubA());

        assertThatThrownBy(() -> cobros.crear(new CrearCobroRequest(escenario.clubA(), escenario.equipoB(),
                "Cuota", new BigDecimal("100.00"), null, limite)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));
        assertThatThrownBy(() -> cobros.crear(new CrearCobroRequest(UUID.randomUUID(), null,
                "Cuota", new BigDecimal("100.00"), null, limite)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(contarCobros(escenario.clubA())).isEqualTo(antes);
    }

    @Test
    @DisplayName("VAL-015: la Directiva, que solo consulta, no puede crear cobros")
    void directivaNoCreaCobros() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.directivaA());

        assertThatThrownBy(() -> cobros.crear(new CrearCobroRequest(escenario.clubA(), null,
                "Cuota", new BigDecimal("100.00"), null, hoyDeNegocio().plusDays(5))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThat(contarCobros(escenario.clubA())).isZero();
    }

    // ── Criterios 2 y 3: adeudos y cobros según el ámbito ────────────────────────────────────

    @Test
    @DisplayName("H-29 criterios 2 y 3 · RF-140: la Directiva solo ve los cobros y adeudos de su club")
    void directivaSoloVeSuClub() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        LocalDate limite = hoyDeNegocio().plusDays(10);
        CobroDto deEquipo = cobros.crear(new CrearCobroRequest(escenario.clubA(), escenario.equipoA(),
                "Cuota de equipo", new BigDecimal("300.00"), null, limite));
        CobroDto delClub = cobros.crear(new CrearCobroRequest(escenario.clubA(), null,
                "Cuota del club", new BigDecimal("100.00"), null, limite));
        cobros.crear(new CrearCobroRequest(escenario.clubB(), escenario.equipoB(),
                "Cuota ajena", new BigDecimal("200.00"), null, limite));

        iniciarSesion(escenario.directivaA());

        Pagina<CobroDto> visibles = cobros.listar(new FiltroCobros(null, null, null, List.of(), null, null, 0, 100,
                "creadoEn,desc"));
        assertThat(visibles.contenido()).extracting(CobroDto::id)
                .containsExactlyInAnyOrder(deEquipo.id(), delClub.id());
        assertThat(visibles.totalElementos()).isEqualTo(2);

        Pagina<CobroDto> ajenos = cobros.listar(new FiltroCobros(escenario.clubB(), null, null, List.of(), null, null,
                0, 100, "creadoEn,desc"));
        assertThat(ajenos.contenido()).isEmpty();
        assertThat(ajenos.totalElementos()).isZero();

        AdeudosDto propios = adeudos.consultar(null);
        assertThat(propios.clubes()).extracting(club -> club.club().id()).containsExactly(escenario.clubA());
        assertThat(propios.total()).isEqualByComparingTo("400.00");
        assertThat(propios.cobros()).isEqualTo(2);

        AdeudosDto ajenosAdeudo = adeudos.consultar(escenario.clubB());
        assertThat(ajenosAdeudo.clubes()).isEmpty();
        assertThat(ajenosAdeudo.total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("RF-140: suma pendientes y vencidos por club y equipo, y excluye pagados y rechazados")
    void adeudosSumanSoloPendientesYVencidos() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        LocalDate limite = hoyDeNegocio().plusDays(10);
        cobros.crear(new CrearCobroRequest(escenario.clubA(), null, "Cuota del club",
                new BigDecimal("100.00"), null, limite));
        cobros.crear(new CrearCobroRequest(escenario.clubA(), escenario.equipoA(),
                "Cuota pendiente", new BigDecimal("200.00"), null, limite));
        UUID vencidoEquipo = cobros.crear(new CrearCobroRequest(escenario.clubA(), escenario.equipoA(),
                "Cuota vencida", new BigDecimal("50.00"), null, limite.minusDays(1))).id();
        UUID pagadoEquipo = cobros.crear(new CrearCobroRequest(escenario.clubA(), escenario.equipoA(),
                "Cuota pagada", new BigDecimal("75.50"), null, limite)).id();
        UUID rechazadoEquipo = cobros.crear(new CrearCobroRequest(escenario.clubA(), escenario.equipoA(),
                "Cuota rechazada", new BigDecimal("25.00"), null, limite)).id();
        cobros.crear(new CrearCobroRequest(escenario.clubB(), escenario.equipoB(),
                "Cuota ajena", new BigDecimal("400.00"), null, limite));
        cambiarEstado(vencidoEquipo, "VENCIDO");
        cambiarEstado(pagadoEquipo, "PAGADO");
        cambiarEstado(rechazadoEquipo, "RECHAZADO");

        AdeudosDto clubA = adeudos.consultar(escenario.clubA());
        assertThat(clubA.total()).isEqualByComparingTo("350.00");
        assertThat(clubA.pendiente()).isEqualByComparingTo("300.00");
        assertThat(clubA.vencido()).isEqualByComparingTo("50.00");
        assertThat(clubA.cobros()).isEqualTo(3);
        assertThat(clubA.clubes()).hasSize(1);
        AdeudosDto.ClubAdeudoDto club = clubA.clubes().getFirst();
        assertThat(club.club().id()).isEqualTo(escenario.clubA());
        assertThat(club.total()).isEqualByComparingTo("350.00");
        assertThat(club.equipos()).hasSize(1);
        AdeudosDto.EquipoAdeudoDto equipo = club.equipos().getFirst();
        assertThat(equipo.equipo().id()).isEqualTo(escenario.equipoA());
        assertThat(equipo.total()).isEqualByComparingTo("250.00");
        assertThat(equipo.pendiente()).isEqualByComparingTo("200.00");
        assertThat(equipo.vencido()).isEqualByComparingTo("50.00");
        assertThat(equipo.cobros()).isEqualTo(2);

        AdeudosDto clubB = adeudos.consultar(escenario.clubB());
        assertThat(clubB.total()).isEqualByComparingTo("400.00");
        assertThat(clubB.pendiente()).isEqualByComparingTo("400.00");
        assertThat(clubB.vencido()).isEqualByComparingTo("0.00");
        assertThat(clubB.cobros()).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-140: el listado filtra por club, equipo, tipo, estado, fecha límite y pagina")
    void listadoFiltraYPagina() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        LocalDate hoy = hoyDeNegocio();
        CobroDto primero = cobros.crear(new CrearCobroRequest(escenario.clubA(), escenario.equipoA(),
                "Cuota 1", new BigDecimal("100.00"), null, hoy.plusDays(10)));
        CobroDto segundo = cobros.crear(new CrearCobroRequest(escenario.clubA(), escenario.equipoA(),
                "Cuota 2", new BigDecimal("50.00"), null, hoy.plusDays(20)));
        CobroDto tercero = cobros.crear(new CrearCobroRequest(escenario.clubA(), escenario.equipoB(),
                "Cuota 3", new BigDecimal("25.00"), null, hoy.plusDays(30)));
        cobros.crear(new CrearCobroRequest(escenario.clubB(), escenario.equipoB(),
                "Cuota 4", new BigDecimal("75.00"), null, hoy.plusDays(40)));
        cambiarEstado(segundo.id(), "VENCIDO");

        Pagina<CobroDto> delClub = cobros.listar(new FiltroCobros(escenario.clubA(), null, null, List.of(), null,
                null, 0, 100, "fechaLimite,asc"));
        assertThat(delClub.contenido()).extracting(CobroDto::id)
                .containsExactly(primero.id(), segundo.id(), tercero.id());
        assertThat(delClub.totalElementos()).isEqualTo(3);

        Pagina<CobroDto> delEquipo = cobros.listar(new FiltroCobros(escenario.clubA(), escenario.equipoA(), null,
                List.of(), null, null, 0, 100, "fechaLimite,asc"));
        assertThat(delEquipo.contenido()).extracting(CobroDto::id).containsExactly(primero.id(), segundo.id());

        Pagina<CobroDto> pendientes = cobros.listar(new FiltroCobros(escenario.clubA(), null, TipoCobro.MANUAL,
                List.of(EstadoCobro.PENDIENTE), null, null, 0, 100, "fechaLimite,asc"));
        assertThat(pendientes.contenido()).extracting(CobroDto::id).containsExactly(primero.id(), tercero.id());

        Pagina<CobroDto> rango = cobros.listar(new FiltroCobros(escenario.clubA(), null, null, List.of(),
                hoy.plusDays(15), hoy.plusDays(35), 0, 100, "fechaLimite,asc"));
        assertThat(rango.contenido()).extracting(CobroDto::id).containsExactly(segundo.id(), tercero.id());

        Pagina<CobroDto> pagina = cobros.listar(new FiltroCobros(escenario.clubA(), null, null, List.of(), null,
                null, 0, 2, "fechaLimite,asc"));
        assertThat(pagina.contenido()).extracting(CobroDto::id).containsExactly(primero.id(), segundo.id());
        assertThat(pagina.totalElementos()).isEqualTo(3);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private Escenario escenario() {
        UUID grupo = grupo();
        UUID clubA = club("A");
        UUID clubB = club("B");
        UUID equipoA = equipo(clubA, grupo, "A");
        UUID equipoB = equipo(clubB, grupo, "B");
        return new Escenario(clubA, clubB, equipoA, equipoB, perfilConRol("TESORERO"), perfilDeDirectiva(clubA));
    }

    private LocalDate hoyDeNegocio() {
        return LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
    }

    private long contarCobros(UUID club) {
        return jdbc.sql("SELECT count(*) FROM siglim.cobros WHERE club_id = :club")
                .param("club", club).query(Long.class).single();
    }

    /** Cambia el estado como dueño del esquema; el trigger de VAL-028 valida la transición. */
    private void cambiarEstado(UUID cobro, String estado) {
        jdbc.sql("UPDATE siglim.cobros SET estado = CAST(:estado AS siglim.estado_cobro) WHERE id = :id")
                .param("estado", estado).param("id", cobro).update();
    }

    private UUID club(String nombre) {
        return jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA)
                .param("nombre", "Club " + nombre + " " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID equipo(UUID club, UUID grupo, String nombre) {
        return jdbc.sql("""
                        INSERT INTO siglim.equipos (club_id, grupo_id, nombre)
                        VALUES (:club, :grupo, :nombre) RETURNING id
                        """)
                .param("club", club).param("grupo", grupo)
                .param("nombre", "Equipo " + nombre + " " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    /** Grupo de una temporada nueva en configuración, con sus parámetros (RF-004). */
    private UUID grupo() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2033-08-01') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, motivo) VALUES (:id, :motivo)")
                .param("id", temporada).param("motivo", "Parámetros de prueba").update();
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
        return jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, :nombre) RETURNING id")
                .param("ct", categoriaTemporada).param("nombre", "Grupo " + UUID.randomUUID())
                .query(UUID.class).single();
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

    /** Perfil con el rol DIRECTIVA_CLUB de la matriz, en ámbito CLUB de ese club. */
    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil).param("club", club).update();
        return perfil;
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private List<Evento> eventos(UUID cobro) {
        return jdbc.sql("""
                        SELECT accion, perfil_id
                          FROM siglim.auditoria
                         WHERE modulo = 'M-13' AND entidad = 'cobros' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", cobro.toString())
                .query((fila, numero) -> new Evento(fila.getString("accion"),
                        fila.getObject("perfil_id", UUID.class)))
                .list();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private record Escenario(UUID clubA, UUID clubB, UUID equipoA, UUID equipoB, UUID tesorero,
                             UUID directivaA) {
    }

    private record FilaCobro(TipoCobro tipo, EstadoCobro estado, BigDecimal monto, LocalDate periodoInicio,
                             LocalDate fechaLimite, UUID creadoPor) {
    }

    private record Evento(String accion, UUID perfilId) {
    }
}

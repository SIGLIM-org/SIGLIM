package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-06.1 contra PostgreSQL real, con {@link ClubService} y {@link EquipoService} completos:
 * autorización con la matriz de la migración (incluida {@code 20261103100000}), SQL, RLS y
 * restricciones. Demuestra los tres criterios de aceptación de H-06.
 *
 * <p>La temporada, la categoría y el grupo se crean por SQL como dueño del esquema: su CRUD es de
 * M-02. Antes de cada prueba se cierra cualquier temporada activa para respetar RN-058.
 */
class ClubesEquiposIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final String MOTIVO = "El club retiró al equipo de la categoría";

    @Autowired
    private ClubService clubes;

    @Autowired
    private EquipoService equipos;

    @Autowired
    private Clock reloj;

    @BeforeEach
    void cerrarTemporadasActivas() {
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE estado = 'ACTIVA'").update();
    }

    // ── Criterio 1: el Secretario registra club y equipo ──────────────────────────────────────

    @Test
    @DisplayName("H-06 criterio 1 · RF-008, RF-009: el SECRETARIO registra club y equipo en la temporada activa")
    void secretarioRegistraClubYEquipo() {
        UUID grupo = grupoEnTemporada(EstadoTemporada.ACTIVA);
        UUID secretario = perfilConRol("SECRETARIO");
        iniciarSesion(secretario);

        String nombreClub = "Club " + UUID.randomUUID();
        ClubDto club = clubes.crear(new CrearClubRequest(nombreClub));
        EquipoDto equipo = equipos.crear(new CrearEquipoRequest(club.id(), grupo, "Halcones"));

        assertThat(club.estado()).isEqualTo(EstadoClub.ACTIVO);
        assertThat(clubes.consultar(club.id()).nombre()).isEqualTo(nombreClub);
        assertThat(equipo.estado()).isEqualTo(EstadoEquipo.ACTIVO);
        assertThat(equipo.temporada().estado()).isEqualTo(EstadoTemporada.ACTIVA);
        assertThat(equipo.grupo().id()).isEqualTo(grupo);

        Pagina<EquipoDto> delClub = equipos.listar(new FiltroEquipos(null, null, club.id(), null, 0, 20));
        assertThat(delClub.contenido()).extracting(EquipoDto::id).containsExactly(equipo.id());
        assertThat(delClub.totalElementos()).isEqualTo(1);

        assertThat(eventos("clubes", club.id())).extracting(Evento::accion).containsExactly("REGISTRAR_CLUB");
        assertThat(eventos("equipos", equipo.id())).extracting(Evento::accion).containsExactly("REGISTRAR_EQUIPO");
        assertThat(eventos("equipos", equipo.id())).allSatisfy(evento ->
                assertThat(evento.perfilId()).isEqualTo(secretario));

        assertThatThrownBy(() -> clubes.crear(new CrearClubRequest(nombreClub)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        assertThatThrownBy(() -> equipos.crear(new CrearEquipoRequest(club.id(), grupo, "Halcones")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
    }

    // ── Criterio 2: la directiva solo ve y opera su club ─────────────────────────────────────

    @Test
    @DisplayName("H-06 criterio 2 · ADR-0007, VAL-024: la DIRECTIVA_CLUB solo ve su club y sus equipos")
    void directivaSoloVeSuClub() {
        UUID grupo = grupoEnTemporada(EstadoTemporada.ACTIVA);
        iniciarSesionComoAdministrador();
        ClubDto propio = clubes.crear(new CrearClubRequest("Propio " + UUID.randomUUID()));
        ClubDto ajeno = clubes.crear(new CrearClubRequest("Ajeno " + UUID.randomUUID()));
        EquipoDto primera = equipos.crear(new CrearEquipoRequest(propio.id(), grupo, "Propio 1"));
        EquipoDto segunda = equipos.crear(new CrearEquipoRequest(propio.id(), grupo, "Propio 2"));
        EquipoDto delAjeno = equipos.crear(new CrearEquipoRequest(ajeno.id(), grupo, "Ajeno 1"));

        iniciarSesion(perfilDeDirectiva(propio.id()));

        Pagina<ClubDto> clubesVisibles = clubes.listar(new FiltroClubes(null, null, 0, 100));
        assertThat(clubesVisibles.contenido()).extracting(ClubDto::id).containsExactly(propio.id());
        assertThat(clubesVisibles.totalElementos()).isEqualTo(1);

        Pagina<EquipoDto> equiposVisibles = equipos.listar(new FiltroEquipos(null, null, null, null, 0, 100));
        assertThat(equiposVisibles.contenido()).extracting(EquipoDto::id)
                .containsExactlyInAnyOrder(primera.id(), segunda.id());
        assertThat(equiposVisibles.totalElementos()).isEqualTo(2);

        Pagina<EquipoDto> filtroAjeno = equipos.listar(new FiltroEquipos(null, null, ajeno.id(), null, 0, 20));
        assertThat(filtroAjeno.contenido()).isEmpty();
        assertThat(filtroAjeno.totalElementos()).isZero();

        assertThatThrownBy(() -> equipos.crear(new CrearEquipoRequest(ajeno.id(), grupo, "Intruso")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> equipos.consultar(delAjeno.id())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> clubes.consultar(ajeno.id())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(equipos.consultar(primera.id()).club().id()).isEqualTo(propio.id());

        EquipoDto nuevo = equipos.crear(new CrearEquipoRequest(propio.id(), grupo, "Propio 3"));
        assertThat(nuevo.club().id()).as("la directiva registra equipos de su club").isEqualTo(propio.id());
    }

    // ── Criterio 3: la baja conserva la fila ─────────────────────────────────────────────────

    @Test
    @DisplayName("H-06 criterio 3 · RF-009, RN-005, RN-021: la baja deja el equipo en BAJA y conserva la fila")
    void bajaConservaElEquipo() {
        UUID grupo = grupoEnTemporada(EstadoTemporada.ACTIVA);
        UUID admin = iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        EquipoDto equipo = equipos.crear(new CrearEquipoRequest(club.id(), grupo, "Halcones"));

        assertThatThrownBy(() -> equipos.darDeBaja(equipo.id(), new BajaEquipoRequest("corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));

        EquipoDto baja = equipos.darDeBaja(equipo.id(), new BajaEquipoRequest(MOTIVO));

        LocalDate hoy = LocalDate.now(reloj.withZone(ZoneId.of("America/Mexico_City")));
        assertThat(baja.estado()).isEqualTo(EstadoEquipo.BAJA);
        assertThat(baja.motivoBaja()).isEqualTo(MOTIVO);
        assertThat(baja.bajaEn()).isEqualTo(hoy);
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.equipos WHERE id = :id AND estado = 'BAJA'")
                .param("id", equipo.id()).query(Long.class).single())
                .as("la fila sigue existiendo").isEqualTo(1);

        List<Evento> eventos = eventos("equipos", equipo.id());
        assertThat(eventos).extracting(Evento::accion).containsExactly("REGISTRAR_EQUIPO", "DAR_BAJA_EQUIPO");
        assertThat(eventos.getLast().motivo()).isEqualTo(MOTIVO);
        assertThat(eventos.getLast().perfilId()).isEqualTo(admin);

        assertThatThrownBy(() -> equipos.darDeBaja(equipo.id(), new BajaEquipoRequest(MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-009"));
        assertThatThrownBy(() -> equipos.actualizar(equipo.id(), new ActualizarEquipoRequest("Otro", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-009"));
    }

    // ── Temporada cerrada y cambio de grupo ──────────────────────────────────────────────────

    @Test
    @DisplayName("RF-003: no se registran equipos en una temporada cerrada (409)")
    void crearEnTemporadaCerrada() {
        UUID grupo = grupoEnTemporada(EstadoTemporada.CERRADA);
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));

        assertThatThrownBy(() -> equipos.crear(new CrearEquipoRequest(club.id(), grupo, "Halcones")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        assertThat(equipos.listar(new FiltroEquipos(null, null, club.id(), null, 0, 20)).totalElementos()).isZero();
    }

    @Test
    @DisplayName("RN-007: en configuración el equipo cambia de grupo dentro de su temporada y no a otra")
    void cambioDeGrupo() {
        UUID temporada = temporada();
        UUID grupoA = grupo(temporada, "A");
        UUID grupoB = grupo(temporada, "B");
        UUID grupoDeOtra = grupo(temporada(), "A");
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        EquipoDto equipo = equipos.crear(new CrearEquipoRequest(club.id(), grupoA, "Halcones"));

        EquipoDto movido = equipos.actualizar(equipo.id(), new ActualizarEquipoRequest(null, grupoB));
        assertThat(movido.grupo().id()).isEqualTo(grupoB);
        assertThatThrownBy(() -> equipos.actualizar(equipo.id(), new ActualizarEquipoRequest(null, grupoDeOtra)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "RN-007"));
        assertThat(eventos("equipos", equipo.id())).extracting(Evento::accion)
                .containsExactly("REGISTRAR_EQUIPO", "EDITAR_EQUIPO");
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /** Grupo «A» de una temporada nueva en el estado pedido (con parámetros, para poder activarla). */
    private UUID grupoEnTemporada(EstadoTemporada estado) {
        UUID temporada = temporada();
        UUID grupo = grupo(temporada, "A");
        if (estado != EstadoTemporada.CONFIGURACION) {
            jdbc.sql("UPDATE siglim.temporadas SET estado = 'ACTIVA' WHERE id = :id").param("id", temporada).update();
        }
        if (estado == EstadoTemporada.CERRADA) {
            jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE id = :id").param("id", temporada).update();
        }
        return grupo;
    }

    /** Temporada en configuración con parámetros y una categoría nueva asignada. */
    private UUID temporada() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2033-08-01') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, motivo) VALUES (:id, 'Parámetros de prueba')")
                .param("id", temporada).update();
        return temporada;
    }

    private UUID grupo(UUID temporada, String nombre) {
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
                .param("ct", categoriaTemporada).param("nombre", nombre)
                .query(UUID.class).single();
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado (LIGA). */
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

    private List<Evento> eventos(String entidad, UUID id) {
        return jdbc.sql("""
                        SELECT accion, perfil_id, motivo
                          FROM siglim.auditoria
                         WHERE modulo = 'M-03' AND entidad = :entidad AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("entidad", entidad)
                .param("id", id.toString())
                .query((fila, numero) -> new Evento(fila.getString("accion"),
                        fila.getObject("perfil_id", UUID.class), fila.getString("motivo")))
                .list();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private record Evento(String accion, UUID perfilId, String motivo) {
    }
}

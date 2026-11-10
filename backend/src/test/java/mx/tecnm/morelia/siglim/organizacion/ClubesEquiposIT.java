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
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-06.1 y B-04 contra PostgreSQL real, con {@link ClubService} y {@link EquipoService} completos:
 * autorización con la matriz de la migración (incluida {@code 20261103100000}), SQL, RLS y
 * restricciones. Demuestra los tres criterios de aceptación de H-06 y el cambio de nombre de club
 * (RF-105, DEC-49, DEC-61): reglas, efectos, auditoría y atomicidad.
 *
 * <p>La temporada, la categoría y el grupo se crean por SQL como dueño del esquema: su CRUD es de
 * M-02. Antes de cada prueba se cierra cualquier temporada activa para respetar RN-058.
 */
class ClubesEquiposIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final String MOTIVO = "El club retiró al equipo de la categoría";
    private static final String MOTIVO_CAMBIO = "La asamblea del club aprobó el nuevo nombre";

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

    // ── B-04: cambio de nombre (RF-105, RN-041, DEC-15, DEC-49, DEC-61) ─────────────────────

    @Test
    @DisplayName("B-04 · RF-105, DEC-15, DEC-61: cierra el club, crea el nuevo, traslada asignaciones y audita")
    void cambioDeNombre() {
        UUID admin = iniciarSesionComoAdministrador();
        ClubDto anterior = clubes.crear(new CrearClubRequest("Anterior " + UUID.randomUUID()));
        UUID directiva = perfilDeDirectiva(anterior.id());
        UUID asignacionAnterior = asignacionActiva(directiva, anterior.id());
        String nombreNuevo = "Nuevo " + UUID.randomUUID();

        ClubDto nuevo = clubes.cambiarNombre(anterior.id(), new CambioNombreClubRequest(" " + nombreNuevo + " ",
                " " + MOTIVO_CAMBIO + " "));

        LocalDate hoy = LocalDate.now(reloj.withZone(ZoneId.of("America/Mexico_City")));
        assertThat(nuevo.id()).isNotEqualTo(anterior.id());
        assertThat(nuevo.nombre()).isEqualTo(nombreNuevo);
        assertThat(nuevo.estado()).isEqualTo(EstadoClub.ACTIVO);
        assertThat(nuevo.clubAnterior()).isEqualTo(new ClubDto.ClubAnteriorDto(anterior.id(), anterior.nombre()));

        ClubDto cerrado = clubes.consultar(anterior.id());
        assertThat(cerrado.estado()).isEqualTo(EstadoClub.CERRADO);
        assertThat(cerrado.cerradoEn()).isEqualTo(hoy);
        assertThat(cerrado.motivoCierre()).isEqualTo(MOTIVO_CAMBIO);
        assertThat(cerrado.nombre()).as("el club anterior conserva su nombre").isEqualTo(anterior.nombre());
        assertThat(jdbc.sql("SELECT club_anterior_id FROM siglim.clubes WHERE id = :id")
                .param("id", nuevo.id()).query(UUID.class).single()).isEqualTo(anterior.id());

        List<Evento> delAnterior = eventos("clubes", anterior.id());
        assertThat(delAnterior).extracting(Evento::accion).containsExactly("REGISTRAR_CLUB", "CERRAR_CLUB");
        assertThat(delAnterior.getLast().motivo()).isEqualTo(MOTIVO_CAMBIO);
        assertThat(delAnterior.getLast().perfilId()).isEqualTo(admin);
        assertThat(eventos("clubes", nuevo.id())).extracting(Evento::accion).containsExactly("REGISTRAR_CLUB");

        // DEC-61: la asignación de ámbito CLUB se revoca y se recrea en el club nuevo (RN-005).
        assertThat(jdbc.sql("SELECT activa FROM siglim.asignaciones_rol WHERE id = :id")
                .param("id", asignacionAnterior).query(Boolean.class).single()).isFalse();
        UUID asignacionNueva = asignacionActiva(directiva, nuevo.id());
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM siglim.asignaciones_rol n JOIN siglim.asignaciones_rol a ON a.id = :anterior
                         WHERE n.id = :nueva AND n.rol_id = a.rol_id AND n.ambito = 'CLUB' AND n.creado_por = :admin
                        """)
                .param("anterior", asignacionAnterior).param("nueva", asignacionNueva).param("admin", admin)
                .query(Long.class).single()).isEqualTo(1);
        assertThat(eventosAsignacion(asignacionAnterior)).containsExactly("REVOCAR_ASIGNACION");
        assertThat(eventosAsignacion(asignacionNueva)).containsExactly("ASIGNAR_ROL");

        iniciarSesion(directiva);
        assertThat(clubes.listar(new FiltroClubes(null, null, 0, 20)).contenido()).extracting(ClubDto::id)
                .as("la directiva ahora alcanza el club nuevo").containsExactly(nuevo.id());
    }

    @Test
    @DisplayName("B-04 · DEC-49: con un equipo ACTIVO en la temporada ACTIVA responde 409 RN-041 y nada cambia")
    void cambioDeNombreConEquipoEnTemporadaActiva() {
        cambioRechazadoPorEquipoInscrito(EstadoTemporada.ACTIVA);
    }

    @Test
    @DisplayName("B-04 · DEC-61: con un equipo ACTIVO en una temporada en CONFIGURACION responde 409 RN-041")
    void cambioDeNombreConEquipoEnConfiguracion() {
        cambioRechazadoPorEquipoInscrito(EstadoTemporada.CONFIGURACION);
    }

    @Test
    @DisplayName("B-04 · DEC-61: un equipo en BAJA o en una temporada CERRADA no impide el cambio de nombre")
    void cambioDeNombreConEquiposQueNoCuentan() {
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));

        UUID temporadaCerrada = temporada();
        UUID grupoCerrado = grupo(temporadaCerrada, "A");
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'ACTIVA' WHERE id = :id").param("id", temporadaCerrada).update();
        equipos.crear(new CrearEquipoRequest(club.id(), grupoCerrado, "Histórico"));
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE id = :id").param("id", temporadaCerrada).update();

        UUID grupoActivo = grupoEnTemporada(EstadoTemporada.ACTIVA);
        EquipoDto retirado = equipos.crear(new CrearEquipoRequest(club.id(), grupoActivo, "Retirado"));
        equipos.darDeBaja(retirado.id(), new BajaEquipoRequest(MOTIVO));

        ClubDto nuevo = clubes.cambiarNombre(club.id(), new CambioNombreClubRequest("Nuevo " + UUID.randomUUID(),
                MOTIVO_CAMBIO));

        assertThat(nuevo.clubAnterior().id()).isEqualTo(club.id());
        assertThat(clubes.consultar(club.id()).estado()).isEqualTo(EstadoClub.CERRADO);
    }

    @Test
    @DisplayName("B-04 · DEC-61, RN-052: con un cobro PENDIENTE responde 409 RN-041 y nada cambia")
    void cambioDeNombreConCobroPendiente() {
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        jdbc.sql("""
                        INSERT INTO siglim.cobros (liga_id, club_id, tipo, concepto, monto, fecha_limite)
                        VALUES (:liga, :club, 'MANUAL', 'Cuota de prueba', 100, DATE '2099-01-01')
                        """)
                .param("liga", LIGA).param("club", club.id()).update();

        assertThatThrownBy(() -> clubes.cambiarNombre(club.id(),
                new CambioNombreClubRequest("Nuevo " + UUID.randomUUID(), MOTIVO_CAMBIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RN-041"));
        sinCambioDeNombre(club.id());
    }

    @Test
    @DisplayName("B-04 · DEC-49, DEC-61: el nombre de un club CERRADO sigue ocupado (409 DUPLICADO)")
    void cambioDeNombreAlDeUnClubCerrado() {
        iniciarSesionComoAdministrador();
        ClubDto cerrado = clubes.crear(new CrearClubRequest("Cerrado " + UUID.randomUUID()));
        clubes.cambiarNombre(cerrado.id(), new CambioNombreClubRequest("Sucesor " + UUID.randomUUID(), MOTIVO_CAMBIO));
        ClubDto otro = clubes.crear(new CrearClubRequest("Otro " + UUID.randomUUID()));

        assertThatThrownBy(() -> clubes.cambiarNombre(otro.id(),
                new CambioNombreClubRequest(cerrado.nombre(), MOTIVO_CAMBIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        sinCambioDeNombre(otro.id());
        assertThatThrownBy(() -> clubes.cambiarNombre(cerrado.id(),
                new CambioNombreClubRequest("Otro más " + UUID.randomUUID(), MOTIVO_CAMBIO)))
                .as("un club cerrado no vuelve a cambiar de nombre")
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RN-041"));
    }

    @Test
    @DisplayName("B-04 · RNF-004: si falla el traslado de asignaciones no queda club nuevo, cierre ni auditoría")
    void cambioDeNombreAtomico() {
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        UUID directiva = perfilDeDirectiva(club.id());
        UUID asignacion = asignacionActiva(directiva, club.id());
        String nombreNuevo = "Nuevo " + UUID.randomUUID();

        // Falla simulada después del INSERT del club y del cierre: al recrear la asignación.
        jdbc.sql("""
                CREATE FUNCTION siglim.prueba_falla_traslado() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.perfil_id = '%s'::uuid THEN
                    RAISE EXCEPTION 'falla simulada del traslado';
                  END IF;
                  RETURN NEW;
                END $$
                """.formatted(directiva)).update();
        jdbc.sql("""
                CREATE TRIGGER tg_prueba_falla_traslado BEFORE INSERT ON siglim.asignaciones_rol
                FOR EACH ROW EXECUTE FUNCTION siglim.prueba_falla_traslado()
                """).update();
        try {
            assertThatThrownBy(() -> clubes.cambiarNombre(club.id(),
                    new CambioNombreClubRequest(nombreNuevo, MOTIVO_CAMBIO)))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.sql("DROP TRIGGER tg_prueba_falla_traslado ON siglim.asignaciones_rol").update();
            jdbc.sql("DROP FUNCTION siglim.prueba_falla_traslado()").update();
        }

        sinCambioDeNombre(club.id());
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.clubes WHERE nombre = :nombre")
                .param("nombre", nombreNuevo).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT activa FROM siglim.asignaciones_rol WHERE id = :id")
                .param("id", asignacion).query(Boolean.class).single()).isTrue();
        assertThat(eventosAsignacion(asignacion)).isEmpty();
    }

    private void cambioRechazadoPorEquipoInscrito(EstadoTemporada estado) {
        UUID grupo = grupoEnTemporada(estado);
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        equipos.crear(new CrearEquipoRequest(club.id(), grupo, "Halcones"));

        assertThatThrownBy(() -> clubes.cambiarNombre(club.id(),
                new CambioNombreClubRequest("Nuevo " + UUID.randomUUID(), MOTIVO_CAMBIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "RN-041").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("1 equipo inscrito");
                });
        sinCambioDeNombre(club.id());
    }

    /** El club sigue ACTIVO, sin sucesor ni evento de cierre. */
    private void sinCambioDeNombre(UUID club) {
        ClubDto actual = clubes.consultar(club);
        assertThat(actual.estado()).isEqualTo(EstadoClub.ACTIVO);
        assertThat(actual.cerradoEn()).isNull();
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.clubes WHERE club_anterior_id = :id")
                .param("id", club).query(Long.class).single()).isZero();
        assertThat(eventos("clubes", club)).extracting(Evento::accion).containsExactly("REGISTRAR_CLUB");
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /** Id de la asignación activa de DIRECTIVA_CLUB del perfil en ese club. */
    private UUID asignacionActiva(UUID perfil, UUID club) {
        return jdbc.sql("""
                        SELECT id FROM siglim.asignaciones_rol
                         WHERE perfil_id = :perfil AND club_id = :club AND ambito = 'CLUB' AND activa
                        """)
                .param("perfil", perfil).param("club", club)
                .query(UUID.class).single();
    }

    /** Acciones de M-01 auditadas sobre una asignación. */
    private List<String> eventosAsignacion(UUID asignacion) {
        return jdbc.sql("""
                        SELECT accion FROM siglim.auditoria
                         WHERE modulo = 'M-01' AND entidad = 'asignaciones_rol' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", asignacion.toString())
                .query(String.class)
                .list();
    }

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

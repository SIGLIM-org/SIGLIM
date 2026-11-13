package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-12.1 contra PostgreSQL real: el alta de jornadas y partidos y el ajuste motivado atraviesan
 * autorización por permiso y ámbito, el trigger del bloque de cancha, el historial del partido y la
 * auditoría. Los conflictos duros los rechaza el trigger hasta que H-12.2 los explique en el servicio.
 */
class ProgramacionIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    private static final String MOTIVO = "Cambio de horario pedido por la Liga";

    @Autowired
    private JornadaService servicio;

    // ── Registro y ajuste (RF-033, RF-036, RF-040) ─────────────────────────────────────────────

    @Test
    @DisplayName("RF-033, RF-036: una jornada en borrador admite partidos y la base calcula su bloque")
    void jornadaYPartidoEnBorrador() {
        UUID programador = iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID local = crearEquipo(grupo, "Halcones");
        UUID visitante = crearEquipo(grupo, "Toros");
        UUID cancha = crearCancha();

        JornadaDto jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1));
        PartidoDto partido = servicio.registrarPartido(jornada.id(),
                new CrearPartidoRequest(local, visitante, cancha, INICIO));

        assertThat(jornada.estadoPublicacion()).isEqualTo(EstadoJornada.BORRADOR);
        assertThat(partido.estado()).isEqualTo(EstadoPartido.PROGRAMADO);
        assertThat(partido.finBloque()).as("bloque de cancha [DEMO] de 120 minutos")
                .isEqualTo(INICIO.plusHours(2));
        assertThat(partido.equipoLocal().nombre()).startsWith("Halcones");
        assertThat(partido.cancha().id()).isEqualTo(cancha);

        JornadaDto detalle = servicio.consultar(jornada.id());
        assertThat(detalle.partidos()).extracting(PartidoDto::id).containsExactly(partido.id());

        assertThat(historialDelPartido(partido.id())).singleElement().satisfies(paso -> {
            assertThat(paso.estadoNuevo()).isEqualTo("PROGRAMADO");
            assertThat(paso.motivo()).isEqualTo("Programación del partido");
            assertThat(paso.cambiadoPor()).isEqualTo(programador);
        });
        assertThat(accionesAuditadas(partido.id()))
                .contains("REGISTRAR_PARTIDO");
        assertThat(accionesAuditadas(jornada.id())).contains("REGISTRAR_JORNADA");
    }

    @Test
    @DisplayName("RF-040, RN-018: el ajuste con motivo mueve el partido y deja el motivo en la auditoría")
    void ajusteConMotivo() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID cancha = crearCancha();
        UUID otraCancha = crearCancha();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        PartidoDto partido = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), cancha, INICIO));
        OffsetDateTime nuevoInicio = INICIO.plusDays(1);

        PartidoDto ajustado = servicio.actualizarPartido(partido.id(), new ActualizarPartidoRequest(
                null, null, otraCancha, nuevoInicio, "  " + MOTIVO + "  "));

        assertThat(ajustado.cancha().id()).isEqualTo(otraCancha);
        assertThat(ajustado.inicio()).isEqualTo(nuevoInicio);
        assertThat(ajustado.finBloque()).isEqualTo(nuevoInicio.plusHours(2));

        record Auditoria(String motivo, String detalle) {
        }
        Auditoria auditoria = jdbc.sql("""
                        SELECT motivo, CAST(detalle AS text) AS detalle
                          FROM siglim.auditoria
                         WHERE entidad = 'partidos' AND entidad_id = :id AND accion = 'AJUSTAR_PARTIDO'
                        """)
                .param("id", partido.id().toString())
                .query(Auditoria.class)
                .single();
        assertThat(auditoria.motivo()).isEqualTo(MOTIVO);
        assertThat(auditoria.detalle()).contains("canchaId", "inicio")
                .doesNotContain("equipoLocalId", "equipoVisitanteId");
    }

    @Test
    @DisplayName("RN-021: un ajuste sin motivo se rechaza y el partido no cambia")
    void ajusteSinMotivo() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 2)).id();
        PartidoDto partido = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), crearCancha(), INICIO));

        assertThatThrownBy(() -> servicio.actualizarPartido(partido.id(),
                new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), "Corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "RN-021"));

        assertThat(servicio.consultar(jornada).partidos().getFirst().inicio()).isEqualTo(INICIO);
    }

    @Test
    @DisplayName("RF-090: en revisión la jornada ya no admite partidos ni ajustes")
    void jornadaEnRevision() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        PartidoDto partido = servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), crearCancha(), INICIO));

        jdbc.sql("UPDATE siglim.jornadas SET estado_publicacion = 'REVISION' WHERE id = :id")
                .param("id", jornada).update();

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Águilas"), crearEquipo(grupo, "Pumas"), crearCancha(), INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));
        assertThatThrownBy(() -> servicio.actualizarPartido(partido.id(),
                new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));

        assertThat(jdbc.sql("SELECT count(*) FROM siglim.partidos WHERE jornada_id = :id")
                .param("id", jornada).query(Long.class).single()).as("no se agregó otro partido").isEqualTo(1L);
    }

    @Test
    @DisplayName("RF-033: un equipo de otro grupo se rechaza con 422 y no queda el partido")
    void equipoDeOtroGrupo() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();

        assertThatThrownBy(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(crearGrupo(), "Ajenos"), crearCancha(), INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "RF-033"));
        assertThat(servicio.consultar(jornada).partidos()).isEmpty();
    }

    // ── El trigger como última garantía (RF-039) ───────────────────────────────────────────────

    @Test
    @DisplayName("VAL-004: la exclusión GiST rechaza dos partidos en la misma cancha y bloque")
    void canchaOcupada() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID cancha = crearCancha();
        servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Halcones"), crearEquipo(grupo, "Toros"), cancha, INICIO));

        PSQLException error = errorBd(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                crearEquipo(grupo, "Águilas"), crearEquipo(grupo, "Pumas"), cancha, INICIO.plusMinutes(30))));

        assertThat(error.getSQLState()).isEqualTo("23P01");
    }

    @Test
    @DisplayName("VAL-003: el trigger rechaza un equipo con otro partido en el mismo bloque")
    void equipoDuplicado() {
        iniciarSesionComoProgramador();
        UUID grupo = crearGrupo();
        UUID jornada = servicio.crear(new CrearJornadaRequest(grupo, null, 1)).id();
        UUID local = crearEquipo(grupo, "Halcones");
        servicio.registrarPartido(jornada, new CrearPartidoRequest(
                local, crearEquipo(grupo, "Toros"), crearCancha(), INICIO));

        PSQLException error = errorBd(() -> servicio.registrarPartido(jornada, new CrearPartidoRequest(
                local, crearEquipo(grupo, "Pumas"), crearCancha(), INICIO.plusHours(1))));

        assertThat(error.getServerErrorMessage().getHint()).isEqualTo("VAL-003");
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private UUID iniciarSesionComoProgramador() {
        UUID perfil = perfilConRol("PROGRAMADOR");
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
        return perfil;
    }

    /** Crea una cuenta con el rol sembrado, con ámbito de liga. */
    private UUID perfilConRol(String codigoRol) {
        UUID usuarioAuth = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO auth.users (id, email, aud, role)
                        VALUES (:id, :correo, 'authenticated', 'authenticated')
                        """)
                .param("id", usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .update();
        UUID perfil = jdbc.sql("""
                        INSERT INTO siglim.perfiles (usuario_auth_id, nombre_mostrado, correo)
                        VALUES (:usuario, :nombre, :correo)
                        RETURNING id
                        """)
                .param("usuario", usuarioAuth)
                .param("nombre", codigoRol + " " + usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, r.id, r.liga_id, 'LIGA'
                          FROM siglim.roles r
                         WHERE r.codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", codigoRol)
                .update();
        return perfil;
    }

    /** Temporada con parámetros, categoría y grupo: lo mínimo para que el trigger calcule el bloque. */
    private UUID crearGrupo() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2031-08-01')
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo) "
                        + "VALUES (:temporada, 1, 'Parámetros de prueba')")
                .param("temporada", temporada).update();
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", "Categoría " + UUID.randomUUID())
                .query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria)
                        RETURNING id
                        """)
                .param("temporada", temporada)
                .param("categoria", categoria)
                .query(UUID.class).single();
        return jdbc.sql("""
                        INSERT INTO siglim.grupos (categoria_temporada_id, nombre)
                        VALUES (:ct, 'A')
                        RETURNING id
                        """)
                .param("ct", categoriaTemporada)
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

    private List<Historial> historialDelPartido(UUID partido) {
        return jdbc.sql("""
                        SELECT estado_nuevo, motivo, cambiado_por
                          FROM siglim.partido_estado_historial
                         WHERE partido_id = :id
                         ORDER BY cambiado_en, id
                        """)
                .param("id", partido)
                .query((fila, numero) -> new Historial(
                        fila.getString("estado_nuevo"), fila.getString("motivo"),
                        fila.getObject("cambiado_por", UUID.class)))
                .list();
    }

    private List<String> accionesAuditadas(UUID entidadId) {
        return jdbc.sql("""
                        SELECT accion FROM siglim.auditoria
                         WHERE entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", entidadId.toString())
                .query(String.class)
                .list();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private static PSQLException errorBd(ThrowingCallable operacion) {
        Throwable error = catchThrowable(operacion);
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof PSQLException psql) {
                return psql;
            }
        }
        throw new AssertionError("Se esperaba un error de PostgreSQL y se obtuvo: " + error, error);
    }

    private record Historial(String estadoNuevo, String motivo, UUID cambiadoPor) {
    }
}

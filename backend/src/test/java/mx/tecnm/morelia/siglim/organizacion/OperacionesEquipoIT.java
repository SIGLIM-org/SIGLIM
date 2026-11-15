package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
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
 * B-05.1 contra PostgreSQL real, con {@link OperacionEquipoService} completo: autorización con la
 * matriz de la migración, SQL, restricciones y auditoría. Demuestra la venta y la conservación de
 * plaza como operaciones independientes, el dictamen del Presidente con su perfil y fecha, el
 * rechazo con observaciones, el 404 para el equipo de otro club y que el equipo no cambia de club al
 * autorizar (RF-103, RF-104, RN-040, VAL-024).
 *
 * <p>La temporada, la categoría y el grupo se crean por SQL como dueño del esquema: su CRUD es de
 * M-02. Antes de cada prueba se cierra cualquier temporada activa para respetar RN-058.
 */
class OperacionesEquipoIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final String OBSERVACIONES = "La asamblea no aprobó la venta";

    @Autowired
    private OperacionEquipoService operaciones;

    @Autowired
    private ClubService clubes;

    @Autowired
    private EquipoService equipos;

    @BeforeEach
    void cerrarTemporadasActivas() {
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE estado = 'ACTIVA'").update();
    }

    // ── Criterios 1, 2 y 3: venta, dictamen y efecto al cierre ───────────────────────────────

    @Test
    @DisplayName("B-05 criterios 1 a 3 · RF-103, RN-040: la Directiva vende y el Presidente autoriza sin mover el equipo")
    void ventaAutorizadaConservaElEquipoEnSuClub() {
        UUID grupo = grupoEnTemporada();
        iniciarSesionComoAdministrador();
        ClubDto origen = clubes.crear(new CrearClubRequest("Origen " + UUID.randomUUID()));
        ClubDto destino = clubes.crear(new CrearClubRequest("Destino " + UUID.randomUUID()));
        EquipoDto equipo = equipos.crear(new CrearEquipoRequest(origen.id(), grupo, "Halcones"));
        UUID directiva = perfilDeDirectiva(origen.id());

        iniciarSesion(directiva);
        OperacionEquipoDto venta = operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, equipo.id(), destino.id(), "Acuerdo de asamblea"));

        assertThat(venta.tipo()).isEqualTo(TipoOperacionEquipo.VENTA);
        assertThat(venta.estado()).isEqualTo(EstadoOperacionEquipo.SOLICITADA);
        assertThat(venta.clubOrigen().id()).isEqualTo(origen.id());
        assertThat(venta.clubDestino().id()).isEqualTo(destino.id());
        assertThat(venta.solicitadaPor().id()).isEqualTo(directiva);
        assertThat(venta.solicitadaEn()).isNotNull();
        assertThat(venta.autorizadaPor()).isNull();
        assertThat(eventos(venta.id())).extracting(Evento::accion)
                .containsExactly("SOLICITAR_OPERACION_EQUIPO");
        assertThat(eventos(venta.id()).getFirst().perfilId()).isEqualTo(directiva);

        assertThatThrownBy(() -> operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, equipo.id(), origen.id(), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "VALIDACION"));

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        OperacionEquipoDto autorizada = operaciones.resolver(venta.id(),
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.AUTORIZADA,
                        "Autorizada en sesión"));

        assertThat(autorizada.estado()).isEqualTo(EstadoOperacionEquipo.AUTORIZADA);
        assertThat(autorizada.autorizadaPor().id()).isEqualTo(presidente);
        assertThat(autorizada.autorizadaEn()).isNotNull();
        assertThat(autorizada.aplicadaEn()).isNull();
        assertThat(autorizada.observaciones()).isEqualTo("Autorizada en sesión");
        assertThat(eventos(venta.id())).extracting(Evento::accion)
                .containsExactly("SOLICITAR_OPERACION_EQUIPO", "AUTORIZAR_OPERACION_EQUIPO");
        assertThat(eventos(venta.id()).getLast().perfilId()).isEqualTo(presidente);

        assertThat(jdbc.sql("SELECT club_id FROM siglim.equipos WHERE id = :id")
                .param("id", equipo.id()).query(UUID.class).single())
                .as("RN-040: el equipo cambia de club al cerrar la temporada, no al autorizar")
                .isEqualTo(origen.id());
        assertThat(jdbc.sql("""
                        SELECT club_origen_id FROM siglim.operaciones_equipo WHERE id = :id
                        """).param("id", venta.id()).query(UUID.class).single())
                .as("el historial de propietarios conserva el club de origen")
                .isEqualTo(origen.id());

        assertThatThrownBy(() -> operaciones.resolver(venta.id(),
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.RECHAZADA, OBSERVACIONES)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-103"));
    }

    @Test
    @DisplayName("B-05 criterios 2 y 4 · RF-104, RN-021: la conservación de plaza es independiente y el rechazo exige observaciones")
    void conservacionIndependienteYRechazo() {
        UUID grupo = grupoEnTemporada();
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        ClubDto destino = clubes.crear(new CrearClubRequest("Destino " + UUID.randomUUID()));
        EquipoDto equipo = equipos.crear(new CrearEquipoRequest(club.id(), grupo, "Halcones"));
        UUID directiva = perfilDeDirectiva(club.id());

        iniciarSesion(directiva);
        OperacionEquipoDto venta = operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, equipo.id(), destino.id(), null));
        OperacionEquipoDto plaza = operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.CONSERVACION_PLAZA, equipo.id(), null, null));

        assertThat(plaza.tipo()).isEqualTo(TipoOperacionEquipo.CONSERVACION_PLAZA);
        assertThat(plaza.estado()).isEqualTo(EstadoOperacionEquipo.SOLICITADA);
        assertThat(plaza.clubOrigen().id()).isEqualTo(club.id());
        assertThat(plaza.clubDestino()).isNull();
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.operaciones_equipo WHERE equipo_id = :id")
                .param("id", equipo.id()).query(Long.class).single())
                .as("la venta y la conservación conviven sin bloquearse")
                .isEqualTo(2);

        assertThatThrownBy(() -> operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.CONSERVACION_PLAZA, equipo.id(), destino.id(), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "VALIDACION"));

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        assertThatThrownBy(() -> operaciones.resolver(plaza.id(),
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.RECHAZADA, "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThat(jdbc.sql("SELECT estado FROM siglim.operaciones_equipo WHERE id = :id")
                .param("id", plaza.id()).query(String.class).single())
                .as("un rechazo inválido no cambia el estado")
                .isEqualTo("SOLICITADA");

        OperacionEquipoDto rechazada = operaciones.resolver(plaza.id(),
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.RECHAZADA, " " + OBSERVACIONES + " "));

        assertThat(rechazada.estado()).isEqualTo(EstadoOperacionEquipo.RECHAZADA);
        assertThat(rechazada.observaciones()).isEqualTo(OBSERVACIONES);
        assertThat(rechazada.autorizadaPor().id()).isEqualTo(presidente);
        assertThat(eventos(plaza.id())).extracting(Evento::accion)
                .containsExactly("SOLICITAR_OPERACION_EQUIPO", "RECHAZAR_OPERACION_EQUIPO");
        assertThat(eventos(plaza.id()).getLast().motivo()).isEqualTo(OBSERVACIONES);
        assertThat(jdbc.sql("SELECT estado FROM siglim.operaciones_equipo WHERE id = :id")
                .param("id", venta.id()).query(String.class).single())
                .as("la conservación rechazada no toca la venta")
                .isEqualTo("SOLICITADA");
    }

    // ── Observaciones de la solicitud y operaciones duplicadas (DEC-132) ──────────────────

    @Test
    @DisplayName("RN-040: autorizar sin nota conserva las observaciones de la solicitud, que también quedan en la auditoría")
    void autorizarSinNotaConservaLasObservaciones() {
        UUID grupo = grupoEnTemporada();
        iniciarSesionComoAdministrador();
        ClubDto origen = clubes.crear(new CrearClubRequest("Origen " + UUID.randomUUID()));
        ClubDto destino = clubes.crear(new CrearClubRequest("Destino " + UUID.randomUUID()));
        EquipoDto equipo = equipos.crear(new CrearEquipoRequest(origen.id(), grupo, "Halcones"));

        iniciarSesion(perfilDeDirectiva(origen.id()));
        OperacionEquipoDto venta = operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, equipo.id(), destino.id(), "Acuerdo de asamblea del 1 de agosto"));

        iniciarSesion(perfilConRol("PRESIDENTE"));
        OperacionEquipoDto autorizada = operaciones.resolver(venta.id(),
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.AUTORIZADA, null));

        assertThat(autorizada.estado()).isEqualTo(EstadoOperacionEquipo.AUTORIZADA);
        assertThat(autorizada.observaciones()).isEqualTo("Acuerdo de asamblea del 1 de agosto");
        assertThat(jdbc.sql("""
                        SELECT detalle ->> 'observaciones' FROM siglim.auditoria
                         WHERE modulo = 'M-03' AND accion = 'SOLICITAR_OPERACION_EQUIPO'
                           AND entidad_id = :id
                        """).param("id", venta.id().toString()).query(String.class).single())
                .isEqualTo("Acuerdo de asamblea del 1 de agosto");
    }

    @Test
    @DisplayName("RF-103, RF-104, DEC-132: un equipo no tiene dos operaciones vigentes del mismo tipo")
    void operacionesVigentesDuplicadas() {
        UUID grupo = grupoEnTemporada();
        iniciarSesionComoAdministrador();
        ClubDto origen = clubes.crear(new CrearClubRequest("Origen " + UUID.randomUUID()));
        ClubDto destino = clubes.crear(new CrearClubRequest("Destino " + UUID.randomUUID()));
        ClubDto otroDestino = clubes.crear(new CrearClubRequest("Otro destino " + UUID.randomUUID()));
        EquipoDto equipo = equipos.crear(new CrearEquipoRequest(origen.id(), grupo, "Halcones"));
        UUID directiva = perfilDeDirectiva(origen.id());
        UUID presidente = perfilConRol("PRESIDENTE");

        iniciarSesion(directiva);
        OperacionEquipoDto venta = operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, equipo.id(), destino.id(), null));
        assertThatThrownBy(() -> operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, equipo.id(), otroDestino.id(), null)))
                .as("venta SOLICITADA")
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-103"));

        iniciarSesion(presidente);
        operaciones.resolver(venta.id(),
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.AUTORIZADA, null));
        iniciarSesion(directiva);
        assertThatThrownBy(() -> operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, equipo.id(), otroDestino.id(), null)))
                .as("venta AUTORIZADA")
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-103"));

        OperacionEquipoDto plaza = operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.CONSERVACION_PLAZA, equipo.id(), null, null));
        assertThatThrownBy(() -> operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.CONSERVACION_PLAZA, equipo.id(), null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-104"));

        iniciarSesion(presidente);
        operaciones.resolver(plaza.id(),
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.RECHAZADA, OBSERVACIONES));
        iniciarSesion(directiva);
        assertThat(operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.CONSERVACION_PLAZA, equipo.id(), null, null)).estado())
                .as("una conservación RECHAZADA ya no cuenta como vigente")
                .isEqualTo(EstadoOperacionEquipo.SOLICITADA);
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.operaciones_equipo WHERE equipo_id = :id")
                .param("id", equipo.id()).query(Long.class).single()).isEqualTo(3);
    }

    // ── Criterio 5: ámbito de club ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-05 criterio 5 · VAL-024, VAL-015: otro club no registra la operación (404) ni la resuelve (403)")
    void otroClubNoVeLaOperacion() {
        UUID grupo = grupoEnTemporada();
        iniciarSesionComoAdministrador();
        ClubDto origen = clubes.crear(new CrearClubRequest("Origen " + UUID.randomUUID()));
        ClubDto destino = clubes.crear(new CrearClubRequest("Destino " + UUID.randomUUID()));
        EquipoDto equipo = equipos.crear(new CrearEquipoRequest(origen.id(), grupo, "Halcones"));

        iniciarSesion(perfilDeDirectiva(origen.id()));
        OperacionEquipoDto venta = operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, equipo.id(), destino.id(), null));

        iniciarSesion(perfilDeDirectiva(destino.id()));
        assertThatThrownBy(() -> operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, equipo.id(), destino.id(), null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> operaciones.resolver(venta.id(),
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.AUTORIZADA, null)))
                .as("la Directiva no tiene organizacion.venta:APROBAR")
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));

        iniciarSesion(perfilConRol("PRESIDENTE"));
        assertThat(operaciones.resolver(venta.id(),
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.AUTORIZADA, null)).estado())
                .isEqualTo(EstadoOperacionEquipo.AUTORIZADA);
    }

    // ── Temporada cerrada y equipo de baja ─────────────────────────────────────────────────

    @Test
    @DisplayName("RF-003, RF-009: una temporada cerrada o un equipo de baja no admiten operaciones")
    void temporadaCerradaYEquipoDeBaja() {
        UUID grupo = grupoEnTemporada();
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        ClubDto destino = clubes.crear(new CrearClubRequest("Destino " + UUID.randomUUID()));
        EquipoDto activo = equipos.crear(new CrearEquipoRequest(club.id(), grupo, "Halcones"));
        EquipoDto retirado = equipos.crear(new CrearEquipoRequest(club.id(), grupo, "Toros"));
        equipos.darDeBaja(retirado.id(),
                new BajaEquipoRequest("El club retiró al equipo de la categoría"));

        iniciarSesion(perfilDeDirectiva(club.id()));
        assertThatThrownBy(() -> operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, retirado.id(), destino.id(), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-009"));

        UUID temporada = jdbc.sql("""
                        SELECT ct.temporada_id FROM siglim.grupos g
                          JOIN siglim.categorias_temporada ct ON ct.id = g.categoria_temporada_id
                         WHERE g.id = :grupo
                        """).param("grupo", grupo).query(UUID.class).single();
        iniciarSesionComoAdministrador();
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE id = :id")
                .param("id", temporada).update();

        iniciarSesion(perfilDeDirectiva(club.id()));
        assertThatThrownBy(() -> operaciones.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, activo.id(), destino.id(), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.operaciones_equipo WHERE equipo_id = :id")
                .param("id", activo.id()).query(Long.class).single()).isZero();
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /** Grupo «A» de una temporada nueva en {@code ACTIVA}. */
    private UUID grupoEnTemporada() {
        UUID temporada = temporada();
        UUID grupo = grupo(temporada, "A");
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'ACTIVA' WHERE id = :id")
                .param("id", temporada).update();
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

    private List<Evento> eventos(UUID operacion) {
        return jdbc.sql("""
                        SELECT accion, perfil_id, motivo
                          FROM siglim.auditoria
                         WHERE modulo = 'M-03' AND entidad = 'operaciones_equipo' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", operacion.toString())
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

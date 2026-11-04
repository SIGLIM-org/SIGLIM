package mx.tecnm.morelia.siglim.canchas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * B-11.1 contra PostgreSQL real, con {@link SedeService} completo: autorización, SQL y la restricción
 * {@code UNIQUE (liga_id, nombre)}. Cada prueba usa nombres nuevos.
 */
class SedesIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Autowired
    private SedeService servicio;

    @Test
    @DisplayName("RF-028: una sede registrada queda disponible en el catálogo, ordenado por nombre")
    void registrarYListar() {
        iniciarSesionComoAdministrador();
        String token = UUID.randomUUID().toString();

        SedeDto zeta = servicio.crear(new CrearSedeRequest("Zeta " + token, " Av. Uno 1 "));
        SedeDto alfa = servicio.crear(new CrearSedeRequest(" Alfa " + token, null));

        assertThat(zeta.direccion()).isEqualTo("Av. Uno 1");
        assertThat(alfa.nombre()).isEqualTo("Alfa " + token);
        List<SedeDto> catalogo = servicio.listar().stream()
                .filter(sede -> sede.nombre().endsWith(token)).toList();
        assertThat(catalogo).containsExactly(alfa, zeta);
    }

    @Test
    @DisplayName("RF-028: un nombre repetido en la liga responde 409 DUPLICADO y no se guarda")
    void nombreRepetido() {
        iniciarSesionComoAdministrador();
        SedeDto existente = servicio.crear(new CrearSedeRequest("Repetida " + UUID.randomUUID(), null));

        assertThatThrownBy(() -> servicio.crear(new CrearSedeRequest(existente.nombre(), "Otra dirección")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"))
                .hasMessageContaining(existente.nombre());
        assertThat(contarConNombre(existente.nombre())).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-028: renombrar a un nombre ocupado responde 409 DUPLICADO; al mismo nombre no hay conflicto")
    void renombrar() {
        iniciarSesionComoAdministrador();
        SedeDto norte = servicio.crear(new CrearSedeRequest("Norte " + UUID.randomUUID(), null));
        SedeDto sur = servicio.crear(new CrearSedeRequest("Sur " + UUID.randomUUID(), null));

        assertThatThrownBy(() -> servicio.actualizar(sur.id(), new ActualizarSedeRequest(norte.nombre(), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        assertThat(servicio.actualizar(norte.id(), new ActualizarSedeRequest(norte.nombre(), null))).isEqualTo(norte);

        String nuevo = "Oriente " + UUID.randomUUID();
        assertThat(servicio.actualizar(sur.id(), new ActualizarSedeRequest(nuevo, null)).nombre()).isEqualTo(nuevo);
    }

    @Test
    @DisplayName("RF-028: la restricción UNIQUE (liga_id, nombre) es la última garantía: 23505")
    void restriccionUnica() {
        iniciarSesionComoAdministrador();
        SedeDto existente = servicio.crear(new CrearSedeRequest("Única " + UUID.randomUUID(), null));

        Throwable error = catchThrowable(() -> jdbc.sql(
                        "INSERT INTO siglim.sedes (liga_id, nombre) VALUES (:liga, :nombre)")
                .param("liga", LIGA).param("nombre", existente.nombre()).update());

        assertThat(sqlState(error)).isEqualTo("23505");
    }

    @Test
    @DisplayName("VAL-024: una sede inexistente responde 404 al editar")
    void sedeInexistente() {
        iniciarSesionComoAdministrador();

        assertThatThrownBy(() -> servicio.actualizar(UUID.randomUUID(), new ActualizarSedeRequest("Otra", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("RF-093: el alta y la edición se auditan sin la dirección")
    void auditoria() {
        iniciarSesionComoAdministrador();
        SedeDto sede = servicio.crear(new CrearSedeRequest("Auditada " + UUID.randomUUID(), "Calle Secreta 123"));
        servicio.actualizar(sede.id(), new ActualizarSedeRequest(null, "Calle Nueva 45"));

        record Fila(String accion, String detalle) {
        }
        List<Fila> filas = jdbc.sql("""
                        SELECT accion, CAST(detalle AS text) AS detalle
                          FROM siglim.auditoria
                         WHERE modulo = 'M-06' AND entidad = 'sedes' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", sede.id().toString())
                .query(Fila.class)
                .list();

        assertThat(filas).extracting(Fila::accion).containsExactly("REGISTRAR_SEDE", "EDITAR_SEDE");
        assertThat(filas).allSatisfy(fila -> assertThat(fila.detalle()).doesNotContain("Secreta", "Nueva"));
    }

    @Test
    @DisplayName("VAL-015: el Programador lista las sedes pero no las registra (D1)")
    void programadorSoloLee() {
        iniciarSesionComoAdministrador();
        SedeDto sede = servicio.crear(new CrearSedeRequest("Visible " + UUID.randomUUID(), null));
        iniciarSesion(perfilConRol("PROGRAMADOR"));

        assertThat(servicio.listar()).contains(sede);
        assertThatThrownBy(() -> servicio.crear(new CrearSedeRequest("No debe " + UUID.randomUUID(), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThatThrownBy(() -> servicio.actualizar(sede.id(), new ActualizarSedeRequest("No debe", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
    }

    @Test
    @DisplayName("VAL-015: un rol sin canchas.* no lista las sedes")
    void rolSinPermisos() {
        iniciarSesion(perfilConRol("CAPTURISTA"));

        assertThatThrownBy(servicio::listar)
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private long contarConNombre(String nombre) {
        return jdbc.sql("SELECT count(*) FROM siglim.sedes WHERE liga_id = :liga AND nombre = :nombre")
                .param("liga", LIGA).param("nombre", nombre).query(Long.class).single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado. */
    private UUID perfilConRol(String rol) {
        UUID perfil = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
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
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    /** SQLSTATE del error de PostgreSQL de la cadena de causas; nunca se compara el texto. */
    private static String sqlState(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof PSQLException psql) {
                return psql.getSQLState();
            }
        }
        throw new AssertionError("Se esperaba un error de PostgreSQL y se obtuvo: " + error, error);
    }
}

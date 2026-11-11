package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
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
 * H-11.1 contra PostgreSQL real, con {@link ArbitroService} completo: autorización con la matriz de
 * la migración, SQL, restricciones y auditoría. Demuestra los criterios de H-11 que cubren los
 * endpoints: el club presenta al árbitro y queda {@code PROPUESTO}; la Comisión lo valida o rechaza
 * con observaciones; solo los {@code VALIDADO} entran al catálogo de designación; y el ámbito de
 * club impide ver u operar árbitros de otro club (RF-021, RF-112, RF-113, VAL-024).
 */
class ArbitrosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final String OBSERVACIONES = "No acreditó la constancia de la escuela de árbitros";

    @Autowired
    private ArbitroService arbitros;

    // ── Criterios 1 y 2: presentación, validación y auditoría ───────────────────────────────────

    @Test
    @DisplayName("H-11 criterios 1 y 2 · RF-112, RF-113: la Directiva presenta y la Comisión valida")
    void directivaPresentaYComisionValida() {
        Club club = club("Halcones");
        UUID persona = persona();
        UUID directiva = perfilDeDirectiva(club.id());

        iniciarSesion(directiva);
        ArbitroDto propuesto = arbitros.crear(new CrearArbitroRequest(persona, club.id()));

        assertThat(propuesto.estado()).isEqualTo(EstadoArbitro.PROPUESTO);
        assertThat(propuesto.club().id()).isEqualTo(club.id());
        assertThat(propuesto.persona().id()).isEqualTo(persona);
        assertThat(propuesto.propuestoPor().id()).isEqualTo(directiva);
        assertThat(propuesto.validadoPor()).isNull();
        assertThat(eventos(propuesto.id())).extracting(Evento::accion)
                .containsExactly("PROPONER_ARBITRO");
        assertThat(eventos(propuesto.id()).getFirst().perfilId()).isEqualTo(directiva);

        assertThatThrownBy(() -> arbitros.crear(new CrearArbitroRequest(persona, club.id())))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "DUPLICADO").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("Halcones").contains("propuesto");
                });

        UUID comision = perfilConRol("COMISION_ARBITRAJE");
        iniciarSesion(comision);
        ArbitroDto validado = arbitros.validar(propuesto.id(),
                new ValidarArbitroRequest(DecisionArbitro.VALIDADO, "Cumple los requisitos"));

        assertThat(validado.estado()).isEqualTo(EstadoArbitro.VALIDADO);
        assertThat(validado.validadoPor().id()).isEqualTo(comision);
        assertThat(validado.validadoEn()).isNotNull();
        assertThat(validado.observaciones()).isEqualTo("Cumple los requisitos");
        assertThat(eventos(propuesto.id())).extracting(Evento::accion)
                .containsExactly("PROPONER_ARBITRO", "VALIDAR_ARBITRO");
        assertThat(eventos(propuesto.id()).getLast().perfilId()).isEqualTo(comision);

        assertThatThrownBy(() -> arbitros.validar(propuesto.id(),
                new ValidarArbitroRequest(DecisionArbitro.RECHAZADO, OBSERVACIONES)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "RF-113"));
    }

    @Test
    @DisplayName("H-11 criterio 2 · RF-113: el rechazo exige observaciones y las guarda con el motivo")
    void comisionRechazaConObservaciones() {
        Club club = club("Toros");
        UUID persona = persona();

        iniciarSesion(perfilDeDirectiva(club.id()));
        ArbitroDto propuesto = arbitros.crear(new CrearArbitroRequest(persona, club.id()));

        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        assertThatThrownBy(() -> arbitros.validar(propuesto.id(),
                new ValidarArbitroRequest(DecisionArbitro.RECHAZADO, "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "RF-113"));
        assertThat(estadoEnBase(propuesto.id())).isEqualTo("PROPUESTO");

        ArbitroDto rechazado = arbitros.validar(propuesto.id(),
                new ValidarArbitroRequest(DecisionArbitro.RECHAZADO, " " + OBSERVACIONES + " "));

        assertThat(rechazado.estado()).isEqualTo(EstadoArbitro.RECHAZADO);
        assertThat(rechazado.observaciones()).isEqualTo(OBSERVACIONES);
        List<Evento> eventos = eventos(propuesto.id());
        assertThat(eventos).extracting(Evento::accion)
                .containsExactly("PROPONER_ARBITRO", "RECHAZAR_ARBITRO");
        assertThat(eventos.getLast().motivo()).isEqualTo(OBSERVACIONES);
    }

    // ── Criterio 3: ámbito por club ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("H-11 criterio 3 · RF-021, VAL-024: la Directiva solo ve y opera los árbitros de su club")
    void directivaSoloVeSuClub() {
        Club propio = club("Propio");
        Club ajeno = club("Ajeno");
        UUID personaPropia = persona();
        UUID personaAjena = persona();
        UUID directivaPropia = perfilDeDirectiva(propio.id());
        UUID directivaAjena = perfilDeDirectiva(ajeno.id());

        iniciarSesion(directivaPropia);
        ArbitroDto dePropio = arbitros.crear(new CrearArbitroRequest(personaPropia, propio.id()));

        iniciarSesion(directivaAjena);
        ArbitroDto delAjeno = arbitros.crear(new CrearArbitroRequest(personaAjena, ajeno.id()));

        iniciarSesion(directivaPropia);
        assertThat(arbitros.listar(new FiltroArbitros(null, null, 0, 100)).contenido())
                .extracting(ArbitroDto::id).containsExactly(dePropio.id());
        assertThat(arbitros.listar(new FiltroArbitros(null, null, 0, 100)).totalElementos())
                .isEqualTo(1);
        assertThatThrownBy(() -> arbitros.crear(new CrearArbitroRequest(persona(), ajeno.id())))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> arbitros.validar(dePropio.id(),
                new ValidarArbitroRequest(DecisionArbitro.VALIDADO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "VAL-015"));

        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        assertThat(arbitros.listar(new FiltroArbitros(null, null, 0, 100)).contenido())
                .extracting(ArbitroDto::id).containsExactlyInAnyOrder(dePropio.id(), delAjeno.id());
        assertThat(arbitros.listar(new FiltroArbitros(null, null, 0, 100)).totalElementos())
                .isEqualTo(2);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private Club club(String nombre) {
        UUID id = jdbc.sql("""
                        INSERT INTO clubes (liga_id, nombre)
                        VALUES (:liga, :nombre)
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", nombre + " " + UUID.randomUUID())
                .query(UUID.class)
                .single();
        return new Club(id, nombre);
    }

    /** Persona con una CURP válida y única; su identidad es global (RF-010, DEC-10). */
    private UUID persona() {
        String sufijo = "" + letra() + letra() + letra();
        return jdbc.sql("""
                        INSERT INTO personas (curp, nombre, apellido_paterno, fecha_nacimiento)
                        VALUES (:curp, 'Carlos Alberto', 'Prado', :nacimiento)
                        RETURNING id
                        """)
                .param("curp", "GAPA800101HMN" + sufijo + "01")
                .param("nacimiento", LocalDate.of(1980, 1, 1))
                .query(UUID.class)
                .single();
    }

    private static char letra() {
        return (char) ('A' + ThreadLocalRandom.current().nextInt(26));
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class)
                .single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado. */
    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", rol)
                .update();
        return perfil;
    }

    /** Perfil con el rol DIRECTIVA_CLUB de la matriz, en ámbito CLUB de ese club. */
    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club FROM roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil)
                .param("club", club)
                .update();
        return perfil;
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private String estadoEnBase(UUID arbitro) {
        return jdbc.sql("SELECT estado::text FROM arbitros WHERE id = :id")
                .param("id", arbitro)
                .query(String.class)
                .single();
    }

    private List<Evento> eventos(UUID arbitro) {
        return jdbc.sql("""
                        SELECT accion, perfil_id, motivo
                          FROM auditoria
                         WHERE modulo = 'M-05' AND entidad = 'arbitros' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", arbitro.toString())
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

    private record Club(UUID id, String nombre) {
    }

    private record Evento(String accion, UUID perfilId, String motivo) {
    }
}

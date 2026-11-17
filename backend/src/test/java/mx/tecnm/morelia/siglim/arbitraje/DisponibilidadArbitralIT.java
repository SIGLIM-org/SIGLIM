package mx.tecnm.morelia.siglim.arbitraje;

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
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * B-09.1 contra PostgreSQL real, con {@link DisponibilidadArbitralService} completo: autorización
 * con la matriz de la migración, SQL sobre {@code tstzrange}, regla de «solo validados» y auditoría.
 *
 * <p>La Comisión (ámbito {@code LIGA}) registra, consulta y retira para cualquier árbitro; el
 * árbitro (ámbito {@code PROPIO}) solo para sí mismo y recibe {@code 404} con otro (VAL-024).
 */
class DisponibilidadArbitralIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime DESDE = OffsetDateTime.parse("2030-08-10T09:00:00-06:00");
    private static final OffsetDateTime HASTA = OffsetDateTime.parse("2030-08-10T12:00:00-06:00");

    @Autowired
    private DisponibilidadArbitralService servicio;

    /** Datos creados por la prueba; se retiran para no contaminar a las demás clases *IT. */
    private final List<UUID> arbitrosCreados = new ArrayList<>();
    private final List<UUID> clubesCreados = new ArrayList<>();

    @AfterEach
    void limpiarDatosCreados() {
        for (UUID arbitro : arbitrosCreados) {
            jdbc.sql("DELETE FROM siglim.disponibilidad_arbitral WHERE arbitro_id = :id")
                    .param("id", arbitro).update();
            jdbc.sql("DELETE FROM siglim.arbitros WHERE id = :id").param("id", arbitro).update();
        }
        for (UUID club : clubesCreados) {
            jdbc.sql("DELETE FROM siglim.clubes WHERE id = :id").param("id", club).update();
        }
        arbitrosCreados.clear();
        clubesCreados.clear();
    }

    @Test
    @DisplayName("B-09.1 · RF-022: la Comisión registra, filtra por rango, retira y audita")
    void comisionGestionaLaDisponibilidad() {
        Club club = club("Arbitraje");
        UUID arbitro = arbitro(club.id(), persona(), EstadoArbitro.VALIDADO);
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));

        DisponibilidadArbitralDto registro = servicio.registrar(arbitro,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, HASTA), null));

        assertThat(registro.arbitroId()).isEqualTo(arbitro);
        assertThat(registro.periodo().desde().toInstant()).isEqualTo(DESDE.toInstant());
        assertThat(registro.periodo().hasta().toInstant()).isEqualTo(HASTA.toInstant());
        assertThat(registro.disponible()).isTrue();

        assertThat(servicio.listar(arbitro, null, null)).extracting(DisponibilidadArbitralDto::id)
                .containsExactly(registro.id());
        assertThat(servicio.listar(arbitro, DESDE.minusHours(1), DESDE.plusHours(1))).hasSize(1);
        assertThat(servicio.listar(arbitro, HASTA, HASTA.plusHours(2))).as("hasta es exclusivo").isEmpty();

        servicio.eliminar(arbitro, registro.id());

        assertThat(servicio.listar(arbitro, null, null)).isEmpty();
        assertThat(eventos(registro.id())).extracting(Evento::accion)
                .containsExactly("REGISTRAR_DISPONIBILIDAD_ARBITRAL", "ELIMINAR_DISPONIBILIDAD_ARBITRAL");
    }

    @Test
    @DisplayName("B-09.1 · RF-022: el árbitro registra su disponibilidad y no la de otro")
    void arbitroSoloGestionaLaPropia() {
        Club club = club("Árbitros propios");
        UUID persona = persona();
        UUID arbitro = arbitro(club.id(), persona, EstadoArbitro.VALIDADO);
        UUID arbitroAjeno = arbitro(club.id(), persona(), EstadoArbitro.VALIDADO);
        iniciarSesion(perfilConRolYPersona("ARBITRO", persona));

        DisponibilidadArbitralDto registro = servicio.registrar(arbitro,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, HASTA), false));
        assertThat(registro.disponible()).isFalse();

        assertThatThrownBy(() -> servicio.registrar(arbitroAjeno,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, HASTA), null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.listar(arbitroAjeno, null, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("B-09.1 · RF-022: solo un árbitro VALIDADO recibe disponibilidad")
    void soloValidados() {
        Club club = club("Propuestos");
        UUID arbitro = arbitro(club.id(), persona(), EstadoArbitro.PROPUESTO);
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));

        assertThatThrownBy(() -> servicio.registrar(arbitro,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, HASTA), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "RF-022"));
        assertThat(servicio.listar(arbitro, null, null)).isEmpty();
    }

    @Test
    @DisplayName("B-09.1 · RF-022: un periodo vacío o invertido responde 400 y no se guarda")
    void periodoInvalido() {
        Club club = club("Periodos");
        UUID arbitro = arbitro(club.id(), persona(), EstadoArbitro.VALIDADO);
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));

        assertThatThrownBy(() -> servicio.registrar(arbitro,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, DESDE), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.registrar(arbitro,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(HASTA, DESDE), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThat(servicio.listar(arbitro, null, null)).isEmpty();
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private UUID arbitro(UUID club, UUID persona, EstadoArbitro estado) {
        UUID id = jdbc.sql("""
                        INSERT INTO siglim.arbitros (persona_id, club_id, estado)
                        VALUES (:persona, :club, CAST(:estado AS siglim.estado_arbitro))
                        RETURNING id
                        """)
                .param("persona", persona)
                .param("club", club)
                .param("estado", estado.name())
                .query(UUID.class)
                .single();
        arbitrosCreados.add(id);
        return id;
    }

    private Club club(String nombre) {
        UUID id = jdbc.sql("""
                        INSERT INTO siglim.clubes (liga_id, nombre)
                        VALUES (:liga, :nombre)
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", nombre + " " + UUID.randomUUID())
                .query(UUID.class)
                .single();
        clubesCreados.add(id);
        return new Club(id);
    }

    /** Persona con una CURP válida y única; su identidad es global (RF-010, DEC-10). */
    private UUID persona() {
        String sufijo = "" + letra() + letra() + letra();
        return jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, fecha_nacimiento)
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
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class)
                .single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado. */
    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", rol)
                .update();
        return perfil;
    }

    /** Perfil del árbitro: la persona del catálogo queda vinculada a su cuenta (ámbito PROPIO). */
    private UUID perfilConRolYPersona(String rol, UUID persona) {
        UUID perfil = jdbc.sql("""
                        INSERT INTO siglim.perfiles (persona_id, nombre_mostrado)
                        VALUES (:persona, 'Árbitro de prueba')
                        RETURNING id
                        """)
                .param("persona", persona)
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", rol)
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

    private List<Evento> eventos(UUID disponibilidad) {
        return jdbc.sql("""
                        SELECT accion, perfil_id
                          FROM siglim.auditoria
                         WHERE modulo = 'M-05' AND entidad = 'disponibilidad_arbitral'
                           AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", disponibilidad.toString())
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

    private record Club(UUID id) {
    }

    private record Evento(String accion, UUID perfilId) {
    }
}

package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DEF-10.1 de punta a punta contra PostgreSQL con RLS: la invitación con CURP deja el perfil
 * vinculado y, con la sesión de la cuenta nueva, {@code persona_usuario()} devuelve la persona y
 * {@code es_titular()} reconoce a la persona y al menor bajo su tutela vigente (H-24, DEC-139).
 *
 * <p>Supabase Auth se sustituye por un {@link InvitadorAuth} simulado que devuelve una cuenta ya
 * creada en {@code auth.users}. El servicio se arma a mano con los beans reales para no crear otro
 * contexto de Spring (un {@code @MockitoBean} lo haría) y corre dentro de una transacción del
 * {@code GestorTransaccionesSiglim}, igual que con {@code @Transactional}.
 */
class InvitacionConPersonaIT extends BaseDatosPrueba {

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private RolRepository roles;

    @Autowired
    private Autorizacion autorizacion;

    @Autowired
    private AuditoriaService auditoria;

    @Autowired
    private SiglimPropiedades propiedades;

    @Autowired
    private PlatformTransactionManager transacciones;

    private InvitadorSimulado invitador;
    private UsuarioService servicio;

    @BeforeEach
    void armarServicio() {
        invitador = new InvitadorSimulado();
        servicio = new UsuarioService(usuarios, roles, autorizacion, auditoria, invitador, propiedades);
    }

    @Test
    @DisplayName("DEF-10.1 · H-24: tras invitar con CURP, persona_usuario() y es_titular() funcionan con la cuenta nueva")
    void invitarConCurpHabilitaElAmbitoPropio() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String curp = curpAlAzar();
        UUID tutor = crearPersona(curp, LocalDate.parse("1985-03-03"));
        UUID menor = crearPersona(curpAlAzar(), LocalDate.now().minusYears(10));
        UUID ajena = crearPersona(curpAlAzar(), LocalDate.parse("1990-01-01"));
        jdbc.sql("INSERT INTO siglim.tutelas (menor_id, tutor_id, parentesco) VALUES (:menor, :tutor, 'Madre')")
                .param("menor", menor).param("tutor", tutor).update();
        String correo = "tutor-" + sufijo + "@ejemplo.mx";
        invitador.cuenta = crearCuentaAuth(correo);

        iniciarSesionComoAdministrador();
        UsuarioDto creado = enTransaccion(() -> servicio.invitar(new CrearUsuarioRequest("Tutor " + sufijo,
                correo, List.of(new AsignacionRequest(rolId("JUGADOR_TUTOR"), null, null)),
                " " + curp.toLowerCase() + " ")));

        assertThat(creado.personaId()).isEqualTo(tutor);
        assertThat(creado.asignaciones()).singleElement()
                .satisfies(asignacion -> assertThat(asignacion.ambito()).isEqualTo("PROPIO"));
        String detalle = jdbc.sql("""
                        SELECT detalle::text FROM siglim.auditoria
                         WHERE accion = 'INVITAR_USUARIO' AND entidad_id = :perfil
                        """)
                .param("perfil", creado.id().toString()).query(String.class).single();
        assertThat(detalle).contains(tutor.toString()).doesNotContain(curp, correo, "Tutor " + sufijo);

        iniciarSesionComo(creado.id());
        TransactionTemplate lectura = new TransactionTemplate(transacciones);
        lectura.setReadOnly(true);
        lectura.executeWithoutResult(estado -> {
            assertThat(jdbc.sql("SELECT siglim.persona_usuario()").query(UUID.class).single()).isEqualTo(tutor);
            assertThat(esTitular(tutor)).isTrue();
            assertThat(esTitular(menor)).isTrue();
            assertThat(esTitular(ajena)).isFalse();
        });
    }

    @Test
    @DisplayName("DEF-10.1: una segunda invitación con la misma CURP responde 409 sin llamar a Supabase Auth")
    void segundaInvitacionConLaMismaCurp() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String curp = curpAlAzar();
        crearPersona(curp, LocalDate.parse("1985-03-03"));
        invitador.cuenta = crearCuentaAuth("primera-" + sufijo + "@ejemplo.mx");
        UUID rol = rolId("JUGADOR_TUTOR");

        iniciarSesionComoAdministrador();
        UsuarioDto primera = enTransaccion(() -> servicio.invitar(new CrearUsuarioRequest("Primera " + sufijo,
                "primera-" + sufijo + "@ejemplo.mx", List.of(new AsignacionRequest(rol, null, null)), curp)));
        int invitacionesPrevias = invitador.invitados.size();

        assertThatThrownBy(() -> enTransaccion(() -> servicio.invitar(new CrearUsuarioRequest(
                "Segunda " + sufijo, "segunda-" + sufijo + "@ejemplo.mx",
                List.of(new AsignacionRequest(rol, null, null)), curp))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo(ErroresTecnicos.DUPLICADO);
                    assertThat(error.propiedades()).containsEntry("campo", "curp")
                            .containsEntry("perfilId", primera.id());
                });
        assertThat(invitador.invitados).hasSize(invitacionesPrevias);
        assertThat(invitador.eliminados).isEmpty();
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /** Supabase Auth simulado: devuelve una cuenta ya creada y anota las llamadas. */
    private static final class InvitadorSimulado implements InvitadorAuth {
        private UUID cuenta;
        private final List<String> invitados = new ArrayList<>();
        private final List<UUID> eliminados = new ArrayList<>();

        @Override
        public UUID invitar(String correo) {
            invitados.add(correo);
            return cuenta;
        }

        @Override
        public void eliminar(UUID usuarioAuthId) {
            eliminados.add(usuarioAuthId);
        }
    }

    private <T> T enTransaccion(Supplier<T> accion) {
        return new TransactionTemplate(transacciones).execute(estado -> accion.get());
    }

    private boolean esTitular(UUID persona) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT siglim.es_titular(:persona)")
                .param("persona", persona).query(Boolean.class).single());
    }

    private static void iniciarSesionComo(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private static String curpAlAzar() {
        ThreadLocalRandom azar = ThreadLocalRandom.current();
        return "PEAA" + String.format("%06d", azar.nextInt(1_000_000)) + "HMNRRN"
                + (char) ('A' + azar.nextInt(26)) + azar.nextInt(10);
    }

    private UUID crearPersona(String curp, LocalDate nacimiento) {
        return jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, apellido_materno, fecha_nacimiento)
                        VALUES (:curp, 'Persona', 'Invitada', 'Prueba', :nacimiento) RETURNING id
                        """)
                .param("curp", curp).param("nacimiento", nacimiento).query(UUID.class).single();
    }

    private UUID crearCuentaAuth(String correo) {
        return jdbc.sql("""
                        INSERT INTO auth.users (id, email, aud, role)
                        VALUES (gen_random_uuid(), :correo, 'authenticated', 'authenticated')
                        RETURNING id
                        """)
                .param("correo", correo).query(UUID.class).single();
    }

    private UUID rolId(String codigo) {
        return jdbc.sql("SELECT id FROM siglim.roles WHERE codigo = :codigo")
                .param("codigo", codigo).query(UUID.class).single();
    }
}

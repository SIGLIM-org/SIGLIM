package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.AutorizacionRepositorio.AsignacionPermiso;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Regla de ámbito de {@code autorizacion.exigir} sin Spring ni base de datos: el repositorio se
 * sustituye por un doble, así que la parte que aquí se prueba es la decisión (LIGA, CLUB, PROPIO,
 * denegación por defecto) y no el SQL. La consulta real se prueba en {@link AutorizacionIT}.
 */
class AutorizacionJdbcTest {

    private static final UUID PERFIL = UUID.fromString("0b7c3e1a-2d4f-4a6b-9c8d-7e6f5a4b3c2d");
    private static final UUID CLUB_PROPIO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID CLUB_AJENO = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PERSONA_PROPIA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID PERSONA_MENOR = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID PERSONA_COMPANERO = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID PERSONA_ARBITRO = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final String PERMISO = "registros.registro:CONSULTAR";

    private final AutorizacionRepositorio repositorio = mock(AutorizacionRepositorio.class);
    private final Autorizacion autorizacion = new AutorizacionJdbc(repositorio);

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    /** Caso de la tabla: la asignación que otorga el permiso, el objetivo evaluado y qué responde. */
    record Caso(String descripcion, AsignacionPermiso asignacion, ObjetivoAutorizacion objetivo, boolean titular) {

        @Override
        public String toString() {
            return descripcion;
        }
    }

    static Stream<Caso> casosQueAlcanzan() {
        return Stream.of(
                new Caso("LIGA alcanza un recurso de liga", liga(), ObjetivoAutorizacion.deLiga(), false),
                new Caso("LIGA alcanza el club de otro", liga(), ObjetivoAutorizacion.deClubes(CLUB_AJENO), false),
                new Caso("LIGA alcanza la persona de otro", liga(),
                        ObjetivoAutorizacion.dePersonas(PERSONA_MENOR), false),
                new Caso("CLUB alcanza su propio club", club(CLUB_PROPIO),
                        ObjetivoAutorizacion.deClubes(CLUB_PROPIO), false),
                new Caso("CLUB alcanza el partido de su club contra otro", club(CLUB_PROPIO),
                        ObjetivoAutorizacion.deClubes(CLUB_PROPIO, CLUB_AJENO), false),
                new Caso("PROPIO alcanza al titular mismo", propio(),
                        ObjetivoAutorizacion.dePersonas(PERSONA_PROPIA), true),
                new Caso("PROPIO alcanza al menor bajo tutela", propio(),
                        ObjetivoAutorizacion.dePersonas(PERSONA_MENOR), true),
                new Caso("CLUB alcanza el registro de su club y de su jugador", club(CLUB_PROPIO),
                        ObjetivoAutorizacion.deClubesYPersonas(List.of(CLUB_PROPIO), List.of(PERSONA_MENOR)), false),
                new Caso("PROPIO alcanza el partido de su equipo", propio(),
                        ObjetivoAutorizacion.deClubesYPersonas(List.of(CLUB_PROPIO, CLUB_AJENO),
                                List.of(PERSONA_COMPANERO, PERSONA_PROPIA)), true),
                new Caso("PROPIO alcanza el partido donde está designado como árbitro", propio(),
                        ObjetivoAutorizacion.deClubesYPersonas(List.of(CLUB_AJENO),
                                List.of(PERSONA_ARBITRO)), true));
    }

    static Stream<Caso> casosFueraDeAlcance() {
        return Stream.of(
                new Caso("CLUB no alcanza el club de otro", club(CLUB_PROPIO),
                        ObjetivoAutorizacion.deClubes(CLUB_AJENO), false),
                new Caso("CLUB no alcanza un recurso de liga", club(CLUB_PROPIO),
                        ObjetivoAutorizacion.deLiga(), false),
                new Caso("PROPIO no alcanza la persona de otro", propio(),
                        ObjetivoAutorizacion.dePersonas(PERSONA_MENOR), false),
                new Caso("PROPIO sin ser titular no alcanza nada", propio(),
                        ObjetivoAutorizacion.dePersonas(PERSONA_PROPIA), false),
                new Caso("PROPIO no alcanza un recurso de liga", propio(),
                        ObjetivoAutorizacion.deLiga(), false),
                new Caso("un ámbito desconocido no autoriza", new AsignacionPermiso("GLOBAL", null),
                        ObjetivoAutorizacion.deClubes(CLUB_PROPIO), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casosQueAlcanzan")
    @DisplayName("RF-094: autoriza cuando el ámbito de alguna asignación alcanza el objetivo")
    void autorizaCuandoAlcanza(Caso caso) {
        iniciarSesion();
        darPermiso(caso.asignacion());
        darTitularidad(caso.titular());

        assertThatCode(() -> autorizacion.exigir(PERMISO, caso.objetivo())).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casosFueraDeAlcance")
    @DisplayName("VAL-024: con el permiso fuera del ámbito responde 404 sin revelar si el recurso existe")
    void deniegaFueraDeAlcance(Caso caso) {
        iniciarSesion();
        darPermiso(caso.asignacion());
        darTitularidad(caso.titular());

        assertThatThrownBy(() -> autorizacion.exigir(PERMISO, caso.objetivo()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("VAL-015: sin ninguna asignación que otorgue el permiso responde 403 y no consulta la titularidad")
    void sinElPermiso() {
        iniciarSesion();
        darPermiso();

        assertThatThrownBy(() -> autorizacion.exigir(PERMISO, ObjetivoAutorizacion.deClubes(CLUB_PROPIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.codigo()).isEqualTo("VAL-015");
                });
        verify(repositorio, never()).esTitularDeAlguna(anyCollection());
    }

    @Test
    @DisplayName("RF-094: un ámbito de liga autoriza sin consultar la titularidad de las personas")
    void ligaNoConsultaLaTitularidad() {
        iniciarSesion();
        darPermiso(liga());

        assertThatCode(() -> autorizacion.exigir(PERMISO, ObjetivoAutorizacion.dePersonas(PERSONA_MENOR)))
                .doesNotThrowAnyException();
        verify(repositorio, never()).esTitularDeAlguna(anyCollection());
    }

    @Test
    @DisplayName("RF-094: basta con que una de las asignaciones alcance el objetivo")
    void bastaUnaAsignacion() {
        iniciarSesion();
        darPermiso(club(CLUB_AJENO), propio());
        darTitularidad(true);

        assertThatCode(() -> autorizacion.exigir(PERMISO, ObjetivoAutorizacion.dePersonas(PERSONA_PROPIA)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("H-02.1: sin sesión es un error de programación y no consulta la base de datos")
    void sinSesion() {
        assertThatThrownBy(() -> autorizacion.exigir(PERMISO, ObjetivoAutorizacion.deLiga()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repositorio);
    }

    // ── Alcance para listados (H-02 criterio 3) ────────────────────────────────────────────────

    @Test
    @DisplayName("RF-094: el alcance de una asignación de liga cubre toda la liga")
    void alcanceDeLiga() {
        iniciarSesion();
        darPermiso(liga());

        Autorizacion.AlcanceAutorizacion alcance = autorizacion.alcance(PERMISO);

        assertThat(alcance.todaLaLiga()).isTrue();
        assertThat(alcance.clubes()).isEmpty();
        assertThat(alcance.propio()).isFalse();
    }

    @Test
    @DisplayName("RF-094: el alcance une los clubes de varias asignaciones CLUB")
    void alcanceUneLosClubes() {
        iniciarSesion();
        darPermiso(club(CLUB_PROPIO), club(CLUB_AJENO));

        Autorizacion.AlcanceAutorizacion alcance = autorizacion.alcance(PERMISO);

        assertThat(alcance.todaLaLiga()).isFalse();
        assertThat(alcance.clubes()).containsExactlyInAnyOrder(CLUB_PROPIO, CLUB_AJENO);
        assertThat(alcance.propio()).isFalse();
    }

    @Test
    @DisplayName("RF-144: el alcance con ámbito PROPIO habilita es_titular y no aporta clubes")
    void alcancePropio() {
        iniciarSesion();
        darPermiso(propio());

        Autorizacion.AlcanceAutorizacion alcance = autorizacion.alcance(PERMISO);

        assertThat(alcance.todaLaLiga()).isFalse();
        assertThat(alcance.clubes()).isEmpty();
        assertThat(alcance.propio()).isTrue();
        verify(repositorio, never()).esTitularDeAlguna(anyCollection());
    }

    @Test
    @DisplayName("RF-094: el alcance suma liga, club y propio de todas las asignaciones")
    void alcanceMezcla() {
        iniciarSesion();
        darPermiso(new AsignacionPermiso("GLOBAL", null), new AsignacionPermiso("CLUB", null),
                club(CLUB_PROPIO), club(CLUB_AJENO), propio());

        Autorizacion.AlcanceAutorizacion alcance = autorizacion.alcance(PERMISO);

        assertThat(alcance.todaLaLiga()).isFalse();
        assertThat(alcance.clubes()).as("un ámbito desconocido no aporta y una asignación CLUB sin club tampoco")
                .containsExactlyInAnyOrder(CLUB_PROPIO, CLUB_AJENO);
        assertThat(alcance.propio()).isTrue();
    }

    @Test
    @DisplayName("VAL-015: el alcance sin ninguna asignación que otorgue el permiso responde 403")
    void alcanceSinPermiso() {
        iniciarSesion();
        darPermiso();

        assertThatThrownBy(() -> autorizacion.alcance(PERMISO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.codigo()).isEqualTo("VAL-015");
                });
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private static AsignacionPermiso liga() {
        return new AsignacionPermiso("LIGA", null);
    }

    private static AsignacionPermiso club(UUID clubId) {
        return new AsignacionPermiso("CLUB", clubId);
    }

    private static AsignacionPermiso propio() {
        return new AsignacionPermiso("PROPIO", null);
    }

    private void darPermiso(AsignacionPermiso... asignaciones) {
        given(repositorio.listarAsignacionesDelPermiso(PERFIL, PERMISO)).willReturn(List.of(asignaciones));
    }

    /**
     * Doble de {@code es_titular}: el usuario es titular de alguna persona del objetivo solo en los
     * casos marcados, y nunca de un objetivo sin personas (como hace el repositorio real).
     */
    private void darTitularidad(boolean titular) {
        given(repositorio.esTitularDeAlguna(anyCollection())).willAnswer(invocacion -> {
            Collection<UUID> personas = invocacion.getArgument(0);
            return titular && !personas.isEmpty();
        });
    }

    private static void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
    }
}

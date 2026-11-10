package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Criterio de terminado de H-02.1 contra PostgreSQL real: los ámbitos {@code LIGA}, {@code CLUB} y
 * {@code PROPIO} de la matriz de permisos de la migración, y el alcance con el que los listados
 * filtran en el SQL.
 *
 * <p>Cada prueba crea su perfil (como dueño del esquema), le pone la sesión y llama a
 * {@code exigir} o {@code alcance} dentro de una transacción, como lo haría el servicio: ambos son
 * {@code Propagation.MANDATORY}. Así se prueba la regla completa: decisión, SQL y función
 * {@code es_titular} de la base.
 */
class AutorizacionIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final String PARTIDO_CONSULTAR = "partidos.partido:CONSULTAR";
    private static final String REGISTRO_CONSULTAR = "registros.registro:CONSULTAR";
    private static final String TEMPORADA_EDITAR = "temporadas.temporada:EDITAR";

    @Autowired
    private Autorizacion autorizacion;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    @DisplayName("RF-094: el ámbito LIGA alcanza el recurso de cualquier club")
    void ligaAlcanzaCualquierClub() {
        UUID perfil = crearPerfil("Ana López");
        asignarRol(perfil, "PROGRAMADOR", null, true);
        UUID clubAjeno = crearClub();
        iniciarSesion(perfil);

        assertThatCode(() -> exigir(PARTIDO_CONSULTAR, ObjetivoAutorizacion.deClubes(clubAjeno)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RF-094: el ámbito CLUB alcanza su club, también en un partido contra otro club")
    void clubAlcanzaSuClub() {
        UUID perfil = crearPerfil("Beto Ruiz");
        UUID clubPropio = crearClub();
        asignarRol(perfil, "DIRECTIVA_CLUB", clubPropio, true);
        iniciarSesion(perfil);

        assertThatCode(() -> exigir(REGISTRO_CONSULTAR, ObjetivoAutorizacion.deClubes(clubPropio)))
                .doesNotThrowAnyException();
        assertThatCode(() -> exigir(REGISTRO_CONSULTAR,
                ObjetivoAutorizacion.deClubes(clubPropio, crearClub())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("VAL-024: el ámbito CLUB no alcanza el club de otro y coincide con club_visible de RLS")
    void clubNoAlcanzaOtroClub() {
        UUID perfil = crearPerfil("Carla Díaz");
        UUID clubPropio = crearClub();
        UUID clubAjeno = crearClub();
        asignarRol(perfil, "DIRECTIVA_CLUB", clubPropio, true);
        iniciarSesion(perfil);

        assertThatThrownBy(() -> exigir(REGISTRO_CONSULTAR, ObjetivoAutorizacion.deClubes(clubAjeno)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        // ADR-0007 §4: la regla de ámbito del servicio y la política RLS deben coincidir.
        assertThat(clubVisible(clubPropio)).as("su club").isTrue();
        assertThat(clubVisible(clubAjeno)).as("el club de otro").isFalse();
    }

    @Test
    @DisplayName("RF-144: el ámbito PROPIO alcanza la persona del titular y no un recurso de liga")
    void propioAlcanzaSuPersona() {
        UUID perfil = crearPerfil("Diana Soto");
        UUID persona = crearPersona("Diana Soto");
        vincularPersona(perfil, persona);
        asignarRol(perfil, "JUGADOR_TUTOR", null, true);
        iniciarSesion(perfil);

        assertThatCode(() -> exigir(REGISTRO_CONSULTAR, ObjetivoAutorizacion.dePersonas(persona)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> exigir(REGISTRO_CONSULTAR, ObjetivoAutorizacion.deLiga()))
                .as("un recurso sin personas no pertenece a nadie en ámbito propio")
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("RF-106: el ámbito PROPIO alcanza al menor bajo tutela vigente")
    void propioAlcanzaAlMenorBajoTutela() {
        UUID perfil = crearPerfil("Elena Mora");
        UUID tutor = crearPersona("Elena Mora");
        UUID menor = crearPersona("Fernanda Mora");
        vincularPersona(perfil, tutor);
        crearTutela(menor, tutor, true);
        asignarRol(perfil, "JUGADOR_TUTOR", null, true);
        iniciarSesion(perfil);

        assertThatCode(() -> exigir(REGISTRO_CONSULTAR, ObjetivoAutorizacion.dePersonas(menor)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RF-144: el ámbito PROPIO alcanza el partido de su equipo sin abrir el del rival")
    void propioAlcanzaElPartidoDeSuEquipo() {
        UUID perfil = crearPerfil("Nora Cárdenas");
        UUID persona = crearPersona("Nora Cárdenas");
        vincularPersona(perfil, persona);
        asignarRol(perfil, "JUGADOR_TUTOR", null, true);
        UUID clubPropio = crearClub();
        UUID clubRival = crearClub();
        iniciarSesion(perfil);

        assertThatCode(() -> exigir(PARTIDO_CONSULTAR,
                ObjetivoAutorizacion.deClubesYPersonas(List.of(clubPropio, clubRival), List.of(persona))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RF-144: el ámbito PROPIO no alcanza a una persona sin tutela vigente")
    void propioNoAlcanzaSinTutelaVigente() {
        UUID perfil = crearPerfil("Fabián Nava");
        UUID tutor = crearPersona("Fabián Nava");
        UUID menor = crearPersona("Greta Nava");
        vincularPersona(perfil, tutor);
        crearTutela(menor, tutor, false);
        asignarRol(perfil, "JUGADOR_TUTOR", null, true);
        iniciarSesion(perfil);

        assertThatThrownBy(() -> exigir(REGISTRO_CONSULTAR, ObjetivoAutorizacion.dePersonas(menor)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("VAL-024: el ámbito PROPIO no alcanza la persona de otro")
    void propioNoAlcanzaOtraPersona() {
        UUID perfil = crearPerfil("Hugo Pineda");
        UUID persona = crearPersona("Hugo Pineda");
        vincularPersona(perfil, persona);
        asignarRol(perfil, "JUGADOR_TUTOR", null, true);
        iniciarSesion(perfil);

        assertThatThrownBy(() -> exigir(REGISTRO_CONSULTAR,
                ObjetivoAutorizacion.dePersonas(crearPersona("Irene Vargas"))))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("VAL-015: sin ninguna asignación que otorgue el permiso responde 403")
    void sinElPermiso() {
        UUID perfil = crearPerfil("Jorge Ávalos");
        asignarRol(perfil, "CAPTURISTA", null, true);
        iniciarSesion(perfil);

        assertThatThrownBy(() -> exigir(TEMPORADA_EDITAR, ObjetivoAutorizacion.deLiga()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.codigo()).isEqualTo("VAL-015");
                });
    }

    @Test
    @DisplayName("RF-094: una asignación revocada ya no otorga el permiso")
    void asignacionRevocadaNoOtorgaPermiso() {
        UUID perfil = crearPerfil("Karla Reyes");
        UUID club = crearClub();
        asignarRol(perfil, "DIRECTIVA_CLUB", club, false);
        iniciarSesion(perfil);

        assertThatThrownBy(() -> exigir(REGISTRO_CONSULTAR, ObjetivoAutorizacion.deClubes(club)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error ->
                        assertThat(error.codigo()).isEqualTo("VAL-015"));
    }

    // ── Alcance para listados (H-02 criterio 3) ────────────────────────────────────────────────

    @Test
    @DisplayName("RF-094: el alcance une los clubes de dos asignaciones CLUB del mismo permiso")
    void alcanceUneLosClubesDeDosAsignaciones() {
        UUID perfil = crearPerfil("Lucía Bahena");
        UUID clubA = crearClub();
        UUID clubB = crearClub();
        asignarRol(perfil, "DIRECTIVA_CLUB", clubA, true);
        asignarRol(perfil, "DIRECTIVA_CLUB", clubB, true);
        iniciarSesion(perfil);

        Autorizacion.AlcanceAutorizacion alcance = alcance(REGISTRO_CONSULTAR);

        assertThat(alcance.todaLaLiga()).isFalse();
        assertThat(alcance.propio()).isFalse();
        assertThat(alcance.clubes()).containsExactlyInAnyOrder(clubA, clubB);
    }

    @Test
    @DisplayName("RF-144: el alcance PROPIO habilita es_titular y no aporta clubes")
    void alcancePropio() {
        UUID perfil = crearPerfil("Mario Quiroz");
        UUID persona = crearPersona("Mario Quiroz");
        vincularPersona(perfil, persona);
        asignarRol(perfil, "JUGADOR_TUTOR", null, true);
        iniciarSesion(perfil);

        Autorizacion.AlcanceAutorizacion alcance = alcance(REGISTRO_CONSULTAR);

        assertThat(alcance.todaLaLiga()).isFalse();
        assertThat(alcance.clubes()).isEmpty();
        assertThat(alcance.propio()).isTrue();
    }

    @Test
    @DisplayName("VAL-015: el alcance sin el permiso responde 403, igual que exigir")
    void alcanceSinElPermiso() {
        UUID perfil = crearPerfil("Nadia Ferrer");
        asignarRol(perfil, "CAPTURISTA", null, true);
        iniciarSesion(perfil);

        assertThatThrownBy(() -> alcance(TEMPORADA_EDITAR))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.codigo()).isEqualTo("VAL-015");
                });
    }

    /**
     * El patrón que documenta {@link Autorizacion#alcance}: misma condición en el listado y en el
     * total, con los clubes como arreglo {@code uuid[]}. Comprueba el riesgo real: el conjunto
     * vacío de clubes no rompe el SQL y el ámbito propio resuelve por fila con {@code es_titular}.
     */
    @Test
    @DisplayName("ADR-0007 §2: el patrón de listado por ámbito funciona con cero y con dos clubes")
    void patronDeListadoPorAmbito() {
        UUID perfil = crearPerfil("Óscar Lemus");
        UUID personaPropia = crearPersona("Óscar Lemus");
        vincularPersona(perfil, personaPropia);
        UUID clubPropio = crearClub();
        UUID clubAjeno = crearClub();
        UUID personaAjena = crearPersona("Pilar Lemus");
        iniciarSesion(perfil);

        assertThat(filasDelPatron(false, List.of(), false, clubPropio, clubAjeno, personaPropia, personaAjena))
                .as("sin clubes ni ámbito propio, el arreglo vacío no rompe el SQL")
                .isEmpty();
        assertThat(filasDelPatron(false, List.of(), true, clubPropio, clubAjeno, personaPropia, personaAjena))
                .as("sin clubes, el ámbito propio deja solo la fila de su persona")
                .containsExactly("propia");
        assertThat(filasDelPatron(false, List.of(clubPropio), false,
                clubPropio, clubAjeno, personaPropia, personaAjena))
                .as("un club del alcance deja solo esa fila")
                .containsExactly("propia");
        assertThat(filasDelPatron(false, List.of(clubPropio, clubAjeno), false,
                clubPropio, clubAjeno, personaPropia, personaAjena))
                .as("dos clubes del alcance dejan las dos filas")
                .containsExactlyInAnyOrder("propia", "ajena");
        assertThat(filasDelPatron(true, List.of(), false, clubPropio, clubAjeno, personaPropia, personaAjena))
                .as("el ámbito de liga deja todo")
                .containsExactlyInAnyOrder("propia", "ajena");
    }

    @Test
    @DisplayName("H-02.1: exigir y alcance fuera de una transacción de servicio es un error")
    void exigenDentroDeUnaTransaccion() {
        UUID perfil = crearPerfil("Rocío Núñez");
        asignarRol(perfil, "DIRECTIVA_CLUB", crearClub(), true);
        iniciarSesion(perfil);

        assertThatThrownBy(() -> autorizacion.exigir(REGISTRO_CONSULTAR, ObjetivoAutorizacion.deLiga()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> autorizacion.alcance(REGISTRO_CONSULTAR))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /** Llama a {@code exigir} dentro de una transacción, como el servicio (Propagation.MANDATORY). */
    private void exigir(String permiso, ObjetivoAutorizacion objetivo) {
        new TransactionTemplate(transacciones).execute(estado -> {
            autorizacion.exigir(permiso, objetivo);
            return null;
        });
    }

    /** Llama a {@code alcance} dentro de una transacción, como el servicio. */
    private Autorizacion.AlcanceAutorizacion alcance(String permiso) {
        return new TransactionTemplate(transacciones).execute(estado -> autorizacion.alcance(permiso));
    }

    /**
     * Ejecuta el patrón de listado por ámbito sobre dos filas de prueba con los clubes y personas
     * reales de la prueba, para que {@code ANY(:clubes)} y {@code es_titular} se evalúen de verdad.
     */
    private List<String> filasDelPatron(boolean todaLaLiga, Collection<UUID> clubes, boolean propio,
            UUID clubPropio, UUID clubAjeno, UUID personaPropia, UUID personaAjena) {
        return new TransactionTemplate(transacciones).execute(estado -> jdbc.sql("""
                        SELECT t.etiqueta
                          FROM (VALUES (CAST(:clubPropio AS uuid), CAST(:personaPropia AS uuid), 'propia'),
                                       (CAST(:clubAjeno AS uuid), CAST(:personaAjena AS uuid), 'ajena'))
                               AS t (club_id, persona_id, etiqueta)
                         WHERE (:todaLaLiga OR t.club_id = ANY(:clubes) OR (:propio AND es_titular(t.persona_id)))
                        """)
                .param("todaLaLiga", todaLaLiga)
                .param("clubes", new SqlArrayValue("uuid", clubes.toArray()))
                .param("propio", propio)
                .param("clubPropio", clubPropio)
                .param("clubAjeno", clubAjeno)
                .param("personaPropia", personaPropia)
                .param("personaAjena", personaAjena)
                .query(String.class)
                .list());
    }

    /** Política de ámbito de RLS, para comparar con la decisión del servicio (ADR-0007 §4). */
    private boolean clubVisible(UUID club) {
        return new TransactionTemplate(transacciones).execute(estado -> jdbc.sql("SELECT club_visible(:club)")
                .param("club", club).query(Boolean.class).single());
    }

    /**
     * Los datos se crean como dueño del esquema (fuera de transacción) y quedan en el contenedor:
     * cada prueba usa sus propios perfiles, clubes y personas para no depender de las demás.
     */
    private UUID crearPerfil(String nombre) {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES (:nombre) RETURNING id")
                .param("nombre", nombre).query(UUID.class).single();
    }

    /** Identidad única por CURP (RF-010); la CURP cumple el formato que exige la migración. */
    private UUID crearPersona(String nombre) {
        String curp = "LOPA%06dHDFRRS0%d".formatted(ThreadLocalRandom.current().nextInt(1_000_000),
                ThreadLocalRandom.current().nextInt(10));
        return jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, fecha_nacimiento)
                        VALUES (:curp, :nombre, 'Prueba', DATE '2008-06-06')
                        RETURNING id
                        """)
                .param("curp", curp).param("nombre", nombre).query(UUID.class).single();
    }

    private void vincularPersona(UUID perfil, UUID persona) {
        jdbc.sql("UPDATE siglim.perfiles SET persona_id = :persona WHERE id = :perfil")
                .param("persona", persona).param("perfil", perfil).update();
    }

    private void crearTutela(UUID menor, UUID tutor, boolean vigente) {
        jdbc.sql("""
                        INSERT INTO siglim.tutelas (menor_id, tutor_id, parentesco, vigente)
                        VALUES (:menor, :tutor, 'Madre', :vigente)
                        """)
                .param("menor", menor).param("tutor", tutor).param("vigente", vigente).update();
    }

    private UUID crearClub() {
        return jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA)
                .param("nombre", "Club de prueba " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    /** Asigna el rol con ámbito CLUB si se indica club; si no, con el ámbito predeterminado del rol. */
    private void asignarRol(UUID perfil, String rol, UUID club, boolean activa) {
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id, activa,
                                                            revocada_en)
                        SELECT :perfil, r.id, r.liga_id,
                               CASE WHEN CAST(:club AS uuid) IS NULL THEN r.ambito_predeterminado ELSE 'CLUB' END,
                               :club, :activa, CASE WHEN :activa THEN NULL ELSE now() END
                          FROM siglim.roles r
                         WHERE r.codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", rol)
                .param("club", club)
                .param("activa", activa)
                .update();
    }

    /** Pone en la petición la sesión de ese perfil, como lo haría la cadena de seguridad. */
    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}

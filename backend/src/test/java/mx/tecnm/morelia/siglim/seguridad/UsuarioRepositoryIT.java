package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.RolRepository.FilaRol;
import mx.tecnm.morelia.siglim.seguridad.UsuarioRepository.FilaAsignacion;
import mx.tecnm.morelia.siglim.seguridad.UsuarioRepository.FilaPerfil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SQL de cuentas y asignaciones contra PostgreSQL real, con las políticas RLS y los roles de la
 * migración. Los datos se crean como dueño del esquema; las consultas corren como el Administrador
 * general, es decir, como {@code siglim_app} con ámbito de liga.
 */
class UsuarioRepositoryIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Autowired
    private UsuarioRepository repositorio;

    @Autowired
    private RolRepository roles;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    @DisplayName("RF-094: el listado filtra por texto, rol, ámbito y club, y el total usa la misma condición")
    void listarConFiltros() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        UUID perfil = crearPerfil("Ana " + sufijo, "ana-" + sufijo + "@ejemplo.mx");
        UUID club = crearClub("Club " + sufijo);
        UUID directiva = rolId("DIRECTIVA_CLUB");
        asignar(perfil, directiva, club, true);
        asignar(perfil, rolId("PROGRAMADOR"), null, false);

        iniciarSesionComoAdministrador();
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(true, Set.of(), false);

        List<FilaPerfil> porTexto = leer(() -> repositorio.listar(
                new FiltroUsuarios(sufijo, null, null, null, null, 0, 20), alcance));
        assertThat(porTexto).extracting(FilaPerfil::id).contains(perfil);
        assertThat(leer(() -> repositorio.contar(
                new FiltroUsuarios(sufijo, null, null, null, null, 0, 20), alcance))).isEqualTo(1);

        assertThat(leer(() -> repositorio.listar(
                new FiltroUsuarios(null, directiva, Ambito.CLUB, club, true, 0, 20), alcance)))
                .extracting(FilaPerfil::id).contains(perfil);
        assertThat(leer(() -> repositorio.listar(
                new FiltroUsuarios(null, directiva, Ambito.LIGA, null, true, 0, 20), alcance)))
                .isEmpty();
        assertThat(leer(() -> repositorio.contar(
                new FiltroUsuarios(null, directiva, null, UUID.randomUUID(), null, 0, 20), alcance))).isZero();

        assertThat(leer(() -> repositorio.existeCorreo("ANA-" + sufijo + "@EJEMPLO.MX"))).isTrue();
        assertThat(leer(() -> repositorio.existeCorreo("nadie-" + sufijo + "@ejemplo.mx"))).isFalse();
    }

    @Test
    @DisplayName("RF-094: rol, ámbito y club deben coincidir en la misma asignación activa")
    void filtrosRequierenLaMismaAsignacion() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        UUID perfil = crearPerfil("Dora " + sufijo, "dora-" + sufijo + "@ejemplo.mx");
        UUID club = crearClub("Club " + sufijo);
        UUID directiva = rolId("DIRECTIVA_CLUB");
        asignar(perfil, directiva, club, true);
        asignar(perfil, rolId("PROGRAMADOR"), null, true);

        iniciarSesionComoAdministrador();
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(true, Set.of(), false);

        assertThat(leer(() -> repositorio.contar(
                new FiltroUsuarios(sufijo, directiva, Ambito.LIGA, null, null, 0, 20), alcance)))
                .as("el rol es de club; no debe cruzarse con la otra asignación de liga").isZero();
        assertThat(leer(() -> repositorio.contar(
                new FiltroUsuarios(sufijo, directiva, Ambito.CLUB, club, null, 0, 20), alcance)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("RF-094: el detalle conserva las asignaciones revocadas y distingue la vigente")
    void asignacionesActivasYRevocadas() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        UUID perfil = crearPerfil("Beto " + sufijo, "beto-" + sufijo + "@ejemplo.mx");
        UUID club = crearClub("Club " + sufijo);
        asignar(perfil, rolId("DIRECTIVA_CLUB"), club, true);
        asignar(perfil, rolId("PROGRAMADOR"), null, false);

        iniciarSesionComoAdministrador();
        List<FilaAsignacion> todas = leer(() -> repositorio.listarAsignaciones(perfil, false));
        List<FilaAsignacion> activas = leer(() -> repositorio.listarAsignaciones(perfil, true));

        assertThat(todas).hasSize(2);
        assertThat(todas).first().satisfies(asignacion -> {
            assertThat(asignacion.activa()).isTrue();
            assertThat(asignacion.ambito()).isEqualTo("CLUB");
            assertThat(asignacion.clubId()).isEqualTo(club);
        });
        assertThat(todas).last().satisfies(asignacion -> {
            assertThat(asignacion.activa()).isFalse();
            assertThat(asignacion.revocadaEn()).isNotNull();
        });
        assertThat(activas).extracting(FilaAsignacion::id).containsExactly(todas.getFirst().id());
    }

    @Test
    @DisplayName("RF-094: el catálogo de roles de la liga se lee con su ámbito predeterminado")
    void listarRoles() {
        UUID directiva = rolId("DIRECTIVA_CLUB");
        iniciarSesionComoAdministrador();

        assertThat(leer(() -> roles.listar(LIGA))).extracting(FilaRol::codigo)
                .contains("ADMINISTRADOR_GENERAL", "DIRECTIVA_CLUB", "SISTEMA");
        assertThat(leer(() -> roles.buscar(LIGA, directiva))).hasValueSatisfying(rol -> {
            assertThat(rol.ambitoPredeterminado()).isEqualTo("CLUB");
            assertThat(rol.asignable()).isTrue();
        });
    }

    @Test
    @DisplayName("RF-094: con ámbito de liga inserta perfil y asignación; sin él, RLS no deja verlos")
    void insertarComoAdministrador() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        UUID auth = crearCuentaAuth("nuevo-" + sufijo + "@ejemplo.mx");
        UUID club = crearClub("Club " + sufijo);
        UUID admin = iniciarSesionComoAdministrador();
        UUID directiva = rolId("DIRECTIVA_CLUB");

        TransactionTemplate escritura = new TransactionTemplate(transacciones);
        FilaAsignacion creada = escritura.execute(estado -> {
            UUID perfil = repositorio.insertarPerfil(auth, "Nuevo " + sufijo, "nuevo-" + sufijo + "@ejemplo.mx");
            UUID asignacion = repositorio.insertarAsignacion(perfil, directiva, LIGA, "CLUB", club, admin);
            assertThat(repositorio.existeAsignacionActiva(perfil, directiva, club)).isTrue();
            return repositorio.buscarAsignacion(asignacion).orElseThrow();
        });

        assertThat(creada.rolCodigo()).isEqualTo("DIRECTIVA_CLUB");
        assertThat(creada.clubId()).isEqualTo(club);
    }

    @Test
    @DisplayName("RF-094: revocar deja la asignación inactiva y con fecha, sin borrarla")
    void revocarAsignacion() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        UUID perfil = crearPerfil("Carla " + sufijo, "carla-" + sufijo + "@ejemplo.mx");
        UUID asignacion = asignar(perfil, rolId("PROGRAMADOR"), null, true);
        iniciarSesionComoAdministrador();

        TransactionTemplate escritura = new TransactionTemplate(transacciones);
        FilaAsignacion revocada = escritura.execute(estado -> {
            assertThat(repositorio.revocarAsignacion(asignacion)).isEqualTo(1);
            return repositorio.buscarAsignacion(asignacion).orElseThrow();
        });

        assertThat(revocada.activa()).isFalse();
        assertThat(revocada.revocadaEn()).isNotNull();
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /** Ejecuta una consulta en una transacción de solo lectura con la sesión ya iniciada. */
    private <T> T leer(Supplier<T> consulta) {
        TransactionTemplate lectura = new TransactionTemplate(transacciones);
        lectura.setReadOnly(true);
        return lectura.execute(estado -> consulta.get());
    }

    private UUID crearPerfil(String nombre, String correo) {
        return jdbc.sql("""
                        INSERT INTO siglim.perfiles (nombre_mostrado, correo)
                        VALUES (:nombre, :correo) RETURNING id
                        """)
                .param("nombre", nombre)
                .param("correo", correo)
                .query(UUID.class)
                .single();
    }

    private UUID crearClub(String nombre) {
        return jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA)
                .param("nombre", nombre)
                .query(UUID.class)
                .single();
    }

    private UUID crearCuentaAuth(String correo) {
        return jdbc.sql("""
                        INSERT INTO auth.users (id, email, aud, role)
                        VALUES (gen_random_uuid(), :correo, 'authenticated', 'authenticated')
                        RETURNING id
                        """)
                .param("correo", correo)
                .query(UUID.class)
                .single();
    }

    private UUID rolId(String codigo) {
        return jdbc.sql("SELECT id FROM siglim.roles WHERE codigo = :codigo")
                .param("codigo", codigo)
                .query(UUID.class)
                .single();
    }

    /** Asigna el rol con ámbito CLUB si se indica club; si no, con el ámbito predeterminado del rol. */
    private UUID asignar(UUID perfil, UUID rol, UUID club, boolean activa) {
        return jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id, activa, revocada_en)
                        SELECT :perfil, r.id, r.liga_id,
                               CASE WHEN CAST(:club AS uuid) IS NULL THEN r.ambito_predeterminado ELSE 'CLUB' END,
                               :club, :activa, CASE WHEN :activa THEN NULL ELSE now() END
                          FROM siglim.roles r
                         WHERE r.id = :rol
                        RETURNING id
                        """)
                .param("perfil", perfil)
                .param("rol", rol)
                .param("club", club)
                .param("activa", activa)
                .query(UUID.class)
                .single();
    }
}

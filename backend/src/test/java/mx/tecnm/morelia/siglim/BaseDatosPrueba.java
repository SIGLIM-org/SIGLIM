package mx.tecnm.morelia.siglim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base de las pruebas de integración con PostgreSQL real ({@code *IT}).
 *
 * <p>Un solo contenedor PostgreSQL 17 (la versión de Supabase) para todas las pruebas que heredan
 * de esta clase: como comparten configuración, Spring reutiliza el mismo contexto y el contenedor
 * se carga una vez. Se carga igual que {@code supabase/pruebas/ejecutar.sh}: la simulación de
 * Supabase y todas las migraciones en orden, con {@code psql}. Además crea dos cuentas: el
 * Administrador general ({@link #SUB_ADMIN}) y una con el perfil desactivado ({@link #SUB_INACTIVO}).
 *
 * <p>El pool tiene una sola conexión para comprobar que el contexto no se filtra de una
 * transacción a la siguiente, como ocurriría en el pooler de Supabase.
 *
 * <p>Requiere Docker; sin Docker las pruebas se omiten (decisión del equipo del 22 oct 2026).
 */
@SpringBootTest(properties = {
        "SUPABASE_URL=https://proyecto-prueba.supabase.co",
        "SUPABASE_SECRET_KEY=clave-secreta-de-prueba",
        "SIGLIM_FRONTEND_ORIGEN=http://localhost:5173",
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=http://localhost:5173",
        "spring.datasource.hikari.maximum-pool-size=1"
})
@Testcontainers(disabledWithoutDocker = true)
public abstract class BaseDatosPrueba {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    protected static final UUID SUB_ADMIN = UUID.fromString("6f1d2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b");
    protected static final UUID SUB_INACTIVO = UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registro) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
            cargarEsquema();
        }
        // Las mismas variables de backend/.env: también prueba la configuración de T-02.1.
        registro.add("DB_URL", POSTGRES::getJdbcUrl);
        registro.add("DB_USERNAME", POSTGRES::getUsername);
        registro.add("DB_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcClient jdbc;

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    /** Pone en la petición la sesión del Administrador general, como lo haría la cadena de seguridad. */
    protected UUID iniciarSesionComoAdministrador() {
        UUID perfil = jdbc.sql("SELECT id FROM siglim.perfiles WHERE usuario_auth_id = :sub")
                .param("sub", SUB_ADMIN).query(UUID.class).single();
        Jwt token = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "ES256")
                .subject(SUB_ADMIN.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
        return perfil;
    }

    // ── Comunicados: el flujo más simple con triggers de estado y de historial ──────────────────

    protected UUID crearComunicado(UUID redactor) {
        return jdbc.sql("""
                        INSERT INTO comunicados (liga_id, titulo, cuerpo, redactado_por)
                        SELECT id, 'Comunicado de prueba', 'Cuerpo', :redactor FROM ligas LIMIT 1
                        RETURNING id
                        """).param("redactor", redactor).query(UUID.class).single();
    }

    protected void enviarARevision(UUID comunicado) {
        jdbc.sql("UPDATE comunicados SET estado_publicacion = 'REVISION' WHERE id = :id")
                .param("id", comunicado).update();
    }

    protected int devolverABorrador(UUID comunicado) {
        return jdbc.sql("UPDATE comunicados SET estado_publicacion = 'BORRADOR' WHERE id = :id")
                .param("id", comunicado).update();
    }

    // ── Carga del esquema ─────────────────────────────────────────────────────────────────────

    /** Carga el esquema como {@code supabase/pruebas/ejecutar.sh} y crea las cuentas de prueba. */
    private static void cargarEsquema() {
        Path supabase = Path.of("..", "supabase").toAbsolutePath().normalize();
        List<Path> scripts = new ArrayList<>();
        scripts.add(supabase.resolve("pruebas/mock_supabase.sql"));
        try (Stream<Path> migraciones = Files.list(supabase.resolve("migrations"))) {
            migraciones.filter(ruta -> ruta.toString().endsWith(".sql")).sorted().forEach(scripts::add);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron listar las migraciones de " + supabase, e);
        }
        for (Path script : scripts) {
            String destino = "/tmp/" + script.getFileName();
            POSTGRES.copyFileToContainer(MountableFile.forHostPath(script), destino);
            psql("-f", destino);
        }
        psql("-c", """
                INSERT INTO auth.users (id, email, aud, role) VALUES
                  ('%s', 'admin@prueba.mx', 'authenticated', 'authenticated'),
                  ('%s', 'inactivo@prueba.mx', 'authenticated', 'authenticated');
                SELECT siglim.registrar_administrador_inicial('admin@prueba.mx');
                INSERT INTO siglim.perfiles (usuario_auth_id, nombre_mostrado, correo, activo)
                VALUES ('%s', 'inactivo', 'inactivo@prueba.mx', false);
                """.formatted(SUB_ADMIN, SUB_INACTIVO, SUB_INACTIVO));
    }

    /** Ejecuta psql dentro del contenedor como dueño del esquema. */
    protected static void psql(String... argumentos) {
        List<String> comando = new ArrayList<>(List.of("psql", "-q", "-v", "ON_ERROR_STOP=1",
                "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName()));
        comando.addAll(List.of(argumentos));
        try {
            Container.ExecResult resultado = POSTGRES.execInContainer(comando.toArray(String[]::new));
            if (resultado.getExitCode() != 0) {
                throw new IllegalStateException("psql " + String.join(" ", argumentos) + " falló:\n"
                        + resultado.getStderr());
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo ejecutar psql en el contenedor", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al cargar el esquema", e);
        }
    }
}

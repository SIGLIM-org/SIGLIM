package mx.tecnm.morelia.siglim.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Criterio de terminado de H-02.2 contra PostgreSQL real: la auditoría se escribe en la
 * transacción de negocio y no admite cambios ni como {@code siglim_app} ni como dueño del esquema.
 * Cada prueba usa un {@code entidad_id} propio para no depender de las demás.
 */
class AuditoriaServiceIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Autowired
    private AuditoriaService auditoria;

    @Autowired
    private MotivoOperacion motivoOperacion;

    @Autowired
    private PlatformTransactionManager transacciones;

    /** Fila tal como queda en la tabla (leída como dueño, sin RLS). */
    record Fila(UUID ligaId, UUID perfilId, String modulo, String accion, String entidad, String entidadId,
                String motivo, boolean conFecha) {
    }

    @Test
    @DisplayName("RF-093: el registro se confirma con la transacción y guarda usuario, fecha y datos del evento")
    void seConfirmaConLaTransaccion() {
        UUID perfil = iniciarSesionComoAdministrador();
        String entidadId = UUID.randomUUID().toString();

        enTransaccion(() -> auditoria.registrar(evento(entidadId, "Alta de prueba", null)));

        assertThat(fila(entidadId)).isEqualTo(new Fila(LIGA, perfil, "M-03", "CREAR_CLUB", "clubes", entidadId,
                "Alta de prueba", true));
    }

    @Test
    @DisplayName("RNF-015: si la operación de negocio se revierte, el registro también")
    void seRevierteConLaTransaccion() {
        iniciarSesionComoAdministrador();
        String entidadId = UUID.randomUUID().toString();

        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            auditoria.registrar(evento(entidadId, "Alta de prueba", null));
            estado.setRollbackOnly();
        });

        assertThat(contar(entidadId)).isZero();
    }

    @Test
    @DisplayName("RNF-015: fuera de una transacción lanza IllegalTransactionStateException y no escribe")
    void exigeTransaccion() {
        iniciarSesionComoAdministrador();
        String entidadId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> auditoria.registrar(evento(entidadId, "Alta de prueba", null)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(contar(entidadId)).isZero();
    }

    @Test
    @DisplayName("RN-021: sin motivo en el evento guarda el fijado con MotivoOperacion")
    void heredaElMotivoDeLaTransaccion() {
        iniciarSesionComoAdministrador();
        String entidadId = UUID.randomUUID().toString();

        enTransaccion(() -> {
            motivoOperacion.fijar("Motivo de la transacción");
            auditoria.registrar(evento(entidadId, null, null));
        });

        assertThat(fila(entidadId).motivo()).isEqualTo("Motivo de la transacción");
    }

    @Test
    @DisplayName("RN-021: el motivo del evento prevalece sobre el de la transacción")
    void motivoExplicitoPrevalece() {
        iniciarSesionComoAdministrador();
        String entidadId = UUID.randomUUID().toString();

        enTransaccion(() -> {
            motivoOperacion.fijar("Motivo de la transacción");
            auditoria.registrar(evento(entidadId, "Motivo del evento", null));
        });

        assertThat(fila(entidadId).motivo()).isEqualTo("Motivo del evento");
    }

    @Test
    @DisplayName("RF-093: detalle se guarda como jsonb consultable")
    void detalleComoJsonb() {
        iniciarSesionComoAdministrador();
        String entidadId = UUID.randomUUID().toString();

        enTransaccion(() -> auditoria.registrar(evento(entidadId, "Alta", Map.of("estadoNuevo", "ACTIVO"))));

        assertThat(jdbc.sql("SELECT detalle->>'estadoNuevo' FROM siglim.auditoria WHERE entidad_id = :id")
                .param("id", entidadId).query(String.class).single()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("RF-093: sin detalle guarda NULL")
    void detalleNulo() {
        iniciarSesionComoAdministrador();
        String entidadId = UUID.randomUUID().toString();

        enTransaccion(() -> auditoria.registrar(evento(entidadId, "Alta", null)));

        assertThat(jdbc.sql("SELECT detalle IS NULL FROM siglim.auditoria WHERE entidad_id = :id")
                .param("id", entidadId).query(Boolean.class).single()).isTrue();
    }

    @Test
    @DisplayName("RNF-015: siglim_app no puede modificar la auditoría (42501)")
    void appNoModifica() {
        long id = registrarFila();
        assertThat(errorDe(() -> enTransaccion(() -> jdbc.sql("UPDATE auditoria SET accion = 'X' WHERE id = :id")
                .param("id", id).update())).getSQLState()).isEqualTo("42501");
    }

    @Test
    @DisplayName("RNF-015: siglim_app no puede borrar la auditoría (42501)")
    void appNoBorra() {
        long id = registrarFila();
        assertThat(errorDe(() -> enTransaccion(() -> jdbc.sql("DELETE FROM auditoria WHERE id = :id")
                .param("id", id).update())).getSQLState()).isEqualTo("42501");
    }

    @Test
    @DisplayName("RNF-015: ni el dueño del esquema puede modificarla: el trigger responde P0001 con HINT RNF-015")
    void duenoNoModifica() {
        long id = registrarFila();
        PSQLException error = errorDe(() -> jdbc.sql("UPDATE siglim.auditoria SET accion = 'X' WHERE id = :id")
                .param("id", id).update());
        assertThat(error.getSQLState()).isEqualTo("P0001");
        assertThat(error.getServerErrorMessage().getHint()).contains("RNF-015");
    }

    @Test
    @DisplayName("RNF-015: ni el dueño del esquema puede borrarla: el trigger responde P0001 con HINT RNF-015")
    void duenoNoBorra() {
        long id = registrarFila();
        PSQLException error = errorDe(() -> jdbc.sql("DELETE FROM siglim.auditoria WHERE id = :id")
                .param("id", id).update());
        assertThat(error.getSQLState()).isEqualTo("P0001");
        assertThat(error.getServerErrorMessage().getHint()).contains("RNF-015");
    }

    @Test
    @DisplayName("RNF-015: siglim_app no puede vaciar la auditoría con TRUNCATE (42501)")
    void appNoTrunca() {
        registrarFila();
        assertThat(errorDe(() -> enTransaccion(() -> jdbc.sql("TRUNCATE auditoria").update()))
                .getSQLState()).isEqualTo("42501");
    }

    @Test
    @DisplayName("RNF-015: ni el dueño del esquema puede vaciarla con TRUNCATE: P0001 con HINT RNF-015")
    void duenoNoTrunca() {
        String entidadId = entidadDeFila(registrarFila());
        PSQLException error = errorDe(() -> truncarSinConfirmar("TRUNCATE siglim.auditoria"));
        assertThat(error.getSQLState()).isEqualTo("P0001");
        assertThat(error.getServerErrorMessage().getHint()).contains("RNF-015");
        assertThat(contar(entidadId)).isEqualTo(1);
    }

    @Test
    @DisplayName("RNF-015: TRUNCATE ... CASCADE sobre una tabla referenciada tampoco vacía la auditoría")
    void duenoNoTruncaPorCascada() {
        String entidadId = entidadDeFila(registrarFila());
        PSQLException error = errorDe(() -> truncarSinConfirmar("TRUNCATE siglim.perfiles CASCADE"));
        assertThat(error.getSQLState()).isEqualTo("P0001");
        assertThat(error.getServerErrorMessage().getHint()).contains("RNF-015");
        assertThat(contar(entidadId)).isEqualTo(1);
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Ejecuta un TRUNCATE como dueño del esquema dentro de un bloque {@code DO} que falla si el
     * TRUNCATE se acepta. El bloque es atómico: si el trigger faltara, la prueba falla con XX000 y
     * nada se vacía, así que no deja sin datos al resto de las pruebas que comparten el contenedor.
     */
    private void truncarSinConfirmar(String truncate) {
        jdbc.sql("""
                DO $$
                BEGIN
                  %s;
                  RAISE EXCEPTION 'El TRUNCATE se aceptó' USING ERRCODE = 'XX000';
                END $$
                """.formatted(truncate)).update();
    }

    private String entidadDeFila(long id) {
        return jdbc.sql("SELECT entidad_id FROM siglim.auditoria WHERE id = :id")
                .param("id", id).query(String.class).single();
    }

    private static EventoAuditoria evento(String entidadId, String motivo, Map<String, Object> detalle) {
        return new EventoAuditoria(LIGA, "M-03", "CREAR_CLUB", "clubes", entidadId, motivo, detalle);
    }

    private void enTransaccion(Runnable operacion) {
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> operacion.run());
    }

    /** Registra y confirma una fila con la sesión del administrador; devuelve su id. */
    private long registrarFila() {
        iniciarSesionComoAdministrador();
        String entidadId = UUID.randomUUID().toString();
        enTransaccion(() -> auditoria.registrar(evento(entidadId, "Alta", null)));
        return jdbc.sql("SELECT id FROM siglim.auditoria WHERE entidad_id = :id")
                .param("id", entidadId).query(Long.class).single();
    }

    private Fila fila(String entidadId) {
        return jdbc.sql("""
                        SELECT liga_id, perfil_id, modulo, accion, entidad, entidad_id, motivo,
                               ocurrido_en IS NOT NULL AS con_fecha
                          FROM siglim.auditoria WHERE entidad_id = :id
                        """).param("id", entidadId).query(Fila.class).single();
    }

    private long contar(String entidadId) {
        return jdbc.sql("SELECT count(*) FROM siglim.auditoria WHERE entidad_id = :id")
                .param("id", entidadId).query(Long.class).single();
    }

    /** Ejecuta y devuelve el error de PostgreSQL de la cadena de causas (se compara SQLSTATE, no el texto). */
    private static PSQLException errorDe(ThrowingCallable operacion) {
        Throwable error = catchThrowable(operacion);
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof PSQLException psql) {
                return psql;
            }
        }
        throw new AssertionError("Se esperaba un error de PostgreSQL y se obtuvo: " + error, error);
    }
}

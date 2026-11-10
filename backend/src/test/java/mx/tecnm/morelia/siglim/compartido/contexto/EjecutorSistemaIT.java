package mx.tecnm.morelia.siglim.compartido.contexto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * {@link EjecutorSistema} contra PostgreSQL real: dentro del ejecutor, la transacción es
 * {@code siglim_app}, {@code siglim.usuario_actual()} es el perfil Sistema de la semilla y RLS lo
 * trata como usuario de liga. Al salir, la siguiente transacción vuelve a ser pública.
 */
class EjecutorSistemaIT extends BaseDatosPrueba {

    @Autowired
    private EjecutorSistema ejecutor;

    @Autowired
    private PlatformTransactionManager transacciones;

    /**
     * Spring Test deja una petición simulada en el hilo de la prueba, y {@link EjecutorSistema} se niega
     * a correr dentro de una petición. Se retira para reproducir el hilo del planificador.
     */
    @BeforeEach
    void comoElHiloDelPlanificador() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** Lo que se observa dentro de la transacción. */
    record Contexto(String rolBd, UUID usuarioActual, boolean usuarioLiga, String claims) {
    }

    @Test
    @DisplayName("ADR-0009 punto 4: dentro del ejecutor, usuario_actual() es el perfil Sistema con siglim_app")
    void transaccionComoSistema() {
        TransactionTemplate plantilla = new TransactionTemplate(transacciones);

        Contexto contexto = ejecutor.ejecutar(() -> plantilla.execute(estado -> jdbc.sql("""
                        SELECT current_user AS rol_bd,
                               siglim.usuario_actual() AS usuario_actual,
                               siglim.es_usuario_liga() AS usuario_liga,
                               current_setting('request.jwt.claims', true) AS claims
                        """).query(Contexto.class).single()));

        assertThat(contexto.rolBd()).isEqualTo("siglim_app");
        assertThat(contexto.usuarioActual()).isEqualTo(EjecutorSistema.PERFIL_SISTEMA);
        assertThat(contexto.usuarioLiga()).as("asignación SISTEMA con ámbito LIGA de la semilla").isTrue();
        assertThat(contexto.claims()).doesNotContain("sub");

        String rolDespues = plantilla.execute(estado ->
                jdbc.sql("SELECT current_user").query(String.class).single());
        assertThat(rolDespues).as("sin sesión fuera del ejecutor").isEqualTo("siglim_publico");
    }
}

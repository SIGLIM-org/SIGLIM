package mx.tecnm.morelia.siglim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;

/**
 * Las tablas inmutables que no son la auditoría ({@code resoluciones} y
 * {@code parametros_temporada}) tampoco se vacían con {@code TRUNCATE ... CASCADE} como dueño del
 * esquema (migración {@code 20261027140000_proteger_inmutables_truncate.sql}). Sin
 * {@code CASCADE} ya las protege la llave foránea de las tablas que las referencian, así que se
 * prueba la variante que sí llegaba a vaciarlas. La auditoría se prueba en {@code AuditoriaServiceIT}.
 *
 * <p>Refs: SCRUM-397, RNF-015, RF-062
 */
class TablasInmutablesIT extends BaseDatosPrueba {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"resoluciones", "parametros_temporada"})
    @DisplayName("RNF-015: el dueño del esquema no vacía la tabla inmutable con TRUNCATE ... CASCADE")
    void duenoNoTruncaPorCascada(String tabla) {
        Throwable error = catchThrowable(() -> jdbc.sql("""
                DO $$
                BEGIN
                  TRUNCATE siglim.%s CASCADE;
                  RAISE EXCEPTION 'El TRUNCATE se aceptó' USING ERRCODE = 'XX000';
                END $$
                """.formatted(tabla)).update());

        PSQLException psql = errorDePostgres(error);
        assertThat(psql.getSQLState()).isEqualTo("P0001");
        assertThat(psql.getServerErrorMessage().getHint()).contains("RNF-015");
    }

    /** Busca el error de PostgreSQL en la cadena de causas (se compara SQLSTATE, no el texto). */
    private static PSQLException errorDePostgres(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof PSQLException psql) {
                return psql;
            }
        }
        throw new AssertionError("Se esperaba un error de PostgreSQL y se obtuvo: " + error, error);
    }
}

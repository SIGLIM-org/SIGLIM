package mx.tecnm.morelia.siglim.disciplina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * H-26.1 contra PostgreSQL real, con {@link TipoIncidenciaService} y {@link ArticulosService}
 * completos: autorización, SQL, restricciones y baja lógica. Cada prueba crea sus propios códigos y
 * números para no chocar con la semilla [DEMO] de la migración.
 *
 * <p>Refs: H-26.1, RF-128, RF-129, DEC-30, RN-005, T-02.3
 */
class CatalogosDisciplinariosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Autowired
    private TiposIncidenciaService tipos;

    @Autowired
    private ArticulosService articulos;

    // ── Tipos de incidencia (RF-128) ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-128: un tipo creado aparece en el catálogo activo y la baja lógica lo oculta sin borrarlo")
    void tiposDeIncidenciaSeCreanListanYSeDanDeBaja() {
        iniciarSesionComoAdministrador();
        String codigo = "PRUEBA_" + UUID.randomUUID().toString().substring(0, 8);

        TipoIncidenciaDto creado = tipos.crear(new CrearTipoIncidenciaRequest(codigo, "Riña en la banca", true));

        assertThat(creado.codigo()).isEqualTo(codigo);
        assertThat(creado.nombre()).isEqualTo("Riña en la banca");
        assertThat(creado.abreExpediente()).isTrue();
        assertThat(creado.activo()).isTrue();
        assertThat(creado.esDemo()).as("solo la semilla es [DEMO]").isFalse();
        assertThat(tipos.listar(true)).extracting(TipoIncidenciaDto::codigo).contains(codigo);
        assertThat(tipos.listar(false)).extracting(TipoIncidenciaDto::codigo).doesNotContain(codigo);

        tipos.actualizar(creado.id(), new ActualizarTipoIncidenciaRequest(null, null, null, false));

        assertThat(tipos.listar(true)).extracting(TipoIncidenciaDto::codigo).doesNotContain(codigo);
        assertThat(tipos.listar(false)).extracting(TipoIncidenciaDto::codigo).contains(codigo);
        assertThat(tipos.listar(null)).as("sin filtro sigue en el catálogo").extracting(TipoIncidenciaDto::codigo)
                .contains(codigo);
        assertThat(filasDeTipo(creado.id())).as("RN-005: la fila se conserva").isEqualTo(1);
    }

    @Test
    @DisplayName("RF-128: un código de tipo repetido en la liga responde 409 DUPLICADO")
    void codigoDeTipoDuplicado() {
        iniciarSesionComoAdministrador();
        String codigo = "DUP_" + UUID.randomUUID().toString().substring(0, 8);
        tipos.crear(new CrearTipoIncidenciaRequest(codigo, "Primera captura", false));

        assertThatThrownBy(() -> tipos.crear(new CrearTipoIncidenciaRequest(codigo, "Segunda captura", true)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.tipos_incidencia WHERE liga_id = :liga AND codigo = :codigo")
                .param("liga", LIGA).param("codigo", codigo).query(Long.class).single()).isEqualTo(1L);
    }

    // ── Artículos (RF-129) ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-129: un artículo creado aparece en el catálogo activo y la baja lógica lo oculta")
    void articulosSeCreanListanYSeDanDeBaja() {
        iniciarSesionComoAdministrador();
        String numero = numeroNuevo();

        ArticuloDto creado = articulos.crear(new CrearArticuloRequest(numero, "Multa por retraso de pago",
                TipoSancion.MULTA, SujetoSancion.CLUB, null, null, null, null, new BigDecimal("750.50"), null));

        assertThat(creado.numero()).isEqualTo(numero);
        assertThat(creado.tipoSancion()).isEqualTo(TipoSancion.MULTA);
        assertThat(creado.montoMulta()).isEqualByComparingTo("750.50");
        assertThat(creado.partidosSuspension()).isNull();
        assertThat(creado.puntos()).isNull();
        assertThat(creado.efecto()).as("nace HACIA_ADELANTE").isEqualTo(EfectoSancion.HACIA_ADELANTE);
        assertThat(creado.aplicacionAutomatica()).as("nace en false").isFalse();
        assertThat(creado.otorgaDefaultRestantes()).as("nace en true").isTrue();
        assertThat(creado.activo()).isTrue();
        assertThat(creado.esDemo()).as("solo la semilla es [DEMO]").isFalse();
        assertThat(articulos.listar(true)).extracting(ArticuloDto::numero).contains(numero);
        assertThat(articulos.listar(false)).extracting(ArticuloDto::numero).doesNotContain(numero);

        articulos.actualizar(creado.id(), new ActualizarArticuloRequest(null, null, null, null, null, null, null,
                null, null, null, false));

        assertThat(articulos.listar(true)).extracting(ArticuloDto::numero).doesNotContain(numero);
        assertThat(articulos.listar(false)).extracting(ArticuloDto::numero).contains(numero);
        assertThat(articulos.listar(null)).as("sin filtro sigue en el catálogo").extracting(ArticuloDto::numero)
                .contains(numero);
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.articulos WHERE id = :id")
                .param("id", creado.id()).query(Long.class).single()).as("RN-005: la fila se conserva").isEqualTo(1L);
    }

    @Test
    @DisplayName("RF-129: un número de artículo repetido en la liga responde 409 DUPLICADO")
    void numeroDeArticuloDuplicado() {
        iniciarSesionComoAdministrador();
        String numero = numeroNuevo();
        articulos.crear(new CrearArticuloRequest(numero, "Baja por riña", TipoSancion.BAJA, SujetoSancion.EQUIPO,
                null, null, null, null, null, null));

        assertThatThrownBy(() -> articulos.crear(new CrearArticuloRequest(numero, "Baja por reincidencia",
                TipoSancion.BAJA, SujetoSancion.EQUIPO, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.articulos WHERE liga_id = :liga AND numero = :numero")
                .param("liga", LIGA).param("numero", numero).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @DisplayName("DEC-30: cambiar el tipo de sanción limpia el valor del tipo anterior en la base")
    void cambiarTipoDeSancionLimpiaElValorAnterior() {
        iniciarSesionComoAdministrador();
        String numero = numeroNuevo();
        ArticuloDto suspension = articulos.crear(new CrearArticuloRequest(numero, "Suspensión por expulsión",
                TipoSancion.SUSPENSION_PARTIDOS, SujetoSancion.JUGADOR, null, null, 3, null, null, null));
        assertThat(suspension.partidosSuspension()).isEqualTo(3);

        ArticuloDto multa = articulos.actualizar(suspension.id(), new ActualizarArticuloRequest(null, null,
                TipoSancion.MULTA, null, null, null, null, null, new BigDecimal("500.00"), null, null));

        assertThat(multa.tipoSancion()).isEqualTo(TipoSancion.MULTA);
        assertThat(multa.partidosSuspension()).as("el valor de la suspensión se limpia").isNull();
        assertThat(multa.puntos()).isNull();
        assertThat(multa.montoMulta()).isEqualByComparingTo("500.00");
        ValoresArticulo persistido = valoresEnBd(suspension.id());
        assertThat(persistido.partidosSuspension()).as("no queda el valor anterior en la tabla").isNull();
        assertThat(persistido.puntos()).isNull();
        assertThat(persistido.montoMulta()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("ck_articulos_valores: PostgreSQL rechaza con 23514 los valores no positivos de cada tipo")
    void elCheckDeValoresRechazaCantidadesNoPositivas() {
        iniciarSesionComoAdministrador();

        PSQLException sinPartidos = errorBd(() -> insertarArticulo(numeroNuevo(), "SUSPENSION_PARTIDOS",
                "0", null, null));
        assertThat(sinPartidos.getSQLState()).as("suspensión con cero partidos").isEqualTo("23514");

        PSQLException sinPuntos = errorBd(() -> insertarArticulo(numeroNuevo(), "PERDIDA_PUNTOS",
                null, "0", null));
        assertThat(sinPuntos.getSQLState()).as("pérdida de puntos con cero puntos").isEqualTo("23514");

        PSQLException sinMonto = errorBd(() -> insertarArticulo(numeroNuevo(), "MULTA",
                null, null, "0.00"));
        assertThat(sinMonto.getSQLState()).as("multa con monto cero").isEqualTo("23514");
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private void insertarArticulo(String numero, String tipo, String partidos, String puntos, String monto) {
        jdbc.sql("""
                        INSERT INTO siglim.articulos (liga_id, numero, descripcion, tipo_sancion, sujeto,
                                                     partidos_suspension, puntos, monto_multa)
                        VALUES (:liga, :numero, 'Artículo de prueba', CAST(:tipo AS siglim.tipo_sancion),
                                'EQUIPO', CAST(:partidos AS smallint), CAST(:puntos AS smallint),
                                CAST(:monto AS numeric(12, 2)))
                        """)
                .param("liga", LIGA)
                .param("numero", numero)
                .param("tipo", tipo)
                .param("partidos", partidos)
                .param("puntos", puntos)
                .param("monto", monto)
                .update();
    }

    private long filasDeTipo(long id) {
        return jdbc.sql("SELECT count(*) FROM siglim.tipos_incidencia WHERE id = :id")
                .param("id", id).query(Long.class).single();
    }

    private ValoresArticulo valoresEnBd(UUID id) {
        return jdbc.sql("""
                        SELECT partidos_suspension, puntos, monto_multa
                          FROM siglim.articulos
                         WHERE id = :id
                        """)
                .param("id", id)
                .query((fila, numero) -> new ValoresArticulo(
                        fila.getObject("partidos_suspension", Integer.class),
                        fila.getObject("puntos", Integer.class),
                        fila.getBigDecimal("monto_multa")))
                .single();
    }

    private static String numeroNuevo() {
        return "T" + UUID.randomUUID().toString().substring(0, 12);
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    /** El error de PostgreSQL de la cadena de causas; se compara SQLSTATE o HINT, nunca el texto. */
    private static PSQLException errorBd(ThrowingCallable operacion) {
        Throwable error = catchThrowable(operacion);
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof PSQLException psql) {
                return psql;
            }
        }
        throw new AssertionError("Se esperaba un error de PostgreSQL y se obtuvo: " + error, error);
    }

    private record ValoresArticulo(Integer partidosSuspension, Integer puntos, BigDecimal montoMulta) {
    }
}

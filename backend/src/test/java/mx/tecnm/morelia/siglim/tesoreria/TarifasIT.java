package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * B-29.1 contra PostgreSQL real, con {@link TarifaService} completo: matriz de permisos de la
 * migración, restricciones de {@code siglim.tarifas} y auditoría. Demuestra los cuatro criterios de
 * aceptación de B-29.
 *
 * <p>Cada prueba crea su propia temporada y categoría: la base se comparte con las demás pruebas de
 * integración.
 */
class TarifasIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");

    @Autowired
    private TarifaService tarifas;

    @Test
    @DisplayName("B-29 criterios 1 y 4 · RF-135: el TESORERO registra una tarifa única por temporada, categoría y concepto, auditada")
    void tarifaUnicaYAuditada() {
        UUID temporada = crearTemporada();
        UUID categoria = asignarCategoriaNueva(temporada);
        UUID tesorero = perfilConRol("TESORERO");
        iniciarSesion(tesorero);

        TarifaDto arbitraje = tarifas.crear(new CrearTarifaRequest(temporada, categoria, ConceptoTarifa.ARBITRAJE,
                new BigDecimal("350.00")));
        TarifaDto cancha = tarifas.crear(new CrearTarifaRequest(temporada, categoria, ConceptoTarifa.CANCHA,
                BigDecimal.ZERO));

        assertThat(arbitraje.monto()).isEqualByComparingTo("350.00").hasScaleOf(2);
        assertThat(arbitraje.esDemo()).isTrue();
        assertThat(cancha.monto()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> tarifas.crear(new CrearTarifaRequest(temporada, categoria, ConceptoTarifa.ARBITRAJE,
                new BigDecimal("400.00"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        assertThat(tarifas.listar(temporada)).extracting(TarifaDto::concepto)
                .containsExactly(ConceptoTarifa.ARBITRAJE, ConceptoTarifa.CANCHA);

        TarifaDto editada = tarifas.actualizar(arbitraje.id(), new ActualizarTarifaRequest(new BigDecimal("420.50")));
        assertThat(editada.monto()).isEqualByComparingTo("420.50");

        assertThat(eventos(arbitraje.id())).containsExactly(
                new Evento("REGISTRAR_TARIFA", tesorero), new Evento("EDITAR_TARIFA", tesorero));
    }

    @Test
    @DisplayName("B-29 criterio 2 · RF-135: un monto negativo se rechaza y no se guarda")
    void montoNegativo() {
        UUID temporada = crearTemporada();
        UUID categoria = asignarCategoriaNueva(temporada);
        iniciarSesion(perfilConRol("TESORERO"));

        assertThatThrownBy(() -> tarifas.crear(new CrearTarifaRequest(temporada, categoria, ConceptoTarifa.CANCHA,
                new BigDecimal("-1.00"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThat(tarifas.listar(temporada)).isEmpty();
    }

    @Test
    @DisplayName("B-29 criterio 3 · RF-003: en una temporada cerrada las tarifas son de solo lectura")
    void temporadaCerradaSoloLectura() {
        UUID temporada = crearTemporada();
        UUID categoria = asignarCategoriaNueva(temporada);
        iniciarSesion(perfilConRol("TESORERO"));
        TarifaDto tarifa = tarifas.crear(new CrearTarifaRequest(temporada, categoria, ConceptoTarifa.ARBITRAJE,
                new BigDecimal("300.00")));

        cerrarTemporada(temporada);

        assertThatThrownBy(() -> tarifas.actualizar(tarifa.id(), new ActualizarTarifaRequest(new BigDecimal("1.00"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        assertThatThrownBy(() -> tarifas.crear(new CrearTarifaRequest(temporada, categoria, ConceptoTarifa.CANCHA,
                new BigDecimal("1.00"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        assertThat(tarifas.listar(temporada)).singleElement()
                .satisfies(fila -> assertThat(fila.monto()).isEqualByComparingTo("300.00"));
    }

    @Test
    @DisplayName("RF-135: una categoría que no participa en la temporada responde 422 REFERENCIA_INVALIDA")
    void categoriaAjena() {
        UUID temporada = crearTemporada();
        UUID ajena = asignarCategoriaNueva(crearTemporada());
        iniciarSesion(perfilConRol("TESORERO"));

        assertThatThrownBy(() -> tarifas.crear(new CrearTarifaRequest(temporada, ajena, ConceptoTarifa.ARBITRAJE,
                new BigDecimal("1.00"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));
    }

    @Test
    @DisplayName("VAL-015: un rol sin tesoreria.tarifa:EDITAR no consulta ni registra tarifas")
    void sinPermiso() {
        UUID temporada = crearTemporada();
        UUID categoria = asignarCategoriaNueva(temporada);
        iniciarSesion(perfilConRol("ARBITRO"));

        assertThatThrownBy(() -> tarifas.listar(temporada))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThatThrownBy(() -> tarifas.crear(new CrearTarifaRequest(temporada, categoria, ConceptoTarifa.ARBITRAJE,
                new BigDecimal("1.00"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.tarifas WHERE temporada_id = :t")
                .param("t", temporada).query(Long.class).single()).isZero();
    }

    // ── Preparación ───────────────────────────────────────────────────────────────────────────

    private UUID crearTemporada() {
        return jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2034-08-01') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID asignarCategoriaNueva(UUID temporada) {
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Categoría " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id) VALUES (:t, :c)")
                .param("t", temporada).param("c", categoria).update();
        return categoria;
    }

    /** Activa y cierra la temporada como lo exige su trigger (RF-001, RF-100). */
    private void cerrarTemporada(UUID temporada) {
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, motivo) VALUES (:id, 'Parámetros de prueba')")
                .param("id", temporada).update();
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'ACTIVA' WHERE id = :id").param("id", temporada).update();
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE id = :id").param("id", temporada).update();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado. */
    private UUID perfilConRol(String rol) {
        UUID perfil = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
        return perfil;
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private List<Evento> eventos(UUID tarifa) {
        return jdbc.sql("""
                        SELECT accion, perfil_id
                          FROM siglim.auditoria
                         WHERE modulo = 'M-13' AND entidad = 'tarifas' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", tarifa.toString())
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

    private record Evento(String accion, UUID perfilId) {
    }
}

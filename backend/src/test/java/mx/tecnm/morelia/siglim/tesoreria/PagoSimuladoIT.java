package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErrorTraducido;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.compartido.error.TraductorErroresBd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H-30.1 contra PostgreSQL real, con {@link PagoSimuladoService} completo: autorización con la
 * matriz de la migración, RLS, el trigger {@code tg_pagos_validar}, el índice único de intento
 * pendiente y la auditoría. Demuestra los criterios 1 a 3 de H-30 y la confirmación única.
 *
 * <p>Cada prueba crea sus propios clubes y cobros; los estados {@code VENCIDO} y {@code RECHAZADO}
 * se preparan con el servicio o por SQL como dueño del esquema, como en {@link CobrosIT}.
 */
class PagoSimuladoIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final BigDecimal MONTO = new BigDecimal("1500.00");

    @Autowired
    private PagoSimuladoService simulados;

    @Autowired
    private CobroService cobros;

    @Autowired
    private TraductorErroresBd traductor;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private Clock reloj;

    // ── Criterio 1: aprobar deja el cobro PAGADO con un pago EN_LINEA_SIMULADO ──────────────

    @Test
    @DisplayName("H-30 criterio 1 · RF-138: la Directiva paga un cobro PENDIENTE y queda PAGADO con EN_LINEA_SIMULADO")
    void directivaPagaCobroPendiente() {
        Escenario escenario = escenario();
        UUID cobro = cobro(escenario.clubA());
        iniciarSesion(escenario.directivaA());

        IntentoPagoDto intento = simulados.iniciar(cobro);
        assertThat(intento.referencia()).matches("^SIM-[0-9]{8}-[A-Z0-9]{6}$");
        assertThat(intento.estado()).isEqualTo("PENDIENTE");
        assertThat(intento.resueltoEn()).isNull();
        assertThat(estado(cobro)).isEqualTo("PENDIENTE");
        assertThat(estadoIntento(intento.id())).isEqualTo("PENDIENTE");

        IntentoPagoDto resuelto = simulados.confirmar(intento.id(), ResultadoPagoSimulado.APROBADO);

        assertThat(resuelto.estado()).isEqualTo("APROBADO");
        assertThat(resuelto.resueltoEn()).isNotNull();
        assertThat(estado(cobro)).isEqualTo("PAGADO");
        assertThat(estadoIntento(intento.id())).isEqualTo("APROBADO");
        assertThat(resueltoEn(intento.id())).isNotNull();

        Pago pago = jdbc.sql("""
                        SELECT id, medio::text AS medio, monto, referencia, intento_id, registrado_por
                          FROM siglim.pagos WHERE cobro_id = :cobro
                        """)
                .param("cobro", cobro)
                .query((fila, numero) -> new Pago(
                        fila.getObject("id", UUID.class),
                        fila.getString("medio"),
                        fila.getBigDecimal("monto"),
                        fila.getString("referencia"),
                        fila.getObject("intento_id", UUID.class),
                        fila.getObject("registrado_por", UUID.class)))
                .single();
        assertThat(pago.medio()).isEqualTo("EN_LINEA_SIMULADO");
        assertThat(pago.monto()).isEqualByComparingTo(MONTO).hasScaleOf(2);
        assertThat(pago.referencia()).isEqualTo(intento.referencia());
        assertThat(pago.intentoId()).isEqualTo(intento.id());
        assertThat(pago.registradoPor()).isEqualTo(escenario.directivaA());
        assertThat(contar("pagos", cobro)).isEqualTo(1);
        assertThat(contar("intentos_pago", cobro)).isEqualTo(1);

        assertThat(eventos(intento.id())).extracting(Evento::accion)
                .containsExactly("INICIAR_PAGO_SIMULADO", "CONFIRMAR_PAGO_SIMULADO");
        assertThat(eventos(intento.id()).get(1).detalle())
                .contains("\"estadoCobro\": \"PAGADO\"", "\"pagoId\": \"" + pago.id() + "\"");
    }

    // ── Criterio 2: rechazar deja RECHAZADO y permite reintentar ─────────────────────────────

    @Test
    @DisplayName("H-30 criterio 2 · RF-138: rechazar deja RECHAZADO y un nuevo intento permite aprobar")
    void rechazaYReintenta() {
        Escenario escenario = escenario();
        UUID cobro = cobro(escenario.clubA());
        iniciarSesion(escenario.directivaA());

        IntentoPagoDto primero = simulados.iniciar(cobro);
        IntentoPagoDto rechazado = simulados.confirmar(primero.id(), ResultadoPagoSimulado.RECHAZADO);

        assertThat(rechazado.estado()).isEqualTo("RECHAZADO");
        assertThat(estado(cobro)).isEqualTo("RECHAZADO");
        assertThat(contar("pagos", cobro)).isZero();

        IntentoPagoDto segundo = simulados.iniciar(cobro);
        assertThat(segundo.id()).isNotEqualTo(primero.id());
        assertThat(segundo.referencia()).isNotEqualTo(primero.referencia());
        assertThat(estado(cobro)).isEqualTo("PENDIENTE");
        assertThat(estadoIntento(primero.id())).isEqualTo("RECHAZADO");

        simulados.confirmar(segundo.id(), ResultadoPagoSimulado.APROBADO);

        assertThat(estado(cobro)).isEqualTo("PAGADO");
        assertThat(estadoIntento(segundo.id())).isEqualTo("APROBADO");
        assertThat(contar("intentos_pago", cobro)).isEqualTo(2);
        assertThat(contar("pagos", cobro)).isEqualTo(1);
    }

    // ── Criterio 3: PAGADO y VENCIDO no se pagan en línea (VAL-028) ──────────────────────────

    @Test
    @DisplayName("H-30 criterio 3 · VAL-028: un cobro PAGADO no inicia otro pago en línea ni se confirma dos veces")
    void cobroPagadoNoSePagaEnLinea() {
        Escenario escenario = escenario();
        UUID cobro = cobro(escenario.clubA());
        iniciarSesion(escenario.directivaA());
        IntentoPagoDto intento = simulados.iniciar(cobro);
        simulados.confirmar(intento.id(), ResultadoPagoSimulado.APROBADO);

        assertThatThrownBy(() -> simulados.iniciar(cobro))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "VAL-028").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("PAGADO");
                });
        assertThatThrownBy(() -> simulados.confirmar(intento.id(), ResultadoPagoSimulado.APROBADO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "VAL-028"));

        assertThat(contar("pagos", cobro)).isEqualTo(1);
        assertThat(contar("intentos_pago", cobro)).isEqualTo(1);
    }

    @Test
    @DisplayName("H-30 criterio 3 · VAL-028: un cobro VENCIDO no se paga en línea, pero sí lo registra Tesorería")
    void cobroVencidoNoSePagaEnLinea() {
        Escenario escenario = escenario();
        UUID cobro = cobro(escenario.clubA());
        cambiarEstado(cobro, "VENCIDO");
        iniciarSesion(escenario.directivaA());

        assertThatThrownBy(() -> simulados.iniciar(cobro))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "VAL-028").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("VENCIDO", "Tesorería");
                });
        assertThat(estado(cobro)).isEqualTo("VENCIDO");
        assertThat(contar("intentos_pago", cobro)).isZero();
        assertThat(contar("pagos", cobro)).isZero();
    }

    // ── Concurrencia y unicidad del intento pendiente ────────────────────────────────────────

    @Test
    @DisplayName("RNF-004: un doble clic devuelve el mismo intento y solo existe uno PENDIENTE")
    void dobleClicUnSoloIntento() {
        Escenario escenario = escenario();
        UUID cobro = cobro(escenario.clubA());
        iniciarSesion(escenario.directivaA());

        IntentoPagoDto primero = simulados.iniciar(cobro);
        IntentoPagoDto segundo = simulados.iniciar(cobro);

        assertThat(segundo.id()).isEqualTo(primero.id());
        assertThat(segundo.referencia()).isEqualTo(primero.referencia());
        assertThat(contar("intentos_pago", cobro)).isEqualTo(1);
        assertThat(contarPendientes(cobro)).isEqualTo(1);
    }

    @Test
    @DisplayName("RNF-004: la base rechaza un segundo intento PENDIENTE para el mismo cobro")
    void indiceUnicoImpideDosPendientes() {
        Escenario escenario = escenario();
        UUID cobro = cobro(escenario.clubA());
        iniciarSesion(escenario.directivaA());
        simulados.iniciar(cobro);

        Throwable error = catchThrowable(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado ->
                jdbc.sql("""
                                INSERT INTO intentos_pago (cobro_id, referencia, iniciado_por)
                                VALUES (:cobro, :referencia, :perfil)
                                """)
                        .param("cobro", cobro)
                        .param("referencia", "SIM-20300301-DUP001")
                        .param("perfil", escenario.directivaA())
                        .update()));

        assertThat(error).as("el índice ux_intentos_pendiente debía rechazar el segundo intento").isNotNull();
        ErrorTraducido traducido = traductor.traducir(error, false);
        assertThat(traducido.estado()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(contarPendientes(cobro)).isEqualTo(1);
    }

    @Test
    @DisplayName("RNF-004: dos confirmaciones del mismo intento dejan un solo pago y la segunda responde 409")
    void dosConfirmacionesSoloUnaGana() {
        Escenario escenario = escenario();
        UUID cobro = cobro(escenario.clubA());
        iniciarSesion(escenario.directivaA());
        IntentoPagoDto intento = simulados.iniciar(cobro);

        simulados.confirmar(intento.id(), ResultadoPagoSimulado.APROBADO);
        assertThatThrownBy(() -> simulados.confirmar(intento.id(), ResultadoPagoSimulado.APROBADO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "VAL-028"));

        assertThat(contar("pagos", cobro)).isEqualTo(1);
        assertThat(contar("intentos_pago", cobro)).isEqualTo(1);
        // La confirmación perdedora no encuentra el intento PENDIENTE: su actualización condicional no toca filas.
        assertThat(jdbc.sql("""
                        UPDATE siglim.intentos_pago
                           SET estado = 'RECHAZADO'
                         WHERE id = :id AND estado = 'PENDIENTE'
                        """)
                .param("id", intento.id())
                .update()).isZero();
    }

    // ── Ámbito y permisos (VAL-015, VAL-024) ────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-015 y VAL-024: el Tesorero no paga en línea y la Directiva de otro club no ve el cobro")
    void permisosYAmbito() {
        Escenario escenario = escenario();
        UUID cobro = cobro(escenario.clubA());
        iniciarSesion(escenario.directivaA());
        IntentoPagoDto intento = simulados.iniciar(cobro);

        iniciarSesion(escenario.tesorero());
        assertThatThrownBy(() -> simulados.iniciar(cobro))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));

        iniciarSesion(escenario.directivaB());
        assertThatThrownBy(() -> simulados.iniciar(cobro)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> simulados.confirmar(intento.id(), ResultadoPagoSimulado.APROBADO))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(estado(cobro)).isEqualTo("PENDIENTE");
        assertThat(estadoIntento(intento.id())).isEqualTo("PENDIENTE");
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private Escenario escenario() {
        UUID clubA = club("A");
        UUID clubB = club("B");
        return new Escenario(clubA, perfilConRol("TESORERO"), perfilDeDirectiva(clubA), perfilDeDirectiva(clubB));
    }

    /** Cobro manual PENDIENTE al club, creado con el servicio como lo hace el Tesorero (H-29). */
    private UUID cobro(UUID club) {
        iniciarSesion(perfilConRol("TESORERO"));
        LocalDate limite = LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO)).plusDays(15);
        return cobros.crear(new CrearCobroRequest(club, null, "Cuota de prueba", MONTO, null, limite)).id();
    }

    /** Cambia el estado como dueño del esquema; el trigger de VAL-028 valida la transición. */
    private void cambiarEstado(UUID cobro, String estado) {
        jdbc.sql("UPDATE siglim.cobros SET estado = CAST(:estado AS siglim.estado_cobro) WHERE id = :id")
                .param("estado", estado).param("id", cobro).update();
    }

    private String estado(UUID cobro) {
        return jdbc.sql("SELECT estado::text FROM siglim.cobros WHERE id = :id")
                .param("id", cobro).query(String.class).single();
    }

    private String estadoIntento(UUID intento) {
        return jdbc.sql("SELECT estado::text FROM siglim.intentos_pago WHERE id = :id")
                .param("id", intento).query(String.class).single();
    }

    private Object resueltoEn(UUID intento) {
        return jdbc.sql("SELECT resuelto_en FROM siglim.intentos_pago WHERE id = :id")
                .param("id", intento).query(Object.class).single();
    }

    private long contar(String tabla, UUID cobro) {
        return jdbc.sql("SELECT count(*) FROM siglim." + tabla + " WHERE cobro_id = :cobro")
                .param("cobro", cobro).query(Long.class).single();
    }

    private long contarPendientes(UUID cobro) {
        return jdbc.sql("""
                        SELECT count(*) FROM siglim.intentos_pago
                         WHERE cobro_id = :cobro AND estado = 'PENDIENTE'
                        """)
                .param("cobro", cobro).query(Long.class).single();
    }

    private java.util.List<Evento> eventos(UUID intento) {
        return jdbc.sql("""
                        SELECT accion, detalle::text AS detalle
                          FROM siglim.auditoria
                         WHERE modulo = 'M-13' AND entidad = 'intentos_pago' AND entidad_id = :id
                         ORDER BY ocurrido_en, id
                        """)
                .param("id", intento.toString())
                .query((fila, numero) -> new Evento(fila.getString("accion"), fila.getString("detalle")))
                .list();
    }

    private UUID club(String nombre) {
        return jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA)
                .param("nombre", "Club " + nombre + " " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado. */
    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
        return perfil;
    }

    /** Perfil con el rol DIRECTIVA_CLUB de la matriz, en ámbito CLUB de ese club. */
    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil).param("club", club).update();
        return perfil;
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private record Escenario(UUID clubA, UUID tesorero, UUID directivaA, UUID directivaB) {
    }

    private record Pago(UUID id, String medio, BigDecimal monto, String referencia, UUID intentoId,
                        UUID registradoPor) {
    }

    private record Evento(String accion, String detalle) {
    }
}

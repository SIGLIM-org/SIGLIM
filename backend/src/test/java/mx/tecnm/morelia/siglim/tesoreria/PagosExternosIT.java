package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.AlmacenamientoEnMemoriaPrueba.AlmacenamientoEnMemoria;
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
 * H-31.1 contra PostgreSQL real, con {@link PagoService} completo: autorización con la matriz de la
 * migración, RLS, el trigger {@code tg_pagos_validar} y las restricciones de {@code documentos}. S3 lo
 * reemplaza el doble en memoria de {@code BaseDatosPrueba} (DEC-56). Una prueba por criterio de H-31.
 *
 * <p>Cada prueba crea sus propios clubes y cobros; los estados {@code VENCIDO} y {@code RECHAZADO} se
 * preparan por SQL como dueño del esquema, como en {@link CobrosIT}, porque sus transiciones son de
 * otras historias.
 */
class PagosExternosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final BigDecimal MONTO = new BigDecimal("1500.00");
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private PagoService pagos;

    @Autowired
    private CobroService cobros;

    @Autowired
    private AlmacenamientoEnMemoria almacenamiento;

    @Autowired
    private TraductorErroresBd traductor;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private Clock reloj;

    // ── Criterio 1: PENDIENTE y VENCIDO quedan PAGADO con comprobante y auditoría ─────────────

    @Test
    @DisplayName("H-31 criterio 1 · RF-139: el TESORERO paga un cobro PENDIENTE y uno VENCIDO; quedan PAGADO con comprobante y auditoría")
    void tesoreroRegistraPagoExterno() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        UUID pendiente = cobro(escenario.clubA());
        UUID vencido = cobro(escenario.clubA());
        cambiarEstado(vencido, "VENCIDO");

        PagoDto pagoPendiente = pagos.registrar(pendiente,
                new RegistrarPagoRequest(MedioPago.TRANSFERENCIA, MONTO, null, "  SPEI-123  "), PDF);
        LocalDate ayer = LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO)).minusDays(1);
        PagoDto pagoVencido = pagos.registrar(vencido,
                new RegistrarPagoRequest(MedioPago.EFECTIVO, MONTO, ayer, null), PDF);

        verificarPagado(pendiente, pagoPendiente, escenario.tesorero(), "PENDIENTE");
        verificarPagado(vencido, pagoVencido, escenario.tesorero(), "VENCIDO");
        assertThat(pagoPendiente.referencia()).isEqualTo("SPEI-123");
        assertThat(pagoPendiente.registradoPor().nombre()).isEqualTo("Usuario de prueba");
        assertThat(pagoVencido.referencia()).isNull();
        assertThat(pagoVencido.pagadoEn().toInstant())
                .isEqualTo(ayer.atStartOfDay(ZONA_DE_NEGOCIO).toInstant());
    }

    // ── Criterio 2: el monto debe ser el total ────────────────────────────────────────────────

    @Test
    @DisplayName("H-31 criterio 2 · RF-139: un monto distinto responde 422, el cobro sigue PENDIENTE y no se sube nada")
    void montoDistintoNoPaga() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        UUID cobro = cobro(escenario.clubA());
        int objetosAntes = almacenamiento.objetos().size();

        assertThatThrownBy(() -> pagos.registrar(cobro,
                new RegistrarPagoRequest(MedioPago.EFECTIVO, new BigDecimal("1000.00"), null, null), PDF))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.UNPROCESSABLE_CONTENT, "RF-139").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("1500.00 MXN");
                });

        assertThat(estado(cobro)).isEqualTo("PENDIENTE");
        assertThat(contar("pagos", cobro)).isZero();
        assertThat(almacenamiento.objetos()).hasSize(objetosAntes);
    }

    // ── Criterio 3: un cobro pagado no admite otro pago ──────────────────────────────────────

    @Test
    @DisplayName("H-31 criterio 3 · VAL-028: un segundo pago al mismo cobro responde 409 y no se duplica")
    void segundoPagoRechazado() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        UUID cobro = cobro(escenario.clubA());
        RegistrarPagoRequest peticion = new RegistrarPagoRequest(MedioPago.EFECTIVO, MONTO, null, null);
        pagos.registrar(cobro, peticion, PDF);

        assertThatThrownBy(() -> pagos.registrar(cobro, peticion, PDF))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "VAL-028").accept(excepcion);
                    assertThat(excepcion.getMessage()).isEqualTo("El cobro está PAGADO; no admite otro pago.");
                });
        assertThat(contar("pagos", cobro)).isEqualTo(1);
    }

    @Test
    @DisplayName("VAL-028: un cobro RECHAZADO no admite pago y sigue RECHAZADO")
    void cobroRechazadoNoAdmitePago() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        UUID cobro = cobro(escenario.clubA());
        cambiarEstado(cobro, "RECHAZADO");

        assertThatThrownBy(() -> pagos.registrar(cobro,
                new RegistrarPagoRequest(MedioPago.EFECTIVO, MONTO, null, null), PDF))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "VAL-028"));
        assertThat(estado(cobro)).isEqualTo("RECHAZADO");
    }

    @Test
    @DisplayName("VAL-028: si dos pagos se cruzan, el trigger rechaza el segundo y TraductorErroresBd responde 409")
    void triggerEsLaUltimaGarantia() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        UUID cobro = cobro(escenario.clubA());
        pagos.registrar(cobro, new RegistrarPagoRequest(MedioPago.EFECTIVO, MONTO, null, null), PDF);

        // El pago que perdió la carrera ya pasó la validación del servicio: llega directo al INSERT.
        Throwable error = catchThrowable(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado ->
                jdbc.sql("""
                                INSERT INTO pagos (cobro_id, medio, monto, registrado_por)
                                VALUES (:cobro, 'EFECTIVO', :monto, :perfil)
                                """)
                        .param("cobro", cobro).param("monto", MONTO).param("perfil", escenario.tesorero())
                        .update()));

        assertThat(error).as("el trigger debía rechazar el segundo pago").isNotNull();
        ErrorTraducido traducido = traductor.traducir(error, false);
        assertThat(traducido.estado()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(traducido.codigo()).isEqualTo("VAL-028");
        assertThat(contar("pagos", cobro)).isEqualTo(1);
    }

    @Test
    @DisplayName("VAL-015: la Directiva, que paga en línea pero no registra pagos externos, recibe 403")
    void directivaNoRegistraPagoExterno() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        UUID cobro = cobro(escenario.clubA());
        iniciarSesion(escenario.directivaA());

        assertThatThrownBy(() -> pagos.registrar(cobro,
                new RegistrarPagoRequest(MedioPago.EFECTIVO, MONTO, null, null), PDF))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThat(estado(cobro)).isEqualTo("PENDIENTE");
    }

    // ── Historial de pagos (RF-140) ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-024 · RF-140: el historial de un cobro de otro club responde 404; el del propio, sin el nombre del Tesorero")
    void historialSegunAmbito() {
        Escenario escenario = escenario();
        iniciarSesion(escenario.tesorero());
        UUID cobro = cobro(escenario.clubA());
        PagoDto pago = pagos.registrar(cobro, new RegistrarPagoRequest(MedioPago.EFECTIVO, MONTO, null, null), PDF);

        HistorialPagosDto delTesorero = pagos.historial(cobro);
        assertThat(delTesorero.pagos()).extracting(PagoDto::id).containsExactly(pago.id());
        assertThat(delTesorero.intentos()).isEmpty();

        iniciarSesion(escenario.directivaB());
        assertThatThrownBy(() -> pagos.historial(cobro)).isInstanceOf(RecursoNoEncontradoException.class);

        iniciarSesion(escenario.directivaA());
        HistorialPagosDto propio = pagos.historial(cobro);
        assertThat(propio.pagos()).singleElement().satisfies(visto -> {
            assertThat(visto.comprobante().tipoMime()).isEqualTo("application/pdf");
            assertThat(visto.registradoPor().id()).isEqualTo(escenario.tesorero());
            assertThat(visto.registradoPor().nombre()).as("RLS de perfiles").isNull();
        });
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private void verificarPagado(UUID cobro, PagoDto pago, UUID tesorero, String estadoAnterior) {
        assertThat(estado(cobro)).isEqualTo("PAGADO");
        assertThat(pago.cobroId()).isEqualTo(cobro);
        assertThat(pago.monto()).isEqualByComparingTo(MONTO).hasScaleOf(2);
        assertThat(contar("pagos", cobro)).isEqualTo(1);

        Comprobante comprobante = jdbc.sql("""
                        SELECT d.id, t.codigo, d.s3_clave, d.tipo_mime, d.tamano_bytes, d.cargado_por
                          FROM siglim.documentos d
                          JOIN siglim.tipos_documento t ON t.id = d.tipo_documento_id
                         WHERE d.pago_id = :pago
                        """)
                .param("pago", pago.id())
                .query((fila, numero) -> new Comprobante(fila.getObject("id", UUID.class), fila.getString("codigo"),
                        fila.getString("s3_clave"), fila.getString("tipo_mime"), fila.getInt("tamano_bytes"),
                        fila.getObject("cargado_por", UUID.class)))
                .single();
        assertThat(comprobante.codigo()).isEqualTo("COMPROBANTE_PAGO");
        assertThat(comprobante.s3Clave()).isEqualTo(
                "liga/" + LIGA + "/comprobantes/" + pago.id() + "/" + comprobante.id() + ".pdf");
        assertThat(comprobante.tipoMime()).isEqualTo("application/pdf");
        assertThat(comprobante.tamanoBytes()).isEqualTo(PDF.length);
        assertThat(comprobante.cargadoPor()).isEqualTo(tesorero);
        assertThat(almacenamiento.objetos()).containsKey(comprobante.s3Clave());
        assertThat(pago.comprobante().documentoId()).isEqualTo(comprobante.id());

        Evento evento = jdbc.sql("""
                        SELECT accion, perfil_id, detalle::text AS detalle
                          FROM siglim.auditoria
                         WHERE modulo = 'M-13' AND entidad = 'pagos' AND entidad_id = :id
                        """)
                .param("id", pago.id().toString())
                .query((fila, numero) -> new Evento(fila.getString("accion"), fila.getObject("perfil_id", UUID.class),
                        fila.getString("detalle")))
                .single();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_PAGO");
        assertThat(evento.perfilId()).isEqualTo(tesorero);
        assertThat(evento.detalle()).contains("\"estadoAnterior\": \"" + estadoAnterior + "\"",
                "\"documentoId\": \"" + comprobante.id() + "\"");
        assertThat(evento.detalle()).doesNotContain("s3", "amazonaws", "https://", comprobante.s3Clave());
    }

    private Escenario escenario() {
        UUID clubA = club("A");
        UUID clubB = club("B");
        return new Escenario(clubA, perfilConRol("TESORERO"), perfilDeDirectiva(clubA), perfilDeDirectiva(clubB));
    }

    /** Cobro manual PENDIENTE al club, creado con el servicio como lo hace el Tesorero (H-29). */
    private UUID cobro(UUID club) {
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

    private long contar(String tabla, UUID cobro) {
        return jdbc.sql("SELECT count(*) FROM siglim." + tabla + " WHERE cobro_id = :cobro")
                .param("cobro", cobro).query(Long.class).single();
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

    private record Comprobante(UUID id, String codigo, String s3Clave, String tipoMime, int tamanoBytes,
                               UUID cargadoPor) {
    }

    private record Evento(String accion, UUID perfilId, String detalle) {
    }
}

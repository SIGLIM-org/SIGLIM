package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.AlmacenamientoDocumentos;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.ArchivoAlmacenado;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.CarpetaDocumento;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.ClaveDocumento;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ArchivoRechazadoException;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.tesoreria.PagoRepository.DatosPago;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link PagoService}. Sin Spring, base de datos ni S3. */
class PagoServiceTest {

    private static final String REGISTRAR = "tesoreria.pago:REGISTRAR";
    private static final String CONSULTAR = "tesoreria.cobro:CONSULTAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID COBRO_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PERFIL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    /** 2030-03-01 06:00 en Ciudad de México: el «hoy» de negocio es el 1 de marzo. */
    private static final Instant AHORA = Instant.parse("2030-03-01T12:00:00Z");
    private static final LocalDate HOY = LocalDate.parse("2030-03-01");
    private static final BigDecimal MONTO = new BigDecimal("1500.00");
    private static final byte[] PDF = "%PDF-1.4 comprobante".getBytes(StandardCharsets.US_ASCII);
    private static final ArchivoAlmacenado ALMACENADO = new ArchivoAlmacenado("siglim-documentos",
            "liga/x/comprobantes/y/z.pdf", "https://siglim-documentos.s3.us-east-2.amazonaws.com/liga/x/z.pdf",
            "application/pdf", PDF.length, "a".repeat(64));

    private final CobroRepository cobros = mock(CobroRepository.class);
    private final PagoRepository pagos = mock(PagoRepository.class);
    private final AlmacenamientoDocumentos almacenamiento = mock(AlmacenamientoDocumentos.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final PagoService servicio = new PagoService(cobros, pagos, almacenamiento, autorizacion, auditoria,
            Clock.fixed(AHORA, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    private static CobroDto cobro(EstadoCobro estado) {
        return new CobroDto(COBRO_ID, TipoCobro.MANUAL, "Cuota", MONTO, HOY, HOY.plusDays(15), estado,
                new CobroDto.ReferenciaDto(CLUB, "Halcones"), null, OffsetDateTime.parse("2030-03-01T06:00:00-06:00"));
    }

    private void cobroEn(EstadoCobro estado) {
        given(cobros.buscar(LIGA, COBRO_ID)).willReturn(Optional.of(cobro(estado)));
    }

    private void subidaYLecturaExitosas() {
        given(almacenamiento.guardar(any(ClaveDocumento.class), eq(PDF))).willReturn(ALMACENADO);
        given(pagos.buscar(eq(LIGA), any(UUID.class))).willAnswer(invocacion -> Optional.of(new PagoDto(
                invocacion.getArgument(1), COBRO_ID, MedioPago.EFECTIVO, MONTO, null, null,
                new CobroDto.ReferenciaDto(PERFIL, "Tesorero"), null)));
    }

    private static RegistrarPagoRequest peticion(MedioPago medio, BigDecimal monto, LocalDate fecha, String referencia) {
        return new RegistrarPagoRequest(medio, monto, fecha, referencia);
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private void sinEscrituras() {
        verifyNoInteractions(almacenamiento, auditoria);
        verify(pagos, never()).insertar(any());
        verify(pagos, never()).insertarComprobante(any(), any(), any(), any());
    }

    // ── Registro de un pago externo (RF-139) ─────────────────────────────────────────────────

    @ParameterizedTest(name = "desde {0}")
    @EnumSource(value = EstadoCobro.class, names = {"PENDIENTE", "VENCIDO"})
    @DisplayName("RF-139: registra el pago total con comprobante, en este orden: sube, inserta pago y documento, audita")
    void registraPago(EstadoCobro estado) {
        cobroEn(estado);
        subidaYLecturaExitosas();

        PagoDto pago = servicio.registrar(COBRO_ID,
                peticion(MedioPago.TRANSFERENCIA, MONTO, null, "  SPEI-123  "), PDF);

        verify(autorizacion).exigir(REGISTRAR, ObjetivoAutorizacion.deClubes(CLUB));

        ArgumentCaptor<ClaveDocumento> clave = ArgumentCaptor.forClass(ClaveDocumento.class);
        verify(almacenamiento).guardar(clave.capture(), eq(PDF));
        assertThat(clave.getValue().ligaId()).isEqualTo(LIGA);
        assertThat(clave.getValue().carpeta()).isEqualTo(CarpetaDocumento.COMPROBANTES);
        assertThat(clave.getValue().entidadId()).isEqualTo(pago.id());

        ArgumentCaptor<DatosPago> datos = ArgumentCaptor.forClass(DatosPago.class);
        verify(pagos).insertar(datos.capture());
        assertThat(datos.getValue().id()).isEqualTo(pago.id());
        assertThat(datos.getValue().cobroId()).isEqualTo(COBRO_ID);
        assertThat(datos.getValue().medio()).isEqualTo(MedioPago.TRANSFERENCIA);
        assertThat(datos.getValue().monto()).isEqualByComparingTo(MONTO);
        assertThat(datos.getValue().referencia()).isEqualTo("SPEI-123");
        assertThat(datos.getValue().pagadoEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
        assertThat(datos.getValue().registradoPor()).isEqualTo(PERFIL);

        verify(pagos).insertarComprobante(clave.getValue().documentoId(), pago.id(), ALMACENADO, PERFIL);

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().modulo()).isEqualTo("M-13");
        assertThat(evento.getValue().accion()).isEqualTo("REGISTRAR_PAGO");
        assertThat(evento.getValue().entidad()).isEqualTo("pagos");
        assertThat(evento.getValue().entidadId()).isEqualTo(pago.id().toString());
        assertThat(evento.getValue().detalle())
                .containsEntry("cobroId", COBRO_ID.toString())
                .containsEntry("medio", "TRANSFERENCIA")
                .containsEntry("monto", MONTO)
                .containsEntry("estadoAnterior", estado.name())
                .containsEntry("documentoId", clave.getValue().documentoId().toString())
                .containsEntry("tipoMime", "application/pdf")
                .containsEntry("tamanoBytes", PDF.length)
                .containsKey("pagadoEn");
    }

    @Test
    @DisplayName("RNF-018: el detalle de auditoría no lleva URL, bucket, clave, hash ni referencia")
    void auditoriaSinDatosDeS3() {
        cobroEn(EstadoCobro.PENDIENTE);
        subidaYLecturaExitosas();

        servicio.registrar(COBRO_ID, peticion(MedioPago.EFECTIVO, MONTO, null, "folio"), PDF);

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().detalle()).doesNotContainKeys("url", "s3Bucket", "s3Clave", "sha256",
                "nombreArchivo", "referencia");
        assertThat(evento.getValue().detalle().values()).noneMatch(valor ->
                String.valueOf(valor).contains(ALMACENADO.url()) || String.valueOf(valor).contains(ALMACENADO.s3Clave())
                        || String.valueOf(valor).contains(ALMACENADO.s3Bucket()));
    }

    @Test
    @DisplayName("RF-139: con fechaPago, el pago queda al inicio de ese día en America/Mexico_City; sin referencia, nula")
    void fechaDePagoEnZonaDeNegocio() {
        cobroEn(EstadoCobro.PENDIENTE);
        subidaYLecturaExitosas();

        servicio.registrar(COBRO_ID, peticion(MedioPago.OTRO, MONTO, HOY.minusDays(3), "   "), PDF);

        ArgumentCaptor<DatosPago> datos = ArgumentCaptor.forClass(DatosPago.class);
        verify(pagos).insertar(datos.capture());
        assertThat(datos.getValue().pagadoEn()).isEqualTo(OffsetDateTime.parse("2030-02-26T00:00:00-06:00"));
        assertThat(datos.getValue().referencia()).isNull();
    }

    @Test
    @DisplayName("RF-139: la fecha de hoy en la zona de negocio se acepta")
    void fechaDeHoy() {
        cobroEn(EstadoCobro.PENDIENTE);
        subidaYLecturaExitosas();

        servicio.registrar(COBRO_ID, peticion(MedioPago.EFECTIVO, MONTO, HOY, null), PDF);

        verify(pagos).insertar(any(DatosPago.class));
    }

    @ParameterizedTest(name = "desde {0}")
    @EnumSource(value = EstadoCobro.class, names = {"PAGADO", "RECHAZADO"})
    @DisplayName("VAL-028: un cobro PAGADO o RECHAZADO responde 409 con su estado, sin subir el comprobante")
    void estadoNoAdmitePago(EstadoCobro estado) {
        cobroEn(estado);

        assertThatThrownBy(() -> servicio.registrar(COBRO_ID, peticion(MedioPago.EFECTIVO, MONTO, null, null), PDF))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(excepcion.codigo()).isEqualTo("VAL-028");
                    assertThat(excepcion.getMessage()).isEqualTo("El cobro está " + estado + "; no admite otro pago.");
                });
        sinEscrituras();
    }

    @Test
    @DisplayName("RF-139: un monto distinto del cobro responde 422 con el monto esperado, sin subir el comprobante")
    void montoDistinto() {
        cobroEn(EstadoCobro.PENDIENTE);

        assertThatThrownBy(() -> servicio.registrar(COBRO_ID,
                peticion(MedioPago.EFECTIVO, new BigDecimal("1000.00"), null, null), PDF))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(excepcion.codigo()).isEqualTo("RF-139");
                    assertThat(excepcion.getMessage())
                            .isEqualTo("El pago debe cubrir el monto total del cobro (1500.00 MXN).");
                });
        sinEscrituras();
    }

    @Test
    @DisplayName("RF-139: el monto se compara por valor (1500 equivale a 1500.00)")
    void montoIgualConOtraEscala() {
        cobroEn(EstadoCobro.PENDIENTE);
        subidaYLecturaExitosas();

        servicio.registrar(COBRO_ID, peticion(MedioPago.EFECTIVO, new BigDecimal("1500"), null, null), PDF);

        verify(pagos).insertar(any(DatosPago.class));
    }

    @Test
    @DisplayName("ADR-0012: EN_LINEA_SIMULADO responde 400 VALIDACION sin subir el comprobante")
    void medioEnLineaSimulado() {
        cobroEn(EstadoCobro.PENDIENTE);

        assertThatThrownBy(() -> servicio.registrar(COBRO_ID,
                peticion(MedioPago.EN_LINEA_SIMULADO, MONTO, null, null), PDF))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        sinEscrituras();
    }

    @Test
    @DisplayName("RF-139: un comprobante vacío responde 400 VALIDACION y uno ausente también, sin subir")
    void archivoVacioOAusente() {
        cobroEn(EstadoCobro.PENDIENTE);
        RegistrarPagoRequest valida = peticion(MedioPago.EFECTIVO, MONTO, null, null);

        assertThatThrownBy(() -> servicio.registrar(COBRO_ID, valida, new byte[0]))
                .isInstanceOfSatisfying(ArchivoRechazadoException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(excepcion.codigo()).isEqualTo("VALIDACION");
                });
        assertThatThrownBy(() -> servicio.registrar(COBRO_ID, valida, null))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        sinEscrituras();
    }

    @Test
    @DisplayName("RF-139: una fecha de pago futura en la zona de negocio responde 400 VALIDACION sin subir")
    void fechaFutura() {
        cobroEn(EstadoCobro.PENDIENTE);

        assertThatThrownBy(() -> servicio.registrar(COBRO_ID,
                peticion(MedioPago.EFECTIVO, MONTO, HOY.plusDays(1), null), PDF))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(excepcion.getMessage()).isEqualTo("La fecha de pago no puede ser posterior a hoy.");
                });
        sinEscrituras();
    }

    @Test
    @DisplayName("VAL-024: un cobro inexistente o fuera de la liga responde 404 sin evaluar el permiso")
    void cobroInexistente() {
        given(cobros.buscar(LIGA, COBRO_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(COBRO_ID, peticion(MedioPago.EFECTIVO, MONTO, null, null), PDF))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
        sinEscrituras();
    }

    @Test
    @DisplayName("VAL-015: sin tesoreria.pago:REGISTRAR no valida, no sube ni escribe")
    void sinPermiso() {
        cobroEn(EstadoCobro.PAGADO);
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(REGISTRAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.registrar(COBRO_ID, peticion(MedioPago.EFECTIVO, MONTO, null, null), PDF))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        sinEscrituras();
    }

    // ── Historial de pagos (RF-140) ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-140: el historial devuelve pagos e intentos del cobro con tesoreria.cobro:CONSULTAR")
    void historial() {
        cobroEn(EstadoCobro.PAGADO);
        PagoDto pago = new PagoDto(UUID.randomUUID(), COBRO_ID, MedioPago.EFECTIVO, MONTO, null, null, null, null);
        IntentoPagoDto intento = new IntentoPagoDto(UUID.randomUUID(), "SIM-20300301-ABC123", "RECHAZADO", null, null);
        given(pagos.listarDeCobro(LIGA, COBRO_ID)).willReturn(List.of(pago));
        given(pagos.listarIntentos(LIGA, COBRO_ID)).willReturn(List.of(intento));

        HistorialPagosDto historial = servicio.historial(COBRO_ID);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deClubes(CLUB));
        assertThat(historial.pagos()).containsExactly(pago);
        assertThat(historial.intentos()).containsExactly(intento);
    }

    @Test
    @DisplayName("VAL-024: el historial de un cobro inexistente responde 404 sin consultar pagos")
    void historialCobroInexistente() {
        given(cobros.buscar(LIGA, COBRO_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.historial(COBRO_ID)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion, pagos);
    }
}

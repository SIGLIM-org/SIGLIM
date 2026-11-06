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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.tesoreria.CobroRepository.DatosCobro;
import mx.tecnm.morelia.siglim.tesoreria.CobroRepository.EquipoDeClub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link CobroService}. Sin Spring ni base de datos. */
class CobroServiceTest {

    private static final String CONSULTAR = "tesoreria.cobro:CONSULTAR";
    private static final String CREAR = "tesoreria.cobro:CREAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTRO_CLUB = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID EQUIPO = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PERFIL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final Instant AHORA = Instant.parse("2030-03-01T12:00:00Z");
    private static final LocalDate HOY = LocalDate.parse("2030-03-01");
    private static final LocalDate LIMITE = LocalDate.parse("2030-03-15");
    private static final String CONCEPTO = "Cuota de inscripción";
    private static final BigDecimal MONTO = new BigDecimal("1500.00");
    private static final CobroDto.ReferenciaDto CLUB_REF = new CobroDto.ReferenciaDto(CLUB, "Halcones");
    private static final EquipoDeClub EQUIPO_DE_CLUB = new EquipoDeClub(EQUIPO, "Halcones A", CLUB);
    private static final CobroDto COBRO = new CobroDto(ID, TipoCobro.MANUAL, CONCEPTO, MONTO, HOY, LIMITE,
            EstadoCobro.PENDIENTE, CLUB_REF, null, OffsetDateTime.parse("2030-03-01T06:00:00-06:00"));

    private final CobroRepository cobros = mock(CobroRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final CobroService servicio = new CobroService(cobros, autorizacion, auditoria,
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

    private static CrearCobroRequest peticion(UUID clubId, UUID equipoId, String concepto, BigDecimal monto,
            LocalDate periodoInicio, LocalDate fechaLimite) {
        return new CrearCobroRequest(clubId, equipoId, concepto, monto, periodoInicio, fechaLimite);
    }

    private static CrearCobroRequest peticionValida() {
        return peticion(CLUB, EQUIPO, "  " + CONCEPTO + "  ", MONTO, null, LIMITE);
    }

    private void clubValido() {
        given(cobros.buscarClub(LIGA, CLUB)).willReturn(Optional.of(CLUB_REF));
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    // ── Alta de cobros manuales (RF-137) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-137: crea un cobro MANUAL PENDIENTE con monto BigDecimal y periodo predeterminado")
    void crearCobroManual() {
        clubValido();
        given(cobros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.of(EQUIPO_DE_CLUB));
        given(cobros.insertar(eq(LIGA), any(DatosCobro.class), eq(PERFIL))).willReturn(ID);
        given(cobros.buscar(LIGA, ID)).willReturn(Optional.of(COBRO));

        CobroDto creado = servicio.crear(peticionValida());

        assertThat(creado).isEqualTo(COBRO);
        verify(autorizacion).exigir(CREAR, ObjetivoAutorizacion.deClubes(CLUB));

        ArgumentCaptor<DatosCobro> datos = ArgumentCaptor.forClass(DatosCobro.class);
        verify(cobros).insertar(eq(LIGA), datos.capture(), eq(PERFIL));
        assertThat(datos.getValue().clubId()).isEqualTo(CLUB);
        assertThat(datos.getValue().equipoId()).isEqualTo(EQUIPO);
        assertThat(datos.getValue().tipo()).isEqualTo(TipoCobro.MANUAL);
        assertThat(datos.getValue().concepto()).isEqualTo(CONCEPTO);
        assertThat(datos.getValue().monto()).isEqualByComparingTo(MONTO);
        assertThat(datos.getValue().periodoInicio()).isEqualTo(HOY);
        assertThat(datos.getValue().fechaLimite()).isEqualTo(LIMITE);

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-13");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_COBRO");
        assertThat(evento.entidad()).isEqualTo("cobros");
        assertThat(evento.entidadId()).isEqualTo(ID.toString());
        assertThat(evento.detalle())
                .containsEntry("clubId", CLUB.toString())
                .containsEntry("equipoId", EQUIPO.toString())
                .containsEntry("tipo", "MANUAL")
                .containsEntry("estado", "PENDIENTE")
                .containsEntry("monto", MONTO)
                .containsEntry("periodoInicio", HOY.toString())
                .containsEntry("fechaLimite", LIMITE.toString())
                .doesNotContainKey("concepto");
    }

    @Test
    @DisplayName("RF-137: un cobro al club no lleva equipo y lo omite en la auditoría")
    void crearCobroAlClub() {
        clubValido();
        given(cobros.insertar(eq(LIGA), any(DatosCobro.class), eq(PERFIL))).willReturn(ID);
        given(cobros.buscar(LIGA, ID)).willReturn(Optional.of(COBRO));

        servicio.crear(peticion(CLUB, null, CONCEPTO, MONTO, null, LIMITE));

        verify(cobros, never()).buscarEquipo(any(), any());
        ArgumentCaptor<DatosCobro> datos = ArgumentCaptor.forClass(DatosCobro.class);
        verify(cobros).insertar(eq(LIGA), datos.capture(), eq(PERFIL));
        assertThat(datos.getValue().equipoId()).isNull();
        assertThat(eventoAuditado().detalle()).doesNotContainKey("equipoId");
    }

    @Test
    @DisplayName("RF-137: respeta el periodo de pago indicado")
    void crearConPeriodoIndicado() {
        clubValido();
        LocalDate inicio = HOY.minusDays(10);
        given(cobros.insertar(eq(LIGA), any(DatosCobro.class), eq(PERFIL))).willReturn(ID);
        given(cobros.buscar(LIGA, ID)).willReturn(Optional.of(COBRO));

        servicio.crear(peticion(CLUB, null, CONCEPTO, MONTO, inicio, LIMITE));

        ArgumentCaptor<DatosCobro> datos = ArgumentCaptor.forClass(DatosCobro.class);
        verify(cobros).insertar(eq(LIGA), datos.capture(), eq(PERFIL));
        assertThat(datos.getValue().periodoInicio()).isEqualTo(inicio);
    }

    @Test
    @DisplayName("VAL-024: un club inexistente o de otra liga responde 404 sin evaluar el permiso")
    void clubInexistente() {
        given(cobros.buscarClub(LIGA, CLUB)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(peticionValida()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion, auditoria);
        verify(cobros, never()).insertar(any(), any(), any());
    }

    @Test
    @DisplayName("VAL-015: sin permiso de crear no escribe ni audita")
    void crearSinPermiso() {
        clubValido();
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(CREAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.crear(peticionValida()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verify(cobros, never()).insertar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("REFERENCIA_INVALIDA: un equipo inexistente responde 422 sin escribir")
    void equipoInexistente() {
        clubValido();
        given(cobros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(peticionValida()))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));
        verify(cobros, never()).insertar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("REFERENCIA_INVALIDA: un equipo de otro club responde 422 sin escribir")
    void equipoDeOtroClub() {
        clubValido();
        given(cobros.buscarEquipo(LIGA, EQUIPO))
                .willReturn(Optional.of(new EquipoDeClub(EQUIPO, "Ajenos", OTRO_CLUB)));

        assertThatThrownBy(() -> servicio.crear(peticionValida()))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"))
                .hasMessageContaining("Ajenos");
        verify(cobros, never()).insertar(any(), any(), any());
    }

    @Test
    @DisplayName("VALIDACION: rechaza concepto vacío o mayor de 200 caracteres")
    void conceptoInvalido() {
        clubValido();

        assertThatThrownBy(() -> servicio.crear(peticion(CLUB, null, "   ", MONTO, null, LIMITE)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.crear(peticion(CLUB, null, "x".repeat(201), MONTO, null, LIMITE)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(cobros, never()).insertar(any(), any(), any());
    }

    @Test
    @DisplayName("RF-137: rechaza monto nulo, cero, negativo o con más de dos decimales")
    void montoInvalido() {
        clubValido();

        for (BigDecimal monto : List.of(new BigDecimal("-1.00"), BigDecimal.ZERO, new BigDecimal("10.123"))) {
            assertThatThrownBy(() -> servicio.crear(peticion(CLUB, null, CONCEPTO, monto, null, LIMITE)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class,
                            error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        }
        assertThatThrownBy(() -> servicio.crear(peticion(CLUB, null, CONCEPTO, null, null, LIMITE)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(cobros, never()).insertar(any(), any(), any());
    }

    @Test
    @DisplayName("RF-137: la fecha límite es obligatoria y no puede ser anterior al periodo")
    void fechaLimiteInvalida() {
        clubValido();

        assertThatThrownBy(() -> servicio.crear(peticion(CLUB, null, CONCEPTO, MONTO, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.crear(peticion(CLUB, null, CONCEPTO, MONTO, HOY.plusDays(5), HOY.plusDays(4))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.crear(peticion(CLUB, null, CONCEPTO, MONTO, null, HOY.minusDays(1))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(cobros, never()).insertar(any(), any(), any());
    }

    // ── Listado y ámbito (RF-140) ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-140: el listado usa el alcance del permiso y la liga activa")
    void listarUsaAlcance() {
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(false, Set.of(CLUB), false);
        FiltroCobros filtro = new FiltroCobros(CLUB, EQUIPO, TipoCobro.MANUAL, List.of(EstadoCobro.PENDIENTE),
                HOY, LIMITE, 0, 20, "fechaLimite,asc");
        given(autorizacion.alcance(CONSULTAR)).willReturn(alcance);
        given(cobros.listar(LIGA, filtro, alcance)).willReturn(List.of(COBRO));
        given(cobros.contar(LIGA, filtro, alcance)).willReturn(21L);

        Pagina<CobroDto> pagina = servicio.listar(filtro);

        assertThat(pagina.contenido()).containsExactly(COBRO);
        assertThat(pagina.totalElementos()).isEqualTo(21);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
        verify(cobros).listar(LIGA, filtro, alcance);
        verify(cobros).contar(LIGA, filtro, alcance);
    }

    @Test
    @DisplayName("RF-140: rechaza orden, tamaño o rango de fechas inválidos sin consultar")
    void listarValidaFiltro() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));

        assertThatThrownBy(() -> servicio.listar(new FiltroCobros(null, null, null, List.of(), null, null, 0, 20,
                "concepto,asc")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.listar(new FiltroCobros(null, null, null, List.of(), null, null, 0, 101,
                "creadoEn,desc")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.listar(new FiltroCobros(null, null, null, List.of(), LIMITE, HOY, 0, 20,
                "creadoEn,desc")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verifyNoInteractions(cobros);
    }
}

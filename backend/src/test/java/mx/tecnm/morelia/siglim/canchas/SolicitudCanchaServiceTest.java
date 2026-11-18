package mx.tecnm.morelia.siglim.canchas;

import static mx.tecnm.morelia.siglim.canchas.DatosSolicitudPrueba.ofertaExterna;
import static mx.tecnm.morelia.siglim.canchas.DatosSolicitudPrueba.prestamo;
import static mx.tecnm.morelia.siglim.canchas.DatosSolicitudPrueba.prestamoDe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.canchas.SolicitudCanchaDto.PerfilDto;
import mx.tecnm.morelia.siglim.canchas.SolicitudCanchaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de los trámites de cancha de {@link SolicitudCanchaService}. Sin Spring ni BD. */
class SolicitudCanchaServiceTest {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CANCHA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    /** 1 de enero de 2032 a las 03:00 UTC: todavía 31 de diciembre de 2031 en Morelia. */
    private static final Instant AHORA = Instant.parse("2032-01-01T03:00:00Z");
    private static final OffsetDateTime CAPTURADA_EN = OffsetDateTime.parse("2031-12-31T21:00:00-06:00");
    private static final String OFICIO = "OF-2031-0007";

    private final SolicitudCanchaRepository solicitudes = mock(SolicitudCanchaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final SolicitudCanchaService servicio = new SolicitudCanchaService(solicitudes, autorizacion,
            auditoria, motivoOperacion, Clock.fixed(AHORA, ZoneOffset.UTC),
            new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void preparar() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA)
                .expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));

        given(solicitudes.buscarClubActivo(LIGA, CLUB)).willReturn(Optional.of(new ReferenciaDto(CLUB, "Halcones")));
        given(solicitudes.existeCancha(LIGA, CANCHA)).willReturn(true);
        given(solicitudes.siguienteNumeroOficio(eq(LIGA), anyInt())).willReturn(OFICIO);
        given(solicitudes.insertar(any(), any(), any(), any(), any(), any(), any(), any())).willReturn(ID);
        given(solicitudes.buscar(LIGA, ID)).willReturn(Optional.of(prestamoPendiente()));
        given(solicitudes.resolver(any(), any(), any(), any(), any())).willReturn(1);
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    private static SolicitudCanchaDto prestamoPendiente() {
        return solicitud(TipoSolicitudCancha.PRESTAMO, EstadoSolicitudCancha.PENDIENTE,
                new ReferenciaDto(CLUB, "Halcones"), null);
    }

    private static SolicitudCanchaDto solicitud(TipoSolicitudCancha tipo, EstadoSolicitudCancha estado,
            ReferenciaDto club, String externo) {
        return new SolicitudCanchaDto(ID, tipo, tipo.formato(), estado, OFICIO, club, externo, null,
                tipo.esOferta() ? null : prestamo(), tipo.esOferta() ? DatosSolicitudPrueba.oferta() : null,
                new PerfilDto(PERFIL, "Mario Ruiz"), CAPTURADA_EN, null, null, null);
    }

    // ── Captura (RF-116, RF-117, RF-118) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-117: el préstamo de un club nace PENDIENTE, con oficio del año de Morelia, y se audita sin datos personales")
    void crearPrestamo() {
        SolicitudCanchaDto creada = servicio.crear(prestamoDe(CLUB));

        assertThat(creada.estado()).isEqualTo(EstadoSolicitudCancha.PENDIENTE);
        verify(autorizacion).exigir(SolicitudCanchaService.CREAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(solicitudes).siguienteNumeroOficio(LIGA, 2031);
        verify(solicitudes).insertar(LIGA, TipoSolicitudCancha.PRESTAMO, OFICIO, CLUB, null, null, prestamo(), PERFIL);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("REGISTRAR_SOLICITUD_CANCHA");
        assertThat(evento.getValue().detalle()).containsEntry("numeroOficio", OFICIO)
                .containsEntry("estado", "PENDIENTE").containsEntry("clubId", CLUB.toString());
        assertThat(evento.getValue().detalle().toString()).doesNotContain(DatosSolicitudPrueba.TELEFONO, "Mario");
    }

    @Test
    @DisplayName("RF-116: la oferta de un externo se autoriza sobre la liga y nace PENDIENTE_NEGOCIACION")
    void crearOfertaExterna() {
        servicio.crear(ofertaExterna("  Rosa Gómez  "));

        verify(autorizacion).exigir(SolicitudCanchaService.CREAR, ObjetivoAutorizacion.deLiga());
        verify(solicitudes).insertar(LIGA, TipoSolicitudCancha.OFERTA, OFICIO, null, "Rosa Gómez", null,
                DatosSolicitudPrueba.oferta(), PERFIL);
        verify(solicitudes, never()).buscarClubActivo(any(), any());
    }

    @Test
    @DisplayName("RF-117: un préstamo sin club o con oferente externo responde 400 sin autorizar ni escribir")
    void prestamoSinClub() {
        assertThatThrownBy(() -> servicio.crear(new CrearSolicitudCanchaRequest(TipoSolicitudCancha.COMODATO,
                null, "Externo", null, prestamo(), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verifyNoInteractions(autorizacion, auditoria);
    }

    @Test
    @DisplayName("RF-116: una oferta con club y externo a la vez responde 400")
    void ofertaConDosOferentes() {
        assertThatThrownBy(() -> servicio.crear(new CrearSolicitudCanchaRequest(TipoSolicitudCancha.OFERTA,
                CLUB, "Externo", null, null, DatosSolicitudPrueba.oferta())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
    }

    @Test
    @DisplayName("R8: cada tipo exige los campos de su formato y rechaza los del otro")
    void bloqueDelFormato() {
        assertThatThrownBy(() -> servicio.crear(new CrearSolicitudCanchaRequest(TipoSolicitudCancha.OFERTA,
                CLUB, null, null, prestamo(), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION))
                .hasMessageContaining("Formato 2");
        assertThatThrownBy(() -> servicio.crear(new CrearSolicitudCanchaRequest(TipoSolicitudCancha.PRESTAMO,
                CLUB, null, null, prestamo(), DatosSolicitudPrueba.oferta())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION))
                .hasMessageContaining("Formato 1");
    }

    @Test
    @DisplayName("RF-117: la hora de término debe ser posterior al inicio")
    void horarioInvalido() {
        CrearSolicitudCanchaRequest peticion = new CrearSolicitudCanchaRequest(TipoSolicitudCancha.PRESTAMO,
                CLUB, null, null, prestamo(LocalTime.of(12, 0), LocalTime.of(12, 0)), null);

        assertThatThrownBy(() -> servicio.crear(peticion))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(solicitudes, never()).insertar(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-117: un club inexistente o cerrado responde 422 REFERENCIA_INVALIDA")
    void clubInexistente() {
        UUID otro = UUID.randomUUID();
        given(solicitudes.buscarClubActivo(LIGA, otro)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(prestamoDe(otro)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
    }

    @Test
    @DisplayName("RF-117: una cancha que no es de la liga responde 422 REFERENCIA_INVALIDA")
    void canchaInexistente() {
        UUID otra = UUID.randomUUID();

        assertThatThrownBy(() -> servicio.crear(new CrearSolicitudCanchaRequest(TipoSolicitudCancha.PRESTAMO,
                CLUB, null, otra, prestamo(), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
    }

    @Test
    @DisplayName("VAL-015: sin canchas.solicitud:CREAR la captura responde 403 y no escribe")
    void crearSinPermiso() {
        org.mockito.BDDMockito.willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(eq(SolicitudCanchaService.CREAR), any());

        assertThatThrownBy(() -> servicio.crear(prestamoDe(CLUB)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verify(solicitudes, never()).siguienteNumeroOficio(any(), anyInt());
    }

    // ── Dictamen (RF-116, RF-117, RN-021) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-117: el Presidente aprueba un préstamo PENDIENTE y se audita el cambio de estado")
    void aprobar() {
        given(solicitudes.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(prestamoPendiente()));

        servicio.resolver(ID, new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.APROBADA, "  "));

        verify(autorizacion).exigir(SolicitudCanchaService.APROBAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(solicitudes).resolver(eq(ID), eq(EstadoSolicitudCancha.APROBADA), eq(PERFIL), any(), eq(null));
        verify(motivoOperacion, never()).fijar(any());
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("RESOLVER_SOLICITUD_CANCHA");
        assertThat(evento.getValue().detalle()).containsEntry("estadoAnterior", "PENDIENTE")
                .containsEntry("estado", "APROBADA");
    }

    @Test
    @DisplayName("RN-021: rechazar una oferta exige observaciones y las deja como motivo")
    void rechazarOferta() {
        given(solicitudes.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitud(
                TipoSolicitudCancha.OFERTA, EstadoSolicitudCancha.PENDIENTE_NEGOCIACION, null, "Rosa Gómez")));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.RECHAZADA, "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));

        String motivo = "El costo excede las tarifas de la liga";
        servicio.resolver(ID, new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.RECHAZADA, motivo));

        verify(autorizacion, org.mockito.Mockito.times(2))
                .exigir(SolicitudCanchaService.APROBAR, ObjetivoAutorizacion.deLiga());
        verify(motivoOperacion).fijar(motivo);
        verify(solicitudes).resolver(eq(ID), eq(EstadoSolicitudCancha.RECHAZADA), eq(PERFIL), any(), eq(motivo));
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().motivo()).isEqualTo(motivo);
    }

    @Test
    @DisplayName("DEC-35: una decisión de otro formato responde 400 VALIDACION")
    void decisionDeOtroFormato() {
        given(solicitudes.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(prestamoPendiente()));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.ACEPTADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(solicitudes, never()).resolver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-117: dictaminar un trámite ya resuelto responde 409 RF-117")
    void yaResuelto() {
        given(solicitudes.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitud(
                TipoSolicitudCancha.COMODATO, EstadoSolicitudCancha.NO_APROBADA, new ReferenciaDto(CLUB, "Halcones"),
                null)));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.APROBADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-117"));
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-024: dictaminar un trámite inexistente responde 404")
    void resolverInexistente() {
        given(solicitudes.buscarParaActualizar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.APROBADA, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    // ── Carta (RF-118) ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-118: la carta lleva el número de oficio, el asunto del formato y la fecha de captura en Morelia")
    void carta() {
        given(solicitudes.nombreLiga(LIGA)).willReturn("Liga Municipal de Futbol Amateur Morelia");

        CartaOficioDto carta = servicio.oficio(ID);

        verify(autorizacion).exigir(SolicitudCanchaService.GENERAR_OFICIO, ObjetivoAutorizacion.deClubes(CLUB));
        assertThat(carta.numeroOficio()).isEqualTo(OFICIO);
        assertThat(carta.formato()).isEqualTo(1);
        assertThat(carta.asunto()).isEqualTo("Solicitud de préstamo de cancha deportiva.");
        assertThat(carta.fechaEmision()).hasToString("2031-12-31");
        assertThat(carta.solicitante()).isEqualTo("Halcones");
        assertThat(carta.prestamo()).isEqualTo(prestamo());
    }

    // ── Bandeja (ADR-0007) ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ADR-0007: la bandeja une los alcances de CREAR, APROBAR y GENERAR")
    void alcanceUnificado() {
        UUID otro = UUID.randomUUID();
        given(autorizacion.alcance(SolicitudCanchaService.CREAR))
                .willReturn(new AlcanceAutorizacion(false, Set.of(CLUB), false));
        given(autorizacion.alcance(SolicitudCanchaService.APROBAR)).willThrow(Autorizacion.sinPermiso());
        given(autorizacion.alcance(SolicitudCanchaService.GENERAR_OFICIO))
                .willReturn(new AlcanceAutorizacion(false, Set.of(otro), false));
        FiltroSolicitudesCancha filtro = new FiltroSolicitudesCancha(List.of(), null, 0, 20);

        servicio.listar(filtro);

        ArgumentCaptor<AlcanceAutorizacion> alcance = ArgumentCaptor.forClass(AlcanceAutorizacion.class);
        verify(solicitudes).listar(eq(LIGA), eq(filtro), alcance.capture());
        assertThat(alcance.getValue().todaLaLiga()).isFalse();
        assertThat(alcance.getValue().clubes()).containsExactlyInAnyOrder(CLUB, otro);
    }

    @Test
    @DisplayName("VAL-015: sin ninguno de los tres permisos la bandeja responde 403")
    void bandejaSinPermiso() {
        given(autorizacion.alcance(any())).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.listar(new FiltroSolicitudesCancha(null, null, 0, 20)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verify(solicitudes, never()).listar(any(), any(), any());
    }

    @Test
    @DisplayName("B-14: el contexto de quien solo dictamina no ofrece clubes ni canchas")
    void contextoSinCaptura() {
        given(autorizacion.alcance(SolicitudCanchaService.APROBAR))
                .willReturn(new AlcanceAutorizacion(true, Set.of(), false));
        given(autorizacion.alcance(SolicitudCanchaService.CREAR)).willThrow(Autorizacion.sinPermiso());
        given(autorizacion.alcance(SolicitudCanchaService.GENERAR_OFICIO)).willThrow(Autorizacion.sinPermiso());

        ContextoSolicitudesCanchaDto contexto = servicio.contexto();

        assertThat(contexto.clubes()).isEmpty();
        assertThat(contexto.canchas()).isEmpty();
        assertThat(contexto.capturaExternos()).isFalse();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }
}

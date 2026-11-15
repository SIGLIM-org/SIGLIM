package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.PerfilDto;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoRepository.EquipoOperable;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de la venta y la conservación de plaza de {@link OperacionEquipoService}. Sin Spring ni BD. */
class OperacionEquipoServiceTest {

    private static final String SOLICITAR = "organizacion.venta:SOLICITAR";
    private static final String APROBAR = "organizacion.venta:APROBAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PRESIDENTE = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTRO_CLUB = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID EQUIPO = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID TEMPORADA = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final Instant AHORA = Instant.parse("2031-08-02T12:00:00Z");
    private static final OffsetDateTime RESUELTA_EN = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final OffsetDateTime SOLICITADA_EN = OffsetDateTime.parse("2031-08-01T06:00:00-06:00");
    private static final String OBSERVACIONES = "La asamblea no aprobó la venta";

    private static final ClubDto CLUB_DESTINO = new ClubDto(OTRO_CLUB, "Club Águilas", EstadoClub.ACTIVO,
            OffsetDateTime.parse("2031-03-01T06:00:00-06:00"), null, null, null);
    private static final ClubDto CLUB_DESTINO_CERRADO = new ClubDto(OTRO_CLUB, "Club Águilas",
            EstadoClub.CERRADO, OffsetDateTime.parse("2031-03-01T06:00:00-06:00"),
            LocalDate.parse("2031-06-01"), "Cambio de nombre", null);

    private final OperacionEquipoRepository operaciones = mock(OperacionEquipoRepository.class);
    private final ClubRepository clubes = mock(ClubRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final OperacionEquipoService servicio = new OperacionEquipoService(operaciones, clubes,
            autorizacion, auditoria, motivoOperacion, Clock.fixed(AHORA, ZoneOffset.UTC),
            new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void preparar() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA)
                .expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));

        given(operaciones.buscarEquipo(LIGA, EQUIPO))
                .willReturn(Optional.of(equipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA)));
        given(clubes.buscarParaCompartir(LIGA, OTRO_CLUB)).willReturn(Optional.of(CLUB_DESTINO));
        given(operaciones.insertar(any(), any(), any(), any(), any(), any())).willReturn(ID);
        given(operaciones.buscar(LIGA, ID))
                .willReturn(Optional.of(operacion(EstadoOperacionEquipo.SOLICITADA, null)));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Datos de prueba ──────────────────────────────────────────────────────────────────────

    private static EquipoOperable equipo(EstadoEquipo estado, EstadoTemporada temporada) {
        return new EquipoOperable(EQUIPO, "Halcones", CLUB, "Club Halcones", estado, TEMPORADA,
                "Temporada 2031", temporada);
    }

    private static OperacionEquipoDto operacion(EstadoOperacionEquipo estado, String observaciones) {
        boolean sinResolver = estado == EstadoOperacionEquipo.SOLICITADA;
        return new OperacionEquipoDto(ID, TipoOperacionEquipo.VENTA, estado,
                new ReferenciaDto(EQUIPO, "Halcones"),
                new ReferenciaDto(CLUB, "Club Halcones"),
                new ReferenciaDto(OTRO_CLUB, "Club Águilas"),
                new TemporadaResumenDto(TEMPORADA, "Temporada 2031", EstadoTemporada.ACTIVA),
                new PerfilDto(PERFIL, "Ana López"), SOLICITADA_EN,
                sinResolver ? null : new PerfilDto(PRESIDENTE, "Presidente"),
                sinResolver ? null : RESUELTA_EN, null, observaciones);
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

    // ── Registro de la operación (criterio 1 y 4) ───────────────────────────────────────────

    @Test
    @DisplayName("RF-103: la Directiva registra la venta y queda SOLICITADA, auditada")
    void registrarVenta() {
        OperacionEquipoDto creada = servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, EQUIPO, OTRO_CLUB, "  Acuerdo de asamblea  "));

        assertThat(creada.estado()).isEqualTo(EstadoOperacionEquipo.SOLICITADA);
        verify(autorizacion).exigir(SOLICITAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(operaciones).insertar(TipoOperacionEquipo.VENTA, EQUIPO, CLUB, OTRO_CLUB, PERFIL,
                "Acuerdo de asamblea");

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-03");
        assertThat(evento.accion()).isEqualTo("SOLICITAR_OPERACION_EQUIPO");
        assertThat(evento.entidad()).isEqualTo("operaciones_equipo");
        assertThat(evento.detalle())
                .containsEntry("tipo", "VENTA")
                .containsEntry("equipoId", EQUIPO.toString())
                .containsEntry("clubOrigenId", CLUB.toString())
                .containsEntry("clubDestinoId", OTRO_CLUB.toString())
                .containsEntry("estado", "SOLICITADA")
                .as("las observaciones de la solicitud quedan en la auditoría")
                .containsEntry("observaciones", "Acuerdo de asamblea");
    }

    @Test
    @DisplayName("RF-103: la venta sin club destino responde 400 VALIDACION")
    void ventaSinDestino() {
        assertThatThrownBy(() -> servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, EQUIPO, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(operaciones, never()).insertar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-103: la venta con el mismo club de origen responde 400 VALIDACION")
    void ventaAlMismoClub() {
        assertThatThrownBy(() -> servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, EQUIPO, CLUB, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(operaciones, never()).insertar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-104: la conservación de plaza se registra sin club destino")
    void registrarConservacion() {
        servicio.crear(new RegistrarOperacionEquipoRequest(TipoOperacionEquipo.CONSERVACION_PLAZA,
                EQUIPO, null, null));

        verify(operaciones).insertar(TipoOperacionEquipo.CONSERVACION_PLAZA, EQUIPO, CLUB, null,
                PERFIL, null);
        assertThat(eventoAuditado().detalle())
                .containsEntry("tipo", "CONSERVACION_PLAZA")
                .doesNotContainKey("clubDestinoId")
                .doesNotContainKey("observaciones");
    }

    @Test
    @DisplayName("RF-103, DEC-132: una segunda venta vigente del mismo equipo responde 409 RF-103")
    void ventaDuplicada() {
        given(operaciones.estadoVigente(EQUIPO, TipoOperacionEquipo.VENTA))
                .willReturn(Optional.of(EstadoOperacionEquipo.AUTORIZADA));

        assertThatThrownBy(() -> servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, EQUIPO, OTRO_CLUB, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-103"))
                .hasMessageContaining("autorizada");
        verify(operaciones, never()).insertar(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-104, DEC-132: una segunda conservación de plaza vigente responde 409 RF-104")
    void conservacionDuplicada() {
        given(operaciones.estadoVigente(EQUIPO, TipoOperacionEquipo.CONSERVACION_PLAZA))
                .willReturn(Optional.of(EstadoOperacionEquipo.SOLICITADA));

        assertThatThrownBy(() -> servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.CONSERVACION_PLAZA, EQUIPO, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-104"));
        verify(operaciones, never()).insertar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("DEC-14, DEC-132: una venta vigente no impide registrar la conservación de plaza")
    void ventaVigenteNoBloqueaConservacion() {
        given(operaciones.estadoVigente(EQUIPO, TipoOperacionEquipo.VENTA))
                .willReturn(Optional.of(EstadoOperacionEquipo.SOLICITADA));

        servicio.crear(new RegistrarOperacionEquipoRequest(TipoOperacionEquipo.CONSERVACION_PLAZA,
                EQUIPO, null, null));

        verify(operaciones).insertar(TipoOperacionEquipo.CONSERVACION_PLAZA, EQUIPO, CLUB, null,
                PERFIL, null);
    }

    @Test
    @DisplayName("RF-104: la conservación de plaza no acepta club destino")
    void conservacionConDestino() {
        assertThatThrownBy(() -> servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.CONSERVACION_PLAZA, EQUIPO, OTRO_CLUB, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verifyNoInteractions(clubes);
    }

    // ── Ámbito, estado y referencias ────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-024: un equipo de otro club responde 404")
    void equipoFueraDeAmbito() {
        willThrow(Autorizacion.fueraDeAmbito(SOLICITAR)).given(autorizacion)
                .exigir(SOLICITAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, EQUIPO, OTRO_CLUB, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(operaciones, never()).insertar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-003: un equipo de una temporada cerrada no admite operaciones (409)")
    void temporadaCerrada() {
        given(operaciones.buscarEquipo(LIGA, EQUIPO))
                .willReturn(Optional.of(equipo(EstadoEquipo.ACTIVO, EstadoTemporada.CERRADA)));

        assertThatThrownBy(() -> servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, EQUIPO, OTRO_CLUB, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
    }

    @Test
    @DisplayName("RF-009: un equipo dado de baja no admite operaciones (409)")
    void equipoDeBaja() {
        given(operaciones.buscarEquipo(LIGA, EQUIPO))
                .willReturn(Optional.of(equipo(EstadoEquipo.BAJA, EstadoTemporada.ACTIVA)));

        assertThatThrownBy(() -> servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.CONSERVACION_PLAZA, EQUIPO, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-009"));
    }

    @Test
    @DisplayName("REFERENCIA_INVALIDA: un club destino de otra liga responde 422")
    void clubDestinoInexistente() {
        given(clubes.buscarParaCompartir(LIGA, OTRO_CLUB)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, EQUIPO, OTRO_CLUB, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
    }

    @Test
    @DisplayName("RN-041: un club destino cerrado no recibe equipos (409)")
    void clubDestinoCerrado() {
        given(clubes.buscarParaCompartir(LIGA, OTRO_CLUB))
                .willReturn(Optional.of(CLUB_DESTINO_CERRADO));

        assertThatThrownBy(() -> servicio.crear(new RegistrarOperacionEquipoRequest(
                TipoOperacionEquipo.VENTA, EQUIPO, OTRO_CLUB, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RN-041"));
    }

    // ── Dictamen del Presidente (criterio 2, RN-040) ────────────────────────────────────────

    @Test
    @DisplayName("RN-040: el Presidente autoriza y se guardan su perfil y la fecha")
    void presidenteAutoriza() {
        given(operaciones.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(operacion(EstadoOperacionEquipo.SOLICITADA, null)));
        given(operaciones.buscar(LIGA, ID))
                .willReturn(Optional.of(operacion(EstadoOperacionEquipo.AUTORIZADA, "Revisado en sesión")));

        OperacionEquipoDto autorizada = servicio.resolver(ID,
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.AUTORIZADA, "Revisado en sesión"));

        assertThat(autorizada.estado()).isEqualTo(EstadoOperacionEquipo.AUTORIZADA);
        verify(autorizacion).exigir(APROBAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(operaciones).resolver(ID, EstadoOperacionEquipo.AUTORIZADA, PERFIL, RESUELTA_EN,
                "Revisado en sesión");
        verify(motivoOperacion, never()).fijar(any());
        assertThat(eventoAuditado().accion()).isEqualTo("AUTORIZAR_OPERACION_EQUIPO");
    }

    @Test
    @DisplayName("RN-040: autorizar sin nota no envía observaciones, para conservar las de la solicitud")
    void autorizarSinNota() {
        given(operaciones.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(operacion(EstadoOperacionEquipo.SOLICITADA, "Acuerdo de asamblea")));

        servicio.resolver(ID, new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.AUTORIZADA, "   "));

        verify(operaciones).resolver(ID, EstadoOperacionEquipo.AUTORIZADA, PERFIL, RESUELTA_EN, null);
    }

    @Test
    @DisplayName("RF-003: no se autoriza una operación de una temporada que ya cerró (409)")
    void autorizarConTemporadaCerrada() {
        OperacionEquipoDto solicitada = operacion(EstadoOperacionEquipo.SOLICITADA, null);
        given(operaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(new OperacionEquipoDto(
                solicitada.id(), solicitada.tipo(), solicitada.estado(), solicitada.equipo(),
                solicitada.clubOrigen(), solicitada.clubDestino(),
                new TemporadaResumenDto(TEMPORADA, "Temporada 2031", EstadoTemporada.CERRADA),
                solicitada.solicitadaPor(), solicitada.solicitadaEn(), null, null, null, null)));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.AUTORIZADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(operaciones, never()).resolver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RN-041: no se autoriza la venta si el club destino cerró después de la solicitud (409)")
    void autorizarConDestinoCerrado() {
        given(operaciones.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(operacion(EstadoOperacionEquipo.SOLICITADA, null)));
        given(clubes.buscarParaCompartir(LIGA, OTRO_CLUB)).willReturn(Optional.of(CLUB_DESTINO_CERRADO));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.AUTORIZADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RN-041"));
        verify(operaciones, never()).resolver(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RN-021: el rechazo no revisa el club destino aunque haya cerrado")
    void rechazarConDestinoCerrado() {
        given(operaciones.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(operacion(EstadoOperacionEquipo.SOLICITADA, null)));
        given(clubes.buscarParaCompartir(LIGA, OTRO_CLUB)).willReturn(Optional.of(CLUB_DESTINO_CERRADO));

        servicio.resolver(ID, new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.RECHAZADA,
                OBSERVACIONES));

        verify(operaciones).resolver(ID, EstadoOperacionEquipo.RECHAZADA, PERFIL, RESUELTA_EN, OBSERVACIONES);
    }

    @Test
    @DisplayName("RN-040, RN-021: el rechazo exige observaciones y las guarda como motivo")
    void presidenteRechaza() {
        given(operaciones.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(operacion(EstadoOperacionEquipo.SOLICITADA, null)));
        given(operaciones.buscar(LIGA, ID))
                .willReturn(Optional.of(operacion(EstadoOperacionEquipo.RECHAZADA, OBSERVACIONES)));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.RECHAZADA, "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        verify(operaciones, never()).resolver(any(), any(), any(), any(), any());

        servicio.resolver(ID, new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.RECHAZADA,
                "  " + OBSERVACIONES + "  "));

        verify(motivoOperacion).fijar(OBSERVACIONES);
        verify(operaciones).resolver(ID, EstadoOperacionEquipo.RECHAZADA, PERFIL, RESUELTA_EN,
                OBSERVACIONES);

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("RECHAZAR_OPERACION_EQUIPO");
        assertThat(evento.getValue().motivo()).isEqualTo(OBSERVACIONES);
    }

    @Test
    @DisplayName("RN-040: una operación ya resuelta responde 409 y no se vuelve a escribir")
    void resolverRepetida() {
        given(operaciones.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(operacion(EstadoOperacionEquipo.AUTORIZADA, null)));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.RECHAZADA, OBSERVACIONES)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-103"));
        verify(operaciones, never()).resolver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-024: resolver una operación de otro club responde 404")
    void resolverFueraDeAmbito() {
        given(operaciones.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(operacion(EstadoOperacionEquipo.SOLICITADA, null)));
        willThrow(Autorizacion.fueraDeAmbito(APROBAR)).given(autorizacion)
                .exigir(APROBAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverOperacionEquipoRequest(DecisionOperacionEquipo.AUTORIZADA, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(operaciones, never()).resolver(any(), any(), any(), any(), any());
    }
}

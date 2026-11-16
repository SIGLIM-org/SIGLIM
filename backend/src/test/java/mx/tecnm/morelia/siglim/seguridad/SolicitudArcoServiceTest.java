package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Registro y seguimiento de solicitudes ARCO (RF-098, B-01), sin Spring ni base de datos. */
class SolicitudArcoServiceTest {

    private static final String GESTIONAR = "seguridad.arco:GESTIONAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PERSONA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PERFIL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID SOLICITUD = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final AlcanceAutorizacion LIGA_COMPLETA = new AlcanceAutorizacion(true, Set.of(), false);

    private final SolicitudArcoRepository solicitudes = mock(SolicitudArcoRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final SolicitudArcoService servicio = new SolicitudArcoService(solicitudes, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Instant ahora = Instant.now();
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(ahora).expiresAt(ahora.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Listado ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-098: con ámbito LIGA lista las solicitudes con el mismo alcance en filas y total")
    void listarEnLiga() {
        FiltroSolicitudesArco filtro = new FiltroSolicitudesArco(EstadoSolicitudArco.RECIBIDA, null, null, 0, 20);
        given(autorizacion.alcance(GESTIONAR)).willReturn(LIGA_COMPLETA);
        given(solicitudes.listar(LIGA, filtro, LIGA_COMPLETA)).willReturn(List.of(solicitud(EstadoSolicitudArco.RECIBIDA)));
        given(solicitudes.contar(LIGA, filtro, LIGA_COMPLETA)).willReturn(1L);

        Pagina<SolicitudArcoDto> pagina = servicio.listar(filtro);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    @DisplayName("ADR-0007: con ámbito CLUB responde una página vacía sin consultar la tabla [PROPUESTA]")
    void listarConAmbitoClub() {
        given(autorizacion.alcance(GESTIONAR)).willReturn(new AlcanceAutorizacion(false, Set.of(UUID.randomUUID()), false));

        Pagina<SolicitudArcoDto> pagina = servicio.listar(new FiltroSolicitudesArco(null, null, null, 0, 20));

        assertThat(pagina.totalElementos()).isZero();
        verifyNoInteractions(solicitudes);
    }

    @Test
    @DisplayName("RF-098: una página o un tamaño fuera de rango responde 400 VALIDACION")
    void listarPaginaInvalida() {
        given(autorizacion.alcance(GESTIONAR)).willReturn(LIGA_COMPLETA);

        esperarError(() -> servicio.listar(new FiltroSolicitudesArco(null, null, null, 0, 101)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
    }

    // ── Registro ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-098: registra la solicitud RECIBIDA, autoriza sobre el titular y audita solo el tipo")
    void registrar() {
        given(solicitudes.existePersona(PERSONA)).willReturn(true);
        given(solicitudes.insertar(LIGA, PERSONA, TipoSolicitudArco.CANCELACION, "Pide cancelar su correo"))
                .willReturn(SOLICITUD);
        given(solicitudes.buscar(SOLICITUD)).willReturn(Optional.of(solicitud(EstadoSolicitudArco.RECIBIDA)));

        SolicitudArcoDto registrada = servicio.registrar(new RegistrarSolicitudArcoRequest(PERSONA, " cancelacion ",
                "  Pide cancelar su correo "));

        assertThat(registrada.estado()).isEqualTo(EstadoSolicitudArco.RECIBIDA);
        verify(autorizacion).exigir(GESTIONAR, ObjetivoAutorizacion.dePersonas(PERSONA));
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-01");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_SOLICITUD_ARCO");
        assertThat(evento.entidad()).isEqualTo("solicitudes_arco");
        assertThat(evento.entidadId()).isEqualTo(SOLICITUD.toString());
        assertThat(evento.detalle()).isEqualTo(Map.of("tipo", "CANCELACION"));
    }

    @Test
    @DisplayName("RF-098: sin titular, con un tipo desconocido o sin descripción responde 400 VALIDACION")
    void registrarDatosInvalidos() {
        esperarError(() -> servicio.registrar(new RegistrarSolicitudArcoRequest(null, "ACCESO", "Ver mis datos")),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.registrar(new RegistrarSolicitudArcoRequest(PERSONA, "BORRADO", "Ver")),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.registrar(new RegistrarSolicitudArcoRequest(PERSONA, "ACCESO", "   ")),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        verify(solicitudes, never()).insertar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin el permiso no registra ni audita")
    void registrarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(GESTIONAR, ObjetivoAutorizacion.dePersonas(PERSONA));

        esperarError(() -> servicio.registrar(new RegistrarSolicitudArcoRequest(PERSONA, "ACCESO", "Ver mis datos")),
                "VAL-015", HttpStatus.FORBIDDEN);
        verify(solicitudes, never()).insertar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-098: una persona inexistente responde 422 REFERENCIA_INVALIDA sin insertar")
    void registrarPersonaInexistente() {
        given(solicitudes.existePersona(PERSONA)).willReturn(false);

        esperarError(() -> servicio.registrar(new RegistrarSolicitudArcoRequest(PERSONA, "ACCESO", "Ver mis datos")),
                "REFERENCIA_INVALIDA", HttpStatus.UNPROCESSABLE_CONTENT);
        verify(solicitudes, never()).insertar(any(), any(), any(), any());
    }

    // ── Seguimiento ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-098: RECIBIDA pasa a EN_ATENCION sin respuesta y se audita la transición")
    void ponerEnAtencion() {
        given(solicitudes.bloquearEstado(LIGA, SOLICITUD)).willReturn(Optional.of(EstadoSolicitudArco.RECIBIDA));
        given(solicitudes.buscar(SOLICITUD)).willReturn(Optional.of(solicitud(EstadoSolicitudArco.EN_ATENCION)));

        servicio.actualizar(SOLICITUD, new ActualizarSolicitudArcoRequest("EN_ATENCION", null));

        verify(autorizacion).exigir(GESTIONAR, ObjetivoAutorizacion.deLiga());
        verify(solicitudes).actualizarEstado(SOLICITUD, EstadoSolicitudArco.EN_ATENCION, null, PERFIL);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("CAMBIAR_ESTADO_SOLICITUD_ARCO");
        assertThat(evento.detalle()).isEqualTo(Map.of("estadoAnterior", "RECIBIDA", "estadoNuevo", "EN_ATENCION"));
    }

    @Test
    @DisplayName("RF-098: al cerrar guarda la respuesta y el perfil que atendió")
    void cerrarConRespuesta() {
        given(solicitudes.bloquearEstado(LIGA, SOLICITUD)).willReturn(Optional.of(EstadoSolicitudArco.EN_ATENCION));
        given(solicitudes.buscar(SOLICITUD)).willReturn(Optional.of(solicitud(EstadoSolicitudArco.ATENDIDA)));

        servicio.actualizar(SOLICITUD, new ActualizarSolicitudArcoRequest("atendida", " Datos bloqueados "));

        verify(solicitudes).actualizarEstado(SOLICITUD, EstadoSolicitudArco.ATENDIDA, "Datos bloqueados", PERFIL);
    }

    @Test
    @DisplayName("RF-098: cerrar sin respuesta, volver a RECIBIDA o responder sin cerrar responde 400 VALIDACION")
    void actualizarDatosInvalidos() {
        esperarError(() -> servicio.actualizar(SOLICITUD, new ActualizarSolicitudArcoRequest("RECHAZADA", " ")),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.actualizar(SOLICITUD, new ActualizarSolicitudArcoRequest("RECIBIDA", null)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.actualizar(SOLICITUD, new ActualizarSolicitudArcoRequest("EN_ATENCION", "Hola")),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.actualizar(SOLICITUD, new ActualizarSolicitudArcoRequest("CERRADA", "Hola")),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        verify(solicitudes, never()).actualizarEstado(any(), any(), any(), any());
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"ATENDIDA, RECHAZADA", "RECHAZADA, ATENDIDA", "EN_ATENCION, EN_ATENCION", "ATENDIDA, EN_ATENCION"})
    @DisplayName("RF-098: una transición no permitida responde 422 RF-098 y no cambia nada")
    void transicionNoPermitida(EstadoSolicitudArco actual, EstadoSolicitudArco nuevo) {
        given(solicitudes.bloquearEstado(LIGA, SOLICITUD)).willReturn(Optional.of(actual));

        esperarError(() -> servicio.actualizar(SOLICITUD, new ActualizarSolicitudArcoRequest(nuevo.name(),
                nuevo.esFinal() ? "Respuesta" : null)), "RF-098", HttpStatus.UNPROCESSABLE_CONTENT);
        verify(solicitudes, never()).actualizarEstado(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-098: una solicitud inexistente responde 404")
    void actualizarInexistente() {
        given(solicitudes.bloquearEstado(LIGA, SOLICITUD)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(SOLICITUD, new ActualizarSolicitudArcoRequest("EN_ATENCION", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static SolicitudArcoDto solicitud(EstadoSolicitudArco estado) {
        return new SolicitudArcoDto(SOLICITUD, PERSONA, "Persona Prueba", TipoSolicitudArco.CANCELACION,
                "Pide cancelar su correo", estado, OffsetDateTime.parse("2026-11-16T10:00:00-06:00"),
                null, null, null, null);
    }

    private static void esperarError(ThrowingCallable accion, String codigo, HttpStatus estado) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.codigo()).isEqualTo(codigo);
            assertThat(error.estado()).isEqualTo(estado);
        });
    }
}

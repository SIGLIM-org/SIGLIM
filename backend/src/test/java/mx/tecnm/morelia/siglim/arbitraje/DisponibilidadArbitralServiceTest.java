package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.DisponibilidadArbitralRepository.ArbitroDisponible;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de la disponibilidad arbitral de {@link DisponibilidadArbitralService}. Sin BD. */
class DisponibilidadArbitralServiceTest {

    private static final String PERMISO = "arbitraje.disponibilidad:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ARBITRO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PERSONA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID DISPONIBILIDAD = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final Instant AHORA = Instant.parse("2030-08-02T12:00:00Z");
    private static final OffsetDateTime DESDE = OffsetDateTime.parse("2030-08-10T09:00:00-06:00");
    private static final OffsetDateTime HASTA = OffsetDateTime.parse("2030-08-10T12:00:00-06:00");

    private final DisponibilidadArbitralRepository disponibilidades =
            mock(DisponibilidadArbitralRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final DisponibilidadArbitralService servicio = new DisponibilidadArbitralService(
            disponibilidades, autorizacion, auditoria, new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
        given(disponibilidades.buscarArbitro(LIGA, ARBITRO))
                .willReturn(Optional.of(new ArbitroDisponible(PERSONA, EstadoArbitro.VALIDADO)));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Alta (RF-022) ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-022: la Comisión registra un periodo y lo audita con la persona del árbitro")
    void registrarPeriodo() {
        DisponibilidadArbitralDto esperado = dto(true);
        given(disponibilidades.insertar(ARBITRO, new PeriodoArbitral(DESDE, HASTA), true))
                .willReturn(esperado);

        DisponibilidadArbitralDto resultado = servicio.registrar(ARBITRO,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, HASTA), null));

        assertThat(resultado).isEqualTo(esperado);
        verify(autorizacion).exigir(PERMISO, ObjetivoAutorizacion.dePersonas(PERSONA));
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-05");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_DISPONIBILIDAD_ARBITRAL");
        assertThat(evento.entidad()).isEqualTo("disponibilidad_arbitral");
        assertThat(evento.entidadId()).isEqualTo(DISPONIBILIDAD.toString());
        assertThat(evento.detalle())
                .containsEntry("arbitroId", ARBITRO.toString())
                .containsEntry("desde", DESDE.toString())
                .containsEntry("hasta", HASTA.toString())
                .containsEntry("disponible", true);
    }

    @Test
    @DisplayName("RF-022: disponible en falso declara que el árbitro no puede ser designado")
    void registrarNoDisponible() {
        given(disponibilidades.insertar(ARBITRO, new PeriodoArbitral(DESDE, HASTA), false))
                .willReturn(dto(false));

        DisponibilidadArbitralDto resultado = servicio.registrar(ARBITRO,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, HASTA), false));

        assertThat(resultado.disponible()).isFalse();
    }

    @Test
    @DisplayName("RF-022: un árbitro que no está VALIDADO responde 409 y no se guarda")
    void registrarSoloValidados() {
        given(disponibilidades.buscarArbitro(LIGA, ARBITRO))
                .willReturn(Optional.of(new ArbitroDisponible(PERSONA, EstadoArbitro.PROPUESTO)));

        assertThatThrownBy(() -> servicio.registrar(ARBITRO,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, HASTA), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "RF-022"));
        verify(disponibilidades, never()).insertar(any(), any(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-022: un periodo vacío o invertido responde 400 VALIDACION y no se guarda")
    void registrarPeriodoInvalido() {
        assertThatThrownBy(() -> servicio.registrar(ARBITRO,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, DESDE), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        assertThatThrownBy(() -> servicio.registrar(ARBITRO,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(HASTA, DESDE), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));

        verify(disponibilidades, never()).insertar(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("VAL-015: sin el permiso de disponibilidad responde 403 y no consulta la base")
    void registrarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(any(), any());

        assertThatThrownBy(() -> servicio.registrar(ARBITRO,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, HASTA), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "VAL-015"));
        verify(disponibilidades, never()).insertar(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("VAL-024: un árbitro inexistente o de otra liga responde 404 antes de pedir permiso")
    void registrarArbitroInexistente() {
        given(disponibilidades.buscarArbitro(LIGA, ARBITRO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(ARBITRO,
                new RegistrarDisponibilidadArbitralRequest(new PeriodoArbitral(DESDE, HASTA), null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    // ── Consulta (RF-022) ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-022: el listado sin fechas devuelve todos los periodos del árbitro")
    void listarTodos() {
        given(disponibilidades.listar(ARBITRO, null)).willReturn(List.of(dto(true)));

        List<DisponibilidadArbitralDto> resultado = servicio.listar(ARBITRO, null, null);

        assertThat(resultado).extracting(DisponibilidadArbitralDto::id).containsExactly(DISPONIBILIDAD);
        verify(autorizacion).exigir(PERMISO, ObjetivoAutorizacion.dePersonas(PERSONA));
    }

    @Test
    @DisplayName("RF-022: el rango del listado filtra por traslape con el periodo")
    void listarPorRango() {
        PeriodoArbitral rango = new PeriodoArbitral(DESDE, HASTA);
        given(disponibilidades.listar(ARBITRO, rango)).willReturn(List.of(dto(true)));

        assertThat(servicio.listar(ARBITRO, DESDE, HASTA)).hasSize(1);
        verify(disponibilidades).listar(ARBITRO, rango);
    }

    @Test
    @DisplayName("RF-022: con una sola fecha del rango responde 400 VALIDACION")
    void listarConUnaSolaFecha() {
        assertThatThrownBy(() -> servicio.listar(ARBITRO, DESDE, null))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(disponibilidades, never()).listar(any(), any());
    }

    // ── Baja (RF-022) ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-022: retirar un periodo lo borra y lo audita")
    void eliminarPeriodo() {
        given(disponibilidades.buscar(ARBITRO, DISPONIBILIDAD)).willReturn(Optional.of(dto(true)));
        given(disponibilidades.eliminar(ARBITRO, DISPONIBILIDAD)).willReturn(true);

        servicio.eliminar(ARBITRO, DISPONIBILIDAD);

        verify(disponibilidades).eliminar(ARBITRO, DISPONIBILIDAD);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("ELIMINAR_DISPONIBILIDAD_ARBITRAL");
        assertThat(evento.entidadId()).isEqualTo(DISPONIBILIDAD.toString());
        assertThat(evento.detalle()).containsEntry("arbitroId", ARBITRO.toString());
    }

    @Test
    @DisplayName("VAL-024: retirar un periodo que no es del árbitro responde 404 y no borra")
    void eliminarPeriodoInexistente() {
        given(disponibilidades.buscar(ARBITRO, DISPONIBILIDAD)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(ARBITRO, DISPONIBILIDAD))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(disponibilidades, never()).eliminar(any(), any());
        verifyNoInteractions(auditoria);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private static DisponibilidadArbitralDto dto(boolean disponible) {
        return new DisponibilidadArbitralDto(DISPONIBILIDAD, ARBITRO,
                new PeriodoArbitral(DESDE, HASTA), disponible, DESDE);
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static java.util.function.Consumer<ReglaNegocioException> error(HttpStatus estado,
            String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }
}

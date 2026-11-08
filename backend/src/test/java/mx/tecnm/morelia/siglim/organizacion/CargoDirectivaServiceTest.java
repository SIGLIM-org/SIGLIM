package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
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

/** Reglas de directiva y vigencias (RF-102), sin Spring ni base de datos. */
class CargoDirectivaServiceTest {

    private static final String CONSULTAR = "organizacion.club:CONSULTAR";
    private static final String EDITAR = "organizacion.directiva:EDITAR";
    private static final String APROBAR = "organizacion.directiva:APROBAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID CLUB = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PERSONA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ANTERIOR = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PERFIL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final Instant AHORA = Instant.parse("2030-03-01T12:00:00Z");

    private final CargoDirectivaRepository cargos = mock(CargoDirectivaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final CargoDirectivaService servicio = new CargoDirectivaService(cargos, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

    private static final CargoDirectivaDto PENDIENTE = new CargoDirectivaDto(ID, CLUB, PERSONA, "Ana López",
            CargoDirectiva.PRESIDENTE, LocalDate.parse("2030-01-01"), null,
            EstadoCargoDirectiva.PENDIENTE, null);
    private static final CargoDirectivaDto AUTORIZADO = new CargoDirectivaDto(ID, CLUB, PERSONA, "Ana López",
            CargoDirectiva.PRESIDENTE, LocalDate.parse("2030-01-01"), null,
            EstadoCargoDirectiva.AUTORIZADO, PERFIL);

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

    @Test
    @DisplayName("RF-102: consulta el historial con el permiso de club y su ámbito")
    void consultarDirectiva() {
        given(cargos.existeClub(LIGA, CLUB)).willReturn(true);
        given(cargos.listar(LIGA, CLUB)).willReturn(List.of(PENDIENTE));

        assertThat(servicio.consultar(CLUB)).containsExactly(PENDIENTE);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deClubes(CLUB));
    }

    @Test
    @DisplayName("RF-102: registra un cargo pendiente y audita solo sus datos no personales")
    void registrarPendiente() {
        var peticion = new RegistrarCargoDirectivaRequest(PERSONA, CargoDirectiva.PRESIDENTE,
                LocalDate.parse("2030-01-01"));
        given(cargos.bloquearClub(LIGA, CLUB)).willReturn(true);
        given(cargos.insertar(CLUB, peticion)).willReturn(ID);
        given(cargos.buscar(LIGA, ID)).willReturn(Optional.of(PENDIENTE));

        assertThat(servicio.registrar(CLUB, peticion)).isEqualTo(PENDIENTE);

        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("REGISTRAR_CARGO_DIRECTIVA");
        assertThat(evento.getValue().entidadId()).isEqualTo(ID.toString());
        assertThat(evento.getValue().detalle()).isEqualTo(Map.of(
                "clubId", CLUB.toString(), "cargo", "PRESIDENTE", "desde", "2030-01-01", "estado", "PENDIENTE"));
    }

    @Test
    @DisplayName("RF-102: no registra cargo si el club no existe")
    void registrarClubInexistente() {
        var peticion = new RegistrarCargoDirectivaRequest(PERSONA, CargoDirectiva.PRESIDENTE,
                LocalDate.parse("2030-01-01"));
        given(cargos.bloquearClub(LIGA, CLUB)).willReturn(false);

        assertThatThrownBy(() -> servicio.registrar(CLUB, peticion))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(cargos, never()).insertar(CLUB, peticion);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-102: al autorizar, termina el cargo anterior el día antes de la nueva vigencia")
    void autorizarCierraVigenciaAnterior() {
        CargoDirectivaRepository.VigenciaCargo anterior = new CargoDirectivaRepository.VigenciaCargo(ANTERIOR,
                LocalDate.parse("2029-01-01"));
        given(cargos.buscar(LIGA, ID)).willReturn(Optional.of(PENDIENTE), Optional.of(AUTORIZADO));
        given(cargos.bloquearClub(LIGA, CLUB)).willReturn(true);
        given(cargos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(PENDIENTE));
        given(cargos.buscarVigente(CLUB, CargoDirectiva.PRESIDENTE)).willReturn(Optional.of(anterior));

        assertThat(servicio.autorizar(ID)).isEqualTo(AUTORIZADO);

        verify(autorizacion).exigir(APROBAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(cargos).terminar(ANTERIOR, LocalDate.parse("2029-12-31"));
        verify(cargos).autorizar(ID, PERFIL);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("AUTORIZAR_CARGO_DIRECTIVA");
        assertThat(evento.getValue().detalle()).containsEntry("cargoAnteriorHasta", "2029-12-31");
    }

    @Test
    @DisplayName("RF-102: la vigencia nueva debe ser posterior a la anterior")
    void autorizarRechazaTraslape() {
        CargoDirectivaDto anteriorPendiente = new CargoDirectivaDto(ID, CLUB, PERSONA, "Ana López",
                CargoDirectiva.PRESIDENTE, LocalDate.parse("2028-01-01"), null,
                EstadoCargoDirectiva.PENDIENTE, null);
        var anterior = new CargoDirectivaRepository.VigenciaCargo(ANTERIOR, LocalDate.parse("2029-01-01"));
        given(cargos.buscar(LIGA, ID)).willReturn(Optional.of(anteriorPendiente));
        given(cargos.bloquearClub(LIGA, CLUB)).willReturn(true);
        given(cargos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(anteriorPendiente));
        given(cargos.buscarVigente(CLUB, CargoDirectiva.PRESIDENTE)).willReturn(Optional.of(anterior));

        assertThatThrownBy(() -> servicio.autorizar(ID))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("VAL-003");
                });
        verify(cargos, never()).autorizar(ID, PERFIL);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: no modifica directiva sin permiso")
    void registrarSinPermiso() {
        var peticion = new RegistrarCargoDirectivaRequest(PERSONA, CargoDirectiva.PRESIDENTE,
                LocalDate.parse("2030-01-01"));
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.registrar(CLUB, peticion))
                .isInstanceOf(ReglaNegocioException.class);
        verifyNoInteractions(cargos, auditoria);
    }
}

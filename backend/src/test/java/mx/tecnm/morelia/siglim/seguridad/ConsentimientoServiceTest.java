package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Aviso vigente y consentimiento (RF-097, VAL-025, DEC-76, DEC-80, DEC-81), sin Spring ni base de datos. */
class ConsentimientoServiceTest {

    private static final String REGISTRAR = "seguridad.consentimiento:REGISTRAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PERSONA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TUTOR = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PERFIL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID AVISO = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID AVISO_ANTERIOR = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID CONSENTIMIENTO = UUID.fromString("99999999-9999-4999-8999-999999999999");
    /** 2026-11-12 02:00 UTC es todavía el 11 de noviembre en la Ciudad de México. */
    private static final Instant AHORA = Instant.parse("2026-11-12T02:00:00Z");
    private static final LocalDate HOY = LocalDate.parse("2026-11-11");
    private static final List<String> FINALIDADES = List.of("REGISTRO_DEPORTIVO", "DIFUSION");
    private static final AvisoPrivacidadDto VIGENTE = new AvisoPrivacidadDto(AVISO, TipoAvisoPrivacidad.SIMPLIFICADO,
            "v1", "Aviso", FINALIDADES, HOY, true);

    private final ConsentimientoRepository consentimientos = mock(ConsentimientoRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ConsentimientoService servicio = new ConsentimientoService(consentimientos, autorizacion, auditoria,
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

    // ── Aviso vigente (DEC-80) ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-80: sin tipo devuelve el SIMPLIFICADO vigente hoy en la Ciudad de México")
    void avisoVigentePorDefecto() {
        given(consentimientos.buscarAvisoVigente(LIGA, TipoAvisoPrivacidad.SIMPLIFICADO, HOY))
                .willReturn(Optional.of(VIGENTE));

        assertThat(servicio.avisoVigente(null)).isEqualTo(VIGENTE);
        verifyNoInteractions(autorizacion);
    }

    @Test
    @DisplayName("DEC-80: un tipo inválido responde 400 VALIDACION; sin aviso vigente, 404")
    void avisoTipoInvalidoOInexistente() {
        esperarError(() -> servicio.avisoVigente("COMPLETO"), "VALIDACION", HttpStatus.BAD_REQUEST);

        given(consentimientos.buscarAvisoVigente(LIGA, TipoAvisoPrivacidad.INTEGRAL, HOY)).willReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.avisoVigente("integral")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Consentimiento (DEC-81) ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-76: en un adulto se guarda otorgado_por_id = persona_id y se audita sin datos personales")
    void consentimientoDeAdulto() {
        avisoValidoPara(false);
        given(consentimientos.insertar(PERSONA, PERSONA, AVISO, List.of("REGISTRO_DEPORTIVO"), PERFIL))
                .willReturn(new ConsentimientoDto(CONSENTIMIENTO, PERSONA, PERSONA, AVISO,
                        List.of("REGISTRO_DEPORTIVO"), OffsetDateTime.parse("2026-11-11T20:00:00-06:00")));

        ConsentimientoDto registrado = servicio.registrar(PERSONA, new RegistrarConsentimientoRequest(AVISO,
                List.of(" REGISTRO_DEPORTIVO ", "REGISTRO_DEPORTIVO"), null));

        assertThat(registrado.otorgadoPorId()).isEqualTo(PERSONA);
        verify(autorizacion).alcance(REGISTRAR);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().modulo()).isEqualTo("M-01");
        assertThat(evento.getValue().accion()).isEqualTo("REGISTRAR_CONSENTIMIENTO");
        assertThat(evento.getValue().entidad()).isEqualTo("consentimientos");
        assertThat(evento.getValue().detalle()).isEqualTo(Map.of("avisoId", AVISO.toString(),
                "finalidades", List.of("REGISTRO_DEPORTIVO")));
    }

    @Test
    @DisplayName("VAL-025: el tutor vigente otorga el consentimiento del menor")
    void consentimientoDeMenor() {
        avisoValidoPara(true);
        given(consentimientos.esTutorVigente(PERSONA, TUTOR)).willReturn(true);
        given(consentimientos.insertar(PERSONA, TUTOR, AVISO, FINALIDADES, PERFIL))
                .willReturn(new ConsentimientoDto(CONSENTIMIENTO, PERSONA, TUTOR, AVISO, FINALIDADES,
                        OffsetDateTime.parse("2026-11-11T20:00:00-06:00")));

        assertThat(servicio.registrar(PERSONA, new RegistrarConsentimientoRequest(AVISO, FINALIDADES, TUTOR))
                .otorgadoPorId()).isEqualTo(TUTOR);
    }

    @Test
    @DisplayName("VAL-015: sin permiso responde 403 antes de validar el cuerpo")
    void sinPermiso() {
        given(autorizacion.alcance(REGISTRAR)).willThrow(Autorizacion.sinPermiso());

        esperarError(() -> servicio.registrar(PERSONA, new RegistrarConsentimientoRequest(null, null, null)),
                "VAL-015", HttpStatus.FORBIDDEN);
        verifyNoInteractions(consentimientos);
    }

    @Test
    @DisplayName("DEC-81: sin aviso, sin finalidades o con una finalidad vacía responde 400 antes de buscar a la persona")
    void cuerpoInvalido() {
        List<String> conVacia = new java.util.ArrayList<>(List.of("DIFUSION"));
        conVacia.add(null);
        for (RegistrarConsentimientoRequest peticion : List.of(
                new RegistrarConsentimientoRequest(null, FINALIDADES, null),
                new RegistrarConsentimientoRequest(AVISO, List.of(), null),
                new RegistrarConsentimientoRequest(AVISO, List.of("DIFUSION", " "), null),
                new RegistrarConsentimientoRequest(AVISO, conVacia, null))) {
            esperarError(() -> servicio.registrar(PERSONA, peticion), "VALIDACION", HttpStatus.BAD_REQUEST);
        }
        verifyNoInteractions(consentimientos);
    }

    @Test
    @DisplayName("DEC-81: una persona inexistente responde 404 antes de revisar el aviso")
    void personaInexistente() {
        given(consentimientos.esMenor(PERSONA, HOY)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(PERSONA, new RegistrarConsentimientoRequest(AVISO, FINALIDADES, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(consentimientos, never()).buscarAviso(any());
    }

    @Test
    @DisplayName("DEC-81: un aviso inexistente responde 422 REFERENCIA_INVALIDA")
    void avisoInexistente() {
        given(consentimientos.esMenor(PERSONA, HOY)).willReturn(Optional.of(false));
        given(consentimientos.buscarAviso(AVISO)).willReturn(Optional.empty());

        esperarError(() -> servicio.registrar(PERSONA, new RegistrarConsentimientoRequest(AVISO, FINALIDADES, null)),
                "REFERENCIA_INVALIDA", HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("RF-097: un aviso que ya no es el vigente de su tipo responde 422 RF-097")
    void avisoNoVigente() {
        given(consentimientos.esMenor(PERSONA, HOY)).willReturn(Optional.of(false));
        given(consentimientos.buscarAviso(AVISO_ANTERIOR)).willReturn(Optional.of(new AvisoPrivacidadDto(
                AVISO_ANTERIOR, TipoAvisoPrivacidad.SIMPLIFICADO, "v0", "Aviso", FINALIDADES, HOY.minusYears(1), true)));
        given(consentimientos.buscarAvisoVigente(LIGA, TipoAvisoPrivacidad.SIMPLIFICADO, HOY)).willReturn(Optional.of(VIGENTE));

        esperarError(() -> servicio.registrar(PERSONA, new RegistrarConsentimientoRequest(AVISO_ANTERIOR, FINALIDADES, null)),
                "RF-097", HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("RF-097: una finalidad que no está en el aviso responde 422 RF-097")
    void finalidadAjena() {
        avisoValidoPara(false);

        esperarError(() -> servicio.registrar(PERSONA, new RegistrarConsentimientoRequest(AVISO,
                List.of("MERCADOTECNIA"), null)), "RF-097", HttpStatus.UNPROCESSABLE_CONTENT);
        verify(consentimientos, never()).insertar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-025: un menor sin otorgante o con un otorgante que no es su tutor vigente responde 422")
    void menorSinTutor() {
        avisoValidoPara(true);
        given(consentimientos.esTutorVigente(PERSONA, TUTOR)).willReturn(false);

        esperarError(() -> servicio.registrar(PERSONA, new RegistrarConsentimientoRequest(AVISO, FINALIDADES, null)),
                "VAL-025", HttpStatus.UNPROCESSABLE_CONTENT);
        esperarError(() -> servicio.registrar(PERSONA, new RegistrarConsentimientoRequest(AVISO, FINALIDADES, TUTOR)),
                "VAL-025", HttpStatus.UNPROCESSABLE_CONTENT);
        verify(consentimientos, never()).insertar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("DEC-76: en un adulto, otro otorgante responde 422 RF-097")
    void adultoConOtroOtorgante() {
        avisoValidoPara(false);

        esperarError(() -> servicio.registrar(PERSONA, new RegistrarConsentimientoRequest(AVISO, FINALIDADES, TUTOR)),
                "RF-097", HttpStatus.UNPROCESSABLE_CONTENT);
        verifyNoInteractions(auditoria);
    }

    private void avisoValidoPara(boolean menor) {
        given(consentimientos.esMenor(PERSONA, HOY)).willReturn(Optional.of(menor));
        given(consentimientos.buscarAviso(AVISO)).willReturn(Optional.of(VIGENTE));
        given(consentimientos.buscarAvisoVigente(LIGA, TipoAvisoPrivacidad.SIMPLIFICADO, HOY)).willReturn(Optional.of(VIGENTE));
    }

    private static void esperarError(ThrowingCallable accion, String codigo, HttpStatus estado) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.codigo()).isEqualTo(codigo);
            assertThat(error.estado()).isEqualTo(estado);
        });
    }
}

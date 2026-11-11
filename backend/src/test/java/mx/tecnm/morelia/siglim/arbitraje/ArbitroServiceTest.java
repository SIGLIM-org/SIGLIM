package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.PerfilDto;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.PersonaResumenDto;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroRepository.ResumenArbitro;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
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

/** Reglas del catálogo de árbitros de {@link ArbitroService}. Sin Spring ni base de datos. */
class ArbitroServiceTest {

    private static final String CONSULTAR = "arbitraje.arbitro:CONSULTAR";
    private static final String PROPONER = "arbitraje.arbitro:PROPONER";
    private static final String VALIDAR = "arbitraje.arbitro:VALIDAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PERSONA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final Instant AHORA = Instant.parse("2030-08-02T12:00:00Z");
    private static final OffsetDateTime PROPUESTO_EN = OffsetDateTime.parse("2030-08-02T06:00:00-06:00");
    private static final OffsetDateTime VALIDADO_EN = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final String OBSERVACIONES = "No acreditó la constancia de la escuela de árbitros";

    private final ArbitroRepository arbitros = mock(ArbitroRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final ArbitroService servicio = new ArbitroService(arbitros, autorizacion, auditoria,
            motivoOperacion, Clock.fixed(AHORA, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
        given(arbitros.existeClub(LIGA, CLUB)).willReturn(true);
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Datos de prueba ─────────────────────────────────────────────────────────────────────────

    private static ArbitroDto arbitro(EstadoArbitro estado, UUID validadoPor, String observaciones) {
        boolean resuelto = estado == EstadoArbitro.VALIDADO || estado == EstadoArbitro.RECHAZADO;
        return new ArbitroDto(ID,
                new PersonaResumenDto(PERSONA, "Carlos Alberto Prado"),
                new ReferenciaDto(CLUB, "Club Halcones"),
                estado,
                new PerfilDto(PERFIL, "Ana López"),
                PROPUESTO_EN,
                resuelto ? new PerfilDto(validadoPor, "Comisión de Arbitraje") : null,
                resuelto ? VALIDADO_EN : null,
                observaciones);
    }

    private static ArbitroDto propuesto() {
        return arbitro(EstadoArbitro.PROPUESTO, null, null);
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private static AlcanceAutorizacion liga() {
        return new AlcanceAutorizacion(true, Set.of(), false);
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    // ── Listado y ámbito (criterio 3 de H-11) ──────────────────────────────────────────────────

    @Test
    @DisplayName("RF-021: el listado usa el alcance de CONSULTAR en la consulta y en el total")
    void listarUsaAlcanceDeConsulta() {
        FiltroArbitros filtro = new FiltroArbitros(EstadoArbitro.PROPUESTO, CLUB, 0, 20);
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(false, Set.of(CLUB), false);
        given(autorizacion.alcance(CONSULTAR)).willReturn(alcance);
        given(arbitros.listar(LIGA, filtro, alcance)).willReturn(List.of(propuesto()));
        given(arbitros.contar(LIGA, filtro, alcance)).willReturn(1L);

        var pagina = servicio.listar(filtro);

        assertThat(pagina.contenido()).extracting(ArbitroDto::id).containsExactly(ID);
        assertThat(pagina.totalElementos()).isEqualTo(1);
        verify(arbitros).listar(LIGA, filtro, alcance);
        verify(arbitros).contar(LIGA, filtro, alcance);
    }

    @Test
    @DisplayName("VAL-015: sin CONSULTAR el listado responde 403 y no consulta la base")
    void listarSinPermiso() {
        given(autorizacion.alcance(CONSULTAR)).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.listar(new FiltroArbitros(null, null, 0, 20)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(arbitros);
    }

    @Test
    @DisplayName("RF-021: una página o un tamaño fuera de rango responde 400 VALIDACION")
    void listarRechazaPaginaInvalida() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(liga());

        assertThatThrownBy(() -> servicio.listar(new FiltroArbitros(null, null, 0, 101)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(arbitros, never()).listar(any(), any(), any());
    }

    // ── Presentación del club (criterio 1 de H-11) ─────────────────────────────────────────────

    @Test
    @DisplayName("H-11 criterio 1 · RF-112: la Directiva presenta al árbitro y queda PROPUESTO ligado a su club")
    void crearDejaPropuestoYAudita() {
        given(arbitros.buscarPorPersona(PERSONA)).willReturn(Optional.empty());
        given(arbitros.insertar(CLUB, PERSONA, PERFIL)).willReturn(Optional.of(ID));
        given(arbitros.buscar(LIGA, ID)).willReturn(Optional.of(propuesto()));

        ArbitroDto creado = servicio.crear(new CrearArbitroRequest(PERSONA, CLUB));

        assertThat(creado.estado()).isEqualTo(EstadoArbitro.PROPUESTO);
        assertThat(creado.club().id()).isEqualTo(CLUB);
        assertThat(creado.persona().id()).isEqualTo(PERSONA);
        verify(autorizacion).exigir(PROPONER, ObjetivoAutorizacion.deClubes(CLUB));
        verify(arbitros).insertar(CLUB, PERSONA, PERFIL);

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-05");
        assertThat(evento.accion()).isEqualTo("PROPONER_ARBITRO");
        assertThat(evento.entidad()).isEqualTo("arbitros");
        assertThat(evento.entidadId()).isEqualTo(ID.toString());
        assertThat(evento.detalle()).containsEntry("clubId", CLUB.toString())
                .containsEntry("personaId", PERSONA.toString())
                .containsEntry("estado", "PROPUESTO");
    }

    @Test
    @DisplayName("RF-010, DEC-10, DEC-88: la persona ya registrada responde 409 solo con el club")
    void crearPersonaDuplicada() {
        given(arbitros.buscarPorPersona(PERSONA))
                .willReturn(Optional.of(new ResumenArbitro("Club Toros")));

        assertThatThrownBy(() -> servicio.crear(new CrearArbitroRequest(PERSONA, CLUB)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO).accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("Club Toros")
                            .doesNotContain("validado").doesNotContain("rechazado");
                    assertThat(excepcion.propiedades())
                            .containsOnly(entry("club", "Club Toros"));
                });
        verify(arbitros, never()).insertar(any(), any(), any());
    }

    @Test
    @DisplayName("DEC-90: si el alta choca con persona_id, el ON CONFLICT deja la transacción sana y da 409")
    void crearCarreraDeDuplicado() {
        given(arbitros.buscarPorPersona(PERSONA))
                .willReturn(Optional.empty(), Optional.of(new ResumenArbitro("Club Toros")));
        given(arbitros.insertar(CLUB, PERSONA, PERFIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(new CrearArbitroRequest(PERSONA, CLUB)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO).accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("Club Toros");
                });

        verify(arbitros).insertar(CLUB, PERSONA, PERFIL);
        verify(arbitros, times(2)).buscarPorPersona(PERSONA);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-024: un club de otra liga o inexistente responde 404 y no autoriza")
    void crearClubInexistente() {
        given(arbitros.existeClub(LIGA, CLUB)).willReturn(false);

        assertThatThrownBy(() -> servicio.crear(new CrearArbitroRequest(PERSONA, CLUB)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
        verify(arbitros, never()).insertar(any(), any(), any());
    }

    @Test
    @DisplayName("VAL-015: sin PROPONER sobre el club no se presenta al árbitro")
    void crearSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(PROPONER, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.crear(new CrearArbitroRequest(PERSONA, CLUB)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verify(arbitros, never()).insertar(any(), any(), any());
    }

    @Test
    @DisplayName("RF-112: la petición sin persona o sin club responde 400 VALIDACION")
    void crearSinDatosObligatorios() {
        assertThatThrownBy(() -> servicio.crear(new CrearArbitroRequest(null, CLUB)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        assertThatThrownBy(() -> servicio.crear(new CrearArbitroRequest(PERSONA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(arbitros, never()).insertar(any(), any(), any());
    }

    // ── Validación de la Comisión (criterio 2 de H-11) ────────────────────────────────────────

    @Test
    @DisplayName("H-11 criterio 2 · RF-113: la Comisión valida, guarda perfil y fecha, y audita")
    void validarAprueba() {
        given(arbitros.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(propuesto()));
        given(arbitros.buscar(LIGA, ID)).willReturn(Optional.of(
                arbitro(EstadoArbitro.VALIDADO, PERFIL, "Todo en orden")));

        ArbitroDto validado = servicio.validar(ID,
                new ValidarArbitroRequest(DecisionArbitro.VALIDADO, "Todo en orden"));

        assertThat(validado.estado()).isEqualTo(EstadoArbitro.VALIDADO);
        assertThat(validado.validadoPor().id()).isEqualTo(PERFIL);
        assertThat(validado.validadoEn()).isEqualTo(VALIDADO_EN);
        verify(autorizacion).exigir(VALIDAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(arbitros).resolver(ID, EstadoArbitro.VALIDADO, PERFIL, VALIDADO_EN, "Todo en orden");
        verify(motivoOperacion, never()).fijar(any());
        assertThat(eventoAuditado().accion()).isEqualTo("VALIDAR_ARBITRO");
    }

    @Test
    @DisplayName("H-11 criterio 2 · RF-113: rechazar sin observaciones suficientes responde 400")
    void validarRechazaSinObservaciones() {
        given(arbitros.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(propuesto()));

        assertThatThrownBy(() -> servicio.validar(ID,
                new ValidarArbitroRequest(DecisionArbitro.RECHAZADO, "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RF-113"));
        verify(arbitros, never()).resolver(any(), any(), any(), any(), any());
        verifyNoInteractions(motivoOperacion);
    }

    @Test
    @DisplayName("H-11 criterio 2 · RF-113: el rechazo guarda observaciones, motivo y auditoría")
    void validarRechazaConObservaciones() {
        given(arbitros.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(propuesto()));
        given(arbitros.buscar(LIGA, ID)).willReturn(Optional.of(
                arbitro(EstadoArbitro.RECHAZADO, PERFIL, OBSERVACIONES)));

        ArbitroDto rechazado = servicio.validar(ID,
                new ValidarArbitroRequest(DecisionArbitro.RECHAZADO, "  " + OBSERVACIONES + "  "));

        assertThat(rechazado.estado()).isEqualTo(EstadoArbitro.RECHAZADO);
        assertThat(rechazado.observaciones()).isEqualTo(OBSERVACIONES);
        verify(motivoOperacion).fijar(OBSERVACIONES);
        verify(arbitros).resolver(ID, EstadoArbitro.RECHAZADO, PERFIL, VALIDADO_EN, OBSERVACIONES);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("RECHAZAR_ARBITRO");
        assertThat(evento.motivo()).isEqualTo(OBSERVACIONES);
    }

    @Test
    @DisplayName("RF-113: repetir el dictamen sobre un árbitro ya resuelto responde 409")
    void validarYaResuelto() {
        given(arbitros.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(
                arbitro(EstadoArbitro.VALIDADO, PERFIL, null)));

        assertThatThrownBy(() -> servicio.validar(ID,
                new ValidarArbitroRequest(DecisionArbitro.RECHAZADO, OBSERVACIONES)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-113"));
        verify(arbitros, never()).resolver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-113: un árbitro de BAJA tampoco admite validación (409)")
    void validarDeBaja() {
        given(arbitros.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(
                arbitro(EstadoArbitro.BAJA, PERFIL, null)));

        assertThatThrownBy(() -> servicio.validar(ID,
                new ValidarArbitroRequest(DecisionArbitro.VALIDADO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-113"));
        verify(arbitros, never()).resolver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-015: la Directiva no puede validar a su propio árbitro (403)")
    void validarSinPermiso() {
        given(arbitros.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(propuesto()));
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(VALIDAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.validar(ID,
                new ValidarArbitroRequest(DecisionArbitro.VALIDADO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verify(arbitros, never()).resolver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-024: un árbitro inexistente responde 404 antes de autorizar")
    void validarInexistente() {
        given(arbitros.buscarParaActualizar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.validar(ID,
                new ValidarArbitroRequest(DecisionArbitro.VALIDADO, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    @Test
    @DisplayName("RF-113: sin decisión responde 400 VALIDACION")
    void validarSinDecision() {
        given(arbitros.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(propuesto()));

        assertThatThrownBy(() -> servicio.validar(ID, new ValidarArbitroRequest(null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(arbitros, never()).resolver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-113: las observaciones admiten hasta 500 caracteres (400 VALIDACION)")
    void validarObservacionesDemasiadoLargas() {
        given(arbitros.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(propuesto()));

        assertThatThrownBy(() -> servicio.validar(ID,
                new ValidarArbitroRequest(DecisionArbitro.VALIDADO, "a".repeat(501))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(arbitros, never()).resolver(any(), any(), any(), any(), any());
    }
}

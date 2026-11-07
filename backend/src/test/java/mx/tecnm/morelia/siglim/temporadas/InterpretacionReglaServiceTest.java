package mx.tecnm.morelia.siglim.temporadas;

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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.InterpretacionReglaRepository.DatosInterpretacion;
import mx.tecnm.morelia.siglim.temporadas.TemporadaRepository.DatosTemporada;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link InterpretacionReglaService} (RF-007). Sin Spring ni base de datos. */
class InterpretacionReglaServiceTest {

    private static final String REGISTRAR = "temporadas.interpretacion:REGISTRAR";
    private static final String APROBAR = "temporadas.interpretacion:APROBAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID TEMPORADA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID INTERPRETACION = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SECRETARIA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID PRESIDENTE = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID CLUB = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final LocalDate INICIO = LocalDate.parse("2032-08-01");
    private static final LocalDate FIN = LocalDate.parse("2032-12-15");
    private static final OffsetDateTime CREADA_EN = OffsetDateTime.of(2032, 8, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime RESUELTA_EN = OffsetDateTime.of(2032, 8, 15, 18, 0, 0, 0, ZoneOffset.UTC);
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2032-08-15T18:00:00Z"), ZoneOffset.UTC);
    private static final String DESCRIPCION = "Se interpreta que el empate en la liguilla se define por penales.";
    private static final String OBSERVACIONES = "No procede: el reglamento ya lo define en el artículo 12.";

    private final InterpretacionReglaRepository interpretaciones = mock(InterpretacionReglaRepository.class);
    private final TemporadaRepository temporadas = mock(TemporadaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final InterpretacionReglaService servicio = new InterpretacionReglaService(interpretaciones,
            temporadas, autorizacion, auditoria, RELOJ, new SiglimPropiedades(null, null, null, LIGA));

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Registro ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-007: registra la interpretación en PENDIENTE con la sesión y la audita")
    void registraPendiente() {
        given(temporadas.buscarParaActualizar(LIGA, TEMPORADA))
                .willReturn(Optional.of(temporada(EstadoTemporada.ACTIVA)));
        given(interpretaciones.insertar(TEMPORADA, DESCRIPCION, SECRETARIA)).willReturn(INTERPRETACION);
        given(interpretaciones.buscar(LIGA, INTERPRETACION)).willReturn(Optional.of(pendiente(SECRETARIA)));
        iniciarSesion(SECRETARIA);

        InterpretacionReglaDto resultado = servicio.registrar(
                new CrearInterpretacionRequest(TEMPORADA, "  " + DESCRIPCION + "  "));

        assertThat(resultado.id()).isEqualTo(INTERPRETACION);
        assertThat(resultado.temporadaId()).isEqualTo(TEMPORADA);
        assertThat(resultado.descripcion()).isEqualTo(DESCRIPCION);
        assertThat(resultado.estado()).isEqualTo(EstadoInterpretacion.PENDIENTE);
        assertThat(resultado.registradaPor()).isEqualTo(SECRETARIA);
        assertThat(resultado.resueltaPor()).isNull();
        verify(autorizacion).exigir(REGISTRAR, ObjetivoAutorizacion.deLiga());

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_INTERPRETACION");
        assertThat(evento.entidad()).isEqualTo("interpretaciones_regla");
        assertThat(evento.entidadId()).isEqualTo(INTERPRETACION.toString());
        assertThat(evento.detalle()).containsEntry("temporadaId", TEMPORADA.toString());
        assertThat(evento.detalle()).containsEntry("estado", "PENDIENTE");
        assertThat(evento.detalle()).as("no se copia el texto de la interpretación").hasSize(2);
        assertThat(evento.motivo()).isNull();
    }

    @Test
    @DisplayName("VAL-015: sin el permiso de registrar no se crea la interpretación")
    void registrarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(REGISTRAR, ObjetivoAutorizacion.deLiga());
        iniciarSesion(SECRETARIA);

        assertThatThrownBy(() -> servicio.registrar(new CrearInterpretacionRequest(TEMPORADA, DESCRIPCION)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(interpretaciones, temporadas, auditoria);
    }

    @Test
    @DisplayName("RF-007: una descripción vacía responde 400 VALIDACION sin tocar la base")
    void registrarSinDescripcion() {
        iniciarSesion(SECRETARIA);

        assertThatThrownBy(() -> servicio.registrar(new CrearInterpretacionRequest(TEMPORADA, "   ")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verifyNoInteractions(interpretaciones, temporadas, auditoria);
    }

    @Test
    @DisplayName("VAL-024: una temporada inexistente responde 404 y no se registra")
    void registrarTemporadaInexistente() {
        given(temporadas.buscarParaActualizar(LIGA, TEMPORADA)).willReturn(Optional.empty());
        iniciarSesion(SECRETARIA);

        assertThatThrownBy(() -> servicio.registrar(new CrearInterpretacionRequest(TEMPORADA, DESCRIPCION)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(interpretaciones, never()).insertar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-003: una temporada cerrada no admite interpretaciones nuevas")
    void registrarTemporadaCerrada() {
        given(temporadas.buscarParaActualizar(LIGA, TEMPORADA))
                .willReturn(Optional.of(temporada(EstadoTemporada.CERRADA)));
        iniciarSesion(SECRETARIA);

        assertThatThrownBy(() -> servicio.registrar(new CrearInterpretacionRequest(TEMPORADA, DESCRIPCION)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(interpretaciones, never()).insertar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    // ── Resolución ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-007: el Presidente autoriza y la resolución queda auditada")
    void autoriza() {
        given(interpretaciones.buscarParaActualizar(LIGA, INTERPRETACION))
                .willReturn(Optional.of(pendiente(SECRETARIA)));
        given(interpretaciones.buscar(LIGA, INTERPRETACION))
                .willReturn(Optional.of(resuelta(EstadoInterpretacion.AUTORIZADA, PRESIDENTE, null)));
        iniciarSesion(PRESIDENTE);

        InterpretacionReglaDto resultado = servicio.resolver(INTERPRETACION,
                new ResolverInterpretacionRequest(EstadoInterpretacion.AUTORIZADA, null));

        assertThat(resultado.estado()).isEqualTo(EstadoInterpretacion.AUTORIZADA);
        assertThat(resultado.resueltaPor()).isEqualTo(PRESIDENTE);
        verify(autorizacion).exigir(APROBAR, ObjetivoAutorizacion.deLiga());
        verify(interpretaciones).resolver(INTERPRETACION, EstadoInterpretacion.AUTORIZADA, PRESIDENTE,
                OffsetDateTime.now(RELOJ), null);

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("RESOLVER_INTERPRETACION");
        assertThat(evento.detalle()).containsEntry("estadoAnterior", "PENDIENTE");
        assertThat(evento.detalle()).containsEntry("estado", "AUTORIZADA");
        assertThat(evento.motivo()).as("autorizar no tiene motivo").isNull();
    }

    @Test
    @DisplayName("RF-007: el rechazo guarda las observaciones y las usa como motivo de la auditoría")
    void rechazaConObservaciones() {
        given(interpretaciones.buscarParaActualizar(LIGA, INTERPRETACION))
                .willReturn(Optional.of(pendiente(SECRETARIA)));
        given(interpretaciones.buscar(LIGA, INTERPRETACION))
                .willReturn(Optional.of(resuelta(EstadoInterpretacion.RECHAZADA, PRESIDENTE, OBSERVACIONES)));
        iniciarSesion(PRESIDENTE);

        InterpretacionReglaDto resultado = servicio.resolver(INTERPRETACION,
                new ResolverInterpretacionRequest(EstadoInterpretacion.RECHAZADA, "  " + OBSERVACIONES + " "));

        assertThat(resultado.estado()).isEqualTo(EstadoInterpretacion.RECHAZADA);
        assertThat(resultado.observaciones()).isEqualTo(OBSERVACIONES);
        verify(interpretaciones).resolver(INTERPRETACION, EstadoInterpretacion.RECHAZADA, PRESIDENTE,
                OffsetDateTime.now(RELOJ), OBSERVACIONES);
        assertThat(eventoAuditado().motivo()).isEqualTo(OBSERVACIONES);
    }

    @Test
    @DisplayName("RF-007: rechazar sin observaciones suficientes responde 400 y no resuelve")
    void rechazaSinObservaciones() {
        given(interpretaciones.buscarParaActualizar(LIGA, INTERPRETACION))
                .willReturn(Optional.of(pendiente(SECRETARIA)));
        iniciarSesion(PRESIDENTE);

        assertThatThrownBy(() -> servicio.resolver(INTERPRETACION,
                new ResolverInterpretacionRequest(EstadoInterpretacion.RECHAZADA, "   ")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RF-007"));
        verify(interpretaciones, never()).resolver(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-024: resolver una interpretación inexistente responde 404 sin evaluar el permiso")
    void resolverInexistente() {
        given(interpretaciones.buscarParaActualizar(LIGA, INTERPRETACION)).willReturn(Optional.empty());
        iniciarSesion(PRESIDENTE);

        assertThatThrownBy(() -> servicio.resolver(INTERPRETACION,
                new ResolverInterpretacionRequest(EstadoInterpretacion.AUTORIZADA, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion, auditoria);
    }

    @Test
    @DisplayName("RF-007: una interpretación ya resuelta no se reabre y responde 409")
    void resolverYaResuelta() {
        given(interpretaciones.buscarParaActualizar(LIGA, INTERPRETACION))
                .willReturn(Optional.of(resuelta(EstadoInterpretacion.AUTORIZADA, PRESIDENTE, null)));
        iniciarSesion(PRESIDENTE);

        assertThatThrownBy(() -> servicio.resolver(INTERPRETACION,
                new ResolverInterpretacionRequest(EstadoInterpretacion.RECHAZADA, OBSERVACIONES)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-007"));
        verify(interpretaciones, never()).resolver(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-007 [PROPUESTA D2]: quien registró la interpretación no puede resolverla")
    void resolverQuienLaRegistro() {
        given(interpretaciones.buscarParaActualizar(LIGA, INTERPRETACION))
                .willReturn(Optional.of(pendiente(SECRETARIA)));
        iniciarSesion(SECRETARIA);

        assertThatThrownBy(() -> servicio.resolver(INTERPRETACION,
                new ResolverInterpretacionRequest(EstadoInterpretacion.AUTORIZADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "RF-007"));
        verify(interpretaciones, never()).resolver(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-007: PENDIENTE no es una resolución y responde 400 VALIDACION")
    void resolverConPendiente() {
        given(interpretaciones.buscarParaActualizar(LIGA, INTERPRETACION))
                .willReturn(Optional.of(pendiente(SECRETARIA)));
        iniciarSesion(PRESIDENTE);

        assertThatThrownBy(() -> servicio.resolver(INTERPRETACION,
                new ResolverInterpretacionRequest(EstadoInterpretacion.PENDIENTE, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(interpretaciones, never()).resolver(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    // ── Listado ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-007: el listado se pagina, filtra y cuenta con el alcance del permiso")
    void listar() {
        given(autorizacion.alcance(REGISTRAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));
        given(interpretaciones.listar(LIGA, TEMPORADA, EstadoInterpretacion.PENDIENTE, 10, 20L, true))
                .willReturn(List.of(pendiente(SECRETARIA)));
        given(interpretaciones.contar(LIGA, TEMPORADA, EstadoInterpretacion.PENDIENTE, true)).willReturn(11L);

        var pagina = servicio.listar(TEMPORADA, EstadoInterpretacion.PENDIENTE, 2, 10);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.contenido().getFirst().estado()).isEqualTo(EstadoInterpretacion.PENDIENTE);
        assertThat(pagina.pagina()).isEqualTo(2);
        assertThat(pagina.totalElementos()).isEqualTo(11);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
    }

    @Test
    @DisplayName("RF-007: el Presidente consulta con su permiso de aprobar")
    void listarComoPresidente() {
        given(autorizacion.alcance(REGISTRAR)).willThrow(Autorizacion.sinPermiso());
        given(autorizacion.alcance(APROBAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));
        given(interpretaciones.listar(LIGA, null, null, 20, 0L, true)).willReturn(List.of());
        given(interpretaciones.contar(LIGA, null, null, true)).willReturn(0L);

        var pagina = servicio.listar(null, null, 0, 20);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
    }

    @Test
    @DisplayName("VAL-015: sin ninguno de los dos permisos el listado responde 403")
    void listarSinPermiso() {
        given(autorizacion.alcance(REGISTRAR)).willThrow(Autorizacion.sinPermiso());
        given(autorizacion.alcance(APROBAR)).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.listar(null, null, 0, 20))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(interpretaciones);
    }

    @Test
    @DisplayName("VAL-024: con ámbito de club el listado responde 404 como un recurso fuera de ámbito")
    void listarFueraDeAmbito() {
        AlcanceAutorizacion deClub = new AlcanceAutorizacion(false, Set.of(CLUB), false);
        given(autorizacion.alcance(REGISTRAR)).willReturn(deClub);
        given(autorizacion.alcance(APROBAR)).willReturn(deClub);

        assertThatThrownBy(() -> servicio.listar(null, null, 0, 20))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(interpretaciones);
    }

    @Test
    @DisplayName("RF-007: una página o un tamaño inválidos responden 400 VALIDACION")
    void listarPaginacionInvalida() {
        given(autorizacion.alcance(REGISTRAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));

        assertThatThrownBy(() -> servicio.listar(null, null, -1, 20))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.listar(null, null, 0, 0))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.listar(null, null, 0, 101))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verifyNoInteractions(interpretaciones);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private static DatosTemporada temporada(EstadoTemporada estado) {
        return new DatosTemporada(TEMPORADA, "Temporada 2032", INICIO, FIN, estado, CREADA_EN, 1, 1, 1, null);
    }

    private static DatosInterpretacion pendiente(UUID registradaPor) {
        return new DatosInterpretacion(INTERPRETACION, TEMPORADA, DESCRIPCION,
                EstadoInterpretacion.PENDIENTE, registradaPor, CREADA_EN, null, null, null);
    }

    private static DatosInterpretacion resuelta(EstadoInterpretacion estado, UUID resueltaPor,
            String observaciones) {
        return new DatosInterpretacion(INTERPRETACION, TEMPORADA, DESCRIPCION, estado, SECRETARIA, CREADA_EN,
                resueltaPor, RESUELTA_EN, observaciones);
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
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
}

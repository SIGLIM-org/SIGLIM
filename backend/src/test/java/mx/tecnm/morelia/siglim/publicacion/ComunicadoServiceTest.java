package mx.tecnm.morelia.siglim.publicacion;

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
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.publicacion.ComunicadoRepository.DatosComunicado;
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

/** Reglas del flujo editorial de {@link ComunicadoService}. Sin Spring ni base de datos. */
class ComunicadoServiceTest {

    private static final String REDACTAR = "publicacion.comunicado:REDACTAR";
    private static final String APROBAR = "publicacion.comunicado:APROBAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID OTRO = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final Instant AHORA = Instant.parse("2030-03-01T12:00:00Z");
    private static final OffsetDateTime CREADO_EN = OffsetDateTime.parse("2030-03-01T06:00:00-06:00");
    private static final OffsetDateTime APROBADO_EN = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final String TITULO = "Suspensión por lluvia";
    private static final String CUERPO = "Se suspende la jornada del domingo.";
    private static final String MOTIVO = "Falta el calendario definitivo";

    private final ComunicadoRepository comunicados = mock(ComunicadoRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final ComunicadoService servicio = new ComunicadoService(comunicados, autorizacion, auditoria,
            motivoOperacion, Clock.fixed(AHORA, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA));

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

    // ── Datos de prueba ──────────────────────────────────────────────────────────────────────

    private static DatosComunicado comunicado(EstadoPublicacion estado, UUID redactor) {
        return new DatosComunicado(ID, TITULO, CUERPO, estado, redactor, null, null, null, null, CREADO_EN,
                CREADO_EN);
    }

    private static DatosComunicado publicado() {
        return new DatosComunicado(ID, TITULO, CUERPO, EstadoPublicacion.PUBLICADO, OTRO, PERFIL, APROBADO_EN,
                APROBADO_EN, null, CREADO_EN, APROBADO_EN);
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

    // ── Alta y edición (RF-090) ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-090: redactar recorta el texto, usa el perfil de la sesión y audita")
    void crearRegistraYAudita() {
        given(comunicados.insertar(LIGA, TITULO, CUERPO, PERFIL)).willReturn(ID);
        given(comunicados.buscar(LIGA, ID)).willReturn(Optional.of(comunicado(EstadoPublicacion.BORRADOR, PERFIL)));

        ComunicadoDto creado = servicio.crear(new CrearComunicadoRequest("  " + TITULO + "  ", " " + CUERPO + " "));

        assertThat(creado.estadoPublicacion()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(creado.redactadoPor()).isEqualTo(PERFIL);
        verify(autorizacion).exigir(REDACTAR, ObjetivoAutorizacion.deLiga());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REDACTAR_COMUNICADO");
        assertThat(evento.entidad()).isEqualTo("comunicados");
        assertThat(evento.entidadId()).isEqualTo(ID.toString());
        assertThat(evento.detalle()).containsEntry("estado", "BORRADOR");
    }

    @Test
    @DisplayName("VAL-015: sin permiso de redacción no se registra ningún comunicado")
    void crearSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(REDACTAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(new CrearComunicadoRequest(TITULO, CUERPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(comunicados, auditoria);
    }

    @Test
    @DisplayName("RF-090: rechaza un título vacío o de más de 200 caracteres y un cuerpo vacío")
    void crearValidaTexto() {
        assertThatThrownBy(() -> servicio.crear(new CrearComunicadoRequest("  ", CUERPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.crear(new CrearComunicadoRequest("x".repeat(201), CUERPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.crear(new CrearComunicadoRequest(TITULO, "  ")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verifyNoInteractions(comunicados, auditoria);
    }

    @Test
    @DisplayName("RF-090: en revisión el comunicado ya no se edita")
    void actualizarFueraDeBorrador() {
        given(comunicados.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.REVISION, PERFIL)));

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarComunicadoRequest("Otro título", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));
        verify(comunicados, never()).actualizar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-090: editar cambia solo lo indicado y audita los campos modificados")
    void actualizarCampos() {
        given(comunicados.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.BORRADOR, PERFIL)));
        given(comunicados.buscar(LIGA, ID)).willReturn(Optional.of(new DatosComunicado(ID, "Otro título", CUERPO,
                EstadoPublicacion.BORRADOR, PERFIL, null, null, null, null, CREADO_EN, CREADO_EN)));

        ComunicadoDto resultado = servicio.actualizar(ID,
                new ActualizarComunicadoRequest("  Otro título  ", null));

        assertThat(resultado.titulo()).isEqualTo("Otro título");
        verify(comunicados).actualizar(ID, "Otro título", CUERPO);
        assertThat(eventoAuditado().detalle()).containsEntry("campos", List.of("titulo"));
    }

    @Test
    @DisplayName("RF-090: un PATCH sin cambios no escribe ni audita")
    void actualizarSinCambios() {
        given(comunicados.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.BORRADOR, PERFIL)));

        servicio.actualizar(ID, new ActualizarComunicadoRequest(" " + TITULO + " ", null));

        verify(comunicados, never()).actualizar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    // ── Envío a revisión (RF-090) ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-090: el borrador pasa a revisión y queda auditado")
    void enviarARevision() {
        given(comunicados.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.BORRADOR, PERFIL)));
        given(comunicados.buscar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.REVISION, PERFIL)));

        ComunicadoDto resultado = servicio.enviarARevision(ID);

        assertThat(resultado.estadoPublicacion()).isEqualTo(EstadoPublicacion.REVISION);
        verify(comunicados).enviarARevision(ID);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("ENVIAR_COMUNICADO_A_REVISION");
        assertThat(evento.detalle()).containsEntry("estado", "REVISION");
    }

    @Test
    @DisplayName("RF-090: no se envía a revisión lo que ya no está en borrador")
    void enviarFueraDeBorrador() {
        given(comunicados.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.REVISION, PERFIL)));

        assertThatThrownBy(() -> servicio.enviarARevision(ID))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));
        verify(comunicados, never()).enviarARevision(any());
        verifyNoInteractions(auditoria);
    }

    // ── Dictamen del Presidente (RF-090, CU-027) ────────────────────────────────────────────

    @Test
    @DisplayName("RF-090: aprobar publica con el perfil de la sesión y deja la fecha de aprobación")
    void aprobarPublica() {
        given(comunicados.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.REVISION, OTRO)));
        given(comunicados.buscar(LIGA, ID)).willReturn(Optional.of(publicado()));

        ComunicadoDto resultado = servicio.resolver(ID,
                new ResolverComunicadoRequest(DecisionComunicado.PUBLICADO, null));

        assertThat(resultado.estadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
        verify(comunicados).resolver(ID, EstadoPublicacion.PUBLICADO, PERFIL, APROBADO_EN, null);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("APROBAR_COMUNICADO");
        assertThat(evento.detalle()).containsEntry("estado", "PUBLICADO");
    }

    @Test
    @DisplayName("RF-090: devolver exige observaciones, fija el motivo y vuelve a borrador")
    void devolverExigeObservaciones() {
        given(comunicados.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.REVISION, OTRO)));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverComunicadoRequest(DecisionComunicado.BORRADOR, "Corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RF-090"));
        verify(motivoOperacion, never()).fijar(any());
        verify(comunicados, never()).resolver(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);

        given(comunicados.buscar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.BORRADOR, OTRO)));

        ComunicadoDto devuelto = servicio.resolver(ID,
                new ResolverComunicadoRequest(DecisionComunicado.BORRADOR, "  " + MOTIVO + "  "));

        assertThat(devuelto.estadoPublicacion()).isEqualTo(EstadoPublicacion.BORRADOR);
        verify(motivoOperacion).fijar(MOTIVO);
        verify(comunicados).resolver(ID, EstadoPublicacion.BORRADOR, null, null, MOTIVO);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("DEVOLVER_COMUNICADO");
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("RF-090: quien redactó no puede aprobar su propio comunicado")
    void aprobarElPropio() {
        given(comunicados.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.REVISION, PERFIL)));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverComunicadoRequest(DecisionComunicado.PUBLICADO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "RF-090"));
        verify(comunicados, never()).resolver(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-090: solo se resuelve un comunicado en revisión y con un dictamen")
    void resolverValidaEstadoYDecision() {
        given(comunicados.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.BORRADOR, OTRO)));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverComunicadoRequest(DecisionComunicado.PUBLICADO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));

        given(comunicados.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(comunicado(EstadoPublicacion.REVISION, OTRO)));

        assertThatThrownBy(() -> servicio.resolver(ID, new ResolverComunicadoRequest(null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(comunicados, never()).resolver(any(), any(), any(), any(), any());
    }

    // ── Consulta y ámbito (RF-090, ADR-0007) ────────────────────────────────────────────────

    @Test
    @DisplayName("RF-090: el listado filtra por estado, pagina y cuenta con la misma condición")
    void listar() {
        given(autorizacion.alcance(REDACTAR)).willReturn(liga());
        given(comunicados.listar(LIGA, EstadoPublicacion.BORRADOR, 20, 0))
                .willReturn(List.of(comunicado(EstadoPublicacion.BORRADOR, PERFIL)));
        given(comunicados.contar(LIGA, EstadoPublicacion.BORRADOR)).willReturn(21L);

        var pagina = servicio.listar(EstadoPublicacion.BORRADOR, 0, 20);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.totalElementos()).isEqualTo(21);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
    }

    @Test
    @DisplayName("RF-090: la Presidencia consulta con su permiso de aprobación")
    void consultarConPermisoDeAprobar() {
        given(autorizacion.alcance(REDACTAR)).willThrow(Autorizacion.sinPermiso());
        given(autorizacion.alcance(APROBAR)).willReturn(liga());
        given(comunicados.buscar(LIGA, ID)).willReturn(Optional.of(publicado()));

        assertThat(servicio.consultar(ID).estadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
    }

    @Test
    @DisplayName("VAL-015: sin ninguno de los dos permisos no se consulta")
    void consultarSinPermiso() {
        given(autorizacion.alcance(REDACTAR)).willThrow(Autorizacion.sinPermiso());
        given(autorizacion.alcance(APROBAR)).willThrow(Autorizacion.sinPermiso());
        given(comunicados.buscar(LIGA, ID)).willReturn(Optional.of(publicado()));

        assertThatThrownBy(() -> servicio.consultar(ID))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
    }

    @Test
    @DisplayName("VAL-024: un comunicado inexistente responde 404 sin evaluar el permiso")
    void consultarInexistente() {
        given(comunicados.buscar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    @Test
    @DisplayName("RF-090: rechaza una página negativa o un tamaño fuera de 1 a 100")
    void listarValidaPaginacion() {
        given(autorizacion.alcance(REDACTAR)).willReturn(liga());

        assertThatThrownBy(() -> servicio.listar(null, -1, 20))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.listar(null, 0, 101))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verifyNoInteractions(comunicados);
    }
}

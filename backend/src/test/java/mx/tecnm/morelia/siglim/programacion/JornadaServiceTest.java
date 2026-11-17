package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.CanchaDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosJornada;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosPartido;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.EquipoDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.GrupoDeLiga;
import mx.tecnm.morelia.siglim.programacion.eventos.JornadaPublicada;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link JornadaService}: estado BORRADOR, ajuste con motivo y auditoría. Sin Spring. */
class JornadaServiceTest {

    private static final String CONSULTAR = "programacion.jornada:CONSULTAR";
    private static final String EDITAR = "programacion.jornada:EDITAR";
    private static final String ENVIAR = "programacion.jornada:ENVIAR";
    private static final String APROBAR = "programacion.jornada:APROBAR";
    private static final UUID PROGRAMADOR = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final String OBSERVACIONES = "Mover el partido de las 10:00 a la cancha 3";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID JORNADA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PARTIDO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID OTRO_GRUPO = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID LOCAL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID VISITANTE = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CANCHA = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID PERFIL = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final Instant AHORA = Instant.parse("2031-03-01T12:00:00Z");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    private static final OffsetDateTime CREADO_EN = OffsetDateTime.parse("2031-03-01T06:00:00-06:00");
    private static final String MOTIVO = "Cambio de cancha por lluvia";

    private final JornadaRepository jornadas = mock(JornadaRepository.class);
    private final ValidadorConflictos validador = mock(ValidadorConflictos.class);
    private final ValidadorLocalia localia = mock(ValidadorLocalia.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final JornadaService servicio = new JornadaService(jornadas, validador, localia, autorizacion, auditoria,
            motivoOperacion, eventos, new SiglimPropiedades(null, null, null, LIGA));

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

    private static DatosJornada jornada(EstadoJornada estado) {
        return jornada(estado, null, null);
    }

    /** Jornada con su autoría: quién la creó y quién la envió a aprobación (RF-121). */
    private static DatosJornada jornada(EstadoJornada estado, UUID creadoPor, UUID enviadaPor) {
        return new DatosJornada(JORNADA, GRUPO, null, 1, estado, creadoPor, enviadaPor,
                enviadaPor == null ? null : CREADO_EN, null, null, null, null, CREADO_EN, CREADO_EN);
    }

    private static DatosPartido partido(EstadoPartido estado, EstadoJornada estadoJornada, OffsetDateTime inicio) {
        return partido(PARTIDO, estado, estadoJornada, inicio);
    }

    private static DatosPartido partido(UUID id, EstadoPartido estado, EstadoJornada estadoJornada,
            OffsetDateTime inicio) {
        return new DatosPartido(id, JORNADA, GRUPO, estadoJornada, LOCAL, "Halcones",
                UUID.randomUUID(), VISITANTE, "Toros", UUID.randomUUID(), CANCHA, "Morelos 2",
                inicio, inicio.plusHours(2), estado, null, CREADO_EN, CREADO_EN,
                "ACTIVO", "ACTIVO", true);
    }

    private static EquipoDeLiga equipo(UUID id, String nombre, UUID grupo) {
        return new EquipoDeLiga(id, nombre, grupo, UUID.randomUUID(), "ACTIVO");
    }

    private static CanchaDeLiga cancha(boolean activa) {
        return new CanchaDeLiga(CANCHA, "Morelos 2", activa);
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

    // ── Alta de la jornada (RF-033) ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-033: registrar jornada la deja en BORRADOR, usa el perfil de la sesión y audita")
    void crearJornada() {
        given(jornadas.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(new GrupoDeLiga(GRUPO, UUID.randomUUID())));
        given(jornadas.insertarJornada(GRUPO, null, 3, PERFIL)).willReturn(JORNADA);
        given(jornadas.buscarJornada(LIGA, JORNADA)).willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));

        JornadaDto creada = servicio.crear(new CrearJornadaRequest(GRUPO, null, 3));

        assertThat(creada.estadoPublicacion()).isEqualTo(EstadoJornada.BORRADOR);
        assertThat(creada.partidos()).isEmpty();
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_JORNADA");
        assertThat(evento.entidad()).isEqualTo("jornadas");
        assertThat(evento.entidadId()).isEqualTo(JORNADA.toString());
        assertThat(evento.detalle()).containsEntry("numero", 3).containsEntry("estado", "BORRADOR");
    }

    @Test
    @DisplayName("RF-033: un grupo inexistente responde 422 REFERENCIA_INVALIDA y no crea nada")
    void crearJornadaConGrupoInexistente() {
        given(jornadas.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(new CrearJornadaRequest(GRUPO, null, 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));
        verify(jornadas, never()).insertarJornada(any(), any(), anyInt(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-033: una fase de otro grupo responde 422 y un número repetido 409 DUPLICADO")
    void crearJornadaValidaFaseYNumero() {
        UUID fase = UUID.randomUUID();
        given(jornadas.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(new GrupoDeLiga(GRUPO, UUID.randomUUID())));
        given(jornadas.faseDelGrupo(GRUPO, fase)).willReturn(false);

        assertThatThrownBy(() -> servicio.crear(new CrearJornadaRequest(GRUPO, fase, 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));

        given(jornadas.faseDelGrupo(GRUPO, fase)).willReturn(true);
        given(jornadas.existeNumero(GRUPO, fase, 1)).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(new CrearJornadaRequest(GRUPO, fase, 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        verify(jornadas, never()).insertarJornada(any(), any(), anyInt(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-033: el listado exige grupo y valida la paginación antes de consultar")
    void listarValidaFiltroYPaginacion() {
        assertThatThrownBy(() -> servicio.listar(null, null, 0, 20))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.listar(GRUPO, null, 0, 101))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verifyNoInteractions(jornadas);
    }

    @Test
    @DisplayName("RF-033: el listado pagina y cuenta con el mismo filtro")
    void listar() {
        given(jornadas.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(new GrupoDeLiga(GRUPO, UUID.randomUUID())));
        given(jornadas.listarJornadas(LIGA, GRUPO, EstadoJornada.BORRADOR, 20, 0))
                .willReturn(List.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.contarJornadas(LIGA, GRUPO, EstadoJornada.BORRADOR)).willReturn(41L);

        var pagina = servicio.listar(GRUPO, EstadoJornada.BORRADOR, 0, 20);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.contenido().getFirst().partidos()).as("el listado no anida partidos").isNull();
        assertThat(pagina.totalElementos()).isEqualTo(41);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
    }

    @Test
    @DisplayName("VAL-024: consultar una jornada inexistente responde 404 y no lee partidos")
    void consultarInexistente() {
        given(jornadas.buscarJornada(LIGA, JORNADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(JORNADA))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(jornadas, never()).listarPartidos(any());
    }

    // ── Verificación de conflictos (H-12.3, RF-039) ─────────────────────────────────────────

    @Test
    @DisplayName("RF-039: verificar devuelve los conflictos de todos los partidos sin escribir ni auditar")
    void verificarAcumulaConflictosDeTodosLosPartidos() {
        UUID segundo = UUID.fromString("99999999-9999-4999-8999-999999999999");
        given(jornadas.buscarJornada(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.listarPartidos(JORNADA)).willReturn(List.of(
                partido(PARTIDO, EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO),
                partido(segundo, EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO.plusHours(3))));
        given(validador.evaluar(eq(GRUPO), eq(PARTIDO), any(), any(), any(), any()))
                .willReturn(List.of(new ValidadorConflictos.Conflicto("VAL-004", "Cancha ocupada.")));
        given(validador.evaluar(eq(GRUPO), eq(segundo), any(), any(), any(), any()))
                .willReturn(List.of(new ValidadorConflictos.Conflicto("VAL-003", "Equipo ocupado.")));

        VerificacionJornadaDto resultado = servicio.verificar(JORNADA);

        assertThat(resultado.conflictos()).containsExactly(
                new VerificacionJornadaDto.Aviso(PARTIDO, "VAL-004", "Cancha ocupada."),
                new VerificacionJornadaDto.Aviso(segundo, "VAL-003", "Equipo ocupado."));
        assertThat(resultado.advertencias()).as("sin advertencias de localía").isEmpty();
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any(), any());
        verify(jornadas, never()).actualizarPartido(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-039: una jornada sin partidos no consulta conflictos ni escribe")
    void verificarJornadaVacia() {
        given(jornadas.buscarJornada(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.listarPartidos(JORNADA)).willReturn(List.of());

        VerificacionJornadaDto resultado = servicio.verificar(JORNADA);

        assertThat(resultado.conflictos()).isEmpty();
        assertThat(resultado.advertencias()).isEmpty();
        verifyNoInteractions(validador, auditoria);
    }

    @Test
    @DisplayName("RF-039: verificar omite los partidos cancelados porque ya no ocupan cancha")
    void verificarOmitePartidosCancelados() {
        given(jornadas.buscarJornada(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.listarPartidos(JORNADA)).willReturn(List.of(
                partido(PARTIDO, EstadoPartido.CANCELADO, EstadoJornada.BORRADOR, INICIO)));

        VerificacionJornadaDto resultado = servicio.verificar(JORNADA);

        assertThat(resultado.conflictos()).isEmpty();
        verifyNoInteractions(validador, auditoria);
    }

    @Test
    @DisplayName("VAL-024: verificar una jornada de otra liga responde 404 y no lee partidos")
    void verificarInexistente() {
        given(jornadas.buscarJornada(LIGA, JORNADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.verificar(JORNADA))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(jornadas, never()).listarPartidos(any());
        verifyNoInteractions(validador, auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin programacion.jornada:EDITAR no se verifica la jornada")
    void verificarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.verificar(JORNADA))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(jornadas, validador, auditoria);
    }

    // ── Registro de partidos (RF-036) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-090: no se registra un partido en una jornada que ya no está en borrador")
    void registrarPartidoFueraDeBorrador() {
        for (EstadoJornada estado : List.of(EstadoJornada.REVISION, EstadoJornada.PUBLICADO)) {
            given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA)).willReturn(Optional.of(jornada(estado)));

            assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(LOCAL)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));
        }
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-036: un equipo de otro grupo responde 422 RF-033 y no se guarda")
    void registrarPartidoConEquipoDeOtroGrupo() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", OTRO_GRUPO)));

        assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(LOCAL)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "RF-033"));
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-036: registrar partido usa la sesión y audita los identificadores")
    void registrarPartido() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, VISITANTE)).willReturn(Optional.of(equipo(VISITANTE, "Toros", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(cancha(true)));
        given(jornadas.insertarPartido(JORNADA, LOCAL, VISITANTE, CANCHA, INICIO, null, PERFIL)).willReturn(PARTIDO);
        given(jornadas.buscarPartido(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));

        PartidoDto creado = servicio.registrarPartido(JORNADA, peticionPartido(CANCHA));

        assertThat(creado.estado()).isEqualTo(EstadoPartido.PROGRAMADO);
        assertThat(creado.finBloque()).as("el bloque de 120 min lo calcula la base")
                .isEqualTo(INICIO.plusHours(2));
        verify(validador).validar(eq(GRUPO), any(), any(), any(), eq(cancha(true)), eq(INICIO));
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_PARTIDO");
        assertThat(evento.entidad()).isEqualTo("partidos");
        assertThat(evento.detalle()).containsEntry("jornadaId", JORNADA.toString())
                .containsEntry("canchaId", CANCHA.toString());
    }

    // ── Localía (B-15.1, RF-120, VAL-027) ──────────────────────────────────────────────────────

    private static final String MOTIVO_LOCALIA = "La cancha del club está en remodelación";
    private static final ValidadorLocalia.Advertencia CANCHA_AJENA = new ValidadorLocalia.Advertencia(
            ValidadorLocalia.Regla.CANCHA_DEL_LOCAL, "El equipo local «Halcones» tiene cancha.");

    private void partidoConCanchaAjena() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, VISITANTE)).willReturn(Optional.of(equipo(VISITANTE, "Toros", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(cancha(true)));
        given(localia.evaluar(eq(GRUPO), eq(JORNADA), any(), eq(CANCHA))).willReturn(List.of(CANCHA_AJENA));
    }

    @Test
    @DisplayName("VAL-027: con advertencia de localía y sin motivo se rechaza con 409 y la explicación")
    void localiaSinMotivo() {
        partidoConCanchaAjena();

        for (String motivo : java.util.Arrays.asList(null, "", "Corto")) {
            assertThatThrownBy(() -> servicio.registrarPartido(JORNADA,
                    new CrearPartidoRequest(LOCAL, VISITANTE, CANCHA, INICIO, motivo)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "VAL-027"))
                    .hasMessageContaining("«Halcones» tiene cancha")
                    .hasMessageContaining("motivo de localía")
                    .satisfies(error -> assertThat(((ReglaNegocioException) error).propiedades())
                            .containsEntry("aceptable", true).containsKey("advertencias"));
        }
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-120: con motivo de localía el partido se guarda con él y la aceptación se audita")
    void localiaConMotivo() {
        partidoConCanchaAjena();
        given(jornadas.insertarPartido(JORNADA, LOCAL, VISITANTE, CANCHA, INICIO, MOTIVO_LOCALIA, PERFIL))
                .willReturn(PARTIDO);
        given(jornadas.buscarPartido(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));

        servicio.registrarPartido(JORNADA,
                new CrearPartidoRequest(LOCAL, VISITANTE, CANCHA, INICIO, "  " + MOTIVO_LOCALIA + " "));

        verify(jornadas).insertarPartido(JORNADA, LOCAL, VISITANTE, CANCHA, INICIO, MOTIVO_LOCALIA, PERFIL);
        ArgumentCaptor<EventoAuditoria> eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, org.mockito.Mockito.times(2)).registrar(eventos.capture());
        EventoAuditoria aceptacion = eventos.getAllValues().get(1);
        assertThat(aceptacion.accion()).isEqualTo("ACEPTAR_LOCALIA");
        assertThat(aceptacion.motivo()).isEqualTo(MOTIVO_LOCALIA);
        assertThat(aceptacion.detalle()).containsEntry("reglas", List.of("CANCHA_DEL_LOCAL"));
    }

    @Test
    @DisplayName("RF-120: sin advertencia el motivo de localía enviado se ignora y no se audita aceptación")
    void sinAdvertenciaIgnoraMotivo() {
        partidoConCanchaAjena();
        given(localia.evaluar(eq(GRUPO), eq(JORNADA), any(), eq(CANCHA))).willReturn(List.of());
        given(jornadas.insertarPartido(JORNADA, LOCAL, VISITANTE, CANCHA, INICIO, null, PERFIL)).willReturn(PARTIDO);
        given(jornadas.buscarPartido(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));

        servicio.registrarPartido(JORNADA, new CrearPartidoRequest(LOCAL, VISITANTE, CANCHA, INICIO, MOTIVO_LOCALIA));

        assertThat(eventoAuditado().accion()).isEqualTo("REGISTRAR_PARTIDO");
    }

    @Test
    @DisplayName("RF-120: el ajuste conserva el motivo de localía guardado si la advertencia sigue")
    void ajusteConservaMotivoLocalia() {
        DatosPartido actual = new DatosPartido(PARTIDO, JORNADA, GRUPO, EstadoJornada.BORRADOR, LOCAL, "Halcones",
                UUID.randomUUID(), VISITANTE, "Toros", UUID.randomUUID(), CANCHA, "Morelos 2",
                INICIO, INICIO.plusHours(2), EstadoPartido.PROGRAMADO, MOTIVO_LOCALIA, CREADO_EN, CREADO_EN,
                "ACTIVO", "ACTIVO", true);
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO)).willReturn(Optional.of(actual));
        given(jornadas.buscarPartido(LIGA, PARTIDO)).willReturn(Optional.of(actual));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, VISITANTE)).willReturn(Optional.of(equipo(VISITANTE, "Toros", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(cancha(true)));
        given(localia.evaluar(eq(GRUPO), eq(JORNADA), any(), eq(CANCHA))).willReturn(List.of(CANCHA_AJENA));

        servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, INICIO.plusHours(3), MOTIVO));

        verify(jornadas).actualizarPartido(PARTIDO, LOCAL, VISITANTE, CANCHA, INICIO.plusHours(3), MOTIVO_LOCALIA);
        assertThat(eventoAuditado().accion()).as("no hay aceptación nueva").isEqualTo("AJUSTAR_PARTIDO");
    }

    @Test
    @DisplayName("B-15.2: aceptar la localía desde la fila guarda solo el motivo y audita ACEPTAR_LOCALIA")
    void aceptarLocaliaSinAjuste() {
        DatosPartido pendiente = partidoConMotivoLocalia(null);
        DatosPartido aceptado = partidoConMotivoLocalia(MOTIVO_LOCALIA);
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO)).willReturn(Optional.of(pendiente));
        given(jornadas.buscarPartido(LIGA, PARTIDO)).willReturn(Optional.of(aceptado));
        given(localia.evaluar(eq(GRUPO), eq(JORNADA), any(), eq(CANCHA))).willReturn(List.of(CANCHA_AJENA));

        PartidoDto resultado = servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, null, null, "  " + MOTIVO_LOCALIA + " "));

        assertThat(resultado.motivoLocalia()).isEqualTo(MOTIVO_LOCALIA);
        verify(jornadas).actualizarMotivoLocalia(PARTIDO, MOTIVO_LOCALIA);
        verify(jornadas, never()).actualizarPartido(any(), any(), any(), any(), any(), any());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("ACEPTAR_LOCALIA");
        assertThat(evento.motivo()).isEqualTo(MOTIVO_LOCALIA);
        assertThat(evento.detalle()).containsEntry("reglas", List.of("CANCHA_DEL_LOCAL"));
    }

    @Test
    @DisplayName("B-15.2: aceptar sin un motivo de 10 caracteres responde 409 VAL-027 y no escribe")
    void aceptarLocaliaSinMotivoSuficiente() {
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                .willReturn(Optional.of(partidoConMotivoLocalia(null)));
        given(localia.evaluar(eq(GRUPO), eq(JORNADA), any(), eq(CANCHA))).willReturn(List.of(CANCHA_AJENA));

        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, null, null, "Corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "VAL-027"))
                .satisfies(error -> assertThat(((ReglaNegocioException) error).propiedades())
                        .containsEntry("aceptable", true).containsKey("advertencias"));
        verify(jornadas, never()).actualizarMotivoLocalia(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("B-15.2: si el partido ya respeta la localía, el motivo de aceptación se ignora")
    void aceptarLocaliaSinAdvertencia() {
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                .willReturn(Optional.of(partidoConMotivoLocalia(null)));
        given(localia.evaluar(eq(GRUPO), eq(JORNADA), any(), eq(CANCHA))).willReturn(List.of());

        PartidoDto resultado = servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, null, null, MOTIVO_LOCALIA));

        assertThat(resultado.motivoLocalia()).isNull();
        verify(jornadas, never()).actualizarMotivoLocalia(any(), any());
        verifyNoInteractions(auditoria);
    }

    /** Partido de la jornada en borrador con el motivo de localía indicado (B-15.2). */
    private static DatosPartido partidoConMotivoLocalia(String motivoLocalia) {
        return new DatosPartido(PARTIDO, JORNADA, GRUPO, EstadoJornada.BORRADOR, LOCAL, "Halcones",
                UUID.randomUUID(), VISITANTE, "Toros", UUID.randomUUID(), CANCHA, "Morelos 2",
                INICIO, INICIO.plusHours(2), EstadoPartido.PROGRAMADO, motivoLocalia, CREADO_EN, CREADO_EN,
                "ACTIVO", "ACTIVO", true);
    }

    @Test
    @DisplayName("VAL-027: la verificación lista las advertencias de localía sin escribir")
    void verificarListaAdvertencias() {
        given(jornadas.buscarJornada(LIGA, JORNADA)).willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.listarPartidos(JORNADA)).willReturn(List.of(
                partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));
        given(localia.evaluar(eq(GRUPO), eq(JORNADA), any(), eq(CANCHA))).willReturn(List.of(CANCHA_AJENA));

        VerificacionJornadaDto resultado = servicio.verificar(JORNADA);

        assertThat(resultado.conflictos()).isEmpty();
        assertThat(resultado.advertencias()).containsExactly(
                new VerificacionJornadaDto.Aviso(PARTIDO, "VAL-027", CANCHA_AJENA.mensaje()));
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-036: los equipos deben ser distintos y la cancha de la liga")
    void registrarPartidoValidaDatos() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));

        assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(LOCAL, LOCAL)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "VALIDACION"));

        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, VISITANTE)).willReturn(Optional.of(equipo(VISITANTE, "Toros", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(CANCHA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(validador, auditoria);
    }

    @Test
    @DisplayName("VAL-004: un conflicto detectado antes de escribir evita el alta y la auditoría")
    void registrarPartidoConConflicto() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, VISITANTE)).willReturn(Optional.of(equipo(VISITANTE, "Toros", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(cancha(true)));
        willThrow(ReglaNegocioException.conflicto("VAL-004",
                "La cancha ya está ocupada por el partido «Águilas vs. Pumas»."))
                .given(validador).validar(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(CANCHA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "VAL-004"));
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    // ── Ajuste del partido (RF-040, RN-018) ─────────────────────────────────────────────────

    @Test
    @DisplayName("RF-040: el ajuste exige motivo de al menos 10 caracteres y no escribe sin él")
    void ajustarSinMotivo() {
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));

        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, INICIO.plusHours(3), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, INICIO.plusHours(3), "Corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        verify(jornadas, never()).actualizarPartido(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(validador, motivoOperacion, auditoria);
    }

    @Test
    @DisplayName("RF-040: el ajuste con motivo actualiza, fija el motivo de la operación y audita")
    void ajustarConMotivo() {
        OffsetDateTime nuevoInicio = INICIO.plusDays(1);
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, VISITANTE)).willReturn(Optional.of(equipo(VISITANTE, "Toros", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(cancha(true)));
        given(jornadas.buscarPartido(LIGA, PARTIDO)).willReturn(Optional.of(
                partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, nuevoInicio)));

        servicio.actualizarPartido(PARTIDO, new ActualizarPartidoRequest(null, null, null, nuevoInicio,
                "  " + MOTIVO + "  "));

        verify(validador).validar(eq(GRUPO), eq(PARTIDO), any(), any(), eq(cancha(true)), eq(nuevoInicio));
        verify(jornadas).actualizarPartido(PARTIDO, LOCAL, VISITANTE, CANCHA, nuevoInicio, null);
        verify(motivoOperacion).fijar(MOTIVO);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("AJUSTAR_PARTIDO");
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        assertThat(evento.detalle()).containsEntry("campos", List.of("inicio"));
    }

    @Test
    @DisplayName("RF-040: el ajuste puede cambiar equipos y deja en la auditoría los campos que cambió")
    void ajustarEquipos() {
        UUID nuevoVisitante = UUID.fromString("99999999-9999-4999-8999-999999999999");
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, nuevoVisitante))
                .willReturn(Optional.of(equipo(nuevoVisitante, "Águilas", GRUPO)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(cancha(true)));
        given(jornadas.buscarPartido(LIGA, PARTIDO)).willReturn(Optional.of(
                partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));

        servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, nuevoVisitante, null, null, MOTIVO));

        verify(jornadas).actualizarPartido(PARTIDO, LOCAL, nuevoVisitante, CANCHA, INICIO, null);
        assertThat(eventoAuditado().detalle()).containsEntry("campos", List.of("equipoVisitanteId"));
    }

    @Test
    @DisplayName("RF-040: un equipo ajeno al grupo al ajustar responde 422 RF-033 y no escribe")
    void ajustarConEquipoDeOtroGrupo() {
        UUID ajeno = UUID.fromString("99999999-9999-4999-8999-999999999991");
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));
        given(jornadas.buscarEquipo(LIGA, LOCAL)).willReturn(Optional.of(equipo(LOCAL, "Halcones", GRUPO)));
        given(jornadas.buscarEquipo(LIGA, ajeno))
                .willReturn(Optional.of(equipo(ajeno, "Ajenos", OTRO_GRUPO)));

        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, ajeno, null, null, MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "RF-033"));
        verify(jornadas, never()).actualizarPartido(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(validador, motivoOperacion, auditoria);
    }

    @Test
    @DisplayName("VAL-024: una jornada y un partido de otra liga responden 404")
    void fueraDeLaLiga() {
        given(jornadas.buscarJornada(LIGA, JORNADA)).willReturn(Optional.empty());
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(JORNADA))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), MOTIVO)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-040: un PATCH sin cambios no escribe, no exige motivo ni audita")
    void ajustarSinCambios() {
        DatosPartido actual = partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO);
        given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO)).willReturn(Optional.of(actual));

        PartidoDto resultado = servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(LOCAL, VISITANTE, CANCHA, INICIO, null));

        assertThat(resultado.id()).isEqualTo(PARTIDO);
        verify(jornadas, never()).actualizarPartido(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(validador, motivoOperacion, auditoria);
    }

    @Test
    @DisplayName("RF-046: un partido finalizado o cancelado no se ajusta")
    void ajustarPartidoCerrado() {
        for (EstadoPartido estado : List.of(EstadoPartido.FINALIZADO, EstadoPartido.CANCELADO)) {
            given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                    .willReturn(Optional.of(partido(estado, EstadoJornada.BORRADOR, INICIO)));

            assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                    new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), MOTIVO)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-046"));
        }
        verify(jornadas, never()).actualizarPartido(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-090: tampoco se ajusta si la jornada está en revisión o publicada")
    void ajustarJornadaNoBorrador() {
        for (EstadoJornada estado : List.of(EstadoJornada.REVISION, EstadoJornada.PUBLICADO)) {
            given(jornadas.buscarPartidoParaActualizar(LIGA, PARTIDO))
                    .willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO, estado, INICIO)));

            assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                    new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), MOTIVO)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));
        }
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin programacion.jornada:EDITAR no se registra ni se ajusta")
    void sinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(new CrearJornadaRequest(GRUPO, null, 1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThatThrownBy(() -> servicio.registrarPartido(JORNADA, peticionPartido(CANCHA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThatThrownBy(() -> servicio.actualizarPartido(PARTIDO,
                new ActualizarPartidoRequest(null, null, null, INICIO.plusDays(1), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(jornadas, auditoria);
    }

    @Test
    @DisplayName("RF-033: la consulta usa CONSULTAR y trae los partidos de la jornada")
    void consultarConPartidos() {
        given(jornadas.buscarJornada(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.listarPartidos(JORNADA))
                .willReturn(List.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));

        JornadaDto encontrada = servicio.consultar(JORNADA);

        assertThat(encontrada.partidos()).hasSize(1);
        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
    }

    private static CrearPartidoRequest peticionPartido(UUID canchaId) {
        return peticionPartido(LOCAL, VISITANTE, canchaId);
    }

    private static CrearPartidoRequest peticionPartido(UUID local, UUID visitante) {
        return peticionPartido(local, visitante, CANCHA);
    }

    // ── Envío a aprobación (H-13.1, RF-121) ─────────────────────────────────────────────────

    @Test
    @DisplayName("RF-121: una jornada en borrador sin conflictos pasa a REVISION con el Programador y se audita")
    void enviarAAprobacion() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.listarPartidos(JORNADA))
                .willReturn(List.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));
        given(jornadas.enviarAAprobacion(JORNADA, PERFIL)).willReturn(1);
        given(jornadas.buscarJornada(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.REVISION, PERFIL, PERFIL)));

        JornadaDto enviada = servicio.enviarAAprobacion(JORNADA);

        assertThat(enviada.estadoPublicacion()).isEqualTo(EstadoJornada.REVISION);
        assertThat(enviada.enviadaPor()).isEqualTo(PERFIL);
        assertThat(enviada.partidos()).hasSize(1);
        verify(autorizacion).exigir(ENVIAR, ObjetivoAutorizacion.deLiga());
        verify(jornadas).enviarAAprobacion(JORNADA, PERFIL);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("ENVIAR_JORNADA_A_APROBACION");
        assertThat(evento.entidadId()).isEqualTo(JORNADA.toString());
        assertThat(evento.detalle()).containsEntry("estadoAnterior", "BORRADOR")
                .containsEntry("estado", "REVISION").containsEntry("partidos", 1L);
    }

    @Test
    @DisplayName("RF-039: una jornada con conflictos no se envía y responde 409 con la lista")
    void enviarConConflictos() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.listarPartidos(JORNADA))
                .willReturn(List.of(partido(EstadoPartido.PROGRAMADO, EstadoJornada.BORRADOR, INICIO)));
        given(validador.evaluar(eq(GRUPO), eq(PARTIDO), any(), any(), any(), any()))
                .willReturn(List.of(new ValidadorConflictos.Conflicto("VAL-004",
                        "La cancha «Morelos 2» ya está ocupada.")));

        assertThatThrownBy(() -> servicio.enviarAAprobacion(JORNADA))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(excepcion.codigo()).isEqualTo("RF-039");
                    assertThat(excepcion.getMessage()).contains("1 conflicto", "Morelos 2");
                    assertThat(excepcion.propiedades()).containsKey("conflictos");
                });
        verify(jornadas, never()).enviarAAprobacion(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-121: una jornada sin partidos vigentes no se envía (409 RF-121)")
    void enviarSinPartidos() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR)));
        given(jornadas.listarPartidos(JORNADA))
                .willReturn(List.of(partido(EstadoPartido.CANCELADO, EstadoJornada.BORRADOR, INICIO)));

        assertThatThrownBy(() -> servicio.enviarAAprobacion(JORNADA))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-121"));
        verify(jornadas, never()).enviarAAprobacion(any(), any());
    }

    @Test
    @DisplayName("RF-090: una jornada que ya está en revisión no se vuelve a enviar")
    void enviarFueraDeBorrador() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.REVISION, PERFIL, PERFIL)));

        assertThatThrownBy(() -> servicio.enviarAAprobacion(JORNADA))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"))
                .hasMessageContaining("revisión");
        verify(jornadas, never()).enviarAAprobacion(any(), any());
    }

    @Test
    @DisplayName("VAL-015: sin permiso de enviar no se lee ni se escribe la jornada")
    void enviarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(ENVIAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.enviarAAprobacion(JORNADA))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(jornadas, auditoria);
    }

    @Test
    @DisplayName("RF-121: enviar una jornada inexistente responde 404")
    void enviarInexistente() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.enviarAAprobacion(JORNADA))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Dictamen del Presidente (H-13.1, RF-121) ────────────────────────────────────────────

    @Test
    @DisplayName("RF-121, RF-136: el Presidente aprueba una jornada en revisión, queda PUBLICADO y se emite JornadaPublicada")
    void aprobar() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.REVISION, PROGRAMADOR, PROGRAMADOR)));
        given(jornadas.resolver(JORNADA, EstadoJornada.PUBLICADO, PERFIL, null)).willReturn(1);
        given(jornadas.buscarJornada(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.PUBLICADO, PROGRAMADOR, PROGRAMADOR)));

        JornadaDto publicada = servicio.resolver(JORNADA,
                new ResolverJornadaRequest(DecisionJornada.PUBLICADO, "   "));

        assertThat(publicada.estadoPublicacion()).isEqualTo(EstadoJornada.PUBLICADO);
        verify(autorizacion).exigir(APROBAR, ObjetivoAutorizacion.deLiga());
        verify(jornadas).resolver(JORNADA, EstadoJornada.PUBLICADO, PERFIL, null);
        verify(motivoOperacion, never()).fijar(any());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("APROBAR_JORNADA");
        assertThat(evento.motivo()).isNull();
        assertThat(evento.detalle()).containsEntry("estadoAnterior", "REVISION").containsEntry("estado", "PUBLICADO");
        verify(eventos).publishEvent(new JornadaPublicada(LIGA, JORNADA, PERFIL));
    }

    @Test
    @DisplayName("RF-121: devolver con observaciones vuelve a BORRADOR, fija el motivo y lo audita")
    void devolverConObservaciones() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.REVISION, PROGRAMADOR, PROGRAMADOR)));
        given(jornadas.resolver(JORNADA, EstadoJornada.BORRADOR, null, OBSERVACIONES)).willReturn(1);
        given(jornadas.buscarJornada(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.BORRADOR, PROGRAMADOR, PROGRAMADOR)));

        JornadaDto devuelta = servicio.resolver(JORNADA,
                new ResolverJornadaRequest(DecisionJornada.BORRADOR, "  " + OBSERVACIONES + "  "));

        assertThat(devuelta.estadoPublicacion()).isEqualTo(EstadoJornada.BORRADOR);
        verify(motivoOperacion).fijar(OBSERVACIONES);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("DEVOLVER_JORNADA");
        assertThat(evento.motivo()).isEqualTo(OBSERVACIONES);
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("RF-121: devolver sin observaciones se rechaza con 400 y la jornada no cambia")
    void devolverSinObservaciones() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.REVISION, PROGRAMADOR, PROGRAMADOR)));

        assertThatThrownBy(() -> servicio.resolver(JORNADA,
                new ResolverJornadaRequest(DecisionJornada.BORRADOR, "Corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RF-121"));
        assertThatThrownBy(() -> servicio.resolver(JORNADA,
                new ResolverJornadaRequest(DecisionJornada.BORRADOR, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RF-121"));
        verify(jornadas, never()).resolver(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-121: quien envió o creó la jornada no puede dictaminarla (403)")
    void separacionPorAutoria() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.REVISION, PROGRAMADOR, PERFIL)));

        assertThatThrownBy(() -> servicio.resolver(JORNADA,
                new ResolverJornadaRequest(DecisionJornada.PUBLICADO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "RF-121"));

        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.REVISION, PERFIL, PROGRAMADOR)));

        assertThatThrownBy(() -> servicio.resolver(JORNADA,
                new ResolverJornadaRequest(DecisionJornada.BORRADOR, OBSERVACIONES)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "RF-121"));
        verify(jornadas, never()).resolver(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-121: un usuario con permiso de editar la jornada no puede aprobarla (403)")
    void separacionPorPermisoDeEditar() {
        given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                .willReturn(Optional.of(jornada(EstadoJornada.REVISION, PROGRAMADOR, PROGRAMADOR)));
        given(autorizacion.permite(EDITAR, ObjetivoAutorizacion.deLiga())).willReturn(true);

        assertThatThrownBy(() -> servicio.resolver(JORNADA,
                new ResolverJornadaRequest(DecisionJornada.PUBLICADO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "RF-121"))
                .hasMessageContaining("editar");
        verify(jornadas, never()).resolver(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-090: solo una jornada en revisión se dictamina; en borrador o publicada responde 409")
    void resolverFueraDeRevision() {
        for (EstadoJornada estado : List.of(EstadoJornada.BORRADOR, EstadoJornada.PUBLICADO)) {
            given(jornadas.buscarJornadaParaActualizar(LIGA, JORNADA))
                    .willReturn(Optional.of(jornada(estado, PROGRAMADOR, PROGRAMADOR)));

            assertThatThrownBy(() -> servicio.resolver(JORNADA,
                    new ResolverJornadaRequest(DecisionJornada.PUBLICADO, null)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));
        }
        verify(jornadas, never()).resolver(any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-015: sin permiso de aprobar no se lee ni se escribe la jornada")
    void resolverSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(APROBAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.resolver(JORNADA,
                new ResolverJornadaRequest(DecisionJornada.PUBLICADO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(jornadas, auditoria);
    }

    private static CrearPartidoRequest peticionPartido(UUID local, UUID visitante, UUID canchaId) {
        return new CrearPartidoRequest(local, visitante, canchaId, INICIO);
    }
}

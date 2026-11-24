package mx.tecnm.morelia.siglim.partidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.MarcadorDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.PartidoDto;
import mx.tecnm.morelia.siglim.partidos.CedulaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.CedulaBloqueada;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.EquipoAlineacion;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.MarcadorDefault;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.PartidoCedula;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.RegistroAlineable;
import mx.tecnm.morelia.siglim.partidos.GuardarAlineacionesRequest.JugadorRequest;
import mx.tecnm.morelia.siglim.partidos.eventos.CedulaEnviada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Captura de la cédula sin Spring ni base: abrirla, alineación con elegibilidad y marcador (H-16.1);
 * eventos, incidencias, observaciones y envío a validación (H-16.2).
 */
class CedulaServiceTest {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PERFIL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID PARTIDO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CEDULA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEMPORADA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID OTRA_TEMPORADA = UUID.fromString("33333333-3333-4333-8333-000000000000");
    private static final UUID LOCAL = UUID.fromString("44444444-4444-4444-8444-000000000001");
    private static final UUID VISITANTE = UUID.fromString("44444444-4444-4444-8444-000000000002");
    private static final UUID LOCAL_SEGUNDA = UUID.fromString("44444444-4444-4444-8444-000000000003");
    private static final UUID AJENO = UUID.fromString("44444444-4444-4444-8444-000000000004");
    private static final UUID CLUB_LOCAL = UUID.fromString("66666666-6666-4666-8666-000000000001");
    private static final UUID CLUB_VISITANTE = UUID.fromString("66666666-6666-4666-8666-000000000002");
    private static final UUID PRIMERA = UUID.fromString("77777777-7777-4777-8777-000000000001");
    private static final UUID SEGUNDA = UUID.fromString("77777777-7777-4777-8777-000000000002");

    private final CedulaRepository cedulas = mock(CedulaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final CedulaService servicio = new CedulaService(cedulas, autorizacion, auditoria, eventos,
            new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Instant ahora = Instant.now();
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(ahora).expiresAt(ahora.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
        given(cedulas.buscar(CEDULA)).willReturn(Optional.of(cedula()));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Jugadores disponibles (H-16.3) ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-172: lista los candidatos con su elegibilidad, el local primero y la categoría inferior después")
    void jugadoresDisponibles() {
        UUID visitante = UUID.randomUUID();
        UUID deSegunda = UUID.randomUUID();
        UUID titular = UUID.randomUUID();
        UUID sancionado = UUID.randomUUID();
        given(cedulas.datosDeCedula(LIGA, CEDULA)).willReturn(Optional.of(bloqueada(EstadoCedula.EN_CAPTURA)));
        given(cedulas.candidatos(CEDULA, TEMPORADA)).willReturn(List.of(
                new CedulaRepository.Candidato(visitante, VISITANTE, new ReferenciaDto(VISITANTE, "Toros")),
                new CedulaRepository.Candidato(deSegunda, LOCAL, new ReferenciaDto(LOCAL_SEGUNDA, "Halcones Segunda")),
                new CedulaRepository.Candidato(titular, LOCAL, new ReferenciaDto(LOCAL, "Halcones")),
                new CedulaRepository.Candidato(sancionado, LOCAL, new ReferenciaDto(LOCAL, "Halcones"))));
        given(cedulas.registros(any(), anyCollection())).willReturn(Map.of(
                visitante, new RegistroAlineable(visitante, "ACTIVO", TEMPORADA, VISITANTE, CLUB_VISITANTE, PRIMERA, null,
                        "Ana Visitante", false),
                deSegunda, new RegistroAlineable(deSegunda, "ACTIVO", TEMPORADA, LOCAL_SEGUNDA, CLUB_LOCAL, SEGUNDA, PRIMERA,
                        "Beto Segunda", false),
                titular, new RegistroAlineable(titular, "ACTIVO", TEMPORADA, LOCAL, CLUB_LOCAL, PRIMERA, null,
                        "Carlos Titular", false),
                sancionado, new RegistroAlineable(sancionado, "ACTIVO", TEMPORADA, LOCAL, CLUB_LOCAL, PRIMERA, null,
                        "Diego Sancionado", true)));
        given(cedulas.equipos(anyCollection())).willReturn(equipos());

        List<JugadorDisponibleDto> jugadores = servicio.jugadoresDisponibles(CEDULA);

        verify(autorizacion).exigir(CedulaService.CAPTURAR, ObjetivoAutorizacion.deLiga());
        assertThat(jugadores).extracting(JugadorDisponibleDto::nombre)
                .containsExactly("Carlos Titular", "Diego Sancionado", "Beto Segunda", "Ana Visitante");
        assertThat(jugadores.get(1).elegible()).isFalse();
        assertThat(jugadores.get(1).causa().codigo()).isEqualTo("RF-020");
        assertThat(jugadores.get(2).categoriaSuperior()).isTrue();
        assertThat(jugadores.get(2).elegible()).isTrue();
        assertThat(jugadores.get(2).equipoRegistro().nombre()).isEqualTo("Halcones Segunda");
        assertThat(jugadores.get(0).causa()).isNull();
    }

    @Test
    @DisplayName("DEC-172: los tipos de incidencia activos se consultan con partidos.cedula:CAPTURAR")
    void tiposIncidencia() {
        given(cedulas.datosDeCedula(LIGA, CEDULA)).willReturn(Optional.of(bloqueada(EstadoCedula.EN_CAPTURA)));
        given(cedulas.tiposIncidenciaActivos(LIGA)).willReturn(List.of(new CedulaDto.TipoIncidenciaDto(2L, "Expulsión", true)));

        assertThat(servicio.tiposIncidencia(CEDULA)).extracting(CedulaDto.TipoIncidenciaDto::nombre).containsExactly("Expulsión");
        verify(autorizacion).exigir(CedulaService.CAPTURAR, ObjetivoAutorizacion.deLiga());

        given(cedulas.datosDeCedula(LIGA, CEDULA)).willReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.tiposIncidencia(CEDULA)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("VAL-024: los jugadores de una cédula que no es de la liga responden 404")
    void jugadoresDeCedulaInexistente() {
        given(cedulas.datosDeCedula(LIGA, CEDULA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.jugadoresDisponibles(CEDULA)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Consultar ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-025: consulta la cédula del partido con partidos.cedula:CONSULTAR en la liga")
    void consultar() {
        given(cedulas.existePartido(LIGA, PARTIDO)).willReturn(true);
        given(cedulas.cedulaDePartido(PARTIDO)).willReturn(Optional.of(CEDULA));

        assertThat(servicio.consultar(PARTIDO).id()).isEqualTo(CEDULA);
        verify(autorizacion).exigir(CedulaService.CONSULTAR, ObjetivoAutorizacion.deLiga());
    }

    @Test
    @DisplayName("VAL-024: un partido de otra liga o sin cédula responde 404")
    void consultarInexistente() {
        given(cedulas.existePartido(LIGA, PARTIDO)).willReturn(false);
        assertThatThrownBy(() -> servicio.consultar(PARTIDO)).isInstanceOf(RecursoNoEncontradoException.class);

        given(cedulas.existePartido(LIGA, PARTIDO)).willReturn(true);
        given(cedulas.cedulaDePartido(PARTIDO)).willReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.consultar(PARTIDO)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Abrir ───────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-025: abre la cédula EN_CAPTURA con la sesión como capturista y la audita")
    void abrir() {
        given(cedulas.bloquearPartido(LIGA, PARTIDO)).willReturn(Optional.of(partido(EstadoPartido.EN_CURSO)));
        given(cedulas.cedulaDePartido(PARTIDO)).willReturn(Optional.empty());
        given(cedulas.insertar(PARTIDO, PERFIL)).willReturn(Optional.of(CEDULA));

        CedulaDto abierta = servicio.abrir(PARTIDO);

        assertThat(abierta.estado()).isEqualTo(EstadoCedula.EN_CAPTURA);
        verify(autorizacion).exigir(CedulaService.CAPTURAR, ObjetivoAutorizacion.deLiga());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("ABRIR_CEDULA");
        assertThat(evento.modulo()).isEqualTo("M-08");
        assertThat(evento.entidadId()).isEqualTo(CEDULA.toString());
    }

    @Test
    @DisplayName("RF-046: un partido que no está en curso responde 409 y no abre la cédula")
    void abrirPartidoNoEnCurso() {
        given(cedulas.bloquearPartido(LIGA, PARTIDO)).willReturn(Optional.of(partido(EstadoPartido.PROGRAMADO)));

        esperarError(() -> servicio.abrir(PARTIDO), "RF-046", HttpStatus.CONFLICT);
        verify(cedulas, never()).insertar(any(), any());
    }

    @Test
    @DisplayName("RF-025: un partido con cédula responde 409 DUPLICADO, también si otra petición la abrió antes")
    void abrirDuplicada() {
        given(cedulas.bloquearPartido(LIGA, PARTIDO)).willReturn(Optional.of(partido(EstadoPartido.EN_CURSO)));
        given(cedulas.cedulaDePartido(PARTIDO)).willReturn(Optional.of(CEDULA));
        esperarError(() -> servicio.abrir(PARTIDO), "DUPLICADO", HttpStatus.CONFLICT);

        given(cedulas.cedulaDePartido(PARTIDO)).willReturn(Optional.empty());
        given(cedulas.insertar(PARTIDO, PERFIL)).willReturn(Optional.empty());
        esperarError(() -> servicio.abrir(PARTIDO), "DUPLICADO", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("VAL-024: un partido que no es de la liga responde 404")
    void abrirPartidoInexistente() {
        given(cedulas.bloquearPartido(LIGA, PARTIDO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.abrir(PARTIDO)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Alineación ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-019 · RF-110: guarda jugadores elegibles, incluido uno de la categoría inmediata superior, y audita")
    void guardarAlineacion() {
        UUID titularLocal = UUID.randomUUID();
        UUID deSegunda = UUID.randomUUID();
        UUID visitante = UUID.randomUUID();
        enCaptura();
        given(cedulas.registros(any(), anyCollection())).willReturn(Map.of(
                titularLocal, registro(titularLocal, "ACTIVO", TEMPORADA, LOCAL, CLUB_LOCAL, PRIMERA, null, false),
                deSegunda, registro(deSegunda, "ACTIVO", TEMPORADA, LOCAL_SEGUNDA, CLUB_LOCAL, SEGUNDA, PRIMERA, false),
                visitante, registro(visitante, "ACTIVO", TEMPORADA, VISITANTE, CLUB_VISITANTE, PRIMERA, null, false)));
        given(cedulas.equipos(anyCollection())).willReturn(equipos());
        given(cedulas.alineacionActual(CEDULA)).willReturn(Map.of());

        servicio.guardarAlineaciones(CEDULA, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(titularLocal, LOCAL, 10, null),
                new JugadorRequest(deSegunda, LOCAL, 14, false),
                new JugadorRequest(visitante, VISITANTE, 9, true))));

        verify(cedulas).insertarAlineacion(CEDULA, titularLocal, LOCAL, 10, true);
        verify(cedulas).insertarAlineacion(CEDULA, deSegunda, LOCAL, 14, false);
        verify(cedulas).insertarAlineacion(CEDULA, visitante, VISITANTE, 9, true);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("CAPTURAR_ALINEACION");
        assertThat(evento.detalle()).containsEntry("jugadoresLocal", 2).containsEntry("jugadoresVisitante", 1);
        assertThat(evento.detalle().toString()).doesNotContain("Jugador");
    }

    @Test
    @DisplayName("VAL-002 · RF-019 · RF-020 · VAL-018 · RF-025: devuelve un error por jugador y no guarda nada")
    void alineacionConJugadoresNoElegibles() {
        UUID pendiente = UUID.randomUUID();
        UUID sancionado = UUID.randomUUID();
        UUID otroClub = UUID.randomUUID();
        UUID otraTemporada = UUID.randomUUID();
        UUID inexistente = UUID.randomUUID();
        UUID equipoAjeno = UUID.randomUUID();
        enCaptura();
        given(cedulas.registros(any(), anyCollection())).willReturn(Map.of(
                pendiente, registro(pendiente, "PENDIENTE_VALIDACION", TEMPORADA, LOCAL, CLUB_LOCAL, PRIMERA, null, false),
                sancionado, registro(sancionado, "ACTIVO", TEMPORADA, LOCAL, CLUB_LOCAL, PRIMERA, null, true),
                otroClub, registro(otroClub, "ACTIVO", TEMPORADA, AJENO, CLUB_VISITANTE, SEGUNDA, PRIMERA, false),
                otraTemporada, registro(otraTemporada, "ACTIVO", OTRA_TEMPORADA, LOCAL, CLUB_LOCAL, PRIMERA, null, false),
                equipoAjeno, registro(equipoAjeno, "ACTIVO", TEMPORADA, LOCAL, CLUB_LOCAL, PRIMERA, null, false)));
        given(cedulas.equipos(anyCollection())).willReturn(equipos());

        assertThatThrownBy(() -> servicio.guardarAlineaciones(CEDULA, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(pendiente, LOCAL, null, null),
                new JugadorRequest(sancionado, LOCAL, null, null),
                new JugadorRequest(otroClub, LOCAL, null, null),
                new JugadorRequest(otraTemporada, LOCAL, null, null),
                new JugadorRequest(inexistente, VISITANTE, null, null),
                new JugadorRequest(equipoAjeno, AJENO, null, null)))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-002");
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> errores = (List<Map<String, Object>>) error.propiedades().get("errores");
                    assertThat(errores).extracting(e -> e.get("codigo"))
                            .containsExactly("RF-019", "RF-020", "VAL-018", "RF-019", "REFERENCIA_INVALIDA", "RF-025");
                    assertThat(errores).extracting(e -> e.get("indice")).containsExactly(0, 1, 2, 3, 4, 5);
                    assertThat(errores.get(0).get("mensaje")).asString()
                            .contains("«Jugador " + pendiente + "»").contains("pendiente de validación");
                    assertThat(errores.get(1).get("mensaje")).asString().contains("sanción vigente");
                    assertThat(errores.get(2).get("mensaje")).asString().contains("categoría inmediata superior");
                });
        verify(cedulas, never()).insertarAlineacion(any(), any(), any(), any(), anyBoolean());
        verify(cedulas, never()).eliminarAlineaciones(anyCollection());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("VAL-018: un jugador de su club dos categorías abajo no se alinea en la superior")
    void alineacionDosCategoriasArriba() {
        UUID deTercera = UUID.randomUUID();
        UUID tercera = UUID.randomUUID();
        enCaptura();
        given(cedulas.registros(any(), anyCollection())).willReturn(Map.of(deTercera,
                registro(deTercera, "ACTIVO", TEMPORADA, LOCAL_SEGUNDA, CLUB_LOCAL, tercera, SEGUNDA, false)));
        given(cedulas.equipos(anyCollection())).willReturn(equipos());

        esperarError(() -> servicio.guardarAlineaciones(CEDULA, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(deTercera, LOCAL, null, null)))), "VAL-002", HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("VALIDACION: sin lista, con un jugador repetido o una playera fuera de 0 a 99 responde 400 con errores")
    void alineacionMalFormada() {
        enCaptura();
        UUID repetido = UUID.randomUUID();
        esperarError(() -> servicio.guardarAlineaciones(CEDULA, new GuardarAlineacionesRequest(null)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> servicio.guardarAlineaciones(CEDULA, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(repetido, LOCAL, 10, true),
                new JugadorRequest(repetido, LOCAL, 11, true),
                new JugadorRequest(null, null, 100, true)))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VALIDACION");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> errores = (List<Map<String, Object>>) error.propiedades().get("errores");
                    assertThat(errores).extracting(e -> e.get("campo")).containsExactly(
                            "jugadores[1].registroJugadorId", "jugadores[2].registroJugadorId",
                            "jugadores[2].equipoId", "jugadores[2].numeroPlayera");
                });
        verify(cedulas, never()).registros(any(), anyCollection());
    }

    @Test
    @DisplayName("RF-122: con la cédula fuera de captura la alineación y el marcador responden 409")
    void contenidoFueraDeCaptura() {
        given(cedulas.bloquear(LIGA, CEDULA)).willReturn(Optional.of(bloqueada(EstadoCedula.PENDIENTE_VALIDACION)));

        esperarError(() -> servicio.guardarAlineaciones(CEDULA, new GuardarAlineacionesRequest(List.of())),
                "RF-122", HttpStatus.CONFLICT);
        esperarError(() -> servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(1, 0, null, null, null)),
                "RF-122", HttpStatus.CONFLICT);
        verify(cedulas, never()).guardarMarcador(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-025: reemplaza la alineación: actualiza a quien sigue, agrega al nuevo y quita al que sale")
    void reemplazarAlineacion() {
        UUID sigue = UUID.randomUUID();
        UUID nuevo = UUID.randomUUID();
        UUID sale = UUID.randomUUID();
        UUID filaSigue = UUID.randomUUID();
        UUID filaSale = UUID.randomUUID();
        enCaptura();
        given(cedulas.registros(any(), anyCollection())).willReturn(Map.of(
                sigue, registro(sigue, "ACTIVO", TEMPORADA, LOCAL, CLUB_LOCAL, PRIMERA, null, false),
                nuevo, registro(nuevo, "ACTIVO", TEMPORADA, VISITANTE, CLUB_VISITANTE, PRIMERA, null, false)));
        given(cedulas.equipos(anyCollection())).willReturn(equipos());
        given(cedulas.alineacionActual(CEDULA)).willReturn(Map.of(sigue, filaSigue, sale, filaSale));
        given(cedulas.conEventos(Set.of(filaSale))).willReturn(Set.of());

        servicio.guardarAlineaciones(CEDULA, new GuardarAlineacionesRequest(List.of(
                new JugadorRequest(sigue, LOCAL, 7, false),
                new JugadorRequest(nuevo, VISITANTE, 3, true))));

        verify(cedulas).eliminarAlineaciones(Set.of(filaSale));
        verify(cedulas).actualizarAlineacion(filaSigue, LOCAL, 7, false);
        verify(cedulas).insertarAlineacion(CEDULA, nuevo, VISITANTE, 3, true);
    }

    @Test
    @DisplayName("RF-025: quitar a un jugador con eventos en la cédula responde 422 y no cambia nada")
    void quitarJugadorConEventos() {
        UUID sale = UUID.randomUUID();
        UUID filaSale = UUID.randomUUID();
        enCaptura();
        given(cedulas.registros(any(), anyCollection())).willReturn(Map.of());
        given(cedulas.equipos(anyCollection())).willReturn(equipos());
        given(cedulas.alineacionActual(CEDULA)).willReturn(Map.of(sale, filaSale));
        given(cedulas.conEventos(Set.of(filaSale))).willReturn(Set.of(filaSale));

        esperarError(() -> servicio.guardarAlineaciones(CEDULA, new GuardarAlineacionesRequest(List.of())),
                "RF-025", HttpStatus.UNPROCESSABLE_CONTENT);
        verify(cedulas, never()).eliminarAlineaciones(anyCollection());
    }

    // ── Marcador ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-047: guarda el marcador y lo audita sin datos personales")
    void guardarMarcador() {
        enCaptura();

        servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(2, 1, null, null, null));

        verify(cedulas).guardarMarcador(CEDULA, 2, 1, null, null, null);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("CAPTURAR_MARCADOR");
        assertThat(evento.detalle()).containsEntry("golesLocal", 2).containsEntry("golesVisitante", 1)
                .containsEntry("porDefault", false);
    }

    @Test
    @DisplayName("RF-126 · DEC-24: el equipo no presentado pierde con el marcador por default de los parámetros")
    void noPresentacion() {
        enCaptura();
        given(cedulas.marcadorDefault(TEMPORADA)).willReturn(Optional.of(new MarcadorDefault(3, 0, true)));

        servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(null, null, null, null, LOCAL));
        verify(cedulas).guardarMarcador(CEDULA, 0, 3, null, null, LOCAL);

        servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(null, null, null, null, VISITANTE));
        verify(cedulas).guardarMarcador(CEDULA, 3, 0, null, null, VISITANTE);
    }

    @Test
    @DisplayName("DEC-72: el marcador por default sale de la versión vigente de los parámetros, no del código")
    void noPresentacionConParametrosPropios() {
        enCaptura();
        given(cedulas.marcadorDefault(TEMPORADA)).willReturn(Optional.of(new MarcadorDefault(2, 0, false)));

        servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(null, null, null, null, VISITANTE));

        verify(cedulas).guardarMarcador(CEDULA, 2, 0, null, null, VISITANTE);
    }

    @Test
    @DisplayName("RF-126 · RF-100: no presentación con goles, de un equipo ajeno o sin parámetros se rechaza")
    void noPresentacionInvalida() {
        enCaptura();
        esperarError(() -> servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(1, 0, null, null, LOCAL)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(null, null, null, null, AJENO)),
                "RF-126", HttpStatus.UNPROCESSABLE_CONTENT);
        given(cedulas.marcadorDefault(TEMPORADA)).willReturn(Optional.empty());
        esperarError(() -> servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(null, null, null, null, LOCAL)),
                "RF-100", HttpStatus.CONFLICT);
        verify(cedulas, never()).guardarMarcador(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-047 · DEC-28: los goles son obligatorios y los penales van completos y solo con empate")
    void marcadorInvalido() {
        enCaptura();
        esperarError(() -> servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(1, null, null, null, null)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(-1, 0, null, null, null)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(1, 1, 4, null, null)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(2, 1, 4, 3, null)),
                "RF-047", HttpStatus.UNPROCESSABLE_CONTENT);

        servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(1, 1, 4, 3, null));
        verify(cedulas).guardarMarcador(CEDULA, 1, 1, 4, 3, null);
    }

    @Test
    @DisplayName("VAL-024: una cédula que no es de la liga responde 404")
    void cedulaInexistente() {
        given(cedulas.bloquear(LIGA, CEDULA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.guardarMarcador(CEDULA, new GuardarMarcadorRequest(1, 0, null, null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Eventos (H-16.2) ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-025: registra un gol con el equipo de la alineación del jugador y lo audita")
    void registrarGol() {
        UUID fila = UUID.randomUUID();
        enCaptura();
        given(cedulas.equipoDeAlineacion(CEDULA, fila)).willReturn(Optional.of(LOCAL));

        servicio.registrarEvento(CEDULA, new RegistrarEventoRequest(TipoEventoCedula.GOL, 34, fila, null, "  Cabezazo "));

        verify(cedulas).insertarEvento(CEDULA, TipoEventoCedula.GOL, 34, LOCAL, fila, null, "Cabezazo");
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_EVENTO_CEDULA");
        assertThat(evento.detalle()).containsEntry("tipo", "GOL").containsEntry("minuto", 34)
                .containsEntry("equipoId", LOCAL.toString());
    }

    @Test
    @DisplayName("RF-025: una sustitución lleva al que entra, distinto y del mismo equipo")
    void registrarSustitucion() {
        UUID sale = UUID.randomUUID();
        UUID entra = UUID.randomUUID();
        UUID rival = UUID.randomUUID();
        enCaptura();
        given(cedulas.equipoDeAlineacion(CEDULA, sale)).willReturn(Optional.of(LOCAL));
        given(cedulas.equipoDeAlineacion(CEDULA, entra)).willReturn(Optional.of(LOCAL));
        given(cedulas.equipoDeAlineacion(CEDULA, rival)).willReturn(Optional.of(VISITANTE));

        esperarError(() -> servicio.registrarEvento(CEDULA,
                new RegistrarEventoRequest(TipoEventoCedula.SUSTITUCION, 60, sale, null, null)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.registrarEvento(CEDULA,
                new RegistrarEventoRequest(TipoEventoCedula.SUSTITUCION, 60, sale, sale, null)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.registrarEvento(CEDULA,
                new RegistrarEventoRequest(TipoEventoCedula.GOL, 60, sale, entra, null)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.registrarEvento(CEDULA,
                new RegistrarEventoRequest(TipoEventoCedula.SUSTITUCION, 60, sale, rival, null)),
                "RF-025", HttpStatus.UNPROCESSABLE_CONTENT);

        servicio.registrarEvento(CEDULA, new RegistrarEventoRequest(TipoEventoCedula.SUSTITUCION, 60, sale, entra, null));
        verify(cedulas).insertarEvento(CEDULA, TipoEventoCedula.SUSTITUCION, 60, LOCAL, sale, entra, null);
    }

    @Test
    @DisplayName("RF-025: sin tipo, con minuto fuera de 0 a 150 o con un jugador no alineado se rechaza")
    void eventoInvalido() {
        UUID fila = UUID.randomUUID();
        enCaptura();
        given(cedulas.equipoDeAlineacion(CEDULA, fila)).willReturn(Optional.empty());

        esperarError(() -> servicio.registrarEvento(CEDULA, new RegistrarEventoRequest(null, 10, fila, null, null)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.registrarEvento(CEDULA,
                new RegistrarEventoRequest(TipoEventoCedula.TARJETA_ROJA, 151, fila, null, null)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.registrarEvento(CEDULA,
                new RegistrarEventoRequest(TipoEventoCedula.TARJETA_ROJA, 80, fila, null, null)),
                "REFERENCIA_INVALIDA", HttpStatus.UNPROCESSABLE_CONTENT);
        verify(cedulas, never()).insertarEvento(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-025: quita un evento de la cédula en captura; uno ajeno responde 404")
    void eliminarEvento() {
        UUID evento = UUID.randomUUID();
        enCaptura();
        given(cedulas.eliminarEvento(CEDULA, evento)).willReturn(1);

        servicio.eliminarEvento(CEDULA, evento);
        assertThat(eventoAuditado().accion()).isEqualTo("ELIMINAR_EVENTO_CEDULA");

        given(cedulas.eliminarEvento(CEDULA, evento)).willReturn(0);
        assertThatThrownBy(() -> servicio.eliminarEvento(CEDULA, evento)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Incidencias (H-16.2) ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-026: registra la incidencia con su tipo activo y sus involucrados, y la audita")
    void registrarIncidencia() {
        UUID persona = UUID.randomUUID();
        UUID incidencia = UUID.randomUUID();
        enCaptura();
        given(cedulas.tipoIncidenciaActivo(LIGA, 2L))
                .willReturn(Optional.of(new CedulaDto.TipoIncidenciaDto(2L, "Expulsión", true)));
        given(cedulas.personasAlineadas(CEDULA)).willReturn(Set.of(persona));
        given(cedulas.insertarIncidencia(CEDULA, 2L, 70, "Agresión al rival")).willReturn(incidencia);

        servicio.registrarIncidencia(CEDULA, new RegistrarIncidenciaRequest(2L, 70, "Agresión al rival", List.of(
                new RegistrarIncidenciaRequest.InvolucradoRequest(persona, null),
                new RegistrarIncidenciaRequest.InvolucradoRequest(null, VISITANTE))));

        verify(cedulas).insertarInvolucrado(incidencia, persona, null);
        verify(cedulas).insertarInvolucrado(incidencia, null, VISITANTE);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_INCIDENCIA_CEDULA");
        assertThat(evento.detalle()).containsEntry("involucrados", 2).doesNotContainValue("Agresión al rival");
    }

    @Test
    @DisplayName("RF-026: tipo inexistente o inactivo, involucrados ausentes, dobles o ajenos se rechazan")
    void incidenciaInvalida() {
        UUID persona = UUID.randomUUID();
        UUID ajena = UUID.randomUUID();
        enCaptura();
        given(cedulas.tipoIncidenciaActivo(LIGA, 9L)).willReturn(Optional.empty());
        given(cedulas.tipoIncidenciaActivo(LIGA, 2L))
                .willReturn(Optional.of(new CedulaDto.TipoIncidenciaDto(2L, "Expulsión", true)));
        given(cedulas.personasAlineadas(CEDULA)).willReturn(Set.of(persona));
        var conPersona = List.of(new RegistrarIncidenciaRequest.InvolucradoRequest(persona, null));

        esperarError(() -> servicio.registrarIncidencia(CEDULA, new RegistrarIncidenciaRequest(2L, 10, "  ", conPersona)),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.registrarIncidencia(CEDULA, new RegistrarIncidenciaRequest(2L, 10, "Riña", List.of())),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.registrarIncidencia(CEDULA, new RegistrarIncidenciaRequest(2L, 10, "Riña",
                List.of(new RegistrarIncidenciaRequest.InvolucradoRequest(persona, LOCAL)))),
                "VALIDACION", HttpStatus.BAD_REQUEST);
        esperarError(() -> servicio.registrarIncidencia(CEDULA, new RegistrarIncidenciaRequest(9L, 10, "Riña", conPersona)),
                "REFERENCIA_INVALIDA", HttpStatus.UNPROCESSABLE_CONTENT);
        esperarError(() -> servicio.registrarIncidencia(CEDULA, new RegistrarIncidenciaRequest(2L, 10, "Riña",
                List.of(new RegistrarIncidenciaRequest.InvolucradoRequest(ajena, null)))),
                "RF-026", HttpStatus.UNPROCESSABLE_CONTENT);
        esperarError(() -> servicio.registrarIncidencia(CEDULA, new RegistrarIncidenciaRequest(2L, 10, "Riña",
                List.of(new RegistrarIncidenciaRequest.InvolucradoRequest(null, AJENO)))),
                "RF-026", HttpStatus.UNPROCESSABLE_CONTENT);
        verify(cedulas, never()).insertarIncidencia(any(), anyLong(), any(), any());
    }

    // ── Observaciones y envío (H-16.2) ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-025: guarda las observaciones recortadas y vacías las quita")
    void guardarObservaciones() {
        enCaptura();

        servicio.guardarObservaciones(CEDULA, new GuardarObservacionesRequest("  Lluvia en el segundo tiempo "));
        verify(cedulas).guardarObservaciones(CEDULA, "Lluvia en el segundo tiempo");

        servicio.guardarObservaciones(CEDULA, new GuardarObservacionesRequest("   "));
        verify(cedulas).guardarObservaciones(CEDULA, null);
    }

    @Test
    @DisplayName("RF-122: envía la cédula completa, la audita y publica CedulaEnviada para el aviso al árbitro")
    void enviar() {
        OffsetDateTime plazo = OffsetDateTime.parse("2031-03-12T10:30:00-06:00");
        enCaptura();
        given(cedulas.datosEnvio(CEDULA)).willReturn(new CedulaRepository.DatosEnvio(true, null, "Halcones", "Toros", 11, 11));
        given(cedulas.enviar(CEDULA)).willReturn(plazo);

        servicio.enviar(CEDULA);

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("ENVIAR_CEDULA");
        ArgumentCaptor<CedulaEnviada> enviada = ArgumentCaptor.forClass(CedulaEnviada.class);
        verify(eventos).publishEvent(enviada.capture());
        assertThat(enviada.getValue()).isEqualTo(new CedulaEnviada(LIGA, CEDULA, PARTIDO, plazo));
    }

    @Test
    @DisplayName("VAL-020: una cédula incompleta no se envía y lista lo que falta")
    void enviarIncompleta() {
        enCaptura();
        given(cedulas.datosEnvio(CEDULA)).willReturn(new CedulaRepository.DatosEnvio(false, null, "Halcones", "Toros", 0, 0));

        assertThatThrownBy(() -> servicio.enviar(CEDULA)).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.codigo()).isEqualTo("VAL-020");
            assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> faltantes = (List<Map<String, Object>>) error.propiedades().get("faltantes");
            assertThat(faltantes).extracting(falta -> falta.get("campo"))
                    .containsExactly("marcador", "alineacionLocal", "alineacionVisitante");
            assertThat(error.getMessage()).contains("«Halcones»").contains("«Toros»");
        });
        verify(cedulas, never()).enviar(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    @DisplayName("VAL-020 · RF-126: el equipo no presentado no necesita alineación para enviar")
    void enviarConNoPresentacion() {
        enCaptura();
        given(cedulas.datosEnvio(CEDULA)).willReturn(new CedulaRepository.DatosEnvio(true, VISITANTE, "Halcones", "Toros", 7, 0));
        given(cedulas.enviar(CEDULA)).willReturn(OffsetDateTime.parse("2031-03-12T10:30:00-06:00"));

        servicio.enviar(CEDULA);

        verify(cedulas).enviar(CEDULA);
    }

    @Test
    @DisplayName("RF-122: fuera de captura no se agregan ni quitan eventos o incidencias, ni se editan observaciones o se reenvía")
    void edicionFueraDeCaptura() {
        for (EstadoCedula estado : List.of(EstadoCedula.PENDIENTE_VALIDACION, EstadoCedula.DEVUELTA, EstadoCedula.APROBADA)) {
            given(cedulas.bloquear(LIGA, CEDULA)).willReturn(Optional.of(bloqueada(estado)));

            esperarError(() -> servicio.registrarEvento(CEDULA,
                    new RegistrarEventoRequest(TipoEventoCedula.GOL, 1, UUID.randomUUID(), null, null)),
                    "RF-122", HttpStatus.CONFLICT);
            esperarError(() -> servicio.eliminarEvento(CEDULA, UUID.randomUUID()), "RF-122", HttpStatus.CONFLICT);
            esperarError(() -> servicio.registrarIncidencia(CEDULA, new RegistrarIncidenciaRequest(1L, 1, "Riña",
                    List.of(new RegistrarIncidenciaRequest.InvolucradoRequest(null, LOCAL)))),
                    "RF-122", HttpStatus.CONFLICT);
            esperarError(() -> servicio.guardarObservaciones(CEDULA, new GuardarObservacionesRequest("Nota")),
                    "RF-122", HttpStatus.CONFLICT);
            esperarError(() -> servicio.enviar(CEDULA), "RF-122", HttpStatus.CONFLICT);
        }
        verify(cedulas, never()).insertarEvento(any(), any(), any(), any(), any(), any(), any());
        verify(cedulas, never()).eliminarEvento(any(), any());
        verify(cedulas, never()).guardarObservaciones(any(), any());
        verify(cedulas, never()).enviar(any());
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private void enCaptura() {
        given(cedulas.bloquear(LIGA, CEDULA)).willReturn(Optional.of(bloqueada(EstadoCedula.EN_CAPTURA)));
    }

    private static CedulaBloqueada bloqueada(EstadoCedula estado) {
        return new CedulaBloqueada(CEDULA, estado, PARTIDO, LOCAL, VISITANTE, TEMPORADA);
    }

    private static PartidoCedula partido(EstadoPartido estado) {
        return new PartidoCedula(PARTIDO, estado, LOCAL, VISITANTE, TEMPORADA);
    }

    private static RegistroAlineable registro(UUID id, String estado, UUID temporada, UUID equipo, UUID club,
            UUID categoria, UUID categoriaSuperior, boolean sancionado) {
        return new RegistroAlineable(id, estado, temporada, equipo, club, categoria, categoriaSuperior,
                "Jugador " + id, sancionado);
    }

    private static Map<UUID, EquipoAlineacion> equipos() {
        return Map.of(
                LOCAL, new EquipoAlineacion(LOCAL, CLUB_LOCAL, PRIMERA),
                VISITANTE, new EquipoAlineacion(VISITANTE, CLUB_VISITANTE, PRIMERA),
                LOCAL_SEGUNDA, new EquipoAlineacion(LOCAL_SEGUNDA, CLUB_LOCAL, SEGUNDA),
                AJENO, new EquipoAlineacion(AJENO, CLUB_VISITANTE, SEGUNDA));
    }

    private static CedulaDto cedula() {
        OffsetDateTime ahora = OffsetDateTime.parse("2031-03-10T10:30:00-06:00");
        return new CedulaDto(CEDULA,
                new PartidoDto(PARTIDO, new ReferenciaDto(LOCAL, "Halcones"), new ReferenciaDto(VISITANTE, "Toros"),
                        ahora, EstadoPartido.EN_CURSO),
                EstadoCedula.EN_CAPTURA, new ReferenciaDto(PERFIL, "Capturista"),
                new MarcadorDto(null, null, null, null, null, false, false), List.of(), List.of(), List.of(), null,
                null, null, ahora, ahora);
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static void esperarError(ThrowingCallable accion, String codigo, HttpStatus estado) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.codigo()).isEqualTo(codigo);
            assertThat(error.estado()).isEqualTo(estado);
        });
    }
}

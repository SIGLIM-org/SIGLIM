package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.arbitraje.CandidatoArbitralDto.CausaDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionDto.ArbitroDesignadoDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionRepository.DesignacionVigente;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionRepository.PartidoBloqueado;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionRepository.PartidoDesignable;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import mx.tecnm.morelia.siglim.programacion.ResultadoCentrales;
import mx.tecnm.morelia.siglim.programacion.ResultadoCentrales.CentralDesignado;
import mx.tecnm.morelia.siglim.programacion.ResultadoCentrales.SinCentral;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link DesignacionAutomatica} (H-34.1, H-34.2, DEC-182, DEC-213 a DEC-216, DEC-220). Sin BD. */
class DesignacionAutomaticaTest {

    private static final String AUTOMATICA = "arbitraje.designacion:AUTOMATICA";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PRIMERO = UUID.fromString("c1111111-1111-4111-8111-111111111111");
    private static final UUID SEGUNDO = UUID.fromString("b2222222-2222-4222-8222-222222222222");
    private static final UUID TERCERO = UUID.fromString("a3333333-3333-4333-8333-333333333333");
    private static final UUID MISMA_HORA = UUID.fromString("b1111111-1111-4111-8111-111111111111");
    private static final UUID ARBITRO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTRO_ARBITRO = UUID.fromString("12121212-1212-4121-8121-121212121212");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final Instant AHORA = Instant.parse("2032-05-01T12:00:00Z");
    private static final OffsetDateTime SABADO = OffsetDateTime.parse("2032-05-08T10:00:00-06:00");

    private final DesignacionRepository designaciones = mock(DesignacionRepository.class);
    private final ElegibilidadArbitral elegibilidad = mock(ElegibilidadArbitral.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final DesignacionAutomatica designador = new DesignacionAutomatica(designaciones, elegibilidad,
            autorizacion, auditoria, new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
        given(designaciones.vigentesDelPartido(any())).willReturn(List.of());
        given(designaciones.ultimoPartidoPorArbitro(any())).willReturn(Map.of());
        given(designaciones.insertar(any(), any(), any(), any(), anyBoolean()))
                .willAnswer(invocacion -> UUID.randomUUID());
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("VAL-015: sin arbitraje.designacion:AUTOMATICA responde 403 y no consulta nada")
    void sinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(AUTOMATICA, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> designador.designarCentrales(List.of(PRIMERO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.codigo()).isEqualTo("VAL-015");
                });
        verifyNoInteractions(designaciones, elegibilidad, auditoria);
    }

    @Test
    @DisplayName("DEC-213: lista vacía o null da un resultado vacío sin consultar, después del permiso")
    void listaVacia() {
        assertThat(designador.designarCentrales(List.of())).isEqualTo(ResultadoCentrales.vacio());
        assertThat(designador.designarCentrales(null)).isEqualTo(ResultadoCentrales.vacio());

        verify(autorizacion, times(2)).exigir(AUTOMATICA, ObjetivoAutorizacion.deLiga());
        verifyNoInteractions(designaciones, elegibilidad, auditoria);
    }

    @Test
    @DisplayName("DEC-215: bloquea partidos, después árbitros validados, y solo entonces elige e inserta")
    void ordenDeBloqueo() {
        partidos(bloqueado(PRIMERO, EstadoPartido.PROGRAMADO, SABADO));
        given(elegibilidad.candidatos(LIGA, PRIMERO)).willReturn(List.of(elegible(ARBITRO, 0)));

        designador.designarCentrales(List.of(PRIMERO));

        InOrder orden = inOrder(autorizacion, designaciones, elegibilidad, auditoria);
        orden.verify(autorizacion).exigir(AUTOMATICA, ObjetivoAutorizacion.deLiga());
        orden.verify(designaciones).bloquearPartidos(eq(LIGA), argThat(ids -> ids.size() == 1 && ids.contains(PRIMERO)));
        orden.verify(designaciones).bloquearArbitrosValidados(LIGA);
        orden.verify(elegibilidad).candidatos(LIGA, PRIMERO);
        orden.verify(designaciones).insertar(PRIMERO, ARBITRO, FuncionArbitral.CENTRAL, PERFIL, true);
        orden.verify(auditoria).registrar(any());
    }

    @Test
    @DisplayName("DEC-182: procesa por inicio aunque los ids lleguen desordenados, y el id por texto en empate")
    void porInicio() {
        // La base los devuelve por id; el componente los ordena por inicio y después id por texto.
        partidos(bloqueado(TERCERO, EstadoPartido.PROGRAMADO, SABADO.plusHours(4)),
                bloqueado(SEGUNDO, EstadoPartido.REPROGRAMADO, SABADO),
                bloqueado(PRIMERO, EstadoPartido.PROGRAMADO, SABADO.minusHours(2)),
                bloqueado(MISMA_HORA, EstadoPartido.PROGRAMADO, SABADO));
        given(elegibilidad.candidatos(any(), any())).willReturn(List.of(elegible(ARBITRO, 0)));

        ResultadoCentrales resultado = designador.designarCentrales(List.of(TERCERO, SEGUNDO,
                MISMA_HORA, PRIMERO));

        assertThat(resultado.designados()).extracting(CentralDesignado::partidoId).containsExactly(PRIMERO,
                MISMA_HORA, SEGUNDO, TERCERO);
        InOrder orden = inOrder(elegibilidad);
        orden.verify(elegibilidad).candidatos(LIGA, PRIMERO);
        orden.verify(elegibilidad).candidatos(LIGA, MISMA_HORA);
        orden.verify(elegibilidad).candidatos(LIGA, SEGUNDO);
        orden.verify(elegibilidad).candidatos(LIGA, TERCERO);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = EstadoPartido.class, names = {"CANCELADO", "FINALIZADO", "EN_CURSO", "SUSPENDIDO"})
    @DisplayName("DEC-213: omite el partido que no está PROGRAMADO ni REPROGRAMADO, sin listarlo")
    void omiteNoDesignable(EstadoPartido estado) {
        partidos(bloqueado(PRIMERO, estado, SABADO));

        ResultadoCentrales resultado = designador.designarCentrales(List.of(PRIMERO));

        assertThat(resultado).isEqualTo(ResultadoCentrales.vacio());
        verifyNoInteractions(elegibilidad, auditoria);
        verify(designaciones, never()).insertar(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("DEC-213: omite el partido que ya tiene central vigente; uno con solo asistentes sí recibe central")
    void omiteConCentral() {
        partidos(bloqueado(PRIMERO, EstadoPartido.PROGRAMADO, SABADO),
                bloqueado(SEGUNDO, EstadoPartido.PROGRAMADO, SABADO.plusHours(3)));
        given(designaciones.vigentesDelPartido(PRIMERO)).willReturn(List.of(
                new DesignacionVigente(UUID.randomUUID(), OTRO_ARBITRO, FuncionArbitral.CENTRAL)));
        given(designaciones.vigentesDelPartido(SEGUNDO)).willReturn(List.of(
                new DesignacionVigente(UUID.randomUUID(), OTRO_ARBITRO, FuncionArbitral.ASISTENTE)));
        given(elegibilidad.candidatos(LIGA, SEGUNDO)).willReturn(List.of(elegible(ARBITRO, 0)));

        ResultadoCentrales resultado = designador.designarCentrales(List.of(PRIMERO, SEGUNDO));

        assertThat(resultado.designados()).extracting(CentralDesignado::partidoId).containsExactly(SEGUNDO);
        assertThat(resultado.sinCentral()).isEmpty();
        verify(elegibilidad, never()).candidatos(LIGA, PRIMERO);
    }

    @Test
    @DisplayName("DEC-182 · RF-093: inserta con automatica = true y el perfil de la sesión, y audita DESIGNAR_ARBITRO")
    void insertaYAudita() {
        UUID designacion = UUID.fromString("77777777-7777-4777-8777-777777777777");
        partidos(bloqueado(PRIMERO, EstadoPartido.PROGRAMADO, SABADO));
        given(elegibilidad.candidatos(LIGA, PRIMERO)).willReturn(List.of(
                new CandidatoArbitralDto(arbitro(ARBITRO), true, List.of(),
                        List.of(new CausaDto("RF-022", "Declaró que no está disponible.")), 0)));
        given(designaciones.insertar(PRIMERO, ARBITRO, FuncionArbitral.CENTRAL, PERFIL, true)).willReturn(designacion);

        ResultadoCentrales resultado = designador.designarCentrales(List.of(PRIMERO));

        assertThat(resultado.designados()).containsExactly(
                new CentralDesignado(PRIMERO, ARBITRO, designacion, List.of("RF-022")));
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().ligaId()).isEqualTo(LIGA);
        assertThat(evento.getValue().modulo()).isEqualTo("M-05");
        assertThat(evento.getValue().accion()).isEqualTo("DESIGNAR_ARBITRO");
        assertThat(evento.getValue().entidad()).isEqualTo("designaciones");
        assertThat(evento.getValue().entidadId()).isEqualTo(designacion.toString());
        assertThat(evento.getValue().motivo()).isNull();
        assertThat(evento.getValue().detalle()).containsOnlyKeys("partidoId", "arbitroId", "funcion", "automatica")
                .containsEntry("partidoId", PRIMERO.toString())
                .containsEntry("arbitroId", ARBITRO.toString())
                .containsEntry("funcion", "CENTRAL")
                .containsEntry("automatica", true);
    }

    @Test
    @DisplayName("RF-023 · DEC-216: sin candidato queda en sinCentral con su causa, sin insertar ni auditar")
    void sinCandidato() {
        partidos(bloqueado(PRIMERO, EstadoPartido.PROGRAMADO, SABADO));
        given(elegibilidad.candidatos(LIGA, PRIMERO)).willReturn(List.of(new CandidatoArbitralDto(arbitro(ARBITRO),
                false, List.of(new CausaDto("VAL-019", "Pertenece al club.")), List.of(), 0)));

        ResultadoCentrales resultado = designador.designarCentrales(List.of(PRIMERO));

        assertThat(resultado.designados()).isEmpty();
        assertThat(resultado.sinCentral()).containsExactly(new SinCentral(PRIMERO, "RF-023",
                "El único árbitro validado no puede pitarlo: 1 es de los clubes del partido (VAL-019)."));
        verify(designaciones, never()).insertar(any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("DEC-214: vuelve a pedir candidatos y último partido por cada partido, sin estado en memoria")
    void consultaPorPartido() {
        partidos(bloqueado(PRIMERO, EstadoPartido.PROGRAMADO, SABADO),
                bloqueado(SEGUNDO, EstadoPartido.PROGRAMADO, SABADO.plusHours(3)));
        // Tras designar al primero, la base ya le cuenta la carga: el segundo va con el otro árbitro.
        given(elegibilidad.candidatos(LIGA, PRIMERO)).willReturn(List.of(elegible(ARBITRO, 0), elegible(OTRO_ARBITRO, 0)));
        given(elegibilidad.candidatos(LIGA, SEGUNDO)).willReturn(List.of(elegible(ARBITRO, 1), elegible(OTRO_ARBITRO, 0)));

        ResultadoCentrales resultado = designador.designarCentrales(List.of(PRIMERO, SEGUNDO));

        assertThat(resultado.designados()).extracting(CentralDesignado::arbitroId).containsExactly(ARBITRO, OTRO_ARBITRO);
        verify(elegibilidad).candidatos(LIGA, PRIMERO);
        verify(elegibilidad).candidatos(LIGA, SEGUNDO);
        verify(designaciones).ultimoPartidoPorArbitro(PRIMERO);
        verify(designaciones).ultimoPartidoPorArbitro(SEGUNDO);
        verify(designaciones, times(1)).bloquearArbitrosValidados(LIGA);
    }

    // ── Explicación de «Sin árbitro» (H-34.2, DEC-220) ──────────────────────────────────────────

    @Test
    @DisplayName("DEC-220: explicarSinCentral no exige permiso propio ni bloquea, y con lista vacía o null no consulta")
    void explicarSinPermisoNiConsulta() {
        assertThat(designador.explicarSinCentral(List.of())).isEmpty();
        assertThat(designador.explicarSinCentral(null)).isEmpty();

        verifyNoInteractions(autorizacion, designaciones, elegibilidad, auditoria);
    }

    @Test
    @DisplayName("DEC-220: omite otra liga, no designables y con central; el resto va en el orden recibido y sin duplicados")
    void explicarOmite() {
        UUID otraLiga = UUID.fromString("d4444444-4444-4444-8444-444444444444");
        UUID suspendido = UUID.fromString("e5555555-5555-4555-8555-555555555555");
        given(designaciones.buscarPartido(LIGA, otraLiga)).willReturn(Optional.empty());
        given(designaciones.buscarPartido(LIGA, suspendido))
                .willReturn(Optional.of(new PartidoDesignable(suspendido, EstadoPartido.SUSPENDIDO)));
        designable(PRIMERO, EstadoPartido.PROGRAMADO);
        designable(SEGUNDO, EstadoPartido.REPROGRAMADO);
        designable(TERCERO, EstadoPartido.PROGRAMADO);
        given(designaciones.vigentesDelPartido(PRIMERO)).willReturn(List.of(
                new DesignacionVigente(UUID.randomUUID(), OTRO_ARBITRO, FuncionArbitral.CENTRAL)));
        given(elegibilidad.candidatos(LIGA, SEGUNDO)).willReturn(List.of());
        given(elegibilidad.candidatos(LIGA, TERCERO)).willReturn(List.of());

        Map<UUID, String> causas = designador.explicarSinCentral(List.of(TERCERO, otraLiga, PRIMERO, suspendido,
                SEGUNDO, TERCERO));

        assertThat(causas.keySet()).containsExactly(TERCERO, SEGUNDO);
        assertThat(causas).containsEntry(TERCERO, "No hay árbitros validados en la liga.");
        assertThatThrownBy(() -> causas.put(PRIMERO, "x")).isInstanceOf(UnsupportedOperationException.class);
        verify(elegibilidad, times(1)).candidatos(LIGA, TERCERO);
        verify(elegibilidad, never()).candidatos(LIGA, PRIMERO);
        verify(designaciones, never()).bloquearPartidos(any(), any());
        verify(designaciones, never()).bloquearArbitrosValidados(any());
        verify(designaciones, never()).bloquearPartido(any(), any());
        verifyNoInteractions(autorizacion, auditoria);
    }

    @Test
    @DisplayName("DEC-216 · DEC-220: sin elegibles da la causa con conteos por regla")
    void explicarSinElegibles() {
        designable(PRIMERO, EstadoPartido.PROGRAMADO);
        given(elegibilidad.candidatos(LIGA, PRIMERO)).willReturn(List.of(
                new CandidatoArbitralDto(arbitro(ARBITRO), false, List.of(new CausaDto("RF-039", "Choca.")), List.of(), 0),
                new CandidatoArbitralDto(arbitro(OTRO_ARBITRO), false, List.of(new CausaDto("VAL-019", "Club.")),
                        List.of(), 0)));

        assertThat(designador.explicarSinCentral(List.of(PRIMERO))).containsExactly(Map.entry(PRIMERO,
                "Ninguno de los 2 árbitros validados puede pitarlo: 1 es de los clubes del partido (VAL-019) "
                        + "y 1 ya pita a esa hora (RF-039)."));
    }

    @Test
    @DisplayName("DEC-220: con elegibles cuenta cuántos pueden pitarlo, en singular y en plural")
    void explicarConElegibles() {
        designable(PRIMERO, EstadoPartido.PROGRAMADO);
        designable(SEGUNDO, EstadoPartido.PROGRAMADO);
        given(elegibilidad.candidatos(LIGA, PRIMERO)).willReturn(List.of(elegible(ARBITRO, 0),
                new CandidatoArbitralDto(arbitro(OTRO_ARBITRO), false, List.of(new CausaDto("RF-039", "Choca.")),
                        List.of(), 0)));
        given(elegibilidad.candidatos(LIGA, SEGUNDO)).willReturn(List.of(elegible(ARBITRO, 0), elegible(OTRO_ARBITRO, 2)));

        Map<UUID, String> causas = designador.explicarSinCentral(List.of(PRIMERO, SEGUNDO));

        assertThat(causas).containsEntry(PRIMERO, "1 árbitro validado puede pitarlo.")
                .containsEntry(SEGUNDO, "2 árbitros validados pueden pitarlo.");
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private void partidos(PartidoBloqueado... bloqueados) {
        given(designaciones.bloquearPartidos(eq(LIGA), any())).willReturn(new ArrayList<>(List.of(bloqueados)));
    }

    private static PartidoBloqueado bloqueado(UUID id, EstadoPartido estado, OffsetDateTime inicio) {
        return new PartidoBloqueado(id, estado, inicio);
    }

    private static CandidatoArbitralDto elegible(UUID id, long carga) {
        return new CandidatoArbitralDto(arbitro(id), true, List.of(), List.of(), carga);
    }

    private static ArbitroDesignadoDto arbitro(UUID id) {
        return new ArbitroDesignadoDto(id, "Árbitro", new ReferenciaDto(UUID.randomUUID(), "Club"));
    }

    private void designable(UUID id, EstadoPartido estado) {
        given(designaciones.buscarPartido(LIGA, id)).willReturn(Optional.of(new PartidoDesignable(id, estado)));
    }
}

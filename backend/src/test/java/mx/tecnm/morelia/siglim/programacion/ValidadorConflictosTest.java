package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.BloqueCancha;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.EquipoEnConflicto;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.PartidoEnConflicto;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.RestriccionCancha;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.SancionDeEquipo;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.CanchaDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.EquipoDeLiga;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Reglas de {@link ValidadorConflictos} (RF-039, VAL-003 a VAL-006) sin Spring ni BD: la validación
 * que rechaza el guardado y la evaluación que acumula conflictos para la verificación (H-12.3).
 */
class ValidadorConflictosTest {

    private static final UUID GRUPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID LOCAL_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID VISITANTE_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CANCHA_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID PARTIDO_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    /** 90 min: distinto del bloque [DEMO] de 120 para probar que se usa el de los parámetros. */
    private static final OffsetDateTime FIN = INICIO.plusMinutes(90);

    private static final EquipoDeLiga LOCAL = new EquipoDeLiga(LOCAL_ID, "Halcones", GRUPO,
            UUID.randomUUID(), "ACTIVO");
    private static final EquipoDeLiga VISITANTE = new EquipoDeLiga(VISITANTE_ID, "Toros", GRUPO,
            UUID.randomUUID(), "ACTIVO");
    private static final CanchaDeLiga CANCHA = new CanchaDeLiga(CANCHA_ID, "Morelos 2", true);

    private final ConflictoRepository conflictos = mock(ConflictoRepository.class);
    private final ValidadorConflictos validador = new ValidadorConflictos(conflictos);

    @BeforeEach
    void prepararSinConflictos() {
        given(conflictos.buscarSancionVigente(GRUPO, LOCAL_ID, VISITANTE_ID, INICIO))
                .willReturn(Optional.empty());
        given(conflictos.calcularBloque(GRUPO, LOCAL_ID, INICIO))
                .willReturn(new BloqueCancha(true, FIN));
        given(conflictos.buscarRestriccion(CANCHA_ID, INICIO, FIN)).willReturn(Optional.empty());
        given(conflictos.buscarCanchaOcupada(CANCHA_ID, INICIO, FIN, PARTIDO_ID))
                .willReturn(Optional.empty());
        given(conflictos.buscarEquipoOcupado(LOCAL_ID, VISITANTE_ID, INICIO, FIN, PARTIDO_ID))
                .willReturn(Optional.empty());
    }

    // ── Camino sin conflictos ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-039: sin conflictos usa el bloque de la categoría en las consultas de traslape")
    void sinConflictos() {
        validador.validar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO);

        verify(conflictos).buscarRestriccion(CANCHA_ID, INICIO, FIN);
        verify(conflictos).buscarCanchaOcupada(CANCHA_ID, INICIO, FIN, PARTIDO_ID);
        verify(conflictos).buscarEquipoOcupado(LOCAL_ID, VISITANTE_ID, INICIO, FIN, PARTIDO_ID);
    }

    // ── VAL-003: equipo duplicado ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-003: un equipo con otro partido en el bloque se rechaza y nombra el partido")
    void equipoOcupado() {
        given(conflictos.buscarEquipoOcupado(LOCAL_ID, VISITANTE_ID, INICIO, FIN, PARTIDO_ID))
                .willReturn(Optional.of(new EquipoEnConflicto(LOCAL_ID, "Halcones", otroPartido())));

        assertConflicto(() -> validador.validar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO),
                "VAL-003", "Halcones", "Águilas vs. Pumas", "Morelos 1", "10/03/2031 10:00");
    }

    // ── VAL-004: cancha ocupada ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-004: una cancha ocupada se rechaza y nombra el otro partido y el horario")
    void canchaOcupada() {
        given(conflictos.buscarCanchaOcupada(CANCHA_ID, INICIO, FIN, PARTIDO_ID))
                .willReturn(Optional.of(otroPartido()));

        assertConflicto(() -> validador.validar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO),
                "VAL-004", "Morelos 2", "Águilas vs. Pumas", "10/03/2031 10:00", "11:30");
    }

    // ── VAL-005: cancha inactiva o restringida ────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-005: una cancha inactiva se rechaza antes de calcular el bloque")
    void canchaInactiva() {
        CanchaDeLiga inactiva = new CanchaDeLiga(CANCHA_ID, "Morelos 2", false);

        assertConflicto(() -> validador.validar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, inactiva, INICIO),
                "VAL-005", "Morelos 2", "inactiva");
    }

    @Test
    @DisplayName("VAL-005: un bloqueo se rechaza con su motivo y su periodo")
    void canchaBloqueada() {
        given(conflictos.buscarRestriccion(CANCHA_ID, INICIO, FIN)).willReturn(Optional.of(
                new RestriccionCancha("BLOQUEO", "Riego del césped",
                        INICIO.minusHours(1), INICIO.plusHours(1))));

        assertConflicto(() -> validador.validar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO),
                "VAL-005", "Morelos 2", "bloqueo", "Riego del césped", "09:00", "11:00");
    }

    @Test
    @DisplayName("VAL-005: un mantenimiento se rechaza con su motivo")
    void canchaEnMantenimiento() {
        given(conflictos.buscarRestriccion(CANCHA_ID, INICIO, FIN)).willReturn(Optional.of(
                new RestriccionCancha("MANTENIMIENTO", "Cambio de pasto",
                        INICIO, INICIO.plusDays(1))));

        assertConflicto(() -> validador.validar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO),
                "VAL-005", "Morelos 2", "mantenimiento", "Cambio de pasto");
    }

    // ── VAL-006: equipo dado de baja o sancionado ─────────────────────────────────────────────

    @Test
    @DisplayName("VAL-006: un equipo dado de baja no se puede programar")
    void equipoDadoDeBaja() {
        EquipoDeLiga baja = new EquipoDeLiga(VISITANTE_ID, "Toros", GRUPO, UUID.randomUUID(), "BAJA");

        assertConflicto(() -> validador.validar(GRUPO, PARTIDO_ID, LOCAL, baja, CANCHA, INICIO),
                "VAL-006", "Toros", "baja");
    }

    @Test
    @DisplayName("VAL-006: una suspensión vigente de partidos impide jugar y dice cuántos")
    void equipoSuspendido() {
        given(conflictos.buscarSancionVigente(GRUPO, LOCAL_ID, VISITANTE_ID, INICIO))
                .willReturn(Optional.of(new SancionDeEquipo(VISITANTE_ID, "Toros",
                        "SUSPENSION_PARTIDOS", (short) 2, (short) 0, null, null)));

        assertConflicto(() -> validador.validar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO),
                "VAL-006", "Toros", "suspensión vigente de 2");
    }

    @Test
    @DisplayName("VAL-006: una sanción vigente de baja impide jugar")
    void equipoConSancionDeBaja() {
        given(conflictos.buscarSancionVigente(GRUPO, LOCAL_ID, VISITANTE_ID, INICIO))
                .willReturn(Optional.of(new SancionDeEquipo(LOCAL_ID, "Halcones",
                        "BAJA", null, (short) 0, null, null)));

        assertConflicto(() -> validador.validar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO),
                "VAL-006", "Halcones", "sanción vigente de baja");
    }

    // ── Verificación sin guardar (H-12.3) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-039: evaluar sin conflictos devuelve la lista vacía")
    void evaluarSinConflictos() {
        assertThat(validador.evaluar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO)).isEmpty();
    }

    @Test
    @DisplayName("RF-039: evaluar acumula la cancha ocupada y el equipo ocupado del mismo partido")
    void evaluarAcumulaConflictos() {
        given(conflictos.buscarCanchaOcupada(CANCHA_ID, INICIO, FIN, PARTIDO_ID))
                .willReturn(Optional.of(otroPartido()));
        given(conflictos.buscarEquipoOcupado(LOCAL_ID, VISITANTE_ID, INICIO, FIN, PARTIDO_ID))
                .willReturn(Optional.of(new EquipoEnConflicto(LOCAL_ID, "Halcones", otroPartido())));

        List<ValidadorConflictos.Conflicto> encontrados =
                validador.evaluar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO);

        assertThat(encontrados).extracting(ValidadorConflictos.Conflicto::codigo)
                .containsExactly("VAL-004", "VAL-003");
        assertThat(encontrados.getFirst().mensaje()).contains("Morelos 2", "Águilas vs. Pumas");
    }

    @Test
    @DisplayName("VAL-006: evaluar explica por separado a los dos equipos dados de baja")
    void evaluarAcumulaEquiposDeBaja() {
        EquipoDeLiga localBaja = new EquipoDeLiga(LOCAL_ID, "Halcones", GRUPO, UUID.randomUUID(), "BAJA");
        EquipoDeLiga visitanteBaja =
                new EquipoDeLiga(VISITANTE_ID, "Toros", GRUPO, UUID.randomUUID(), "BAJA");

        List<ValidadorConflictos.Conflicto> encontrados =
                validador.evaluar(GRUPO, PARTIDO_ID, localBaja, visitanteBaja, CANCHA, INICIO);

        assertThat(encontrados).extracting(ValidadorConflictos.Conflicto::codigo)
                .containsExactly("VAL-006", "VAL-006");
        assertThat(encontrados).extracting(ValidadorConflictos.Conflicto::mensaje)
                .anySatisfy(mensaje -> assertThat(mensaje).contains("Halcones"))
                .anySatisfy(mensaje -> assertThat(mensaje).contains("Toros"));
    }

    @Test
    @DisplayName("RF-039: validar rechaza con el primer conflicto aunque la evaluación encuentre más")
    void validarLanzaElPrimeroDeVarios() {
        given(conflictos.buscarCanchaOcupada(CANCHA_ID, INICIO, FIN, PARTIDO_ID))
                .willReturn(Optional.of(otroPartido()));
        given(conflictos.buscarEquipoOcupado(LOCAL_ID, VISITANTE_ID, INICIO, FIN, PARTIDO_ID))
                .willReturn(Optional.of(new EquipoEnConflicto(LOCAL_ID, "Halcones", otroPartido())));

        assertConflicto(() -> validador.validar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO),
                "VAL-004", "Morelos 2");
    }

    // ── RF-100: sin parámetros no hay bloque ──────────────────────────────────────────────────

    @Test
    @DisplayName("RF-100: sin parámetros de temporada responde 409 y no consulta traslapes")
    void temporadaSinParametros() {
        given(conflictos.calcularBloque(GRUPO, LOCAL_ID, INICIO))
                .willReturn(new BloqueCancha(false, null));

        assertConflicto(() -> validador.validar(GRUPO, PARTIDO_ID, LOCAL, VISITANTE, CANCHA, INICIO),
                "RF-100", "parámetros");
        verify(conflictos, never()).buscarRestriccion(any(), any(), any());
        verify(conflictos, never()).buscarCanchaOcupada(any(), any(), any(), any());
        verify(conflictos, never()).buscarEquipoOcupado(any(), any(), any(), any(), any());
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /** Partido ya programado en otra cancha, de 10:00 a 11:30. */
    private static PartidoEnConflicto otroPartido() {
        return new PartidoEnConflicto(UUID.randomUUID(), "Morelos 1", UUID.randomUUID(), "Águilas",
                UUID.randomUUID(), "Pumas", INICIO, INICIO.plusMinutes(90));
    }

    private static void assertConflicto(ThrowingCallable operacion, String codigo, String... fragmentos) {
        assertThatThrownBy(operacion)
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(excepcion.codigo()).isEqualTo(codigo);
                    assertThat(excepcion.getMessage()).contains(fragmentos);
                });
    }
}

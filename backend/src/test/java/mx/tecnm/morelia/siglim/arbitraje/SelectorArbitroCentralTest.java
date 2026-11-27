package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.arbitraje.CandidatoArbitralDto.CausaDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionDto.ArbitroDesignadoDto;
import mx.tecnm.morelia.siglim.arbitraje.SelectorArbitroCentral.Eleccion;
import mx.tecnm.morelia.siglim.arbitraje.SelectorArbitroCentral.Elegido;
import mx.tecnm.morelia.siglim.arbitraje.SelectorArbitroCentral.SinCandidato;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Orden y causas de {@link SelectorArbitroCentral} (H-34.1, DEC-214, DEC-216). Dominio puro. */
class SelectorArbitroCentralTest {

    // «8…» va antes que «1…» con UUID.compareTo (con signo) y después por texto (DEC-185).
    private static final UUID A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID C = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID D = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ALTO = UUID.fromString("80000000-0000-4000-8000-000000000000");
    private static final OffsetDateTime SABADO = OffsetDateTime.parse("2032-05-08T10:00:00-06:00");
    private static final CausaDto NO_DISPONIBLE = new CausaDto("RF-022", "Declaró que no está disponible.");
    private static final CausaDto MISMO_CLUB = new CausaDto("VAL-019", "Pertenece al club.");
    private static final CausaDto CHOQUE = new CausaDto("RF-039", "Ya está designado a esa hora.");
    private static final CausaDto YA_DESIGNADO = new CausaDto(ElegibilidadArbitral.YA_DESIGNADO, "Ya está.");

    // ── Orden (DEC-214) ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-182: gana el elegible de menor carga")
    void menorCarga() {
        assertThat(elegido(List.of(elegible(A, 3), elegible(B, 1), elegible(C, 2)), Map.of())).isEqualTo(B);
    }

    @Test
    @DisplayName("VAL-019 · RF-039: un no elegible nunca se elige, aunque tenga menor carga")
    void noElegiblesNunca() {
        List<CandidatoArbitralDto> candidatos = List.of(noElegible(A, 0, MISMO_CLUB), noElegible(B, 0, CHOQUE),
                noElegible(C, 0, YA_DESIGNADO), elegible(D, 9));
        assertThat(elegido(candidatos, Map.of())).isEqualTo(D);
    }

    @Test
    @DisplayName("DEC-214 [PROPUESTA]: sin RF-022 va antes que uno con RF-022 y menor carga")
    void sinAdvertenciaAntesQueMenorCarga() {
        assertThat(elegido(List.of(conAdvertencia(A, 0), elegible(B, 5)), Map.of())).isEqualTo(B);
    }

    @Test
    @DisplayName("DEC-204: si solo quedan con RF-022, se elige uno y conserva la advertencia")
    void soloConAdvertencia() {
        Eleccion eleccion = SelectorArbitroCentral.elegir(
                List.of(conAdvertencia(A, 2), conAdvertencia(B, 1), noElegible(C, 0, MISMO_CLUB)), Map.of());

        assertThat(eleccion).isInstanceOfSatisfying(Elegido.class, e -> {
            assertThat(e.candidato().arbitro().id()).isEqualTo(B);
            assertThat(e.candidato().advertencias()).extracting(CausaDto::codigo).containsExactly("RF-022");
        });
    }

    @Test
    @DisplayName("DEC-214: con la misma carga gana el de último partido más antiguo")
    void empateDeCargaPorUltimoPartido() {
        Map<UUID, OffsetDateTime> ultimos = Map.of(A, SABADO.plusDays(7), B, SABADO, C, SABADO.plusHours(2));
        assertThat(elegido(List.of(elegible(A, 2), elegible(B, 2), elegible(C, 2)), ultimos)).isEqualTo(B);
    }

    @Test
    @DisplayName("DEC-214: el último partido se compara como instante, sin importar la zona")
    void ultimoPartidoComoInstante() {
        // 09:00 -08:00 (17:00 Z) es posterior a 10:00 -06:00 (16:00 Z) aunque su hora local sea menor.
        Map<UUID, OffsetDateTime> ultimos = Map.of(A, OffsetDateTime.parse("2032-05-08T09:00:00-08:00"), B, SABADO);
        assertThat(elegido(List.of(elegible(A, 1), elegible(B, 1)), ultimos)).isEqualTo(B);
    }

    @Test
    @DisplayName("DEC-214: nunca designado va antes que cualquiera con designaciones")
    void nuncaDesignadoPrimero() {
        Map<UUID, OffsetDateTime> ultimos = Map.of(A, SABADO.minusYears(5), B, SABADO);
        assertThat(elegido(List.of(elegible(A, 0), elegible(B, 0), elegible(D, 0)), ultimos)).isEqualTo(D);
    }

    @Test
    @DisplayName("DEC-185: en empate total decide el id por texto, no UUID.compareTo")
    void empateTotalPorTexto() {
        assertThat(ALTO.compareTo(A)).as("compareTo pondría primero a ALTO").isNegative();
        assertThat(elegido(List.of(elegible(ALTO, 1), elegible(B, 1), elegible(A, 1)), Map.of())).isEqualTo(A);
    }

    @ParameterizedTest(name = "semilla {0}")
    @ValueSource(longs = {1, 7, 42, 2032})
    @DisplayName("DEC-214: el resultado no depende del orden de entrada (invertida y barajada)")
    void independienteDelOrden(long semilla) {
        List<CandidatoArbitralDto> candidatos = List.of(elegible(A, 2), elegible(B, 1), conAdvertencia(C, 0),
                elegible(D, 1), elegible(ALTO, 1), noElegible(UUID.fromString(
                        "00000000-0000-4000-8000-000000000000"), 0, CHOQUE));
        Map<UUID, OffsetDateTime> ultimos = Map.of(B, SABADO, D, SABADO, A, SABADO.minusDays(7));
        UUID esperado = elegido(candidatos, ultimos);

        List<CandidatoArbitralDto> invertida = new ArrayList<>(candidatos);
        Collections.reverse(invertida);
        List<CandidatoArbitralDto> barajada = new ArrayList<>(candidatos);
        Collections.shuffle(barajada, new Random(semilla));

        assertThat(esperado).as("nunca designado, sin RF-022, carga 1, primero por texto").isEqualTo(ALTO);
        assertThat(elegido(invertida, ultimos)).isEqualTo(esperado);
        assertThat(elegido(barajada, ultimos)).isEqualTo(esperado);
    }

    // ── Causas (DEC-216) ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-216: sin árbitros validados en la liga")
    void sinArbitros() {
        assertThat(causa(List.of())).isEqualTo("No hay árbitros validados en la liga.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("causas")
    @DisplayName("DEC-216: conteos por regla, en orden, con singulares y « y » antes de la última")
    void causasSinCandidato(String caso, List<CandidatoArbitralDto> candidatos, String esperada) {
        assertThat(causa(candidatos)).isEqualTo(esperada);
    }

    static Stream<Arguments> causas() {
        return Stream.of(
                Arguments.of("todos VAL-019",
                        List.of(noElegible(A, 0, MISMO_CLUB), noElegible(B, 0, MISMO_CLUB)),
                        "Ninguno de los 2 árbitros validados puede pitarlo: 2 son de los clubes del partido (VAL-019)."),
                Arguments.of("uno solo, VAL-019",
                        List.of(noElegible(A, 0, MISMO_CLUB)),
                        "El único árbitro validado no puede pitarlo: 1 es de los clubes del partido (VAL-019)."),
                Arguments.of("mezcla en plural",
                        List.of(noElegible(A, 0, YA_DESIGNADO), noElegible(B, 0, CHOQUE), noElegible(C, 0, MISMO_CLUB),
                                noElegible(D, 0, CHOQUE), noElegible(ALTO, 0, MISMO_CLUB),
                                noElegible(UUID.randomUUID(), 0, YA_DESIGNADO)),
                        "Ninguno de los 6 árbitros validados puede pitarlo: 2 son de los clubes del partido (VAL-019), "
                                + "2 ya pitan a esa hora (RF-039) y 2 ya están designados en este partido (YA_DESIGNADO)."),
                Arguments.of("mezcla en singular",
                        List.of(noElegible(A, 0, YA_DESIGNADO), noElegible(B, 0, CHOQUE), noElegible(C, 0, MISMO_CLUB)),
                        "Ninguno de los 3 árbitros validados puede pitarlo: 1 es de los clubes del partido (VAL-019), "
                                + "1 ya pita a esa hora (RF-039) y 1 ya está designado en este partido (YA_DESIGNADO)."),
                Arguments.of("sin VAL-019",
                        List.of(noElegible(A, 0, CHOQUE), noElegible(B, 0, YA_DESIGNADO), noElegible(C, 0, CHOQUE)),
                        "Ninguno de los 3 árbitros validados puede pitarlo: 2 ya pitan a esa hora (RF-039) "
                                + "y 1 ya está designado en este partido (YA_DESIGNADO)."),
                Arguments.of("dos causas cuentan una vez, por la primera",
                        List.of(noElegible(A, 0, CHOQUE, MISMO_CLUB), noElegible(B, 0, YA_DESIGNADO, CHOQUE)),
                        "Ninguno de los 2 árbitros validados puede pitarlo: 1 es de los clubes del partido (VAL-019) "
                                + "y 1 ya pita a esa hora (RF-039)."));
    }

    @Test
    @DisplayName("DEC-216: un candidato con RF-022 no cambia la causa si ninguno es elegible")
    void advertenciaNoCuenta() {
        CandidatoArbitralDto choqueNoDisponible = new CandidatoArbitralDto(arbitro(A), false, List.of(CHOQUE),
                List.of(NO_DISPONIBLE), 0);
        assertThat(causa(List.of(choqueNoDisponible)))
                .isEqualTo("El único árbitro validado no puede pitarlo: 1 ya pita a esa hora (RF-039).");
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private static UUID elegido(List<CandidatoArbitralDto> candidatos, Map<UUID, OffsetDateTime> ultimos) {
        Eleccion eleccion = SelectorArbitroCentral.elegir(candidatos, ultimos);
        assertThat(eleccion).isInstanceOf(Elegido.class);
        return ((Elegido) eleccion).candidato().arbitro().id();
    }

    private static String causa(List<CandidatoArbitralDto> candidatos) {
        Eleccion eleccion = SelectorArbitroCentral.elegir(candidatos, Map.of());
        assertThat(eleccion).isInstanceOf(SinCandidato.class);
        return ((SinCandidato) eleccion).causa();
    }

    private static CandidatoArbitralDto elegible(UUID id, long carga) {
        return new CandidatoArbitralDto(arbitro(id), true, List.of(), List.of(), carga);
    }

    private static CandidatoArbitralDto conAdvertencia(UUID id, long carga) {
        return new CandidatoArbitralDto(arbitro(id), true, List.of(), List.of(NO_DISPONIBLE), carga);
    }

    private static CandidatoArbitralDto noElegible(UUID id, long carga, CausaDto... causas) {
        return new CandidatoArbitralDto(arbitro(id), false, List.of(causas), List.of(), carga);
    }

    private static ArbitroDesignadoDto arbitro(UUID id) {
        return new ArbitroDesignadoDto(id, "Árbitro " + id, new ReferenciaDto(UUID.randomUUID(), "Club"));
    }
}

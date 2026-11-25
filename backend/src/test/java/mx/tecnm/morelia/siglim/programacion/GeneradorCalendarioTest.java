package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Generador del calendario por el método del círculo (H-32.1), sin Spring ni base de datos.
 *
 * <p>Las propiedades recorren de forma exhaustiva N = 2…30 equipos × 1…4 vueltas con
 * {@code @ParameterizedTest} (DEC-183: no se usa jqwik).
 */
class GeneradorCalendarioTest {

    private static final int EQUIPOS_MAXIMOS = 30;

    /** N = 2…30 × vueltas = 1…{@link GeneradorCalendario#VUELTAS_MAXIMAS}. */
    static Stream<Arguments> casos() {
        return IntStream.rangeClosed(2, EQUIPOS_MAXIMOS).boxed()
                .flatMap(n -> IntStream.rangeClosed(1, GeneradorCalendario.VUELTAS_MAXIMAS)
                        .mapToObj(vueltas -> Arguments.of(n, vueltas)));
    }

    private static List<UUID> equipos(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> UUID.nameUUIDFromBytes(("equipo-" + i).getBytes(StandardCharsets.UTF_8)))
                .toList();
    }

    private static Map<Integer, List<Emparejamiento>> porFecha(List<Emparejamiento> calendario) {
        return calendario.stream().collect(Collectors.groupingBy(Emparejamiento::numeroFecha, TreeMap::new,
                Collectors.toList()));
    }

    private static Set<UUID> par(Emparejamiento e) {
        return Set.of(e.equipoLocalId(), e.equipoVisitanteId());
    }

    // --- Propiedades --------------------------------------------------------------------------------

    @ParameterizedTest(name = "N={0}, vueltas={1}")
    @MethodSource("casos")
    @DisplayName("RN-012: en cada fecha cada equipo aparece exactamente una vez, jugando o descansando")
    void cadaEquipoUnaVezPorFecha(int n, int vueltas) {
        List<UUID> equipos = equipos(n);

        porFecha(GeneradorCalendario.generar(equipos, vueltas)).forEach((fecha, renglones) -> {
            List<UUID> presentes = new ArrayList<>();
            for (Emparejamiento e : renglones) {
                presentes.add(e.equipoLocalId());
                if (!e.esDescanso()) {
                    presentes.add(e.equipoVisitanteId());
                }
            }
            assertThat(presentes).as("fecha %d", fecha).containsExactlyInAnyOrderElementsOf(equipos);
        });
    }

    @ParameterizedTest(name = "N={0}, vueltas={1}")
    @MethodSource("casos")
    @DisplayName("DEC-174: cada par se enfrenta `vueltas` veces y cambia de localía en vueltas consecutivas")
    void cadaParSeEnfrentaVueltasVecesAlternandoLocalia(int n, int vueltas) {
        List<UUID> equipos = equipos(n);
        Map<Set<UUID>, List<Emparejamiento>> partidosPorPar = new HashMap<>();
        GeneradorCalendario.generar(equipos, vueltas).stream()
                .filter(e -> !e.esDescanso())
                .forEach(e -> partidosPorPar.computeIfAbsent(par(e), k -> new ArrayList<>()).add(e));

        assertThat(partidosPorPar).hasSize(n * (n - 1) / 2);
        partidosPorPar.forEach((par, partidos) -> {
            assertThat(partidos).as("par %s", par).hasSize(vueltas);
            assertThat(partidos).extracting(Emparejamiento::vuelta).as("par %s", par)
                    .containsExactlyElementsOf(IntStream.rangeClosed(1, vueltas).boxed().toList());
            for (int k = 1; k < partidos.size(); k++) {
                assertThat(partidos.get(k).equipoLocalId()).as("par %s, vuelta %d", par, k + 1)
                        .isEqualTo(partidos.get(k - 1).equipoVisitanteId());
            }
        });
    }

    @ParameterizedTest(name = "N={0}, vueltas={1}")
    @MethodSource("casos")
    @DisplayName("RF-037: con N impar hay un descanso por fecha y cada equipo descansa `vueltas` veces; con N par, ninguno")
    void descansos(int n, int vueltas) {
        List<UUID> equipos = equipos(n);
        List<Emparejamiento> calendario = GeneradorCalendario.generar(equipos, vueltas);

        if (n % 2 == 0) {
            assertThat(calendario).noneMatch(Emparejamiento::esDescanso);
            return;
        }
        porFecha(calendario).forEach((fecha, renglones) ->
                assertThat(renglones).as("fecha %d", fecha).filteredOn(Emparejamiento::esDescanso).hasSize(1));
        Map<UUID, Long> descansosPorEquipo = calendario.stream().filter(Emparejamiento::esDescanso)
                .collect(Collectors.groupingBy(Emparejamiento::equipoLocalId, Collectors.counting()));
        assertThat(descansosPorEquipo).hasSize(n).allSatisfy((equipo, veces) ->
                assertThat(veces).as("descansos de %s", equipo).isEqualTo((long) vueltas));
    }

    @ParameterizedTest(name = "N={0}, vueltas={1}")
    @MethodSource("casos")
    @DisplayName("H-32 (criterio 3) · DEC-184: en cada vuelta, |local − visitante| ≤ 1 por equipo")
    void localiaEquilibradaPorVuelta(int n, int vueltas) {
        List<UUID> equipos = equipos(n);
        List<Emparejamiento> calendario = GeneradorCalendario.generar(equipos, vueltas);

        for (int v = 1; v <= vueltas; v++) {
            int vuelta = v;
            Map<UUID, Integer> saldo = new HashMap<>();
            calendario.stream().filter(e -> e.vuelta() == vuelta && !e.esDescanso()).forEach(e -> {
                saldo.merge(e.equipoLocalId(), 1, Integer::sum);
                saldo.merge(e.equipoVisitanteId(), -1, Integer::sum);
            });
            assertThat(saldo).hasSize(n).allSatisfy((equipo, diferencia) ->
                    assertThat(Math.abs(diferencia)).as("vuelta %d, equipo %s", vuelta, equipo).isLessThanOrEqualTo(1));
        }
    }

    @ParameterizedTest(name = "N={0}, vueltas={1}")
    @MethodSource("casos")
    @DisplayName("DEC-174: fechas 1…vueltas·fechasPorVuelta sin huecos, N/2 partidos por fecha y vueltas pares en espejo")
    void fechasYEspejo(int n, int vueltas) {
        List<Emparejamiento> calendario = GeneradorCalendario.generar(equipos(n), vueltas);
        int porVuelta = GeneradorCalendario.fechasPorVuelta(n);
        Map<Integer, List<Emparejamiento>> fechas = porFecha(calendario);

        assertThat(fechas.keySet())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, vueltas * porVuelta).boxed().toList());
        fechas.forEach((fecha, renglones) -> {
            assertThat(renglones).as("fecha %d", fecha).filteredOn(e -> !e.esDescanso()).hasSize(n / 2);
            assertThat(renglones).extracting(Emparejamiento::vuelta).as("fecha %d", fecha)
                    .containsOnly((fecha - 1) / porVuelta + 1);
        });
        assertThat(calendario).extracting(Emparejamiento::numeroFecha).isSorted();

        for (int v = 2; v <= vueltas; v += 2) {
            for (int r = 1; r <= porVuelta; r++) {
                List<Emparejamiento> anterior = fechas.get((v - 2) * porVuelta + r);
                List<Emparejamiento> espejo = fechas.get((v - 1) * porVuelta + r);
                assertThat(espejo).hasSameSizeAs(anterior);
                for (int i = 0; i < anterior.size(); i++) {
                    Emparejamiento a = anterior.get(i);
                    Emparejamiento b = espejo.get(i);
                    if (a.esDescanso()) {
                        assertThat(b.esDescanso()).isTrue();
                        assertThat(b.equipoLocalId()).isEqualTo(a.equipoLocalId());
                    } else {
                        assertThat(b.equipoLocalId()).isEqualTo(a.equipoVisitanteId());
                        assertThat(b.equipoVisitanteId()).isEqualTo(a.equipoLocalId());
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "N={0}, vueltas={1}")
    @MethodSource("casos")
    @DisplayName("DEC-185: la misma entrada en cualquier orden da el mismo calendario")
    void determinista(int n, int vueltas) {
        List<UUID> equipos = equipos(n);
        List<Emparejamiento> esperado = GeneradorCalendario.generar(equipos, vueltas);

        assertThat(GeneradorCalendario.generar(equipos, vueltas)).isEqualTo(esperado);
        for (long semilla : new long[] {1L, 7L, 42L, 2026L, 987_654_321L}) {
            List<UUID> barajados = new ArrayList<>(equipos);
            Collections.shuffle(barajados, new Random(semilla));
            assertThat(GeneradorCalendario.generar(barajados, vueltas)).as("semilla %d", semilla).isEqualTo(esperado);
        }
    }

    @Test
    @DisplayName("DEC-174: una vuelta ocupa N − 1 fechas con N par y N con N impar")
    void fechasPorVuelta() {
        assertThat(GeneradorCalendario.fechasPorVuelta(2)).isEqualTo(1);
        assertThat(GeneradorCalendario.fechasPorVuelta(4)).isEqualTo(3);
        assertThat(GeneradorCalendario.fechasPorVuelta(5)).isEqualTo(5);
        assertThat(GeneradorCalendario.fechasPorVuelta(30)).isEqualTo(29);
    }

    // --- Casos dorados ------------------------------------------------------------------------------

    // Orden de texto e0 < e1 < … ; e3 y e4 empiezan con 8 y 9, así que UUID.compareTo (con signo) los
    // pondría antes que e0: si el generador usara compareTo, estos casos fallarían (DEC-185).
    private static final UUID E0 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID E1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID E2 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID E3 = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID E4 = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static Emparejamiento p(int fecha, UUID local, UUID visitante) {
        return new Emparejamiento(1, fecha, local, visitante);
    }

    private static Emparejamiento descansa(int fecha, UUID equipo) {
        return new Emparejamiento(1, fecha, equipo, null);
    }

    @Test
    @DisplayName("DEC-184 · DEC-185: N = 4, una vuelta, calendario exacto (orden por texto del UUID)")
    void doradoCuatroEquipos() {
        assertThat(E3.compareTo(E0)).as("compareTo daría otro orden").isNegative();

        List<Emparejamiento> calendario = GeneradorCalendario.generar(List.of(E3, E1, E0, E2), 1);

        assertThat(calendario).containsExactly(
                p(1, E0, E3), p(1, E1, E2),
                p(2, E2, E0), p(2, E3, E1),
                p(3, E0, E1), p(3, E2, E3));
    }

    @Test
    @DisplayName("RF-037 · DEC-184: N = 5, una vuelta, descanso fijo y calendario exacto")
    void doradoCincoEquipos() {
        List<Emparejamiento> calendario = GeneradorCalendario.generar(List.of(E4, E2, E0, E3, E1), 1);

        assertThat(calendario).containsExactly(
                descansa(1, E4), p(1, E0, E3), p(1, E2, E1),
                descansa(2, E3), p(2, E4, E2), p(2, E1, E0),
                descansa(3, E2), p(3, E3, E1), p(3, E0, E4),
                descansa(4, E1), p(4, E2, E0), p(4, E4, E3),
                descansa(5, E0), p(5, E1, E4), p(5, E3, E2));
    }

    @Test
    @DisplayName("DEC-174: el resultado es inmutable")
    void resultadoInmutable() {
        List<Emparejamiento> calendario = GeneradorCalendario.generar(List.of(E0, E1), 1);

        assertThatThrownBy(() -> calendario.add(p(2, E0, E1))).isInstanceOf(UnsupportedOperationException.class);
    }

    // --- Validaciones -------------------------------------------------------------------------------

    @Test
    @DisplayName("H-32.1: sin equipos, con uno solo o con la lista nula se rechaza")
    void rechazaMenosDeDosEquipos() {
        assertThatThrownBy(() -> GeneradorCalendario.generar(List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos 2 equipos");
        assertThatThrownBy(() -> GeneradorCalendario.generar(List.of(E0), 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos 2 equipos");
        assertThatThrownBy(() -> GeneradorCalendario.generar(null, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatoria");
    }

    @Test
    @DisplayName("H-32.1: un equipo nulo o repetido se rechaza")
    void rechazaNuloORepetido() {
        assertThatThrownBy(() -> GeneradorCalendario.generar(Arrays.asList(E0, null, E1), 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vacíos");
        assertThatThrownBy(() -> GeneradorCalendario.generar(List.of(E0, E1, E0), 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repetido");
    }

    @ParameterizedTest(name = "vueltas={0}")
    @ValueSource(ints = {0, 5})
    @DisplayName("DEC-174: las vueltas fuera de 1…4 se rechazan (ck_parametros_vueltas)")
    void rechazaVueltasFueraDeRango(int vueltas) {
        assertThatThrownBy(() -> GeneradorCalendario.generar(List.of(E0, E1), vueltas))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 1 y 4");
    }

    @Test
    @DisplayName("RF-037: el renglón de calendario valida sus datos")
    void emparejamientoValida() {
        assertThat(new Emparejamiento(1, 1, E0, null).esDescanso()).isTrue();
        assertThat(new Emparejamiento(1, 1, E0, E1).esDescanso()).isFalse();
        assertThatThrownBy(() -> new Emparejamiento(0, 1, E0, E1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Emparejamiento(1, 0, E0, E1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Emparejamiento(1, 1, null, E1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Emparejamiento(1, 1, E0, E0)).isInstanceOf(IllegalArgumentException.class);
    }
}

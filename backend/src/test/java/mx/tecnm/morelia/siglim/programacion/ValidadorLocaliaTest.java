package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.EquipoDeLiga;
import mx.tecnm.morelia.siglim.programacion.LocaliaRepository.CanchaLocalia;
import mx.tecnm.morelia.siglim.programacion.LocaliaRepository.FechaDeJornada;
import mx.tecnm.morelia.siglim.programacion.ValidadorLocalia.Advertencia;
import mx.tecnm.morelia.siglim.programacion.ValidadorLocalia.Regla;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** B-15.1: reglas de localía de {@link ValidadorLocalia} (RF-120, VAL-027, RN-049, DEC-34). */
class ValidadorLocaliaTest {

    private static final UUID GRUPO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID JORNADA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CLUB = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final EquipoDeLiga LOCAL = new EquipoDeLiga(
            UUID.fromString("44444444-4444-4444-8444-444444444444"), "Halcones", GRUPO, CLUB, "ACTIVO");
    private static final CanchaLocalia UNIDAD = new CanchaLocalia(
            UUID.fromString("55555555-5555-4555-8555-555555555555"), "Morelos 2", true);
    private static final CanchaLocalia DEL_CLUB = new CanchaLocalia(
            UUID.fromString("66666666-6666-4666-8666-666666666666"), "Campo Halcones", false);
    private static final CanchaLocalia EXTERNA = new CanchaLocalia(
            UUID.fromString("77777777-7777-4777-8777-777777777777"), "Deportivo Tres Puentes", false);

    private final LocaliaRepository repositorio = mock(LocaliaRepository.class);
    private final ValidadorLocalia validador = new ValidadorLocalia(repositorio);

    private List<Advertencia> evaluar(CanchaLocalia cancha, List<CanchaLocalia> delClub, FechaDeJornada fecha) {
        given(repositorio.buscarCancha(cancha.id())).willReturn(Optional.of(cancha));
        given(repositorio.canchasDelClub(CLUB, GRUPO, LOCAL.id())).willReturn(delClub);
        given(repositorio.buscarFecha(JORNADA)).willReturn(Optional.ofNullable(fecha));
        return validador.evaluar(GRUPO, JORNADA, LOCAL, cancha.id());
    }

    private static FechaDeJornada regular(int numero) {
        return new FechaDeJornada(numero, true, 18, 10);
    }

    @Test
    @DisplayName("DEC-34: el local con cancha programado en otra recibe la advertencia que nombra ambas")
    void localConCanchaEnOtra() {
        List<Advertencia> advertencias = evaluar(UNIDAD, List.of(DEL_CLUB), regular(1));

        assertThat(advertencias).singleElement().satisfies(advertencia -> {
            assertThat(advertencia.regla()).isEqualTo(Regla.CANCHA_DEL_LOCAL);
            assertThat(advertencia.mensaje()).contains("«Halcones»", "«Campo Halcones»", "«Morelos 2»");
        });
    }

    @Test
    @DisplayName("DEC-34: el local que juega en su cancha o que no tiene cancha no recibe advertencia")
    void localEnSuCanchaOSinCancha() {
        assertThat(evaluar(DEL_CLUB, List.of(DEL_CLUB), regular(1))).isEmpty();
        assertThat(evaluar(UNIDAD, List.of(), regular(1))).isEmpty();
    }

    @Test
    @DisplayName("RN-049: cancha externa en las últimas 10 fechas de la fase regular se advierte como no oficial")
    void canchaExternaEnLasUltimasFechas() {
        List<Advertencia> advertencias = evaluar(EXTERNA, List.of(), regular(9));

        assertThat(advertencias).singleElement().satisfies(advertencia -> {
            assertThat(advertencia.regla()).isEqualTo(Regla.UNIDAD_DEPORTIVA);
            assertThat(advertencia.mensaje()).contains("jornada 9", "últimas 10", "de 18",
                    "«Deportivo Tres Puentes»", "no oficial");
        });
    }

    @Test
    @DisplayName("RN-049: antes de las últimas fechas, en la unidad deportiva o sin total definido no se advierte")
    void sinAdvertenciaDeUnidadDeportiva() {
        assertThat(evaluar(EXTERNA, List.of(), regular(8))).as("18 − 10 = 8 no es de las últimas").isEmpty();
        assertThat(evaluar(UNIDAD, List.of(), regular(18))).as("en la unidad deportiva").isEmpty();
        assertThat(evaluar(EXTERNA, List.of(), new FechaDeJornada(18, true, null, 10)))
                .as("sin total de fechas").isEmpty();
        assertThat(evaluar(EXTERNA, List.of(), new FechaDeJornada(18, true, 18, 0)))
                .as("con la regla desactivada").isEmpty();
        assertThat(evaluar(EXTERNA, List.of(), new FechaDeJornada(1, false, 18, 10)))
                .as("en fase final").isEmpty();
        assertThat(evaluar(EXTERNA, List.of(), new FechaDeJornada(18, true, null, null)))
                .as("sin parámetros").isEmpty();
    }

    @Test
    @DisplayName("VAL-027: las dos reglas se advierten juntas cuando ambas se incumplen")
    void ambasReglas() {
        assertThat(evaluar(EXTERNA, List.of(DEL_CLUB), regular(10)))
                .extracting(Advertencia::regla)
                .containsExactly(Regla.CANCHA_DEL_LOCAL, Regla.UNIDAD_DEPORTIVA);
    }
}

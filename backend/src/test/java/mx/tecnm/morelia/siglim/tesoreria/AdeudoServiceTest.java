package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.tesoreria.CobroRepository.FilaAdeudo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Agrupación y sumas de {@link AdeudoService}. Sin Spring ni base de datos. */
class AdeudoServiceTest {

    private static final String CONSULTAR = "tesoreria.cobro:CONSULTAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID CLUB_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID EQUIPO_1 = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID EQUIPO_2 = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private final CobroRepository cobros = mock(CobroRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AdeudoService servicio = new AdeudoService(cobros, autorizacion,
            new SiglimPropiedades(null, null, null, LIGA));

    private static FilaAdeudo fila(UUID club, String clubNombre, UUID equipo, String equipoNombre,
            String pendiente, String vencido, long cobros) {
        BigDecimal pendienteBd = new BigDecimal(pendiente);
        BigDecimal vencidoBd = new BigDecimal(vencido);
        return new FilaAdeudo(club, clubNombre, equipo, equipoNombre, pendienteBd, vencidoBd,
                pendienteBd.add(vencidoBd), cobros);
    }

    @Test
    @DisplayName("RF-140: suma adeudos por club y equipo, solo PENDIENTE y VENCIDO, con dos decimales")
    void sumaPorClubYEquipo() {
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(true, Set.of(), false);
        given(autorizacion.alcance(CONSULTAR)).willReturn(alcance);
        given(cobros.adeudos(LIGA, null, alcance)).willReturn(List.of(
                fila(CLUB_A, "Halcones", null, null, "100.00", "0.00", 1),
                fila(CLUB_A, "Halcones", EQUIPO_1, "Halcones A", "200.00", "50.00", 2),
                fila(CLUB_A, "Halcones", EQUIPO_2, "Halcones B", "0.00", "75.50", 1),
                fila(CLUB_B, "Toros", null, null, "0.00", "400.00", 1)));

        AdeudosDto adeudos = servicio.consultar(null);

        assertThat(adeudos.total()).isEqualByComparingTo("825.50").hasScaleOf(2);
        assertThat(adeudos.pendiente()).isEqualByComparingTo("300.00");
        assertThat(adeudos.vencido()).isEqualByComparingTo("525.50");
        assertThat(adeudos.cobros()).isEqualTo(5);

        assertThat(adeudos.clubes()).extracting(club -> club.club().nombre())
                .containsExactly("Halcones", "Toros");

        AdeudosDto.ClubAdeudoDto clubA = adeudos.clubes().getFirst();
        assertThat(clubA.club().id()).isEqualTo(CLUB_A);
        assertThat(clubA.total()).isEqualByComparingTo("425.50");
        assertThat(clubA.pendiente()).isEqualByComparingTo("300.00");
        assertThat(clubA.vencido()).isEqualByComparingTo("125.50");
        assertThat(clubA.cobros()).isEqualTo(4);
        assertThat(clubA.equipos()).extracting(equipo -> equipo.equipo().nombre())
                .containsExactly("Halcones A", "Halcones B");
        assertThat(clubA.equipos().getFirst().total()).isEqualByComparingTo("250.00");
        assertThat(clubA.equipos().getLast().vencido()).isEqualByComparingTo("75.50");

        AdeudosDto.ClubAdeudoDto clubB = adeudos.clubes().getLast();
        assertThat(clubB.total()).isEqualByComparingTo("400.00");
        assertThat(clubB.pendiente()).isEqualByComparingTo("0.00");
        assertThat(clubB.equipos()).isEmpty();

        verify(autorizacion).alcance(CONSULTAR);
        verify(cobros).adeudos(LIGA, null, alcance);
    }

    @Test
    @DisplayName("RF-140: sin adeudos responde ceros con dos decimales y lista vacía")
    void sinAdeudos() {
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(false, Set.of(CLUB_A), false);
        given(autorizacion.alcance(CONSULTAR)).willReturn(alcance);
        given(cobros.adeudos(LIGA, CLUB_A, alcance)).willReturn(List.of());

        AdeudosDto adeudos = servicio.consultar(CLUB_A);

        assertThat(adeudos.total()).isEqualByComparingTo("0.00").hasScaleOf(2);
        assertThat(adeudos.pendiente()).isEqualByComparingTo("0.00").hasScaleOf(2);
        assertThat(adeudos.vencido()).isEqualByComparingTo("0.00").hasScaleOf(2);
        assertThat(adeudos.cobros()).isZero();
        assertThat(adeudos.clubes()).isEmpty();
        verify(cobros).adeudos(LIGA, CLUB_A, alcance);
    }

    @Test
    @DisplayName("RF-140: dos filas del mismo club y equipo se acumulan en una sola entrada")
    void acumulaFilasRepetidas() {
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(true, Set.of(), false);
        given(autorizacion.alcance(CONSULTAR)).willReturn(alcance);
        given(cobros.adeudos(LIGA, null, alcance)).willReturn(List.of(
                fila(CLUB_A, "Halcones", EQUIPO_1, "Halcones A", "10.00", "0.00", 1),
                fila(CLUB_A, "Halcones", EQUIPO_1, "Halcones A", "0.00", "5.50", 1)));

        AdeudosDto adeudos = servicio.consultar(null);

        assertThat(adeudos.clubes()).hasSize(1);
        assertThat(adeudos.clubes().getFirst().equipos()).hasSize(1);
        assertThat(adeudos.clubes().getFirst().equipos().getFirst().total()).isEqualByComparingTo("15.50");
        assertThat(adeudos.clubes().getFirst().cobros()).isEqualTo(2);
    }
}

package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.CanchaAsignable;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.PeriodoCancha;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.TipoPeriodo;
import mx.tecnm.morelia.siglim.programacion.VentanasDeCancha.Intervalo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Ventanas de juego de una cancha e intervalos semiabiertos (H-33.1, DEC-176), sin Spring. */
class VentanasDeCanchaTest {

    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDate DOMINGO = LocalDate.of(2026, 12, 13);
    private static final LocalTime OCHO = LocalTime.of(8, 0);
    private static final LocalTime DIECIOCHO = LocalTime.of(18, 0);

    private static Instant a(LocalDate dia, int hora) {
        return dia.atTime(hora, 0).atZone(ZONA).toInstant();
    }

    private static Intervalo de(int desde, int hasta) {
        return new Intervalo(a(DOMINGO, desde), a(DOMINGO, hasta));
    }

    private static PeriodoCancha periodo(TipoPeriodo tipo, LocalDate dia, int desde, LocalDate diaFin, int hasta) {
        return new PeriodoCancha(tipo, dia.atTime(desde, 0).atZone(ZONA).toOffsetDateTime(),
                diaFin.atTime(hasta, 0).atZone(ZONA).toOffsetDateTime());
    }

    private static CanchaAsignable cancha(boolean activa, PeriodoCancha... periodos) {
        return new CanchaAsignable(UUID.nameUUIDFromBytes("cancha".getBytes(StandardCharsets.UTF_8)),
                "Cancha 1", activa, false, List.of(periodos));
    }

    @Test
    @DisplayName("DEC-176: sin periodos, la ventana es el horario de juego por defecto")
    void sinPeriodosUsaElHorarioPorDefecto() {
        assertThat(VentanasDeCancha.ventanas(cancha(true), DOMINGO, OCHO, DIECIOCHO, ZONA))
                .containsExactly(de(8, 18));
    }

    @Test
    @DisplayName("DEC-176: con periodos DISPONIBLE del día se usan esos, recortados al día y fusionados")
    void conDisponibleUsaSoloEsos() {
        CanchaAsignable cancha = cancha(true,
                periodo(TipoPeriodo.DISPONIBLE, DOMINGO, 12, DOMINGO, 15),
                periodo(TipoPeriodo.DISPONIBLE, DOMINGO, 14, DOMINGO, 16),
                periodo(TipoPeriodo.DISPONIBLE, DOMINGO, 20, DOMINGO.plusDays(1), 2));

        assertThat(VentanasDeCancha.ventanas(cancha, DOMINGO, OCHO, DIECIOCHO, ZONA))
                .containsExactly(de(12, 16), new Intervalo(a(DOMINGO, 20), a(DOMINGO.plusDays(1), 0)));
    }

    @Test
    @DisplayName("DEC-176: un DISPONIBLE de otro día no cambia el horario por defecto de este")
    void disponibleDeOtroDiaNoCuenta() {
        CanchaAsignable cancha = cancha(true, periodo(TipoPeriodo.DISPONIBLE, DOMINGO.minusDays(1), 9,
                DOMINGO.minusDays(1), 12));

        assertThat(VentanasDeCancha.ventanas(cancha, DOMINGO, OCHO, DIECIOCHO, ZONA))
                .containsExactly(de(8, 18));
    }

    @Test
    @DisplayName("VAL-005, DEC-176: bloqueo y mantenimiento parten la ventana")
    void bloqueoYMantenimientoPartenLaVentana() {
        CanchaAsignable cancha = cancha(true,
                periodo(TipoPeriodo.BLOQUEO, DOMINGO, 10, DOMINGO, 11),
                periodo(TipoPeriodo.MANTENIMIENTO, DOMINGO, 15, DOMINGO.plusDays(2), 0));

        assertThat(VentanasDeCancha.ventanas(cancha, DOMINGO, OCHO, DIECIOCHO, ZONA))
                .containsExactly(de(8, 10), de(11, 15));
    }

    @Test
    @DisplayName("VAL-005: una cancha inactiva no tiene ventanas")
    void canchaInactivaSinVentanas() {
        assertThat(VentanasDeCancha.ventanas(cancha(false), DOMINGO, OCHO, DIECIOCHO, ZONA)).isEmpty();
    }

    @Test
    @DisplayName("Intervalos semiabiertos: lo que solo se toca no se resta ni se traslapa")
    void intervalosSemiabiertos() {
        assertThat(de(8, 10).traslapa(de(10, 12))).isFalse();
        assertThat(de(8, 10).traslapa(de(9, 12))).isTrue();
        assertThat(VentanasDeCancha.restar(List.of(de(8, 18)), List.of(de(6, 8), de(18, 20))))
                .containsExactly(de(8, 18));
        assertThat(VentanasDeCancha.restar(List.of(de(8, 18)), List.of(de(7, 19)))).isEmpty();
    }

    @Test
    @DisplayName("RF-042: las franjas cuentan bloques completos por intervalo, sin juntar intervalos")
    void franjasCuentanBloquesCompletos() {
        assertThat(VentanasDeCancha.franjas(List.of(de(8, 18)), 120)).isEqualTo(5);
        assertThat(VentanasDeCancha.franjas(List.of(de(8, 11), de(11, 12)), 120)).isEqualTo(1);
        assertThat(VentanasDeCancha.franjas(List.of(), 120)).isZero();
    }
}

package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Días de cada fecha del calendario (H-32, DEC-176, DEC-186), sin Spring ni base de datos. */
class DiasDeFechaTest {

    private static LocalDate d(String fecha) {
        return LocalDate.parse(fecha);
    }

    @Test
    @DisplayName("DEC-186: si el inicio es sábado, la fecha 1 es ese sábado y el domingo siguiente")
    void inicioEnSabado() {
        assertThat(DiasDeFecha.dias(d("2026-11-28"), 1)).containsExactly(d("2026-11-28"), d("2026-11-29"));
    }

    @Test
    @DisplayName("DEC-186: si el inicio es miércoles, la fecha 1 es el sábado siguiente")
    void inicioEnMiercoles() {
        assertThat(DiasDeFecha.dias(d("2026-11-25"), 1)).containsExactly(d("2026-11-28"), d("2026-11-29"));
    }

    @Test
    @DisplayName("DEC-186: si el inicio es domingo, la fecha 1 es el sábado siguiente, no el día anterior")
    void inicioEnDomingo() {
        assertThat(DiasDeFecha.dias(d("2026-11-29"), 1)).containsExactly(d("2026-12-05"), d("2026-12-06"));
    }

    @Test
    @DisplayName("DEC-186: la fecha 3 cae 14 días después de la fecha 1")
    void fechaTres() {
        assertThat(DiasDeFecha.dias(d("2026-11-25"), 3)).containsExactly(d("2026-12-12"), d("2026-12-13"));
    }

    @Test
    @DisplayName("DEC-186: las fechas cruzan de diciembre a enero sin saltos")
    void cruceDeAnio() {
        assertThat(DiasDeFecha.dias(d("2026-12-26"), 2)).containsExactly(d("2027-01-02"), d("2027-01-03"));
        assertThat(DiasDeFecha.dias(d("2026-12-30"), 1)).containsExactly(d("2027-01-02"), d("2027-01-03"));
    }

    @Test
    @DisplayName("DEC-186: el número de fecha 0 o un inicio nulo se rechazan")
    void rechazaDatosInvalidos() {
        assertThatThrownBy(() -> DiasDeFecha.dias(d("2026-11-28"), 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 o mayor");
        assertThatThrownBy(() -> DiasDeFecha.dias(null, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatoria");
    }
}

package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Enmascarado de la CURP de convenciones de API §9.2 (DEC-110, [PROPUESTA]). */
class EnmascaradoCurpTest {

    @Test
    @DisplayName("DEC-110 · RNF-012: conserva los caracteres 1–4, 11–13 y 17–18 y enmascara 5–10 y 14–16")
    void enmascaraUnaCurp() {
        assertThat(EnmascaradoCurp.enmascarar("GAPA750101HMNRRS05")).isEqualTo("GAPA******HMN***05");
    }

    @Test
    @DisplayName("DEC-110: ignora los espacios de los extremos (columna char)")
    void ignoraEspacios() {
        assertThat(EnmascaradoCurp.enmascarar(" GAPA750101HMNRRS05  ")).isEqualTo("GAPA******HMN***05");
    }

    @Test
    @DisplayName("DEC-110: con otra longitud deja los 4 primeros caracteres y enmascara el resto")
    void otraLongitud() {
        assertThat(EnmascaradoCurp.enmascarar("GAPA750101HMN")).isEqualTo("GAPA*********");
        assertThat(EnmascaradoCurp.enmascarar("GAPA750101HMNRRS05X")).isEqualTo("GAPA***************");
        assertThat(EnmascaradoCurp.enmascarar("GAP")).isEqualTo("GAP");
    }

    @Test
    @DisplayName("DEC-110: sin CURP devuelve null")
    void sinCurp() {
        assertThat(EnmascaradoCurp.enmascarar(null)).isNull();
    }
}

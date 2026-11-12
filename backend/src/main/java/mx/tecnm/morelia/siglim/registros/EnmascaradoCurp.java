package mx.tecnm.morelia.siglim.registros;

/**
 * Enmascarado de la CURP para los listados y para quien no puede verla completa (convenciones de API
 * §9.2, DEC-110). <b>[PROPUESTA]</b>: el ERS no fija el formato.
 *
 * <p>De una CURP de 18 caracteres se conservan los caracteres 1–4 (iniciales), 11–13 (sexo y entidad)
 * y 17–18 (homoclave y dígito verificador); los caracteres 5–10 (fecha de nacimiento) y 14–16
 * (consonantes internas) se cambian por {@code *}: {@code GAPA750101HMNRRS05} →
 * {@code GAPA******HMN***05}. Un valor de otra longitud conserva sus 4 primeros caracteres y enmascara
 * el resto, para no exponer un dato mal capturado.
 *
 * <p>Refs: H-10.5, RNF-012, DEC-110
 */
final class EnmascaradoCurp {

    private static final int LONGITUD_CURP = 18;
    private static final int VISIBLES_AL_INICIO = 4;

    private EnmascaradoCurp() {
    }

    /**
     * @param curp CURP tal como está en la base; los espacios de los extremos se ignoran
     * @return la CURP enmascarada, o {@code null} si no hay CURP
     */
    static String enmascarar(String curp) {
        if (curp == null) {
            return null;
        }
        String valor = curp.strip();
        StringBuilder enmascarada = new StringBuilder(valor.length());
        for (int i = 0; i < valor.length(); i++) {
            enmascarada.append(visible(i, valor.length()) ? valor.charAt(i) : '*');
        }
        return enmascarada.toString();
    }

    /** Si el carácter en la posición {@code i} (desde 0) se conserva. */
    private static boolean visible(int i, int longitud) {
        if (i < VISIBLES_AL_INICIO) {
            return true;
        }
        if (longitud != LONGITUD_CURP) {
            return false;
        }
        return (i >= 10 && i <= 12) || i >= 16;
    }
}

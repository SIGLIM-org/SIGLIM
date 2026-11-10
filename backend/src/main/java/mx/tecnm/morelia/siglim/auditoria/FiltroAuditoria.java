package mx.tecnm.morelia.siglim.auditoria;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros, página y orden de {@code GET /api/v1/auditoria} (convenciones de API §4 y §6.1).
 *
 * @param perfilId perfil que ejecutó la acción; {@code null} para todos
 * @param modulos  módulos {@code M-xx}; vacía para todos
 * @param acciones acciones; vacía para todas
 * @param desde    fecha inicial, incluida; {@code null} sin límite
 * @param hasta    fecha final, excluida; {@code null} sin límite
 * @param pagina   página, desde 0
 * @param tamano   filas por página, de 1 a 100
 * @param orden    {@code ocurridoEn,asc} u {@code ocurridoEn,desc}; sin dirección, {@code asc}
 */
public record FiltroAuditoria(UUID perfilId, List<String> modulos, List<String> acciones, OffsetDateTime desde,
                              OffsetDateTime hasta, int pagina, int tamano, String orden) {

    private static final int TAMANO_MAXIMO = 100;
    private static final Pattern MODULO = Pattern.compile("^M-[0-9]{2}$");
    private static final Pattern ACCION = Pattern.compile("^[A-Z][A-Z0-9_]{0,79}$");
    private static final String CAMPO_ORDEN = "ocurridoEn";
    private static final String ORDEN_ASCENDENTE = "a.ocurrido_en ASC, a.id ASC";
    private static final String ORDEN_DESCENDENTE = "a.ocurrido_en DESC, a.id DESC";

    public FiltroAuditoria {
        modulos = modulos == null ? List.of() : List.copyOf(modulos);
        acciones = acciones == null ? List.of() : List.copyOf(acciones);
    }

    /** Parte un parámetro separado por comas (convenciones de API §4) y descarta los valores vacíos. */
    static List<String> separarValores(String valores) {
        if (valores == null) {
            return List.of();
        }
        return Arrays.stream(valores.split(",")).map(String::strip).filter(valor -> !valor.isEmpty()).toList();
    }

    /** @throws ReglaNegocioException 400 {@code VALIDACION} si la página, un filtro o el orden no son válidos */
    void validar() {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw invalido("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        for (String modulo : modulos) {
            if (!MODULO.matcher(modulo).matches()) {
                throw invalido("El módulo «" + modulo + "» no es válido. Usa el formato M-xx, por ejemplo M-08.");
            }
        }
        for (String accion : acciones) {
            if (!ACCION.matcher(accion).matches()) {
                throw invalido("La acción «" + accion + "» no es válida. Usa mayúsculas, dígitos y guion bajo, "
                        + "hasta 80 caracteres.");
            }
        }
        if (desde != null && hasta != null && !desde.isBefore(hasta)) {
            throw invalido("La fecha «desde» debe ser anterior a «hasta».");
        }
        ordenSql();
    }

    long desplazamiento() {
        return (long) pagina * tamano;
    }

    /** Expresión de {@code ORDER BY} armada solo con constantes; el id desempata en la misma dirección. */
    String ordenSql() {
        String[] partes = orden.split(",", -1);
        if (partes.length <= 2 && CAMPO_ORDEN.equals(partes[0].strip())) {
            String direccion = partes.length == 1 ? "asc" : partes[1].strip();
            if ("desc".equalsIgnoreCase(direccion)) {
                return ORDEN_DESCENDENTE;
            }
            if ("asc".equalsIgnoreCase(direccion)) {
                return ORDEN_ASCENDENTE;
            }
        }
        throw invalido("El orden «" + orden + "» no es válido. Usa ocurridoEn,asc u ocurridoEn,desc.");
    }

    private static ReglaNegocioException invalido(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}

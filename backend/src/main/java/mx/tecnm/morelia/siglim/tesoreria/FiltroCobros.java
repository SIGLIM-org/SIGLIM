package mx.tecnm.morelia.siglim.tesoreria;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros, página y orden de {@code GET /api/v1/cobros} (convenciones de API §4).
 *
 * <p>{@code estados} admite uno o varios estados separados por coma; vacío significa todos.
 * {@code desde} (incluido) y {@code hasta} (excluido) filtran por {@code fechaLimite}, es decir, por
 * el periodo en que el cobro vence.
 *
 * <p>Refs: H-29.1, RF-140, UI-027
 *
 * @param clubId  club; {@code null} para todos los del ámbito
 * @param equipoId equipo; {@code null} para todos
 * @param tipo    tipo de cobro; {@code null} para todos
 * @param estados estados; vacío para todos
 * @param desde   fecha límite desde la que se busca, incluida; {@code null} sin límite
 * @param hasta   fecha límite hasta la que se busca, excluida; {@code null} sin límite
 * @param pagina  página, desde 0
 * @param tamano  filas por página, de 1 a 100
 * @param orden   {@code campo,asc|desc}, repetible como {@code fechaLimite,asc,monto,desc}
 */
public record FiltroCobros(
        UUID clubId,
        UUID equipoId,
        TipoCobro tipo,
        List<EstadoCobro> estados,
        LocalDate desde,
        LocalDate hasta,
        int pagina,
        int tamano,
        String orden) {

    private static final int TAMANO_MAXIMO = 100;
    private static final String ORDEN_PREDETERMINADO = "creadoEn,desc";

    public FiltroCobros {
        estados = estados == null ? List.of() : List.copyOf(estados);
        orden = orden == null || orden.isBlank() ? ORDEN_PREDETERMINADO : orden;
    }

    /** Campos que se pueden ordenar. La columna es una constante: nunca se concatena la entrada. */
    private enum CampoOrden {
        CREADO_EN("creadoEn", "c.creado_en"),
        FECHA_LIMITE("fechaLimite", "c.fecha_limite"),
        MONTO("monto", "c.monto");

        private final String nombre;
        private final String columna;

        CampoOrden(String nombre, String columna) {
            this.nombre = nombre;
            this.columna = columna;
        }

        static CampoOrden de(String nombre) {
            for (CampoOrden campo : values()) {
                if (campo.nombre.equals(nombre)) {
                    return campo;
                }
            }
            throw invalido("No se puede ordenar por «" + nombre + "». Usa creadoEn, fechaLimite o monto.");
        }
    }

    /**
     * @throws ReglaNegocioException 400 {@code VALIDACION} si la página, el tamaño, el orden o el
     *                               rango de fechas no son válidos
     */
    void validar() {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw invalido("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        if (desde != null && hasta != null && !desde.isBefore(hasta)) {
            throw invalido("La fecha desde debe ser anterior a la fecha hasta.");
        }
        ordenSql();
    }

    long desplazamiento() {
        return (long) pagina * tamano;
    }

    /** Expresión de {@code ORDER BY} armada solo con constantes; el id da un orden estable. */
    String ordenSql() {
        List<String> criterios = new ArrayList<>();
        String[] partes = orden.split(",");
        for (int i = 0; i < partes.length; i++) {
            CampoOrden campo = CampoOrden.de(partes[i].strip());
            String direccion = "ASC";
            if (i + 1 < partes.length && esDireccion(partes[i + 1])) {
                direccion = partes[++i].strip().toUpperCase(Locale.ROOT);
            }
            criterios.add(campo.columna + " " + direccion);
        }
        criterios.add("c.id ASC");
        return String.join(", ", criterios);
    }

    private static boolean esDireccion(String parte) {
        String texto = parte.strip();
        return "asc".equalsIgnoreCase(texto) || "desc".equalsIgnoreCase(texto);
    }

    private static ReglaNegocioException invalido(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}

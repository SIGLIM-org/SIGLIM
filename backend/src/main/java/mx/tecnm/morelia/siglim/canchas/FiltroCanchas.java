package mx.tecnm.morelia.siglim.canchas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros, página y orden de {@code GET /api/v1/canchas} (convenciones de API §4).
 *
 * @param activa      solo activas o inactivas; {@code null} para todas
 * @param ubicacion   ubicación; {@code null} para todas
 * @param propietario propietario; {@code null} para todos
 * @param sedeId      sede (RF-028); {@code null} para todas. Una sede inexistente o de otra liga no es
 *                    error: el listado sale vacío (D4)
 * @param q           texto dentro del nombre; {@code null} o en blanco para no filtrar
 * @param pagina      página, desde 0
 * @param tamano      filas por página, de 1 a 100
 * @param orden       {@code campo,asc|desc}, repetible como {@code nombre,asc,creadoEn,desc}
 */
public record FiltroCanchas(Boolean activa, UbicacionCancha ubicacion, PropietarioCancha propietario, UUID sedeId,
                            String q, int pagina, int tamano, String orden) {

    private static final int TAMANO_MAXIMO = 100;

    /** Campos que se pueden ordenar. La columna es una constante: nunca se concatena la entrada. */
    private enum CampoOrden {
        NOMBRE("nombre", "c.nombre"),
        CREADO_EN("creadoEn", "c.creado_en");

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
            throw invalido("No se puede ordenar por «" + nombre + "». Usa nombre o creadoEn.");
        }
    }

    /** @throws ReglaNegocioException 400 {@code VALIDACION} si la página, el tamaño o el orden no son válidos */
    void validar() {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw invalido("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        ordenSql();
    }

    /** Texto buscado, sin espacios en los extremos, o {@code null} si no se filtra por texto. */
    String texto() {
        return q == null || q.isBlank() ? null : q.strip();
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

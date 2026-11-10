package mx.tecnm.morelia.siglim.organizacion;

import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros y página de {@code GET /api/v1/clubes} (convenciones de API §4). El orden es fijo, por
 * nombre.
 *
 * @param q      texto dentro del nombre; {@code null} o en blanco para no filtrar
 * @param estado estado del club; {@code null} para todos
 * @param pagina página, desde 0
 * @param tamano filas por página, de 1 a 100
 */
public record FiltroClubes(String q, EstadoClub estado, int pagina, int tamano) {

    private static final int TAMANO_MAXIMO = 100;

    /** @throws ReglaNegocioException 400 {@code VALIDACION} si la página o el tamaño no son válidos */
    void validar() {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
    }

    /** Texto buscado, sin espacios en los extremos, o {@code null} si no se filtra por texto. */
    String texto() {
        return q == null || q.isBlank() ? null : q.strip();
    }

    long desplazamiento() {
        return (long) pagina * tamano;
    }
}

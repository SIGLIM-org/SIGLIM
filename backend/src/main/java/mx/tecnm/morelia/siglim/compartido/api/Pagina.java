package mx.tecnm.morelia.siglim.compartido.api;

import java.util.List;

/**
 * Respuesta paginada de un listado (convenciones de API §4).
 *
 * @param contenido      filas de la página
 * @param pagina         número de página, desde 0
 * @param tamano         tamaño de página pedido
 * @param totalElementos filas visibles para el usuario en todo el listado
 * @param totalPaginas   páginas que resultan de {@code totalElementos} y {@code tamano}
 * @param <T>            tipo de cada fila
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, long totalPaginas) {

    public Pagina {
        contenido = List.copyOf(contenido);
    }

    public static <T> Pagina<T> de(List<T> contenido, int pagina, int tamano, long totalElementos) {
        long totalPaginas = (totalElementos + tamano - 1) / tamano;
        return new Pagina<>(contenido, pagina, tamano, totalElementos, totalPaginas);
    }
}

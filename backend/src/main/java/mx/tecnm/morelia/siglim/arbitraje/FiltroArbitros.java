package mx.tecnm.morelia.siglim.arbitraje;

import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros y página de {@code GET /api/v1/arbitros} (convenciones de API §4). El orden es fijo: el
 * más reciente primero y, dentro de la misma fecha, por identificador.
 *
 * <p>Un {@code clubId} fuera del ámbito del usuario no es un error: el filtro de ámbito del SQL deja
 * la página vacía (ADR-0007).
 *
 * @param estado estado del árbitro; {@code null} para todos
 * @param clubId club que lo presenta; {@code null} para todos
 * @param pagina página, desde 0
 * @param tamano filas por página, de 1 a 100
 */
public record FiltroArbitros(EstadoArbitro estado, UUID clubId, int pagina, int tamano) {

    private static final int TAMANO_MAXIMO = 100;

    /** @throws ReglaNegocioException 400 {@code VALIDACION} si la página o el tamaño no son válidos */
    void validar() {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
    }

    long desplazamiento() {
        return (long) pagina * tamano;
    }
}

package mx.tecnm.morelia.siglim.organizacion;

import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros y página de {@code GET /api/v1/afiliaciones} (convenciones de API §4). El orden es fijo:
 * temporada más reciente primero y, dentro de ella, nombre del club.
 *
 * <p>Un {@code temporadaId} fuera del ámbito del usuario no es un error: el filtro de ámbito del SQL
 * deja la página vacía (ADR-0007).
 *
 * @param temporadaId temporada; {@code null} para todas
 * @param estado      estado del tarjetón; {@code null} para todos
 * @param pagina      página, desde 0
 * @param tamano      filas por página, de 1 a 100
 */
public record FiltroAfiliaciones(UUID temporadaId, EstadoAfiliacion estado, int pagina, int tamano) {

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

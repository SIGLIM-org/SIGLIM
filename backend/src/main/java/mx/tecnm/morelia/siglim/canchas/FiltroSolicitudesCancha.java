package mx.tecnm.morelia.siglim.canchas;

import java.util.List;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros y página de {@code GET /api/v1/solicitudes-cancha} (convenciones de API §4). El orden es
 * fijo: la captura más reciente primero.
 *
 * @param tipos  tipos de trámite; vacío para todos (la pestaña del Formato 1 manda
 *               {@code PRESTAMO} y {@code COMODATO})
 * @param estado estado del trámite; {@code null} para todos
 * @param pagina página, desde 0
 * @param tamano filas por página, de 1 a 100
 */
public record FiltroSolicitudesCancha(List<TipoSolicitudCancha> tipos, EstadoSolicitudCancha estado,
        int pagina, int tamano) {

    private static final int TAMANO_MAXIMO = 100;

    public FiltroSolicitudesCancha {
        tipos = tipos == null ? List.of() : List.copyOf(tipos);
    }

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

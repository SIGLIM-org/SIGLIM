package mx.tecnm.morelia.siglim.seguridad;

import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros y página de {@code GET /api/v1/solicitudes-arco} (convenciones de API §4). El orden es
 * fijo: las más recientes primero.
 *
 * <p>Refs: B-01, RF-098
 *
 * @param estado    estado del seguimiento; {@code null} para todos
 * @param tipo      derecho ejercido; {@code null} para todos
 * @param personaId titular; {@code null} para todos
 * @param pagina    página, desde 0
 * @param tamano    filas por página, de 1 a 100
 */
record FiltroSolicitudesArco(EstadoSolicitudArco estado, TipoSolicitudArco tipo, UUID personaId, int pagina,
        int tamano) {

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

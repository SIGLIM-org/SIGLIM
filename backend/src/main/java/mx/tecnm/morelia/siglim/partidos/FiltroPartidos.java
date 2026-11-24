package mx.tecnm.morelia.siglim.partidos;

import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;

/**
 * Filtros del listado de partidos (convenciones de API §6.8): jornada, estado y rango de inicio
 * ({@code desde} incluido, {@code hasta} excluido), con paginación.
 *
 * <p>Refs: H-15.1, RF-043
 */
public record FiltroPartidos(UUID jornadaId, EstadoPartido estado, OffsetDateTime desde, OffsetDateTime hasta,
                             int pagina, int tamano) {

    static final int TAMANO_MAXIMO = 100;

    /** {@code 400 VALIDACION} si la página, el tamaño o el rango no son válidos. */
    void validar() {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        if (desde != null && hasta != null && !hasta.isAfter(desde)) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "La fecha «hasta» debe ser posterior a «desde».");
        }
    }
}

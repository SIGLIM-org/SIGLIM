package mx.tecnm.morelia.siglim.arbitraje;

import java.time.OffsetDateTime;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros de {@code GET /api/v1/mis-designaciones} (DEC-205): rango opcional sobre el inicio del
 * partido ({@code desde} incluido, {@code hasta} excluido) y paginación (convenciones de API §4).
 *
 * <p>Refs: H-14.1, RF-023, DEC-205
 */
public record FiltroMisDesignaciones(OffsetDateTime desde, OffsetDateTime hasta, int pagina, int tamano) {

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

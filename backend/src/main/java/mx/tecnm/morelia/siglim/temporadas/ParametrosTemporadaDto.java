package mx.tecnm.morelia.siglim.temporadas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Respuesta de {@code GET /api/v1/temporadas/{id}/parametros} (D8): la versión vigente, el
 * historial y, mientras no haya versiones, los valores predeterminados para precargar el formulario.
 *
 * <p>Refs: H-05.2, RF-006, RF-100
 *
 * @param vigente         versión mayor; {@code null} (presente en el JSON) si la temporada aún no
 *                        tiene parámetros
 * @param historial       todas las versiones, de la mayor a la menor, incluida la vigente
 * @param predeterminados valores por defecto de la tabla [DEMO]; solo si no hay versiones
 */
public record ParametrosTemporadaDto(
        ParametrosDto vigente,
        List<VersionParametrosResumenDto> historial,
        @JsonInclude(JsonInclude.Include.NON_NULL) ParametrosPredeterminadosDto predeterminados) {

    public ParametrosTemporadaDto {
        historial = List.copyOf(historial);
    }
}

package mx.tecnm.morelia.siglim.temporadas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Temporada con el resumen que necesita la pantalla F-10 (UI-003). Los campos opcionales vacíos se
 * omiten (convenciones de API §1).
 *
 * <p>{@code activacion} solo se envía para una temporada en {@code CONFIGURACION}: trae la lista de
 * verificación que debe cumplir antes de activarse y si ya puede hacerlo. La interfaz deshabilita el
 * botón y explica qué falta (H-04.2, RF-100).
 *
 * <p>Refs: H-04.1, H-04.2, B-02.1, RF-001, RF-002, RF-003, RF-004, RF-005, RF-100, RN-005,
 * RN-058, UI-003
 *
 * @param categoriasAsignadas categorías de la temporada registradas hasta ahora
 * @param gruposCreados       grupos de esas categorías
 * @param versionParametros   versión vigente de los parámetros, si existe
 * @param convocatoria        referencia de la convocatoria asociada; se omite si aún no tiene
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TemporadaDto(
        UUID id,
        String nombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        EstadoTemporada estado,
        OffsetDateTime creadoEn,
        int categoriasAsignadas,
        int gruposCreados,
        Integer versionParametros,
        ActivacionDto activacion,
        ConvocatoriaDto convocatoria) {

    /** Lo que falta para activar, con el código del requisito del ERS que lo exige. */
    public record ActivacionDto(boolean puedeActivarse, List<RequisitoActivacionDto> requisitos) {

        public ActivacionDto {
            requisitos = List.copyOf(requisitos);
        }
    }

    /**
     * Un punto de la lista de verificación de activación.
     *
     * @param codigo      requisito del ERS: {@code RF-004}, {@code RF-005}, {@code RF-100} o
     *                    {@code RN-058}
     * @param descripcion qué se necesita; si no se cumple, el texto dice qué falta
     * @param cumple      si el requisito ya está satisfecho
     */
    public record RequisitoActivacionDto(String codigo, String descripcion, boolean cumple) {
    }
}

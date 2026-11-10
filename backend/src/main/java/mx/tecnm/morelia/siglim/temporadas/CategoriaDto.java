package mx.tecnm.morelia.siglim.temporadas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Categoría del catálogo de la liga con su orden y su categoría superior inmediata (RF-099,
 * DEC-16). La superior viaja anidada (convenciones de API §9.1) y se omite si no tiene (§1).
 *
 * <p>Refs: H-05.1, RF-099, RF-110
 *
 * @param categoriaSuperior categoría inmediata superior para alinear jugadores (RF-110); ausente
 *                          si es el tope de su rama (CU-002)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoriaDto(
        UUID id,
        String nombre,
        TipoCategoria tipo,
        int orden,
        ReferenciaDto categoriaSuperior,
        boolean activa,
        OffsetDateTime creadoEn) {

    /** Identificador y nombre de una entidad relacionada. */
    public record ReferenciaDto(UUID id, String nombre) {
    }
}

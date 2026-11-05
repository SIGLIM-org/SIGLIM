package mx.tecnm.morelia.siglim.temporadas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;

/**
 * Una versión en el historial de parámetros de la temporada (RF-006, D8).
 *
 * <p>Refs: H-05.2, RF-006
 *
 * @param creadoPor       perfil que registró la versión; se omite si no hay
 * @param camposCambiados campos que difieren de la versión anterior, en camelCase; incluye
 *                        {@code categorias} si cambió el conjunto de filas por categoría. La versión
 *                        1 los lista todos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VersionParametrosResumenDto(
        UUID id,
        int version,
        boolean esDemo,
        String motivo,
        ReferenciaDto creadoPor,
        OffsetDateTime creadoEn,
        List<String> camposCambiados) {

    public VersionParametrosResumenDto {
        camposCambiados = List.copyOf(camposCambiados);
    }
}

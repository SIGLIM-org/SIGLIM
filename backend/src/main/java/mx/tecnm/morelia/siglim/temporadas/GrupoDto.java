package mx.tecnm.morelia.siglim.temporadas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;

/**
 * Grupo de una categoría de la temporada (RF-005). El listado trae solo {@code id}, {@code nombre},
 * {@code categoriaTemporadaId} y {@code equipos}; el detalle agrega la temporada y la categoría. Los
 * campos vacíos se omiten (convenciones de API §1).
 *
 * <p>Refs: H-05.1, RF-005
 *
 * @param temporadaId temporada del grupo; solo en el detalle
 * @param categoria   categoría del catálogo; solo en el detalle
 * @param equipos     equipos del grupo en cualquier estado, también los dados de baja
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrupoDto(
        UUID id,
        String nombre,
        UUID categoriaTemporadaId,
        UUID temporadaId,
        ReferenciaDto categoria,
        int equipos) {
}

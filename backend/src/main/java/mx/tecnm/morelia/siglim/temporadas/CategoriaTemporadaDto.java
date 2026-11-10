package mx.tecnm.morelia.siglim.temporadas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;

/**
 * Categoría que participa en una temporada (RF-004) con sus grupos anidados (RF-005), para que la
 * pantalla de estructura de la temporada no haga una petición por categoría (convenciones de API
 * §9.1).
 *
 * <p>Refs: H-05.1, RF-004, RF-005, RF-099
 *
 * @param id        identificador de la fila de {@code categorias_temporada}; es el
 *                  {@code categoriaTemporadaId} de los grupos
 * @param categoria la categoría del catálogo
 * @param grupos    grupos de la categoría en la temporada, por nombre; vacío si no tiene
 */
public record CategoriaTemporadaDto(UUID id, CategoriaResumenDto categoria, List<GrupoResumenDto> grupos) {

    /** La categoría del catálogo; la superior se omite si no tiene. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CategoriaResumenDto(UUID id, String nombre, TipoCategoria tipo, int orden,
                                      ReferenciaDto categoriaSuperior) {
    }

    /**
     * Grupo con su conteo de equipos.
     *
     * @param equipos equipos del grupo en cualquier estado, también los dados de baja
     */
    public record GrupoResumenDto(UUID id, String nombre, int equipos) {
    }
}

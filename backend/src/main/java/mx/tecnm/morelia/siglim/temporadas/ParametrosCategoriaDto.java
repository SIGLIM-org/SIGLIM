package mx.tecnm.morelia.siglim.temporadas;

import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;

/**
 * Duración de juego y bloque de cancha propios de una categoría en una versión de parámetros
 * ({@code parametros_categoria}); las categorías sin fila usan los de la temporada (RF-100, DEC-25).
 *
 * <p>Refs: H-05.2, RF-100, DEC-25
 */
public record ParametrosCategoriaDto(ReferenciaDto categoria, int duracionJuegoMin, int bloqueCanchaMin) {
}

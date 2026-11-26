package mx.tecnm.morelia.siglim.temporadas;

import java.util.List;

/**
 * Cuerpo de {@code PATCH /api/v1/temporadas/{temporadaId}/categorias/{categoriaTemporadaId}}: los
 * días de la semana en que juega la categoría en la temporada (DEC-177, DEC-191).
 *
 * <p>La validación (de 1 a 7 valores, cada uno de 1 a 7, sin repetir) la hace el servicio para
 * responder con un mensaje que nombre el problema; se guardan ordenados.
 *
 * <p>Refs: H-32.2, RF-035, DEC-177, DEC-191
 *
 * @param diasJuego días ISO 8601: 1 = lunes … 6 = sábado, 7 = domingo
 */
public record ActualizarDiasJuegoRequest(List<Integer> diasJuego) {
}

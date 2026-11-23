package mx.tecnm.morelia.siglim.partidos;

import java.time.OffsetDateTime;
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;

/**
 * Una transición del partido con usuario, fecha y motivo (RF-045, RN-021), escrita por el trigger
 * {@code tg_partidos_historial}.
 *
 * <p>Refs: H-15.1, RF-045, RN-021
 *
 * @param estadoAnterior {@code null} en la programación del partido
 * @param cambiadoPor    perfil que hizo el cambio; el nombre va {@code null} si la sesión no puede ver
 *                       ese perfil (RLS) y todo el campo es {@code null} si no se guardó usuario
 */
public record CambioEstadoPartidoDto(EstadoPartido estadoAnterior, EstadoPartido estadoNuevo, String motivo,
                                     ReferenciaDto cambiadoPor, OffsetDateTime cambiadoEn) {
}

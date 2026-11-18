package mx.tecnm.morelia.siglim.registros;

import java.time.OffsetDateTime;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;

/**
 * Un cambio de estado del historial de un registro ({@code registro_estado_historial}), que escribe
 * {@code tg_registros_historial} en el alta y en cada cambio de estado (RF-018).
 *
 * <p>Refs: B-07.1, RF-018
 *
 * @param estadoAnterior estado antes del cambio; {@code null} en el alta
 * @param estadoNuevo    estado después del cambio
 * @param motivo         motivo de la operación o del cambio
 * @param cambiadoPor    perfil que hizo el cambio con su nombre mostrado; el nombre es {@code null}
 *                       si la sesión no puede ver ese perfil, y todo el campo es {@code null} si el
 *                       historial no guardó usuario
 * @param cambiadoEn     fecha y hora del cambio en {@code America/Mexico_City}
 */
public record CambioEstadoRegistroDto(EstadoRegistro estadoAnterior, EstadoRegistro estadoNuevo, String motivo,
                                      ReferenciaDto cambiadoPor, OffsetDateTime cambiadoEn) {
}

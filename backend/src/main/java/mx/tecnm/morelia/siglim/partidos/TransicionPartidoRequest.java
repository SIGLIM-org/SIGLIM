package mx.tecnm.morelia.siglim.partidos;

import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;

/**
 * Suspender, reprogramar o cancelar un partido (convenciones de API §2.1). Las reglas las valida el
 * servicio para responder con el código del ERS.
 *
 * <p>Refs: H-15.1, RF-043, RF-045, RN-021, DEC-23
 *
 * @param estadoNuevo {@code SUSPENDIDO}, {@code REPROGRAMADO} o {@code CANCELADO}
 * @param motivo      de 10 a 500 caracteres (RN-021); queda en el historial y en la auditoría
 * @param inicio      solo al reprogramar: nueva fecha y hora, posterior al momento de la petición
 * @param canchaId    solo al reprogramar: otra cancha; si se omite, la misma
 */
public record TransicionPartidoRequest(EstadoPartido estadoNuevo, String motivo, OffsetDateTime inicio,
                                       UUID canchaId) {
}

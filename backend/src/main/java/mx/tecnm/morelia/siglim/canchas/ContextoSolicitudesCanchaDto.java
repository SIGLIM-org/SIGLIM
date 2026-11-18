package mx.tecnm.morelia.siglim.canchas;

import java.util.List;
import mx.tecnm.morelia.siglim.canchas.SolicitudCanchaDto.ReferenciaDto;

/**
 * Catálogos para capturar un trámite de cancha (B-14): los clubes por los que la sesión puede
 * capturar, las canchas activas de la liga y si puede capturar ofertas de externos. Solo
 * identificadores y nombres, sin datos personales.
 *
 * <p>Refs: B-14, RF-116, RF-117
 *
 * @param clubes           clubes {@code ACTIVO} del alcance de {@code canchas.solicitud:CREAR}
 * @param canchas          canchas activas de la liga, para referenciar la solicitada u ofrecida
 * @param capturaExternos  {@code true} si la sesión captura con ámbito de liga (ofertas de externos)
 */
public record ContextoSolicitudesCanchaDto(List<ReferenciaDto> clubes, List<ReferenciaDto> canchas,
        boolean capturaExternos) {
}

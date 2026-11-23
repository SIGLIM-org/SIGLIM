package mx.tecnm.morelia.siglim.partidos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.partidos.PartidoDetalleDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;

/**
 * Partido en el listado (convenciones de API §6.8 y §9.1): equipos, cancha, horario, estado y las
 * acciones que la sesión puede hacer, para que la interfaz muestre solo esos botones (RF-046).
 *
 * <p>Refs: H-15.1, RF-043, RF-046
 */
public record PartidoResumenDto(
        UUID id,
        UUID jornadaId,
        int jornadaNumero,
        ReferenciaDto equipoLocal,
        ReferenciaDto equipoVisitante,
        ReferenciaDto cancha,
        OffsetDateTime inicio,
        OffsetDateTime finBloque,
        EstadoPartido estado,
        List<AccionPartido> accionesPermitidas) {
}

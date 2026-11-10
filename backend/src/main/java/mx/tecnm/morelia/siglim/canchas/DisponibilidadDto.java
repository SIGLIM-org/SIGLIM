package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Registro de disponibilidad, bloqueo o mantenimiento de una cancha (RF-029, RF-030). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DisponibilidadDto(UUID id, TipoDisponibilidad tipo, Intervalo periodo, String motivo,
                                OffsetDateTime creadoEn) {
}

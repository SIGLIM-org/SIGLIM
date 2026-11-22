package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Horario por categoría de una cancha en una temporada (RF-119, DEC-33). {@code diaSemana} va de
 * 1 (lunes) a 7 (domingo); las horas se escriben {@code HH:mm:ss} y el fin es exclusivo.
 *
 * <p>Refs: B-13.1, RF-119, DEC-33
 */
public record HorarioCanchaClubDto(UUID id, UUID canchaId, UUID temporadaId, ReferenciaDto categoria,
                                   ReferenciaDto club, int diaSemana,
                                   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss") LocalTime horaInicio,
                                   @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss") LocalTime horaFin,
                                   boolean esComodato) {

    public record ReferenciaDto(UUID id, String nombre) {
    }
}

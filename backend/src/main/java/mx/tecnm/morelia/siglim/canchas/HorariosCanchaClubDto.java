package mx.tecnm.morelia.siglim.canchas;

import java.time.LocalTime;
import java.util.UUID;

/** Horario semanal de una cancha de club o en comodato (RF-119). */
public record HorariosCanchaClubDto(
        UUID id,
        UUID canchaId,
        UUID clubId,
        UUID temporadaId,
        UUID categoriaId,
        int diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin,
        boolean esComodato) {
}

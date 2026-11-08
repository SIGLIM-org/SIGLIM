package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Cuerpo de reemplazo completo de {@code PUT /api/v1/canchas/{id}/horarios-club} (RF-119). */
public record RegistrarHorarioRequest(@NotNull List<@Valid HorarioCanchaClubItem> horarios) {

    /** Un horario semanal por categoría, día y temporada. */
    public record HorarioCanchaClubItem(
            @NotNull UUID temporadaId,
            @NotNull UUID categoriaId,
            @NotNull @Min(1) @Max(7) Integer diaSemana,
            @NotNull LocalTime horaInicio,
            @NotNull LocalTime horaFin,
            UUID clubId,
            boolean esComodato) {
    }
}

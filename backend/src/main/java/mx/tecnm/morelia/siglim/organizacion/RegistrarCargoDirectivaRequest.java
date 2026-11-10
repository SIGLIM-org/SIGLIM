package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** Cuerpo de {@code POST /api/v1/clubes/{id}/directiva}; la nueva vigencia queda pendiente (RF-102). */
public record RegistrarCargoDirectivaRequest(
        @NotNull(message = "La persona es obligatoria") UUID personaId,
        @NotNull(message = "El cargo es obligatorio") CargoDirectiva cargo,
        @NotNull(message = "La fecha de inicio es obligatoria") LocalDate desde) {
}

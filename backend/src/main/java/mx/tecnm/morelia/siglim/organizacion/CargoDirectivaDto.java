package mx.tecnm.morelia.siglim.organizacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;

/** Cargo de directiva con su vigencia y estado derivado de la autorización (RF-102). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CargoDirectivaDto(
        UUID id,
        UUID clubId,
        UUID personaId,
        String nombrePersona,
        CargoDirectiva cargo,
        LocalDate desde,
        LocalDate hasta,
        EstadoCargoDirectiva estado,
        UUID autorizadoPor) {
}

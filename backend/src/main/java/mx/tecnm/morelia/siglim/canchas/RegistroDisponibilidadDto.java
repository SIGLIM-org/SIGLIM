package mx.tecnm.morelia.siglim.canchas;

import java.util.List;

/**
 * Respuesta de registrar disponibilidad o un bloqueo: los partidos que el bloqueo afecta y que no
 * se reprograman solos (PEN-007).
 */
public record RegistroDisponibilidadDto(DisponibilidadDto disponibilidad, List<PartidoEnCanchaDto> partidosAfectados) {

    public RegistroDisponibilidadDto {
        partidosAfectados = List.copyOf(partidosAfectados);
    }
}

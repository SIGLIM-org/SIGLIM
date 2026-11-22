package mx.tecnm.morelia.siglim.seguridad;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Consentimiento registrado (RF-097, DEC-81): quién es el titular, quién lo otorgó, sobre qué versión
 * del aviso y para qué finalidades.
 *
 * <p>Refs: H-09.1, RF-097, DEC-76, DEC-81
 */
public record ConsentimientoDto(
        UUID id,
        UUID personaId,
        UUID otorgadoPorId,
        UUID avisoId,
        List<String> finalidades,
        OffsetDateTime otorgadoEn) {

    public ConsentimientoDto {
        finalidades = List.copyOf(finalidades);
    }
}

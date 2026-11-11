package mx.tecnm.morelia.siglim.seguridad;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Versión del aviso de privacidad que se muestra antes de pedir el consentimiento (RF-097, DEC-80).
 * {@code esDemo} indica que el texto es de demostración y la interfaz lo rotula «No oficial» (DEC-74).
 *
 * <p>Refs: H-09.1, RF-097, DEC-74, DEC-80
 */
public record AvisoPrivacidadDto(
        UUID id,
        TipoAvisoPrivacidad tipo,
        String version,
        String contenido,
        List<String> finalidades,
        LocalDate vigenteDesde,
        boolean esDemo) {

    public AvisoPrivacidadDto {
        finalidades = List.copyOf(finalidades);
    }
}

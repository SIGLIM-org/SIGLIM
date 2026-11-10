package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/v1/temporadas/{id}/convocatoria} (RF-002).
 *
 * <p>Es un reemplazo completo: la referencia es obligatoria y los demás campos son opcionales; lo
 * que no venga queda vacío. La temporada cerrada no admite cambios (RF-003), y el enlace, si viene,
 * debe ser una URL http o https.
 *
 * @param referencia título o referencia de la convocatoria
 * @param version    versión de la convocatoria
 * @param documentoUrl enlace al documento de la convocatoria
 */
public record ReemplazarConvocatoriaRequest(
        @NotBlank(message = "La referencia de la convocatoria es obligatoria")
        String referencia,
        @Size(max = 30, message = "La versión admite hasta 30 caracteres")
        String version,
        String documentoUrl) {
}

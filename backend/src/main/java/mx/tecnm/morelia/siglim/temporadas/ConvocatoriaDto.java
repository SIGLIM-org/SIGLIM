package mx.tecnm.morelia.siglim.temporadas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Referencia normativa asociada a una temporada (RF-002, RN-002).
 *
 * <p>Es una referencia, no un archivo: la convocatoria vive fuera del sistema y aquí se guarda su
 * título, su versión y, si se conoce, el enlace a su documento. La temporada puede existir sin
 * convocatoria mientras se configura (CU-001, alternativa «convocatoria no disponible»).
 *
 * <p>Refs: B-02.1, RF-002, RN-002, CU-001, UI-003
 *
 * @param version      versión de la convocatoria; opcional
 * @param documentoUrl enlace al documento; opcional
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConvocatoriaDto(
        UUID id,
        UUID temporadaId,
        String referencia,
        String version,
        String documentoUrl,
        OffsetDateTime creadoEn) {

    /** Traduce la fila de la base; {@code null} cuando la temporada no tiene convocatoria. */
    static ConvocatoriaDto desde(ConvocatoriaRepository.DatosConvocatoria datos) {
        if (datos == null) {
            return null;
        }
        return new ConvocatoriaDto(datos.id(), datos.temporadaId(), datos.referencia(),
                datos.version(), datos.documentoUrl(), datos.creadoEn());
    }
}

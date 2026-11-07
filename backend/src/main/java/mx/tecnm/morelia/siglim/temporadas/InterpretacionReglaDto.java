package mx.tecnm.morelia.siglim.temporadas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Interpretación administrativa de una regla, pendiente de autorización del Presidente o ya
 * resuelta (RF-007, RN-006).
 *
 * <p>No es una regla permanente ni modifica los parámetros versionados de la temporada: solo deja
 * constancia de la interpretación y de quién la autorizó o la rechazó. La descripción y los
 * identificadores salen de {@code siglim.interpretaciones_regla}; los nombres de las personas no se
 * exponen, solo su {@code perfiles.id}.
 *
 * <p>Refs: B-03.1, RF-007, RN-006, RF-093
 *
 * @param estado        {@code PENDIENTE} al registrarse; {@code AUTORIZADA} o {@code RECHAZADA}
 *                      cuando el Presidente la resuelve
 * @param registradaPor perfil que la registró: quien la registró no puede resolverla (DEC-57)
 * @param resueltaPor   perfil del Presidente que la resolvió; se omite mientras está pendiente
 * @param observaciones observaciones del registro o de la resolución; obligatorias al rechazar
 *                      (DEC-58)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterpretacionReglaDto(
        UUID id,
        UUID temporadaId,
        String descripcion,
        EstadoInterpretacion estado,
        UUID registradaPor,
        OffsetDateTime registradaEn,
        UUID resueltaPor,
        OffsetDateTime resueltaEn,
        String observaciones) {

    /** Traduce la fila de la base; {@code null} cuando no se encontró. */
    static InterpretacionReglaDto desde(InterpretacionReglaRepository.DatosInterpretacion datos) {
        if (datos == null) {
            return null;
        }
        return new InterpretacionReglaDto(datos.id(), datos.temporadaId(), datos.descripcion(),
                datos.estado(), datos.registradaPor(), datos.registradaEn(), datos.resueltaPor(),
                datos.resueltaEn(), datos.observaciones());
    }
}

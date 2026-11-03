package mx.tecnm.morelia.siglim.publicacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Comunicado con los datos que necesitan la pantalla B-08 y la aprobación del Presidente (UI-031).
 * Los campos opcionales vacíos se omiten (convenciones de API §1).
 *
 * <p>El cuerpo viaja completo para que la vista previa y el dictamen se hagan con el texto que se
 * publicará. Quién redactó y quién aprobó se identifican por {@code perfiles.id}; el nombre lo
 * resuelve cada pantalla con su consulta de perfiles.
 *
 * <p>Refs: B-27.1, RF-090, UI-031
 *
 * @param estadoPublicacion estado del flujo Borrador → Revisión → Publicado
 * @param redactadoPor      perfil que redactó el comunicado
 * @param aprobadoPor       perfil que lo aprobó; nulo mientras no se publique
 * @param aprobadoEn        fecha de aprobación; nula mientras no se publique
 * @param publicadoEn       fecha de publicación que fija el trigger; nula mientras no se publique
 * @param observaciones     motivo de la devolución o nota de la aprobación
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComunicadoDto(
        UUID id,
        String titulo,
        String cuerpo,
        EstadoPublicacion estadoPublicacion,
        UUID redactadoPor,
        UUID aprobadoPor,
        OffsetDateTime aprobadoEn,
        OffsetDateTime publicadoEn,
        String observaciones,
        OffsetDateTime creadoEn,
        OffsetDateTime actualizadoEn) {
}

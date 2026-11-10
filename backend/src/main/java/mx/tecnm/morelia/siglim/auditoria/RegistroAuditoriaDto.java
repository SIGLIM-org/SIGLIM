package mx.tecnm.morelia.siglim.auditoria;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

/**
 * Fila de {@code GET /api/v1/auditoria}. Trae el motivo completo y el detalle, así que no hace falta
 * un endpoint de detalle.
 *
 * @param id         identificador del registro
 * @param ocurridoEn fecha y hora de la acción, en la zona de negocio
 * @param perfil     quién ejecutó la acción; {@code null} si el registro no tiene perfil
 * @param modulo     módulo del ERS, p. ej. {@code M-08}
 * @param accion     acción realizada, p. ej. {@code APROBAR_CEDULA}
 * @param entidad    tabla o recurso afectado
 * @param entidadId  identificador del registro afectado
 * @param motivo     motivo de la operación
 * @param detalle    datos adicionales; por contrato sin datos personales ({@link AuditoriaService})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistroAuditoriaDto(long id, OffsetDateTime ocurridoEn, PerfilResumenDto perfil, String modulo,
                                   String accion, String entidad, String entidadId, String motivo, JsonNode detalle) {
}

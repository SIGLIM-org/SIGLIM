package mx.tecnm.morelia.siglim.auditoria;

import java.util.UUID;

/**
 * Perfil en las respuestas de la auditoría. Solo identificador y nombre: nunca correo ni persona
 * (RNF-012, convenciones de API §9.2).
 *
 * @param id             identificador del perfil
 * @param nombreMostrado nombre que se muestra en la interfaz
 */
public record PerfilResumenDto(UUID id, String nombreMostrado) {
}

package mx.tecnm.morelia.siglim.seguridad;

import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/solicitudes-arco}. Se valida en {@link SolicitudArcoService} para
 * explicar cada error.
 *
 * <p>Refs: B-01, RF-098
 *
 * @param personaId   titular de los datos
 * @param tipo        {@code ACCESO}, {@code RECTIFICACION}, {@code CANCELACION} u {@code OPOSICION}
 * @param descripcion lo que pide el titular
 */
public record RegistrarSolicitudArcoRequest(UUID personaId, String tipo, String descripcion) {
}

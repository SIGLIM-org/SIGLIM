package mx.tecnm.morelia.siglim.seguridad;

/**
 * Cuerpo de {@code PATCH /api/v1/solicitudes-arco/{id}}: cambio de estado del seguimiento.
 *
 * <p>Refs: B-01, RF-098
 *
 * @param estado    {@code EN_ATENCION}, {@code ATENDIDA} o {@code RECHAZADA}
 * @param respuesta respuesta al titular; obligatoria al cerrar ({@code ATENDIDA} o {@code RECHAZADA})
 */
public record ActualizarSolicitudArcoRequest(String estado, String respuesta) {
}

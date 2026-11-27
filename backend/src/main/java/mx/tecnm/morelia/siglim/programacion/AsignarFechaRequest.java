package mx.tecnm.morelia.siglim.programacion;

/**
 * Cuerpo de {@code POST /fechas/{id}/asignacion} (H-33.2). Las reglas las valida el servicio: falta de
 * {@code modo} ({@code 400 VALIDACION}) y motivo al regenerar ({@code 400 RN-021}).
 *
 * @param modo   {@code PENDIENTES} o {@code REGENERAR}
 * @param motivo obligatorio para regenerar (al menos 10 caracteres); opcional en {@code PENDIENTES}
 */
public record AsignarFechaRequest(ModoAsignacion modo, String motivo) {
}

package mx.tecnm.morelia.siglim.programacion;

import java.time.LocalDate;
import java.util.List;

/**
 * Cuerpo de {@code PATCH /api/v1/fechas/{id}}: cambia o agrega días de la fecha mientras está en
 * borrador (DEC-176, DEC-193).
 *
 * <p>Las reglas de los días (de 1 a 7, sin repetir, dentro de 7 días consecutivos y sin traslape con
 * otra fecha) y del motivo las valida el servicio para explicar el problema.
 *
 * <p>Refs: H-32.2, RF-035, RF-090, RN-021, DEC-176, DEC-193
 *
 * @param dias   días calendario de la fecha; se guardan ordenados
 * @param motivo razón del cambio, de al menos 10 caracteres (RN-021)
 */
public record ActualizarFechaRequest(List<LocalDate> dias, String motivo) {
}

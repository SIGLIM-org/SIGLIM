package mx.tecnm.morelia.siglim.programacion;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code POST /api/v1/jornadas/{id}/resolucion} (RF-121, CU-007).
 *
 * <p>La devolución exige observaciones de al menos 10 caracteres; la aprobación puede llevarlas,
 * pero no son obligatorias. El servicio valida la longitud para responder con un mensaje claro
 * antes de que el trigger de publicación exija el motivo (RF-121, RN-021).
 *
 * @param decision      aprobar y publicar, o devolver a borrador
 * @param observaciones motivo de la devolución o nota de la aprobación
 */
public record ResolverJornadaRequest(
        @NotNull(message = "Indica si la jornada se publica o se devuelve")
        DecisionJornada decision,
        String observaciones) {
}

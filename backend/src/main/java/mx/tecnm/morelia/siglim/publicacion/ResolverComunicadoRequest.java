package mx.tecnm.morelia.siglim.publicacion;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code POST /api/v1/comunicados/{id}/resolucion} (RF-090, CU-027).
 *
 * <p>La devolución exige observaciones de al menos 10 caracteres; la aprobación puede llevarlas,
 * pero no son obligatorias. El servicio valida la longitud para responder con un mensaje claro
 * antes de que el trigger de publicación exija el motivo (RN-021, RF-090).
 *
 * @param decision      aprobar y publicar, o devolver a borrador
 * @param observaciones motivo de la devolución o nota de la aprobación
 */
public record ResolverComunicadoRequest(
        @NotNull(message = "Indica si el comunicado se publica o se devuelve")
        DecisionComunicado decision,
        String observaciones) {
}

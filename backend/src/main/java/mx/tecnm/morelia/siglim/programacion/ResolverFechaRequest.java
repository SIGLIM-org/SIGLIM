package mx.tecnm.morelia.siglim.programacion;

import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de {@code POST /api/v1/fechas/{id}/resolucion}: aprueba o devuelve la fecha completa
 * (H-35.1, RF-121, DEC-175). Igual que el de la jornada: devolver exige observaciones de al menos 10
 * caracteres; aprobar puede llevarlas.
 *
 * @param decision      {@code PUBLICADO} aprueba y publica todas las jornadas; {@code BORRADOR} las devuelve
 * @param observaciones motivo de la devolución o nota de la aprobación
 */
public record ResolverFechaRequest(
        @NotNull(message = "Indica si la fecha se publica o se devuelve")
        DecisionJornada decision,
        String observaciones) {
}

package mx.tecnm.morelia.siglim.tesoreria;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tarifa de un concepto para una categoría en una temporada (RF-135), con las referencias anidadas
 * para que la interfaz no haga una petición por fila (convenciones de API §9.1).
 *
 * <p>El monto es {@link BigDecimal} en pesos mexicanos con dos decimales (convenciones de API §1).
 *
 * <p>Refs: B-29.1, RF-135, PEN-042
 *
 * @param esDemo valor de demostración [DEMO] que la interfaz rotula «No oficial»: las tarifas reales
 *               están pendientes (PEN-042), así que hoy siempre es {@code true}
 */
public record TarifaDto(
        UUID id,
        ReferenciaDto temporada,
        ReferenciaDto categoria,
        ConceptoTarifa concepto,
        BigDecimal monto,
        boolean esDemo) {

    /** Identificador y nombre de una entidad relacionada. */
    public record ReferenciaDto(UUID id, String nombre) {
    }
}

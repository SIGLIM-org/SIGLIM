package mx.tecnm.morelia.siglim.tesoreria;

import java.util.UUID;

/**
 * Puerto de la pasarela de pago (ADR-0012). Aísla a la tesorería del proveedor: en esta entrega la
 * única implementación es {@link PasarelaSimulada}, que no cobra nada.
 *
 * <p>El flujo real futuro sería: {@link #iniciar(UUID)} crea la sesión de pago en el proveedor y
 * devuelve su referencia; un <i>webhook</i> entrega el resultado y se confirma con
 * {@link #confirmar(String, ResultadoPagoSimulado)}. La simulación conserva las dos operaciones para
 * que integrar una pasarela real no cambie el resto de la tesorería.
 *
 * <p>La persistencia del intento, el pago, la auditoría y el evento son responsabilidad de
 * {@link PagoSimuladoService}; la pasarela solo genera la referencia y valida la confirmación.
 *
 * <p>Refs: H-30.1, RF-138, ADR-0012
 */
public interface PasarelaDePago {

    /**
     * Inicia el cobro en la pasarela.
     *
     * @param cobroId cobro que se va a pagar
     * @return referencia que identifica el intento; en la simulación es ficticia
     */
    String iniciar(UUID cobroId);

    /**
     * Confirma el resultado del intento.
     *
     * @param referencia referencia devuelta por {@link #iniciar(UUID)}
     * @param resultado  aprobación o rechazo elegido por la persona
     */
    void confirmar(String referencia, ResultadoPagoSimulado resultado);
}

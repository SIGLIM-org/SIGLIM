package mx.tecnm.morelia.siglim.tesoreria;

/**
 * Resultado que la persona elige al confirmar un pago simulado: aprobarlo o rechazarlo
 * (RF-138, ADR-0012).
 *
 * <p>No es el estado del intento: al iniciar queda {@code PENDIENTE}; la confirmación lo pasa a
 * {@code APROBADO} o {@code RECHAZADO}, que son los valores de
 * {@code siglim.estado_intento_pago}.
 *
 * <p>Refs: H-30.1, RF-138, ADR-0012
 */
public enum ResultadoPagoSimulado {
    APROBADO,
    RECHAZADO
}

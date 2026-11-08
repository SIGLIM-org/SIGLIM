package mx.tecnm.morelia.siglim.organizacion;

import java.util.UUID;

/**
 * Adeudos de un club, tal como los necesita este módulo para el cambio de nombre (DEC-61). La
 * implementa {@code tesoreria}: está en un nivel superior y {@code organizacion} no consulta
 * {@code cobros} directamente (ADR-0006).
 *
 * <p>Refs: B-04, RF-105, RN-041, RN-052, DEC-61, ADR-0006
 */
public interface ConsultaAdeudosClub {

    /** Verdadero si el club tiene algún cobro {@code PENDIENTE} o {@code VENCIDO}. */
    boolean tieneCobrosPendientesOVencidos(UUID clubId);
}

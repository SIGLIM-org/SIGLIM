package mx.tecnm.morelia.siglim.seguridad;

import java.util.EnumSet;
import java.util.Set;

/**
 * Seguimiento de una solicitud ARCO. Coincide con el enum {@code siglim.estado_solicitud_arco}.
 *
 * <p><b>[PROPUESTA]</b> Transiciones: el ERS no fija una máquina de estados para ARCO (§4.3), así
 * que se propone la mínima: {@code RECIBIDA → EN_ATENCION}, y de {@code RECIBIDA} o
 * {@code EN_ATENCION} a {@code ATENDIDA} o {@code RECHAZADA}. {@code ATENDIDA} y {@code RECHAZADA}
 * son finales. Queda a validar con la Liga.
 *
 * <p>Refs: B-01, RF-098
 */
public enum EstadoSolicitudArco {
    RECIBIDA,
    EN_ATENCION,
    ATENDIDA,
    RECHAZADA;

    /** Estados a los que puede pasar desde este. */
    Set<EstadoSolicitudArco> siguientes() {
        return switch (this) {
            case RECIBIDA -> EnumSet.of(EN_ATENCION, ATENDIDA, RECHAZADA);
            case EN_ATENCION -> EnumSet.of(ATENDIDA, RECHAZADA);
            case ATENDIDA, RECHAZADA -> EnumSet.noneOf(EstadoSolicitudArco.class);
        };
    }

    /** Cierra la solicitud: lleva respuesta al titular, perfil y fecha de atención. */
    boolean esFinal() {
        return this == ATENDIDA || this == RECHAZADA;
    }
}

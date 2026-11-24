package mx.tecnm.morelia.siglim.partidos;

import java.util.List;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;

/**
 * Acción manual sobre el estado de un partido y el estado al que lo lleva (DEC-23, ERS §4.3.1). Las
 * transiciones a {@code EN_CURSO} y {@code FINALIZADO} son automáticas (RF-127) y no son acciones.
 *
 * <p>Refs: H-15.1, RF-043, RF-046, DEC-23
 */
public enum AccionPartido {
    SUSPENDER(EstadoPartido.SUSPENDIDO),
    REPROGRAMAR(EstadoPartido.REPROGRAMADO),
    CANCELAR(EstadoPartido.CANCELADO);

    private final EstadoPartido destino;

    AccionPartido(EstadoPartido destino) {
        this.destino = destino;
    }

    /** Estado al que lleva la acción. */
    public EstadoPartido destino() {
        return destino;
    }

    /**
     * Acciones manuales que admite el estado (RF-046): programado se reprograma o cancela; en curso se
     * suspende; suspendido se reprograma o cancela. Reprogramado, finalizado y cancelado no admiten
     * ninguna: el reprogramado espera su nueva hora (RF-127).
     */
    public static List<AccionPartido> desde(EstadoPartido estado) {
        return switch (estado) {
            case PROGRAMADO, SUSPENDIDO -> List.of(REPROGRAMAR, CANCELAR);
            case EN_CURSO -> List.of(SUSPENDER);
            case REPROGRAMADO, FINALIZADO, CANCELADO -> List.of();
        };
    }

    /** La acción que lleva a ese estado, o {@code null} si ninguna lo hace a mano. */
    public static AccionPartido hacia(EstadoPartido destino) {
        for (AccionPartido accion : values()) {
            if (accion.destino == destino) {
                return accion;
            }
        }
        return null;
    }
}

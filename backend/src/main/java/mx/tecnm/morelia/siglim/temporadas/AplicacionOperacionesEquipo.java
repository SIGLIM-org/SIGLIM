package mx.tecnm.morelia.siglim.temporadas;

import java.util.UUID;

/**
 * Aplica al cierre de una temporada las operaciones de equipo que el Presidente autorizó
 * (B-05.2, RF-103, RF-104, RN-040).
 *
 * <p>La implementa {@code organizacion}, que está en un nivel superior de dependencias y es dueña
 * de {@code operaciones_equipo} y de {@code equipos}; {@code temporadas} no puede depender de ese
 * paquete (ADR-0006). El cierre la llama dentro de su propia transacción, así que la temporada, las
 * operaciones y el cambio de club se confirman o se revierten juntos.
 *
 * <p>Refs: B-05.2, H-04.1, RF-103, RF-104, RN-040, ADR-0006
 */
public interface AplicacionOperacionesEquipo {

    /**
     * Aplica las operaciones {@code AUTORIZADA} de la temporada: las pasa a {@code APLICADA} con
     * {@code aplicada_en} y mueve el equipo al club destino de cada venta. Las {@code SOLICITADA} y
     * las {@code RECHAZADA} no se tocan. El historial de propietarios se conserva porque
     * {@code club_origen_id} no se reescribe (RN-040).
     *
     * @param temporadaId temporada que se está cerrando
     * @return cantidad de operaciones aplicadas
     */
    int aplicarAutorizadas(UUID temporadaId);
}

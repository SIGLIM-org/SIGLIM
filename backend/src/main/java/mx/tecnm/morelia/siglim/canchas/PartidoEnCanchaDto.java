package mx.tecnm.morelia.siglim.canchas;

import java.util.UUID;

/**
 * Partido que ocupa una cancha, con su bloque como {@code periodo}. {@code estado} es el valor de
 * {@code siglim.estado_partido}, sin traducir.
 */
public record PartidoEnCanchaDto(UUID id, String estado, Intervalo periodo, EquipoDto equipoLocal,
                                 EquipoDto equipoVisitante) {

    public record EquipoDto(UUID id, String nombre) {
    }
}

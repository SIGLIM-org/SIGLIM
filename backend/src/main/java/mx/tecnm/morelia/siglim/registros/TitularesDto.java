package mx.tecnm.morelia.siglim.registros;

import java.util.List;
import java.util.UUID;

/**
 * Personas cuya información puede consultar la cuenta: primero la suya y después los menores bajo su
 * tutela vigente, por nombre (H-24.1, RF-144, DEC-139). Una cuenta sin persona vinculada recibe la
 * lista vacía.
 *
 * <p>Refs: H-24.1, RF-144, DEC-139
 */
public record TitularesDto(List<TitularDto> titulares) {

    public TitularesDto {
        titulares = List.copyOf(titulares);
    }

    /**
     * Una persona elegible en el selector de F-35.
     *
     * @param esPropia si es la persona de la cuenta; {@code false} para un menor bajo su tutela
     * @param esMenor  si es menor de edad hoy, en {@code America/Mexico_City}
     */
    public record TitularDto(UUID personaId, String nombre, boolean esPropia, boolean esMenor) {
    }
}

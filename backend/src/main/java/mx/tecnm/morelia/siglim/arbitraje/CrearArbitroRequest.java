package mx.tecnm.morelia.siglim.arbitraje;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/arbitros} (RF-112).
 *
 * <p>La Directiva de club elige una persona ya registrada por su CURP y la presenta con su club. La
 * identidad es única (RF-010, DEC-10): el servicio rechaza con {@code 409} si esa persona ya está
 * en el catálogo de árbitros.
 *
 * <p>Refs: H-11.1, RF-112, RF-010, DEC-10, DEC-17
 *
 * @param personaId persona que se presenta como árbitro
 * @param clubId    club que la presenta
 */
public record CrearArbitroRequest(
        @NotNull(message = "Indica la persona que se presenta como árbitro")
        UUID personaId,
        @NotNull(message = "Indica el club que presenta al árbitro")
        UUID clubId) {
}

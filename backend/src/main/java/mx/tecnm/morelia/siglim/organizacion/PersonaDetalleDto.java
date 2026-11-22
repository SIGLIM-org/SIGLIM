package mx.tecnm.morelia.siglim.organizacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Detalle de una persona que devuelven el alta y la edición (convenciones de API §6.3). Es un dato
 * personal: el controlador responde con {@code Cache-Control: no-store} (RNF-012). Los campos
 * opcionales vacíos se omiten.
 *
 * <p>Refs: H-09.1, RF-010, RF-011, DEC-62
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonaDetalleDto(
        UUID id,
        String curp,
        String nombre,
        String apellidoPaterno,
        String apellidoMaterno,
        LocalDate fechaNacimiento,
        String correo,
        String telefono) {
}

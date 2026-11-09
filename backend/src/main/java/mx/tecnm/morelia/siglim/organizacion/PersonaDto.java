package mx.tecnm.morelia.siglim.organizacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Identidad de una persona por CURP, sin datos de contacto (RF-010, RF-011, convenciones de API §6.3).
 *
 * <p>Es la consulta mínima que necesita F-15 para registrar un cambio de directiva: el alta de
 * cargos recibe un {@code personaId}, así que la interfaz busca a la persona por su CURP y muestra
 * su nombre antes de enviar el cambio. {@code apellidoMaterno} se omite cuando no existe.
 *
 * <p>Refs: H-22.2, RF-010, RF-102
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonaDto(
        UUID id,
        String curp,
        String nombre,
        String apellidoPaterno,
        String apellidoMaterno) {
}

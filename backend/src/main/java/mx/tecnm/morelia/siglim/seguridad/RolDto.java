package mx.tecnm.morelia.siglim.seguridad;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Rol del catálogo de la liga para elegir al invitar o asignar (RF-094, RF-095).
 *
 * <p>{@code asignable} es falso para los roles técnicos ({@code SISTEMA} y
 * {@code USUARIO_PUBLICO}): se listan para la pantalla de roles, pero no se pueden asignar a una
 * cuenta.
 *
 * <p>Refs: H-03.1, RF-094, RF-095
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RolDto(
        UUID id,
        String codigo,
        String nombre,
        String descripcion,
        Ambito ambitoPredeterminado,
        boolean asignable) {
}

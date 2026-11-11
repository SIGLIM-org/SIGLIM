package mx.tecnm.morelia.siglim.organizacion;

import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/v1/personas} (DEC-62, DEC-84). Obligatorios: CURP, nombre, apellido
 * paterno y fecha de nacimiento; el resto es opcional. Las validaciones las hace
 * {@link PersonaService} después del permiso, para respetar el orden de errores de la convención.
 *
 * <p>Refs: H-09.1, RF-010, DEC-62
 */
public record CrearPersonaRequest(
        String curp,
        String nombre,
        String apellidoPaterno,
        String apellidoMaterno,
        LocalDate fechaNacimiento,
        String correo,
        String telefono) {
}

package mx.tecnm.morelia.siglim.organizacion;

import java.time.LocalDate;

/**
 * Cuerpo de {@code PATCH /api/v1/personas/{id}}: un campo ausente conserva su valor (DEC-83).
 *
 * <p>Correo y teléfono los edita quien tenga {@code organizacion.persona:EDITAR} en cualquier
 * ámbito; CURP, nombre, apellidos y fecha de nacimiento exigen ese permiso en toda la liga, porque
 * la identidad es global (DEC-77). <b>[PROPUESTA]</b> Una cadena vacía en {@code apellidoMaterno},
 * {@code correo} o {@code telefono} borra el dato, porque en este cuerpo {@code null} significa «no
 * cambia».
 *
 * <p>Refs: H-09.1, RF-010, RF-011, DEC-77, DEC-83
 */
public record ActualizarPersonaRequest(
        String curp,
        String nombre,
        String apellidoPaterno,
        String apellidoMaterno,
        LocalDate fechaNacimiento,
        String correo,
        String telefono) {

    boolean sinCampos() {
        return curp == null && nombre == null && apellidoPaterno == null && apellidoMaterno == null
                && fechaNacimiento == null && correo == null && telefono == null;
    }
}

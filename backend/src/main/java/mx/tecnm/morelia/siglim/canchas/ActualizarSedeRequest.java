package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /api/v1/sedes/{id}}: un campo ausente conserva su valor. Un {@code nombre}
 * en blanco se rechaza con {@code 400 VALIDACION}; una {@code direccion} en blanco se vacía.
 */
public record ActualizarSedeRequest(
        @Size(max = 120, message = "El nombre admite hasta 120 caracteres")
        String nombre,
        String direccion) {
}

package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/sedes} (RF-028). La dirección es opcional; en blanco se omite. */
public record CrearSedeRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre admite hasta 120 caracteres")
        String nombre,
        String direccion) {
}

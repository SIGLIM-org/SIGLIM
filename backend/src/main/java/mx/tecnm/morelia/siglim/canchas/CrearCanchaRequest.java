package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/canchas} (RF-027). {@code sedeId} es opcional: sin él, la cancha
 * queda sin sede (RF-028); una sede inexistente o de otra liga responde {@code 422 REFERENCIA_INVALIDA}.
 */
public record CrearCanchaRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre admite hasta 120 caracteres")
        String nombre,
        @NotNull(message = "La ubicación es obligatoria")
        UbicacionCancha ubicacion,
        String direccion,
        @NotNull(message = "El propietario es obligatorio")
        PropietarioCancha propietario,
        UUID clubId,
        @Size(max = 150, message = "El propietario externo admite hasta 150 caracteres")
        String propietarioExterno,
        String caracteristicas,
        UUID sedeId) {
}

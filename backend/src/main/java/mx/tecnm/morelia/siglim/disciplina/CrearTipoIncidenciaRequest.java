package mx.tecnm.morelia.siglim.disciplina;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/tipos-incidencia} (RF-128). El tipo siempre nace activo; se
 * desactiva con {@code PATCH} y el sello {@code esDemo} no se captura: solo viene de la semilla.
 */
public record CrearTipoIncidenciaRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 40, message = "El código admite hasta 40 caracteres")
        String codigo,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres")
        String nombre,
        @NotNull(message = "Indica si abre expediente")
        Boolean abreExpediente) {
}

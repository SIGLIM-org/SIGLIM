package mx.tecnm.morelia.siglim.publicacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/comunicados} (RF-090).
 *
 * <p>El comunicado nace en {@code BORRADOR} y se envía a revisión con una acción aparte, para que
 * la Secretaría pueda guardar mientras redacta (UI-031).
 *
 * @param titulo título oficial; el máximo es el de la columna {@code comunicados.titulo}
 * @param cuerpo texto completo del comunicado
 */
public record CrearComunicadoRequest(
        @NotBlank(message = "El título del comunicado es obligatorio")
        @Size(max = 200, message = "El título admite hasta 200 caracteres")
        String titulo,
        @NotBlank(message = "El cuerpo del comunicado es obligatorio")
        String cuerpo) {
}

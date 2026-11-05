package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/categorias} (RF-099). El orden lo asigna el sistema al final del
 * catálogo; la liga sale de la configuración del backend.
 *
 * @param categoriaSuperiorId categoría inmediata superior; opcional (sin ella, la categoría es el
 *                            tope de su rama, CU-002)
 */
public record CrearCategoriaRequest(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        @Size(max = 80, message = "El nombre admite hasta 80 caracteres")
        String nombre,
        @NotNull(message = "El tipo de la categoría es obligatorio")
        TipoCategoria tipo,
        UUID categoriaSuperiorId) {
}

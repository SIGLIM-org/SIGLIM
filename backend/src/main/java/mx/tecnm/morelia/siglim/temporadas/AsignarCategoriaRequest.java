package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Cuerpo de {@code POST /api/v1/temporadas/{id}/categorias}: la categoría del catálogo (RF-004). */
public record AsignarCategoriaRequest(
        @NotNull(message = "La categoría es obligatoria")
        UUID categoriaId) {
}

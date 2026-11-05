package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/categorias/reordenamiento} (RF-099): todas las categorías de la
 * liga, activas e inactivas, en el orden nuevo y sin repetidos. La primera recibe el orden 1.
 */
public record ReordenarCategoriasRequest(
        @NotNull(message = "La lista de categorías es obligatoria")
        List<UUID> ids) {
}

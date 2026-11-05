package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Cuerpo de {@code POST /api/v1/grupos}: el grupo nace en una categoría de la temporada (RF-005). */
public record CrearGrupoRequest(
        @NotNull(message = "La categoría de la temporada es obligatoria")
        UUID categoriaTemporadaId,
        @NotBlank(message = "El nombre del grupo es obligatorio")
        @Size(max = 60, message = "El nombre admite hasta 60 caracteres")
        String nombre) {
}

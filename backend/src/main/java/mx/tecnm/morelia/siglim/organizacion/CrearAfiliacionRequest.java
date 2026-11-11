package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/afiliaciones} (RF-101, CU-018).
 *
 * <p>El folio, el estado inicial {@code SOLICITADA} y el perfil que captura los pone el servidor: no
 * vienen en el cuerpo. El club sale del ámbito de la sesión y la temporada debe ser de la liga.
 *
 * <p>Refs: H-08.1, RF-101, CU-018, DEC-09
 *
 * @param clubId           club que se afilia
 * @param temporadaId      temporada en la que se afilia
 * @param coloresUniformes colores declarados en el tarjetón
 * @param categoriaIds     categorías de la temporada en que se inscribe el club
 */
public record CrearAfiliacionRequest(
        @NotNull(message = "El club es obligatorio")
        UUID clubId,
        @NotNull(message = "La temporada es obligatoria")
        UUID temporadaId,
        @NotBlank(message = "Los colores del uniforme son obligatorios")
        @Size(max = 120, message = "Los colores del uniforme admiten hasta 120 caracteres")
        String coloresUniformes,
        @NotEmpty(message = "Indica al menos una categoría del tarjetón")
        List<@NotNull(message = "Las categorías no pueden incluir un valor vacío") UUID> categoriaIds) {
}

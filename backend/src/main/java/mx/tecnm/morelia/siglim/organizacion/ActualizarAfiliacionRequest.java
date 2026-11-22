package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/afiliaciones/{id}} (RF-101). Solo se edita el tarjetón mientras
 * está {@code SOLICITADA}; lo ausente conserva su valor.
 *
 * @param coloresUniformes colores del uniforme; {@code null} conserva el actual
 * @param categoriaIds     categorías del tarjetón; {@code null} conserva las actuales y una lista
 *                         vacía responde {@code 400 VALIDACION}
 */
public record ActualizarAfiliacionRequest(
        @Size(max = 120, message = "Los colores del uniforme admiten hasta 120 caracteres")
        String coloresUniformes,
        List<@NotNull(message = "Las categorías no pueden incluir un valor vacío") UUID> categoriaIds) {
}

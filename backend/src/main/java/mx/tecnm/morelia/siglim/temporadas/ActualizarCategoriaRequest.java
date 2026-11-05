package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code PATCH /api/v1/categorias/{id}}: un campo ausente conserva su valor (RF-099).
 *
 * <ul>
 *   <li><b>Superior.</b> Sin {@code categoriaSuperiorId} ni {@code quitarSuperior}, la superior se
 *       conserva; con {@code categoriaSuperiorId}, se asigna esa; con {@code "quitarSuperior": true},
 *       la categoría queda como tope de su rama. Los dos juntos responden {@code 400 VALIDACION}.</li>
 *   <li><b>Activa.</b> {@code false} desactiva la categoría si ninguna temporada en configuración o
 *       activa la usa y no es la superior de otra categoría activa; {@code true} la reactiva.</li>
 *   <li>El orden no se edita aquí: se cambia con {@code POST /categorias/reordenamiento}.</li>
 * </ul>
 */
public record ActualizarCategoriaRequest(
        @Size(max = 80, message = "El nombre admite hasta 80 caracteres")
        String nombre,
        TipoCategoria tipo,
        UUID categoriaSuperiorId,
        Boolean quitarSuperior,
        Boolean activa) {
}

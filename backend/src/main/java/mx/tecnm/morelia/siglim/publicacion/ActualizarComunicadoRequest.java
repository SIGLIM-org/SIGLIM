package mx.tecnm.morelia.siglim.publicacion;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /api/v1/comunicados/{id}}: un campo ausente conserva su valor (RF-090).
 *
 * <p>Solo se edita mientras el comunicado está en {@code BORRADOR}; el servicio rechaza el cambio
 * en revisión o publicado. El estado no se cambia aquí, sino con las acciones de envío y resolución.
 *
 * @param titulo título nuevo, si se cambia
 * @param cuerpo texto nuevo, si se cambia
 */
public record ActualizarComunicadoRequest(
        @Size(max = 200, message = "El título admite hasta 200 caracteres")
        String titulo,
        String cuerpo) {
}

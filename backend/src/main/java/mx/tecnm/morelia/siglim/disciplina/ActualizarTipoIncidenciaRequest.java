package mx.tecnm.morelia.siglim.disciplina;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /api/v1/tipos-incidencia/{id}}: un campo ausente conserva su valor. El
 * {@code codigo} es editable porque las incidencias del reglamento lo referencian por
 * {@code id}, no por código; desactivar el tipo no lo borra: se hace con {@code activo: false}
 * (RN-005).
 */
public record ActualizarTipoIncidenciaRequest(
        @Size(max = 40, message = "El código admite hasta 40 caracteres")
        String codigo,
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres")
        String nombre,
        Boolean abreExpediente,
        /** {@code false} deja de ofrecer el tipo sin borrar su historial. */
        Boolean activo) {
}

package mx.tecnm.morelia.siglim.disciplina;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Cuerpo de {@code PATCH /api/v1/articulos/{id}}: un campo ausente conserva su valor. Al cambiar
 * {@code tipoSancion}, si no se envía el valor del nuevo tipo y el anterior no corresponde, el
 * servicio marca el faltante; los valores del tipo anterior se limpian solos. Desactivar el
 * artículo no lo borra: se hace con {@code activo: false} (RN-005).
 */
public record ActualizarArticuloRequest(
        @Size(max = 20, message = "El número admite hasta 20 caracteres")
        String numero,
        String descripcion,
        TipoSancion tipoSancion,
        SujetoSancion sujeto,
        EfectoSancion efecto,
        Boolean aplicacionAutomatica,
        Integer partidosSuspension,
        Integer puntos,
        @DecimalMin(value = "0.01", message = "El monto de la multa debe ser mayor a cero")
        BigDecimal montoMulta,
        Boolean otorgaDefaultRestantes,
        /** {@code false} deja de aplicar el artículo sin borrar su historial. */
        Boolean activo) {
}

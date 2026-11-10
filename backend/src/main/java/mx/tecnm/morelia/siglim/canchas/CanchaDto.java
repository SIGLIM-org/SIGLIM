package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cancha del catálogo (RF-027). Los campos opcionales vacíos se omiten (convenciones de API §1):
 * {@code sede} no aparece si la cancha no tiene sede (RF-028).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CanchaDto(UUID id, String nombre, UbicacionCancha ubicacion, SedeResumenDto sede, String direccion,
                        PropietarioCancha propietario, ClubDto club, String propietarioExterno,
                        String caracteristicas, boolean activa, OffsetDateTime creadoEn) {

    public record ClubDto(UUID id, String nombre) {
    }

    /** Sede de la cancha, solo con lo necesario para mostrarla y filtrar (RF-028). */
    public record SedeResumenDto(UUID id, String nombre) {
    }
}

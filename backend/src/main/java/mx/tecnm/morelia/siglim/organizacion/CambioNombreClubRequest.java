package mx.tecnm.morelia.siglim.organizacion;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/clubes/{id}/cambio-nombre} (RF-105, B-04).
 *
 * <p>Aquí solo se limita la longitud ({@code 400 VALIDACION}). Que el nombre y el motivo vengan
 * escritos se valida en el servicio, en su orden de validación: el motivo con el código RN-021,
 * como en las demás transiciones de estado.
 *
 * @param nombre nombre del club nuevo
 * @param motivo motivo del cierre del club anterior (mínimo 10 caracteres, RN-021)
 */
public record CambioNombreClubRequest(
        @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
        String nombre,
        @Size(max = 500, message = "El motivo admite hasta 500 caracteres")
        String motivo) {
}

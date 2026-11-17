package mx.tecnm.morelia.siglim.canchas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/solicitudes-cancha} (RF-116, RF-117).
 *
 * <p>Un préstamo o comodato lleva {@code clubId} y el bloque {@code prestamo} (Formato 1). Una
 * oferta lleva el bloque {@code oferta} (Formato 2) y, como oferente, {@code clubId} o
 * {@code solicitanteExterno} (nunca los dos). El servicio valida esas combinaciones para
 * explicarlas con un mensaje claro.
 *
 * <p>Refs: B-14, RF-116, RF-117, DEC-35
 *
 * @param tipo               préstamo, comodato u oferta
 * @param clubId             club solicitante u oferente
 * @param solicitanteExterno nombre del oferente externo sin cuenta (lo captura el Secretario)
 * @param canchaId           cancha del catálogo a la que se refiere el trámite, si ya existe
 * @param prestamo           campos del Formato 1
 * @param oferta             campos del Formato 2
 */
public record CrearSolicitudCanchaRequest(
        @NotNull(message = "Indica si el trámite es un préstamo, un comodato o una oferta")
        TipoSolicitudCancha tipo,
        UUID clubId,
        @Size(max = 150, message = "El nombre del oferente externo admite hasta 150 caracteres")
        String solicitanteExterno,
        UUID canchaId,
        @Valid DatosPrestamo prestamo,
        @Valid DatosOferta oferta) {
}

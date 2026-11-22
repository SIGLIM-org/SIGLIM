package mx.tecnm.morelia.siglim.seguridad;

import java.util.List;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/personas/{id}/consentimientos} (DEC-81). {@code otorgadoPorId} es
 * obligatorio para un menor (su tutor vigente) y se omite o es la misma persona para un adulto
 * (DEC-76). Lo valida {@link ConsentimientoService}, después del permiso.
 *
 * <p>Refs: H-09.1, RF-097, VAL-025, DEC-76, DEC-81
 */
public record RegistrarConsentimientoRequest(UUID avisoId, List<String> finalidades, UUID otorgadoPorId) {
}

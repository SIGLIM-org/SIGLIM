package mx.tecnm.morelia.siglim.tesoreria;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pago de un cobro (RF-138, RF-139). Los campos opcionales vacíos se omiten (convenciones de API §1).
 *
 * <p>El comprobante solo lleva sus metadatos: <b>nunca</b> el bucket, la clave ni la URL guardados en
 * {@code siglim.documentos} (convenciones de API §7, ADR-0013). El enlace temporal para verlo queda
 * para el Sprint 3 (DEC-56).
 *
 * <p>Refs: H-31.1, RF-139, RNF-018, ADR-0013
 *
 * @param registradoPor perfil que registró el pago; el nombre se omite si el perfil no es visible
 *                      para quien consulta (RLS de {@code perfiles})
 * @param comprobante   se omite en un pago sin comprobante (el pago en línea simulado)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PagoDto(
        UUID id,
        UUID cobroId,
        MedioPago medio,
        BigDecimal monto,
        String referencia,
        OffsetDateTime pagadoEn,
        CobroDto.ReferenciaDto registradoPor,
        ComprobanteDto comprobante) {

    /** Metadatos del comprobante; el contenido se lee con un enlace prefirmado (ADR-0013). */
    public record ComprobanteDto(UUID documentoId, String tipoMime, int tamanoBytes) {
    }
}

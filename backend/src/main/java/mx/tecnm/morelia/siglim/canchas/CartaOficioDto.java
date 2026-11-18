package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Datos de la carta de un trámite de cancha con su número de oficio (RF-118). La interfaz la
 * compone con la plantilla del formato del cliente (R8) y la imprime o guarda en PDF.
 *
 * <p>Refs: B-14, RF-118, R8
 *
 * @param numeroOficio       número de oficio asignado al capturar el trámite
 * @param formato            1 (préstamo o comodato) o 2 (oferta)
 * @param asunto             asunto de la carta según el formato
 * @param liga               nombre de la liga que emite o recibe la carta
 * @param fechaEmision       fecha de la carta: el día de la captura en la zona de la liga
 * @param solicitante        club o oferente externo
 * @param prestamo           campos del Formato 1
 * @param oferta             campos del Formato 2
 * @param estado             estado vigente, para el espacio de autorización o el acuse
 * @param resueltaEn         fecha del dictamen, si ya lo hubo
 * @param resueltaPor        nombre de quien dictaminó, si RLS permite verlo
 * @param observaciones      observaciones o condiciones del dictamen
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartaOficioDto(
        String numeroOficio,
        TipoSolicitudCancha tipo,
        int formato,
        String asunto,
        String liga,
        LocalDate fechaEmision,
        String solicitante,
        DatosPrestamo prestamo,
        DatosOferta oferta,
        EstadoSolicitudCancha estado,
        OffsetDateTime resueltaEn,
        String resueltaPor,
        String observaciones) {
}

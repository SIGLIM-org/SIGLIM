package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trámite de cancha: préstamo o comodato (Formato 1) u oferta (Formato 2) con su número de oficio
 * (RF-116 a RF-118). Los campos sin valor se omiten (convenciones de API §1).
 *
 * <p>Exactamente uno de {@code club} o {@code solicitanteExterno} está presente, y exactamente uno
 * de {@code prestamo} u {@code oferta}, según el formato.
 *
 * <p>Refs: B-14, RF-116, RF-117, RF-118
 *
 * @param formato            1 para préstamo o comodato, 2 para oferta (R8)
 * @param numeroOficio       número de oficio de la carta (RF-118)
 * @param club               club solicitante u oferente
 * @param solicitanteExterno oferente externo sin cuenta
 * @param cancha             cancha del catálogo, si el trámite la referencia
 * @param capturadaPor       perfil que capturó; el nombre se omite si RLS no permite verlo
 * @param resueltaPor        perfil del Presidente que dictaminó
 * @param observaciones      motivo o condiciones del dictamen
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SolicitudCanchaDto(
        UUID id,
        TipoSolicitudCancha tipo,
        int formato,
        EstadoSolicitudCancha estado,
        String numeroOficio,
        ReferenciaDto club,
        String solicitanteExterno,
        ReferenciaDto cancha,
        DatosPrestamo prestamo,
        DatosOferta oferta,
        PerfilDto capturadaPor,
        OffsetDateTime capturadaEn,
        PerfilDto resueltaPor,
        OffsetDateTime resueltaEn,
        String observaciones) {

    /** Referencia a un catálogo por identificador y nombre (convenciones de API §9.1). */
    public record ReferenciaDto(UUID id, String nombre) {
    }

    /** Perfil sin correo ni otros datos de contacto. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PerfilDto(UUID id, String nombreMostrado) {
    }

    /** Nombre de quien solicita u ofrece: el club o el oferente externo. */
    public String nombreSolicitante() {
        return club != null ? club.nombre() : solicitanteExterno;
    }
}

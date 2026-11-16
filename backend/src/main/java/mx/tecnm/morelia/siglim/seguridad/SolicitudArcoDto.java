package mx.tecnm.morelia.siglim.seguridad;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Solicitud ARCO y su seguimiento ({@code GET/POST/PATCH /api/v1/solicitudes-arco}).
 *
 * <p>Refs: B-01, RF-098
 *
 * @param id                identificador
 * @param personaId         titular de los datos
 * @param personaNombre     nombre completo del titular; {@code null} si la sesión no puede verlo
 * @param tipo              derecho que se ejerce
 * @param descripcion       lo que pide el titular
 * @param estado            estado del seguimiento
 * @param recibidaEn        fecha y hora de recepción
 * @param atendidaPor       perfil que cerró la solicitud; {@code null} mientras siga abierta
 * @param atendidaPorNombre nombre de ese perfil
 * @param atendidaEn        fecha y hora del cierre
 * @param respuesta         respuesta entregada al titular
 */
public record SolicitudArcoDto(
        UUID id,
        UUID personaId,
        String personaNombre,
        TipoSolicitudArco tipo,
        String descripcion,
        EstadoSolicitudArco estado,
        OffsetDateTime recibidaEn,
        UUID atendidaPor,
        String atendidaPorNombre,
        OffsetDateTime atendidaEn,
        String respuesta) {
}

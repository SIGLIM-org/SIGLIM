package mx.tecnm.morelia.siglim.organizacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;

/**
 * Operación de venta o de conservación de plaza de un equipo (RF-103, RF-104, RN-040). Los campos
 * sin valor se omiten (convenciones de API §1).
 *
 * <p>{@code clubDestino} solo aparece en una venta. {@code autorizadaPor}, {@code autorizadaEn} y
 * {@code aplicadaEn} solo aparecen cuando ya ocurrieron; la fecha de aplicación la fija el cierre de
 * la temporada (H-04.1).
 *
 * <p>Refs: B-05.1, RF-103, RF-104, RN-040
 *
 * @param equipo         equipo sobre el que se registra la operación
 * @param clubOrigen     club propietario al solicitar; se conserva como historial de propietarios
 * @param clubDestino    club comprador; solo en una venta
 * @param temporada      temporada del equipo, cuyo estado decide si el cierre ya la aplicó
 * @param solicitadaPor  perfil que registró la solicitud
 * @param autorizadaPor  perfil del Presidente que la resolvió
 * @param observaciones  de la solicitud, o las del dictamen que las reemplaza (motivo del rechazo o
 *                       nota de la autorización); autorizar sin nota conserva las de la solicitud
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperacionEquipoDto(
        UUID id,
        TipoOperacionEquipo tipo,
        EstadoOperacionEquipo estado,
        ReferenciaDto equipo,
        ReferenciaDto clubOrigen,
        ReferenciaDto clubDestino,
        TemporadaResumenDto temporada,
        PerfilDto solicitadaPor,
        OffsetDateTime solicitadaEn,
        PerfilDto autorizadaPor,
        OffsetDateTime autorizadaEn,
        OffsetDateTime aplicadaEn,
        String observaciones) {

    /** Referencia a un catálogo por identificador y nombre (convenciones de API §9.1). */
    public record ReferenciaDto(UUID id, String nombre) {
    }

    /** Temporada del equipo, con su estado para que la interfaz sepa si ya cerró. */
    public record TemporadaResumenDto(UUID id, String nombre, EstadoTemporada estado) {
    }

    /**
     * Perfil que solicitó o resolvió, sin correo ni otros datos de contacto. El nombre se omite si
     * las políticas RLS de {@code perfiles} no permiten verlo al usuario de la sesión.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PerfilDto(UUID id, String nombreMostrado) {
    }
}

package mx.tecnm.morelia.siglim.arbitraje;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Árbitro presentado por un club y validado por la Comisión Arbitral (RF-021, RF-112, RF-113).
 *
 * <p>Los campos sin valor se omiten (convenciones de API §1). La persona se resume con su nombre
 * completo y su identificador; la CURP y los datos de contacto no se exponen en el listado
 * (convenciones de API §9.2). El nombre del perfil que propuso o validó solo aparece si las
 * políticas RLS de {@code perfiles} permiten verlo.
 *
 * <p>Refs: H-11.1, RF-021, RF-112, RF-113, UI-007
 *
 * @param persona       persona que actúa como árbitro
 * @param club          club que lo presenta (DEC-17)
 * @param propuestoPor  perfil que lo presentó
 * @param validadoPor   perfil que lo validó o rechazó
 * @param observaciones motivo del rechazo o nota de la validación
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArbitroDto(
        UUID id,
        PersonaResumenDto persona,
        ReferenciaDto club,
        EstadoArbitro estado,
        PerfilDto propuestoPor,
        OffsetDateTime propuestoEn,
        PerfilDto validadoPor,
        OffsetDateTime validadoEn,
        String observaciones) {

    /** Referencia a un catálogo por identificador y nombre (convenciones de API §9.1). */
    public record ReferenciaDto(UUID id, String nombre) {
    }

    /** Identidad mínima de la persona para el listado; sin CURP ni datos de contacto. */
    public record PersonaResumenDto(UUID id, String nombreCompleto) {
    }

    /**
     * Perfil que propuso o validó, sin correo ni otros datos de contacto. El nombre se omite si
     * las políticas RLS de {@code perfiles} no permiten verlo.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PerfilDto(UUID id, String nombreMostrado) {
    }
}

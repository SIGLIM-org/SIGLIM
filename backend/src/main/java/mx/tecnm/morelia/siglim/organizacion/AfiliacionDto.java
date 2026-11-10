package mx.tecnm.morelia.siglim.organizacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;

/**
 * Tarjetón de afiliación de un club en una temporada (RF-101, DEC-09). Los campos sin valor se
 * omiten (convenciones de API §1).
 *
 * <p>El folio lo asigna el sistema al capturar el tarjetón y es único por temporada. Las categorías
 * son las que el club declara en el tarjetón; {@code resueltaPor}, {@code resueltaEn} y
 * {@code observaciones} solo aparecen cuando el Presidente ya resolvió.
 *
 * <p>Refs: H-08.1, RF-101, CU-018, UI-023, DEC-09
 *
 * @param club            club afiliado
 * @param temporada       temporada en la que se afilia
 * @param categorias      categorías declaradas en el tarjetón
 * @param capturadaPor    perfil que capturó el tarjetón
 * @param resueltaPor     perfil que lo aprobó o rechazó
 * @param resueltaEn      fecha y hora de la resolución
 * @param observaciones   motivo del rechazo o nota de la aprobación
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AfiliacionDto(
        UUID id,
        String folio,
        ReferenciaDto club,
        TemporadaResumenDto temporada,
        String coloresUniformes,
        EstadoAfiliacion estado,
        List<ReferenciaDto> categorias,
        PerfilDto capturadaPor,
        OffsetDateTime capturadaEn,
        PerfilDto resueltaPor,
        OffsetDateTime resueltaEn,
        String observaciones) {

    /** Referencia a un catálogo por identificador y nombre (convenciones de API §9.1). */
    public record ReferenciaDto(UUID id, String nombre) {
    }

    /** Temporada del tarjetón, con su estado para que la interfaz sepa si es de solo lectura. */
    public record TemporadaResumenDto(UUID id, String nombre, EstadoTemporada estado) {
    }

    /** Perfil que capturó o resolvió, sin correo ni otros datos de contacto. */
    public record PerfilDto(UUID id, String nombreMostrado) {
    }
}

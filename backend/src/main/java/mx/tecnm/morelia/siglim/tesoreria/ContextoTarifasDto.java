package mx.tecnm.morelia.siglim.tesoreria;

import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;

/**
 * Datos de apoyo de la pestaña «Tarifas» de F-33 ({@code GET /api/v1/tarifas/contexto}, B-29.2):
 * las temporadas de la liga, de la más reciente a la más antigua, con las categorías asignadas a
 * cada una en el orden del catálogo.
 *
 * <p>Existe porque el Tesorero no tiene {@code temporadas.temporada:CONSULTAR} en la matriz
 * inicial (Apéndice E) y necesita elegir la temporada y ver sus categorías para capturar tarifas.
 * Se autoriza con {@code tesoreria.tarifa:EDITAR}, igual que los contextos de afiliaciones y de
 * operaciones de equipo; solo lleva identificadores, nombres y el estado de la temporada.
 *
 * <p>Refs: B-29.2, RF-135, RF-003, ADR-0007
 */
public record ContextoTarifasDto(List<TemporadaDto> temporadas) {

    /** Temporada con su estado, para mostrar sus tarifas de solo lectura si está cerrada (RF-003). */
    public record TemporadaDto(UUID id, String nombre, EstadoTemporada estado,
                               List<TarifaDto.ReferenciaDto> categorias) {
    }
}

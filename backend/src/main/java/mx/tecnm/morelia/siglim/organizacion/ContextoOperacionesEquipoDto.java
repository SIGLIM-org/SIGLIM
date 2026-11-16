package mx.tecnm.morelia.siglim.organizacion;

import java.util.List;
import mx.tecnm.morelia.siglim.organizacion.OperacionEquipoDto.ReferenciaDto;

/**
 * Datos de apoyo de B-09 para registrar una venta o una conservación de plaza
 * ({@code GET /api/v1/operaciones-equipo/contexto}, B-05.3).
 *
 * <p>{@code clubes} es el catálogo de clubes {@code ACTIVO} de la liga que pueden recibir un
 * equipo. La Directiva lo necesita completo para elegir al comprador, aunque su ámbito de consulta
 * de clubes solo alcance el propio; no incluye datos personales.
 *
 * <p>Refs: B-05.3, RF-103, RF-104, ADR-0007
 */
public record ContextoOperacionesEquipoDto(List<ReferenciaDto> clubes) {
}

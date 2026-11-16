package mx.tecnm.morelia.siglim.arbitraje;

import java.util.UUID;

/**
 * Cuota de árbitros de un club en una temporada (RF-114, DEC-17, RN-042).
 *
 * <p>La regla es configurable: por cada {@code equiposPorArbitro} equipos activos del club debe
 * presentar un árbitro {@code VALIDADO}, con redondeo hacia arriba (5 equipos y 4 por árbitro
 * requieren 2). {@code equiposPorArbitro} sale de la versión vigente de {@code parametros_temporada},
 * nunca de una constante del código (AGENTS.md §3.3, DEC-72). El resultado se expone dentro de
 * {@code cuotaArbitros} para que la pantalla lo rotule como indicador calculado.
 *
 * <p>Refs: B-08.1, RF-114, RN-042, DEC-17, F-20
 *
 * @param equiposActivos     equipos del club en estado {@code ACTIVO} en la temporada
 * @param equiposPorArbitro  parámetro [DEMO] de la temporada: equipos que cubre cada árbitro
 * @param cuotaArbitros      requeridos, validados y si el club cumple
 */
public record CuotaArbitrosDto(
        ReferenciaDto club,
        ReferenciaDto temporada,
        int equiposActivos,
        int equiposPorArbitro,
        ResumenCuotaDto cuotaArbitros) {

    /** Identificador y nombre de una entidad relacionada (convenciones de API §9.1). */
    public record ReferenciaDto(UUID id, String nombre) {
    }

    /**
     * Resultado de la cuota.
     *
     * @param requeridos árbitros validados que el club debe presentar
     * @param validados  árbitros del club en estado {@code VALIDADO}
     * @param cumple     si {@code validados} alcanza {@code requeridos}
     */
    public record ResumenCuotaDto(int requeridos, int validados, boolean cumple) {
    }
}

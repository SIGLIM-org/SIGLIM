/**
 * Ruta de F-24 (DEC-209). Vive aparte de la pantalla para que el detalle del partido enlace aquí
 * sin cargar el fragmento de la designación.
 */
export const RUTA_DESIGNACIONES = '/app/programacion/designaciones'

/** Ruta de «Mis designaciones» del árbitro (DEC-212). */
export const RUTA_MIS_DESIGNACIONES = '/app/mis-designaciones'

/** Enlace a la jornada de un partido en F-24. */
export function rutaJornadaDesignaciones(jornadaId: string): string {
  return `${RUTA_DESIGNACIONES}?jornadaId=${encodeURIComponent(jornadaId)}`
}

/**
 * Rutas de M-04 en la aplicación interna (DEC-108, DEC-111). Viven aparte para que el asistente,
 * la página de documentos y la bandeja se enlacen sin importarse entre sí.
 */

export const RUTA_ORGANIZACION = '/app/organizacion'

export const RUTA_ALTA_JUGADOR = '/app/organizacion/jugadores/alta'

/** Página de documentos de un registro (paso 4 de F-18, DEC-111). */
export const RUTA_DOCUMENTOS_REGISTRO = '/app/organizacion/jugadores/:registroId/documentos'

/** Bandeja de validación (F-19, DEC-111). */
export const RUTA_VALIDACION_REGISTROS = '/app/organizacion/registros'

export function rutaDocumentosRegistro(registroId: string): string {
  return `/app/organizacion/jugadores/${registroId}/documentos`
}

/** Estado de navegación con el que el asistente llega a los documentos tras el `201`. */
export type EstadoDesdeAlta = { desdeAlta: true }

/** Guarda de tipo del `location.state`, que puede traer cualquier cosa (o nada tras recargar). */
export function vieneDelAlta(estado: unknown): estado is EstadoDesdeAlta {
  return (
    typeof estado === 'object' &&
    estado !== null &&
    (estado as Record<string, unknown>).desdeAlta === true
  )
}

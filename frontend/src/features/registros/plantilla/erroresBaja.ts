import { ApiError } from '@/lib/api/ApiError'
import { esNoEncontrado, esSinPermiso } from '@/lib/api/errores'

/** Qué hace la plantilla con un error al dar de baja un registro (DEC-129). */
export type AccionErrorBaja = {
  mensaje: string
  /**
   * `modal`: el modal sigue abierto con el mensaje y se puede reintentar; `pagina`: el modal se
   * cierra y la plantilla muestra el mensaje arriba de la lista.
   */
  lugar: 'modal' | 'pagina'
  /** El registro cambió o ya no se ve: se recargan la lista, el detalle y los requisitos. */
  recargar: boolean
}

export const MENSAJE_NO_ENCONTRADO =
  'No encontramos este registro o ya no pertenece a tu club. La lista se actualizó.'

const ERROR_GENERICO =
  'No se pudo dar de baja el registro. Revisa tu conexión e intenta de nuevo.'

/**
 * Traduce el error de `POST /registros-jugador/{id}/baja` por `codigo` y `status`, nunca por el
 * texto (DEC-63); no muestra códigos. Función pura para probarla aparte. Un `403` aquí no lleva a
 * la página sin permiso: esa regla de la plantilla es solo para las consultas.
 *
 * Refs: RF-014, RF-017, RF-003, RN-021, DEC-63, DEC-129
 */
export function accionErrorBaja(error: unknown): AccionErrorBaja {
  if (!(error instanceof ApiError)) {
    return { mensaje: ERROR_GENERICO, lugar: 'modal', recargar: false }
  }

  const { codigo, detail } = error.problema
  const mensaje = detail || ERROR_GENERICO

  // El registro ya no está ACTIVO (o hubo una carrera) o la temporada se cerró: la fila cambió.
  if (codigo === 'RF-017' || codigo === 'RF-003') {
    return { mensaje, lugar: 'pagina', recargar: true }
  }
  // Fuera de ámbito o inexistente: no se distingue cuál (VAL-024).
  if (esNoEncontrado(error)) {
    return { mensaje: MENSAJE_NO_ENCONTRADO, lugar: 'pagina', recargar: true }
  }
  if (esSinPermiso(error)) {
    return { mensaje, lugar: 'pagina', recargar: false }
  }
  // RN-021, VALIDACION y lo demás: el detail explica la causa y se puede corregir el motivo.
  return { mensaje, lugar: 'modal', recargar: false }
}

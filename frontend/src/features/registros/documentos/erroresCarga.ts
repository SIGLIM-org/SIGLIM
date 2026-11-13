import { ApiError } from '@/lib/api/ApiError'

/** Qué se recarga tras un error: nada, solo los requisitos o el registro completo. */
export type Recarga = 'nada' | 'requisitos' | 'todo'

/** Qué hace la página de documentos con un error de carga o reemplazo (DEC-114). */
export type AccionErrorCarga = {
  /** `archivo`: se marca junto al archivo elegido; `general`: aviso de la página. */
  lugar: 'archivo' | 'general'
  mensaje: string
  recargar: Recarga
  /** El registro o la temporada ya no admiten cargas. */
  soloLectura: boolean
}

const ERROR_GENERICO = 'No se pudo subir el archivo. Revisa tu conexión e intenta de nuevo.'

/**
 * Traduce el error de `POST /registros-jugador/{id}/documentos` o `POST /documentos/{id}/reemplazo`
 * por `codigo`, nunca por el texto (DEC-63). El mensaje es siempre el `detail` del backend.
 *
 * Refs: RF-108, RF-109, RF-017, RF-003, DEC-112, DEC-114
 */
export function accionErrorCarga(error: unknown): AccionErrorCarga {
  if (!(error instanceof ApiError)) {
    return { lugar: 'general', mensaje: ERROR_GENERICO, recargar: 'nada', soloLectura: false }
  }

  const { codigo, detail } = error.problema
  const mensaje = detail || ERROR_GENERICO

  switch (codigo) {
    case 'ARCHIVO_DEMASIADO_GRANDE':
    case 'TIPO_ARCHIVO_NO_PERMITIDO':
      return { lugar: 'archivo', mensaje, recargar: 'nada', soloLectura: false }
    // El registro ya se resolvió (RF-017) o la temporada se cerró (RF-003).
    case 'RF-017':
    case 'RF-003':
      return { lugar: 'general', mensaje, recargar: 'todo', soloLectura: true }
    // Cupo lleno o el documento cambió mientras tanto: los requisitos muestran lo vigente.
    case 'DUPLICADO':
    case 'RF-109':
      return { lugar: 'general', mensaje, recargar: 'requisitos', soloLectura: false }
    default:
      // RF-108, REFERENCIA_INVALIDA, VALIDACION y lo demás: el detail explica la causa.
      return { lugar: 'general', mensaje, recargar: 'nada', soloLectura: false }
  }
}

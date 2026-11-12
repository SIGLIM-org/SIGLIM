import type { Recarga } from '@/features/registros/documentos/erroresCarga'
import { faltantesDeError, type Faltante } from '@/features/registros/validacion/faltantes'
import { ApiError } from '@/lib/api/ApiError'

/** Qué hace la bandeja con un error al validar un documento o un registro (DEC-114). */
export type AccionErrorValidacion = {
  mensaje: string
  recargar: Recarga
  /** La temporada se cerró: no se ofrecen más acciones. */
  soloLectura: boolean
  /** Solo en `422 VAL-001`. */
  faltantes: Faltante[]
}

export const MENSAJE_YA_RESUELTO = 'Otra persona ya resolvió este registro.'

export const MENSAJE_SEPARACION =
  'No puedes validar un registro o documento que tú mismo diste de alta o cargaste. Pide a otra persona de la liga que lo revise.'

const ERROR_GENERICO = 'No se pudo guardar la validación. Revisa tu conexión e intenta de nuevo.'

/**
 * Traduce el error de `POST /documentos/{id}/validacion` o `POST /registros-jugador/{id}/validacion`
 * por `codigo`, nunca por el texto (DEC-63); no muestra códigos. Función pura para probarla aparte.
 *
 * Refs: RF-017, RF-109, RF-003, RF-013, VAL-001, VAL-022, VAL-025, DEC-114
 */
export function accionErrorValidacion(error: unknown): AccionErrorValidacion {
  const accion = (
    mensaje: string,
    recargar: Recarga = 'nada',
    extra: Partial<AccionErrorValidacion> = {},
  ): AccionErrorValidacion => ({ mensaje, recargar, soloLectura: false, faltantes: [], ...extra })

  if (!(error instanceof ApiError)) {
    return accion(ERROR_GENERICO)
  }

  const { codigo, detail } = error.problema
  const mensaje = detail || ERROR_GENERICO

  switch (codigo) {
    case 'RF-017':
      return accion(MENSAJE_YA_RESUELTO, 'todo')
    case 'RF-109':
      return accion(mensaje, 'requisitos')
    case 'RF-003':
      return accion(mensaje, 'todo', { soloLectura: true })
    case 'VAL-022':
      return accion(MENSAJE_SEPARACION)
    case 'DUPLICADO':
      return accion(mensaje, 'todo')
    case 'VAL-001':
      return accion(mensaje, 'requisitos', { faltantes: faltantesDeError(error) })
    default:
      // RF-013, VAL-025, VALIDACION y lo demás: el detail explica la causa.
      return accion(mensaje)
  }
}

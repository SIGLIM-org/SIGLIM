import { ApiError } from '@/lib/api/ApiError'

/** La API negó el permiso (403 `VAL-015`, H-02.4). */
export function esSinPermiso(error: unknown): boolean {
  return error instanceof ApiError && error.problema.status === 403
}

/** El recurso no existe o está fuera del ámbito; no se distingue cuál (VAL-024). */
export function esNoEncontrado(error: unknown): boolean {
  return error instanceof ApiError && error.problema.status === 404
}

/**
 * Política de reintentos de las consultas: un 4xx no cambia al repetirlo, así que
 * solo se reintenta, una vez, lo demás (red, 5xx).
 */
export function reintentarConsulta(fallos: number, error: unknown): boolean {
  if (
    error instanceof ApiError &&
    error.problema.status >= 400 &&
    error.problema.status < 500
  ) {
    return false
  }

  return fallos < 1
}

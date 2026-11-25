import { ApiError } from '@/lib/api/ApiError'

/** El `detail` del problema (ya explica la causa) o un mensaje genérico si no hubo respuesta. */
export function mensajeDeError(error: unknown, generico: string): string {
  return error instanceof ApiError ? error.problema.detail : generico
}

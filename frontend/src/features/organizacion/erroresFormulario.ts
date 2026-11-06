import { ApiError } from '@/lib/api/ApiError'

/** Máximo del nombre de club y de equipo (CrearClubRequest, CrearEquipoRequest). */
export const MAXIMO_NOMBRE = 150

/** Mensajes de `errores` del problema que corresponden a campos del formulario (convenciones §3). */
export function erroresDeCampos(error: ApiError, campos: readonly string[]): Record<string, string> {
  return Object.fromEntries(
    error.errores.filter(({ campo }) => campos.includes(campo)).map(({ campo, mensaje }) => [campo, mensaje]),
  )
}

/** Texto para un fallo que no trae problema RFC 9457 (p. ej. sin red). */
export function detalleDeFallo(error: unknown, alternativa: string): string {
  return error instanceof ApiError ? error.problema.detail : alternativa
}

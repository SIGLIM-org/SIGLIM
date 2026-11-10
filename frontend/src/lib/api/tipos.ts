/**
 * Tipos del contrato REST (docs/arquitectura/convenciones-api.md).
 * Los DTO de cada módulo viven en `features/<modulo>/tipos.ts`.
 */

/** Campo señalado en una respuesta de error (convenciones §3). */
export type ErrorCampo = {
  campo: string
  mensaje: string
}

/** Cuerpo `application/problem+json` (RFC 9457) que devuelve la API. */
export type Problema = {
  type?: string
  title: string
  status: number
  detail: string
  instance?: string
  /** Código del ERS (`VAL-004`, `RF-039`…) o técnico (`NO_AUTENTICADO`). */
  codigo: string
  errores?: ErrorCampo[]
  /**
   * Solo en advertencias aceptables. El campo con el que se confirma el reintento
   * depende del endpoint: `motivoAceptacion` en la localía (VAL-027) y
   * `confirmarPartidosAfectados` en canchas (RF-030).
   */
  aceptable?: boolean
  /** Miembros del problema que no son estándar ni de `ErrorCampo`, p. ej. `partidosAfectados`. */
  extensiones?: Record<string, unknown>
}

/** Envoltura de los listados paginados (convenciones §4). */
export type Pagina<T> = {
  contenido: T[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

/** Parámetros comunes de un listado paginado (convenciones §4). */
export type ParametrosPagina = {
  /** Empieza en 0; por defecto 0. */
  pagina?: number
  /** Por defecto 20; máximo 100. */
  tamano?: number
  /** `campo,asc|desc`; puede repetirse. */
  orden?: string | string[]
}

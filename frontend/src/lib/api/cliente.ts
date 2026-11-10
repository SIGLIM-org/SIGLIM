import { env } from '@/config/env'
import { ApiError } from '@/lib/api/ApiError'
import type { Problema } from '@/lib/api/tipos'
import { supabase } from '@/lib/supabase'

export { ApiError } from '@/lib/api/ApiError'
export type {
  ErrorCampo,
  Pagina,
  ParametrosPagina,
  Problema,
} from '@/lib/api/tipos'

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type OpcionesPeticion = Omit<RequestInit, 'body' | 'method'> & {
  metodo?: Metodo
  /**
   * Un `FormData` se envía tal cual como `multipart/form-data`, sin fijar `Content-Type` para que
   * el navegador agregue el `boundary`. Cualquier otro valor se serializa como JSON y fija
   * `Content-Type: application/json`.
   */
  cuerpo?: unknown
}

function construirUrl(ruta: string): string {
  return `${env.apiUrl.replace(/\/+$/, '')}/${ruta.replace(/^\/+/, '')}`
}

/** El token sale de Supabase Auth; el backend vuelve a validarlo (ADR-0003). */
async function tokenActual(): Promise<string | undefined> {
  const { data } = await supabase.auth.getSession()
  return data.session?.access_token
}

/** Miembros del problema con campo propio; los demás van a `extensiones`. */
const MIEMBROS_CONOCIDOS = new Set([
  'type',
  'title',
  'status',
  'detail',
  'instance',
  'codigo',
  'errores',
  'aceptable',
])

/**
 * Lee el problema RFC 9457 de una respuesta de error. Si un proxy o una caída
 * responden sin JSON, se usa un respaldo con la misma forma para que la
 * interfaz siempre reciba un `codigo` (convenciones §3).
 */
async function leerProblema(respuesta: Response): Promise<Problema> {
  const tipo = respuesta.headers.get('Content-Type') ?? ''

  if (tipo.includes('json')) {
    try {
      const problema = (await respuesta.json()) as Partial<Problema> &
        Record<string, unknown>
      const extensiones = Object.fromEntries(
        Object.entries(problema).filter(
          ([clave]) => !MIEMBROS_CONOCIDOS.has(clave),
        ),
      )

      if (typeof problema.codigo === 'string' && problema.codigo.length > 0) {
        return {
          type: problema.type,
          title: problema.title ?? 'Error',
          status: respuesta.status,
          detail: problema.detail ?? 'La solicitud no se pudo completar.',
          instance: problema.instance,
          codigo: problema.codigo,
          errores: problema.errores,
          aceptable: problema.aceptable,
          ...(Object.keys(extensiones).length > 0 ? { extensiones } : {}),
        }
      }
    } catch {
      // Respuesta sin JSON legible: se usa el respaldo.
    }
  }

  if (respuesta.status === 401) {
    return {
      title: 'Sesión no válida',
      status: 401,
      detail: 'Tu sesión venció. Inicia sesión de nuevo.',
      codigo: 'NO_AUTENTICADO',
    }
  }

  return {
    title: 'Error inesperado',
    status: respuesta.status,
    detail: 'El servicio no pudo completar la solicitud. Intenta de nuevo.',
    codigo: 'ERROR_INTERNO',
  }
}

/**
 * Ejecuta una petición al API, agrega el token de Supabase y convierte los
 * errores RFC 9457 en `ApiError` (T-03.4, convenciones de API §3).
 *
 * El cuerpo viaja como JSON o, si es un `FormData`, como `multipart/form-data` (p. ej. un
 * comprobante de pago, convenciones §6.13).
 *
 * Una respuesta exitosa sin cuerpo (204, o 200/201 construidos con `build()`)
 * resuelve `undefined` en lugar de fallar al intentar leer JSON.
 */
export async function peticion<T>(
  ruta: string,
  opciones: OpcionesPeticion = {},
): Promise<T> {
  const { metodo = 'GET', cuerpo, headers, ...resto } = opciones
  const cabeceras = new Headers(headers)

  cabeceras.set('Accept', 'application/json')

  const token = await tokenActual()

  if (token) {
    cabeceras.set('Authorization', `Bearer ${token}`)
  }

  let contenido: string | FormData | undefined

  if (cuerpo instanceof FormData) {
    // Sin Content-Type: el navegador lo fija con el boundary del multipart.
    contenido = cuerpo
  } else if (cuerpo !== undefined) {
    cabeceras.set('Content-Type', 'application/json')
    contenido = JSON.stringify(cuerpo)
  }

  let respuesta: Response

  try {
    respuesta = await fetch(construirUrl(ruta), {
      ...resto,
      method: metodo,
      headers: cabeceras,
      body: contenido,
    })
  } catch (error) {
    // Abortar es intencional: el consumidor decide qué hacer.
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }

    throw new ApiError({
      title: 'Servicio no disponible',
      status: 503,
      detail:
        'No se pudo conectar con el servicio. Revisa tu conexión e intenta de nuevo.',
      codigo: 'SERVICIO_NO_DISPONIBLE',
    })
  }

  if (!respuesta.ok) {
    throw new ApiError(await leerProblema(respuesta))
  }

  const texto = await respuesta.text()

  return (texto ? JSON.parse(texto) : undefined) as T
}

export const api = {
  get<T>(ruta: string, opciones?: OpcionesPeticion): Promise<T> {
    return peticion<T>(ruta, { ...opciones, metodo: 'GET' })
  },
  post<T>(
    ruta: string,
    cuerpo?: unknown,
    opciones?: OpcionesPeticion,
  ): Promise<T> {
    return peticion<T>(ruta, { ...opciones, metodo: 'POST', cuerpo })
  },
  put<T>(
    ruta: string,
    cuerpo?: unknown,
    opciones?: OpcionesPeticion,
  ): Promise<T> {
    return peticion<T>(ruta, { ...opciones, metodo: 'PUT', cuerpo })
  },
  patch<T>(
    ruta: string,
    cuerpo?: unknown,
    opciones?: OpcionesPeticion,
  ): Promise<T> {
    return peticion<T>(ruta, { ...opciones, metodo: 'PATCH', cuerpo })
  },
  delete<T>(ruta: string, opciones?: OpcionesPeticion): Promise<T> {
    return peticion<T>(ruta, { ...opciones, metodo: 'DELETE' })
  },
}

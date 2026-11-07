import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '@/lib/api/cliente'

const { obtenerSesion } = vi.hoisted(() => ({ obtenerSesion: vi.fn() }))

vi.mock('@/lib/supabase', () => ({
  supabase: { auth: { getSession: obtenerSesion } },
}))

vi.mock('@/config/env', () => ({
  env: {
    supabaseUrl: 'https://supabase.test',
    supabasePublishableKey: 'clave-de-prueba',
    apiUrl: '/api/v1',
  },
}))

type RespuestaSimulada = {
  ok: boolean
  status: number
  headers: { get(nombre: string): string | null }
  text(): Promise<string>
  json(): Promise<unknown>
}

function respuestaJson(status: number, cuerpo: unknown): RespuestaSimulada {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (nombre) =>
        nombre.toLowerCase() === 'content-type' ? 'application/json' : null,
    },
    text: async () => (cuerpo === undefined ? '' : JSON.stringify(cuerpo) ?? ''),
    json: async () => {
      if (cuerpo === undefined) {
        throw new SyntaxError('Unexpected end of JSON input')
      }

      return cuerpo
    },
  }
}

function respuestaProblema(
  status: number,
  problema: unknown,
): RespuestaSimulada {
  return {
    ...respuestaJson(status, problema),
    headers: { get: () => 'application/problem+json' },
  }
}

function simularFetch(respuesta: RespuestaSimulada) {
  const buscar = vi.fn().mockResolvedValue(respuesta)
  vi.stubGlobal('fetch', buscar)
  return buscar
}

function opcionesDe(buscar: ReturnType<typeof vi.fn>) {
  return buscar.mock.calls[0] as [string, RequestInit]
}

describe('api', () => {
  beforeEach(() => {
    obtenerSesion.mockReset()
    obtenerSesion.mockResolvedValue({
      data: { session: { access_token: 'token-de-prueba' } },
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('agrega el token de Supabase y llama a /api/v1', async () => {
    const buscar = simularFetch(respuestaJson(200, { id: 'perfil-1' }))

    await expect(api.get('/yo')).resolves.toEqual({ id: 'perfil-1' })

    const [url, opciones] = opcionesDe(buscar)
    expect(url).toBe('/api/v1/yo')
    expect(opciones.method).toBe('GET')
    expect(new Headers(opciones.headers).get('Authorization')).toBe(
      'Bearer token-de-prueba',
    )
  })

  it('omite Authorization cuando no hay sesión', async () => {
    obtenerSesion.mockResolvedValue({ data: { session: null } })
    const buscar = simularFetch(respuestaJson(200, []))

    await api.get('/publico/programacion')

    const [, opciones] = opcionesDe(buscar)
    expect(new Headers(opciones.headers).has('Authorization')).toBe(false)
  })

  it('envía el cuerpo como JSON', async () => {
    const buscar = simularFetch(respuestaJson(201, { id: 'nuevo' }))

    await api.post('/temporadas', { nombre: '2026/27' })

    const [, opciones] = opcionesDe(buscar)
    expect(opciones.body).toBe('{"nombre":"2026/27"}')
    expect(new Headers(opciones.headers).get('Content-Type')).toBe(
      'application/json',
    )
  })

  it('RF-139: envía un FormData sin serializar ni fijar Content-Type', async () => {
    const buscar = simularFetch(respuestaJson(201, { id: 'pago-1' }))
    const formulario = new FormData()
    formulario.append('medio', 'EFECTIVO')

    await api.post('cobros/cb-1/pagos', formulario)

    const [, opciones] = opcionesDe(buscar)
    const cabeceras = new Headers(opciones.headers)
    expect(opciones.body).toBe(formulario)
    expect(cabeceras.has('Content-Type')).toBe(false)
    expect(cabeceras.get('Authorization')).toBe('Bearer token-de-prueba')
  })

  it('devuelve undefined en un 201 sin cuerpo', async () => {
    simularFetch(respuestaJson(201, undefined))

    await expect(
      api.post('/temporadas', { nombre: '2026/27' }),
    ).resolves.toBeUndefined()
  })

  it('RF-030: un 409 conserva aceptable y partidosAfectados en extensiones', async () => {
    const partidosAfectados = [{ id: 'partido-1' }]
    simularFetch(
      respuestaProblema(409, {
        title: 'Conflicto',
        status: 409,
        detail: 'El bloqueo afecta a 1 partido(s).',
        codigo: 'RF-030',
        aceptable: true,
        partidosAfectados,
      }),
    )

    await expect(api.post('canchas/c1/disponibilidad', {})).rejects.toMatchObject({
      problema: {
        codigo: 'RF-030',
        aceptable: true,
        extensiones: { partidosAfectados },
      },
    })
  })

  it('omite extensiones cuando el problema solo trae miembros conocidos', async () => {
    simularFetch(
      respuestaProblema(404, { title: 'No', status: 404, detail: 'No existe.', codigo: 'NO_ENCONTRADO' }),
    )

    const error = await api.get('canchas/c1').catch((motivo: ApiError) => motivo)

    expect((error as ApiError).problema).not.toHaveProperty('extensiones')
  })

  it('expone problema.codigo y errores en un 409', async () => {
    simularFetch(
      respuestaProblema(409, {
        type: 'https://siglim.mx/errores/val-004',
        title: 'Conflicto de programación',
        status: 409,
        detail: 'La cancha ya tiene un partido en ese bloque.',
        codigo: 'VAL-004',
        errores: [
          { campo: 'canchaId', mensaje: 'Cancha ocupada en ese bloque' },
        ],
      }),
    )

    const error = await api
      .post('/partidos', { canchaId: 'cancha-1' })
      .then(() => null, (causa: unknown) => causa)

    expect(error).toBeInstanceOf(ApiError)

    const apiError = error as ApiError
    expect(apiError.problema.codigo).toBe('VAL-004')
    expect(apiError.estado).toBe(409)
    expect(apiError.errores).toEqual([
      { campo: 'canchaId', mensaje: 'Cancha ocupada en ese bloque' },
    ])
    expect(apiError.mensajeDe('canchaId')).toBe(
      'Cancha ocupada en ese bloque',
    )
    expect(apiError.message).toBe(
      'La cancha ya tiene un partido en ese bloque.',
    )
  })

  it('responde NO_AUTENTICADO si el 401 no trae un problema legible', async () => {
    simularFetch({
      ok: false,
      status: 401,
      headers: { get: () => 'text/html' },
      text: async () => '<html>No autenticado</html>',
      json: async () => ({}),
    })

    const error = await api
      .get('/yo')
      .then(() => null, (causa: unknown) => causa)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).codigo).toBe('NO_AUTENTICADO')
  })

  it('convierte un fallo de red en SERVICIO_NO_DISPONIBLE', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new TypeError('Failed to fetch')),
    )

    const error = await api
      .get('/yo')
      .then(() => null, (causa: unknown) => causa)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).codigo).toBe('SERVICIO_NO_DISPONIBLE')
  })
})

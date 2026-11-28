import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaNotificaciones } from '@/features/notificaciones/PantallaNotificaciones'
import type { Aviso } from '@/features/notificaciones/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, post, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR: Permiso = {
  codigo: 'notificaciones.aviso:CONSULTAR',
  modulo: 'M-14',
  ambito: 'CLUB',
  clubId: 'club-1',
}

const SIN_LEER: Aviso = {
  id: 'aviso-1',
  evento: 'COBRO_VENCIDO',
  titulo: 'Cobro vencido',
  cuerpo: 'Venció un cobro de arbitraje por $350.00 MXN con fecha límite del 20/11/2026.',
  enlace: '/app/tesoreria',
  creadaEn: '2026-11-21T06:05:00-06:00',
  leidaEn: null,
}

const LEIDO: Aviso = {
  id: 'aviso-2',
  evento: 'PROGRAMACION_PUBLICADA',
  titulo: 'Programación publicada',
  cuerpo: 'Se publicó la jornada 3 de Libre – A. Consulta tus partidos.',
  enlace: '/app/programacion/jornadas',
  creadaEn: '2026-11-20T10:00:00-06:00',
  leidaEn: '2026-11-20T11:00:00-06:00',
}

function pagina(contenido: Aviso[], extra: object = {}) {
  return {
    contenido,
    pagina: 0,
    tamano: 20,
    totalElementos: contenido.length,
    totalPaginas: contenido.length ? 1 : 0,
    ...extra,
  }
}

/** Responde según la consulta: la campana y la pestaña «Sin leer» piden `leida=false`. */
function responder(noLeidos: Aviso[], todos: Aviso[]) {
  get.mockImplementation((ruta: string) =>
    Promise.resolve(ruta.includes('leida=false') ? pagina(noLeidos) : pagina(todos)),
  )
}

function montar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const enrutador = createMemoryRouter(
    [
      { path: '/app/notificaciones', element: <PantallaNotificaciones /> },
      { path: '/app/tesoreria', element: <h1>Tesorería</h1> },
      { path: '/app/programacion/jornadas', element: <h1>Jornadas</h1> },
    ],
    { initialEntries: ['/app/notificaciones'] },
  )

  render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

describe('PantallaNotificaciones (F-09, UI-030)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    sesion.yo.permisos = [CONSULTAR]
  })

  it('abre en «Sin leer» con el contador y los avisos sin leer', async () => {
    responder([SIN_LEER], [SIN_LEER, LEIDO])

    montar()

    const sinLeer = await screen.findByRole('tab', { name: /Sin leer/ })
    expect(sinLeer).toHaveAttribute('aria-selected', 'true')
    await waitFor(() => expect(sinLeer).toHaveTextContent('Sin leer1'))
    expect(await screen.findByRole('link', { name: /Cobro vencido/ })).toHaveAttribute(
      'href',
      '/app/tesoreria',
    )
    expect(screen.queryByText('Programación publicada')).not.toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('notificaciones?pagina=0&tamano=20&leida=false')
  })

  it('la pestaña «Todos» pide todos los avisos y distingue los leídos', async () => {
    responder([SIN_LEER], [SIN_LEER, LEIDO])
    montar()

    await userEvent.click(await screen.findByRole('tab', { name: 'Todos' }))

    const panel = screen.getByRole('tabpanel')
    expect(await within(panel).findByRole('link', { name: 'Programación publicada' })).toBeInTheDocument()
    expect(within(panel).getByText('Leído')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('notificaciones?pagina=0&tamano=20')
  })

  it('las flechas del teclado cambian de pestaña', async () => {
    responder([SIN_LEER], [SIN_LEER, LEIDO])
    montar()

    ;(await screen.findByRole('tab', { name: /Sin leer/ })).focus()
    await userEvent.keyboard('{ArrowRight}')

    expect(screen.getByRole('tab', { name: 'Todos' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Todos' })).toHaveFocus()
  })

  it('RF-142: marca un aviso como leído y lo quita de «Sin leer»', async () => {
    responder([SIN_LEER], [SIN_LEER, LEIDO])
    post.mockImplementation(() => {
      responder([], [{ ...SIN_LEER, leidaEn: '2026-11-21T07:00:00-06:00' }, LEIDO])
      return Promise.resolve({ ...SIN_LEER, leidaEn: '2026-11-21T07:00:00-06:00' })
    })
    montar()

    await userEvent.click(
      await screen.findByRole('button', { name: 'Marcar como leído: Cobro vencido' }),
    )

    expect(post).toHaveBeenCalledWith('notificaciones/aviso-1/lectura')
    expect(await screen.findByText('Estás al día')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Marcar todos como leídos' })).toBeDisabled()
  })

  it('H-25 criterio 2: abrir un aviso lleva al trámite y lo marca como leído', async () => {
    responder([SIN_LEER], [SIN_LEER, LEIDO])
    post.mockResolvedValue({ ...SIN_LEER, leidaEn: '2026-11-21T07:00:00-06:00' })
    montar()

    await userEvent.click(await screen.findByRole('link', { name: /Cobro vencido/ }))

    expect(await screen.findByRole('heading', { name: 'Tesorería' })).toBeInTheDocument()
    expect(post).toHaveBeenCalledWith('notificaciones/aviso-1/lectura')
  })

  it('abrir un aviso ya leído no vuelve a marcarlo', async () => {
    responder([], [LEIDO])
    montar()

    await userEvent.click(await screen.findByRole('tab', { name: 'Todos' }))
    await userEvent.click(await screen.findByRole('link', { name: 'Programación publicada' }))

    expect(await screen.findByRole('heading', { name: 'Jornadas' })).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('marca todos como leídos y lo confirma', async () => {
    responder([SIN_LEER], [SIN_LEER, LEIDO])
    post.mockImplementation(() => {
      responder([], [LEIDO])
      return Promise.resolve({ marcadas: 1 })
    })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Marcar todos como leídos' }))

    expect(post).toHaveBeenCalledWith('notificaciones/lectura')
    expect(await screen.findByText('Se marcó 1 aviso como leído.')).toBeInTheDocument()
    expect(await screen.findByText('Estás al día')).toBeInTheDocument()
  })

  it('muestra el detalle del error si no se pueden marcar todos', async () => {
    responder([SIN_LEER], [SIN_LEER])
    post.mockRejectedValue(
      new ApiError({
        title: 'Error',
        status: 503,
        detail: 'No se pudo conectar con el servicio. Revisa tu conexión e intenta de nuevo.',
        codigo: 'SERVICIO_NO_DISPONIBLE',
      }),
    )
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Marcar todos como leídos' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar con el servicio')
  })

  it('«Todos» vacío explica qué avisos llegarán', async () => {
    responder([], [])
    montar()

    expect(await screen.findByText('Estás al día')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Todos' }))
    expect(await screen.findByText('Aún no tienes avisos')).toBeInTheDocument()
  })

  it('VAL-015: un 403 del API se muestra con su causa', async () => {
    get.mockRejectedValue(
      new ApiError({
        title: 'Prohibido',
        status: 403,
        detail: 'Tu cuenta no tiene el permiso necesario para esta operación.',
        codigo: 'VAL-015',
      }),
    )
    montar()

    expect(await screen.findByText('No tienes permiso para consultar esto')).toBeInTheDocument()
  })

  it('sin el permiso de avisos muestra la 403 sin pedir datos', async () => {
    sesion.yo.permisos = []
    montar()

    expect(await screen.findByRole('heading', { level: 1 })).not.toHaveTextContent('Avisos')
    expect(get).not.toHaveBeenCalled()
  })
})

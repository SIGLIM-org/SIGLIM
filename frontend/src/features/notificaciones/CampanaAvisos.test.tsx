import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CampanaAvisos } from '@/features/notificaciones/CampanaAvisos'
import type { Aviso } from '@/features/notificaciones/tipos'

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post } }))

const CEDULA: Aviso = {
  id: 'aviso-cedula',
  evento: 'CEDULA_PENDIENTE',
  titulo: 'Cédula pendiente de validación',
  cuerpo: 'La cédula de Halcones vs Toros (jornada 3) espera tu validación.',
  enlace: '/app/partidos/partido-1',
  creadaEn: new Date(Date.now() - 15 * 60_000).toISOString(),
  leidaEn: null,
}

const COBRO: Aviso = {
  id: 'aviso-cobro',
  evento: 'COBRO_VENCIDO',
  titulo: 'Cobro vencido',
  cuerpo: 'Venció un cobro de arbitraje por $350.00 MXN.',
  enlace: '/app/tesoreria',
  creadaEn: new Date(Date.now() - 3 * 3_600_000).toISOString(),
  leidaEn: null,
}

function pagina(contenido: Aviso[], totalElementos = contenido.length) {
  return { contenido, pagina: 0, tamano: 5, totalElementos, totalPaginas: 1 }
}

function montar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const enrutador = createMemoryRouter(
    [
      { path: '/app', element: <CampanaAvisos /> },
      { path: '/app/partidos/:partidoId', element: <h1>Detalle del partido</h1> },
      { path: '/app/notificaciones', element: <h1>Avisos</h1> },
    ],
    { initialEntries: ['/app'] },
  )

  render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

describe('CampanaAvisos (F-09, UI-030)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
    focusManager.setFocused(undefined)
  })

  it('muestra el contador de no leídas al cargar con el total del API', async () => {
    get.mockResolvedValue(pagina([CEDULA, COBRO], 12))

    montar()

    expect(await screen.findByRole('button', { name: 'Avisos, 12 sin leer' })).toHaveTextContent('12')
    expect(get).toHaveBeenCalledWith('notificaciones?pagina=0&tamano=5&leida=false')
  })

  it('sin avisos sin leer no muestra insignia y el panel dice que está al día', async () => {
    get.mockResolvedValue(pagina([]))
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Avisos, ninguno sin leer' }))

    const panel = screen.getByRole('dialog', { name: 'Avisos' })
    expect(within(panel).getByText('Estás al día')).toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: 'Ver todos los avisos' })).toHaveAttribute(
      'href',
      '/app/notificaciones',
    )
  })

  it('H-25 criterio 3: el contador se vuelve a consultar cada 60 s con la pestaña visible', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    get.mockResolvedValueOnce(pagina([CEDULA], 1)).mockResolvedValue(pagina([CEDULA, COBRO], 2))
    montar()
    await screen.findByRole('button', { name: 'Avisos, 1 sin leer' })

    await act(() => vi.advanceTimersByTimeAsync(60_000))

    expect(await screen.findByRole('button', { name: 'Avisos, 2 sin leer' })).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('H-25 criterio 3: con la pestaña oculta no consulta y lo hace al volver', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    get.mockResolvedValue(pagina([CEDULA], 1))
    montar()
    await screen.findByRole('button', { name: 'Avisos, 1 sin leer' })

    act(() => focusManager.setFocused(false))
    await act(() => vi.advanceTimersByTimeAsync(180_000))
    expect(get).toHaveBeenCalledTimes(1)

    act(() => focusManager.setFocused(true))
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
  })

  it('H-25 criterio 2: abrir un aviso lo marca como leído y lleva a la pantalla del trámite', async () => {
    get.mockResolvedValue(pagina([CEDULA, COBRO], 2))
    post.mockResolvedValue({ ...CEDULA, leidaEn: new Date().toISOString() })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Avisos, 2 sin leer' }))
    await userEvent.click(screen.getByRole('link', { name: /Cédula pendiente de validación/ }))

    expect(await screen.findByRole('heading', { name: 'Detalle del partido' })).toBeInTheDocument()
    expect(post).toHaveBeenCalledWith('notificaciones/aviso-cedula/lectura')
  })

  it('marca todos como leídos desde el panel', async () => {
    get.mockResolvedValueOnce(pagina([CEDULA, COBRO], 2)).mockResolvedValue(pagina([]))
    post.mockResolvedValue({ marcadas: 2 })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Avisos, 2 sin leer' }))
    await userEvent.click(screen.getByRole('button', { name: 'Marcar todos como leídos' }))

    expect(post).toHaveBeenCalledWith('notificaciones/lectura')
    expect(await screen.findByText('Estás al día')).toBeInTheDocument()
  })

  it('Escape cierra el panel y devuelve el foco a la campana', async () => {
    get.mockResolvedValue(pagina([CEDULA], 1))
    montar()
    const campana = await screen.findByRole('button', { name: 'Avisos, 1 sin leer' })

    await userEvent.click(campana)
    expect(campana).toHaveAttribute('aria-expanded', 'true')
    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(campana).toHaveFocus()
  })

  it('no sigue un enlace que no sea de la aplicación interna', async () => {
    get.mockResolvedValue(pagina([{ ...CEDULA, enlace: 'https://otro-sitio.example/' }], 1))
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Avisos, 1 sin leer' }))

    const panel = screen.getByRole('dialog', { name: 'Avisos' })
    expect(within(panel).getByText('Cédula pendiente de validación')).toBeInTheDocument()
    expect(within(panel).queryByRole('link', { name: /Cédula pendiente/ })).not.toBeInTheDocument()
  })
})

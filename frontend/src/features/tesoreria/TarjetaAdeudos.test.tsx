import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TarjetaAdeudos } from '@/features/tesoreria/TarjetaAdeudos'
import type { Adeudos } from '@/features/tesoreria/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { get } }))

const CON_ADEUDOS: Adeudos = {
  total: 2350.5,
  pendiente: 1500,
  vencido: 850.5,
  cobros: 2,
  clubes: [
    {
      club: { id: 'c-1', nombre: 'Club Halcones' },
      total: 2350.5,
      pendiente: 1500,
      vencido: 850.5,
      cobros: 2,
      equipos: [],
    },
  ],
}

const SIN_ADEUDOS: Adeudos = {
  total: 0,
  pendiente: 0,
  vencido: 0,
  cobros: 0,
  clubes: [],
}

function responder(respuesta: unknown = CON_ADEUDOS) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta !== 'adeudos') {
      throw new Error(`Ruta inesperada: ${ruta}`)
    }
    if (respuesta instanceof Error) {
      throw respuesta
    }

    return respuesta
  })
}

function montar() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/app']}>
        <TarjetaAdeudos />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TarjetaAdeudos', () => {
  beforeEach(() => {
    get.mockReset()
    responder()
  })

  it('H-29.2: muestra el total, los cobros y los clubes con adeudo', async () => {
    montar()

    expect(
      screen.getByRole('heading', { name: 'Tesorería y cuotas' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('$2,350.50')).toBeInTheDocument()
    expect(screen.getByText('2 cobros en 1 club.')).toBeInTheDocument()
    expect(screen.getByText('Vencido: $850.50')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver tesorería' })).toHaveAttribute(
      'href',
      '/app/tesoreria',
    )
    expect(get).toHaveBeenCalledWith('adeudos')
  })

  it('H-29.2: sin adeudos lo indica y no muestra el vencido', async () => {
    responder(SIN_ADEUDOS)
    montar()

    expect(await screen.findByText('$0.00')).toBeInTheDocument()
    expect(screen.getByText('Sin adeudos registrados.')).toBeInTheDocument()
    expect(screen.queryByText(/Vencido:/)).not.toBeInTheDocument()
  })

  it('H-02.4: un 403 muestra la causa y no ofrece reintentar', async () => {
    responder(
      new ApiError({
        title: 'Sin permiso',
        status: 403,
        codigo: 'VAL-015',
        detail: 'No tienes el permiso tesoreria.cobro:CONSULTAR.',
      }),
    )
    montar()

    expect(
      await screen.findByText('No tienes el permiso tesoreria.cobro:CONSULTAR.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })
})

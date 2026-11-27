import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { MiDesignacion } from '@/features/arbitraje/designaciones/tipos'
import {
  PantallaMisDesignaciones,
  RUTA_MIS_DESIGNACIONES,
} from '@/features/arbitraje/mis-designaciones/PantallaMisDesignaciones'
import type { Pagina } from '@/lib/api/tipos'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/lib/api/cliente', () => ({ api: { get } }))

const ref = (id: string, nombre: string) => ({ id, nombre })

function miDesignacion(
  id: string,
  cambios: Partial<MiDesignacion['partido']> = {},
  extra: Partial<MiDesignacion> = {},
): MiDesignacion {
  return {
    designacionId: `d-${id}`,
    funcion: 'CENTRAL',
    partido: {
      id,
      inicio: '2026-11-28T10:00:00-06:00',
      finBloque: '2026-11-28T12:00:00-06:00',
      estado: 'PROGRAMADO',
      cancha: ref('k1', 'Unidad Deportiva, cancha 2'),
      equipoLocal: ref('e1', 'Halcones'),
      equipoVisitante: ref('e2', 'Toros'),
      categoria: ref('c1', 'Libre'),
      grupo: ref('g1', 'Grupo A'),
      jornada: { id: 'j1', numero: 3 },
      ...cambios,
    },
    cedula: null,
    ...extra,
  }
}

function pagina(contenido: MiDesignacion[]): Pagina<MiDesignacion> {
  return {
    contenido,
    pagina: 0,
    tamano: 20,
    totalElementos: contenido.length,
    totalPaginas: contenido.length > 0 ? 1 : 0,
  }
}

function montar() {
  const enrutador = createMemoryRouter(
    [{ path: RUTA_MIS_DESIGNACIONES, element: <PantallaMisDesignaciones /> }],
    { initialEntries: [RUTA_MIS_DESIGNACIONES] },
  )

  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

describe('PantallaMisDesignaciones (DEC-212, H-14.2)', () => {
  beforeEach(() => {
    get.mockReset()
  })

  it('H-14.2: lista los partidos con su función, el cancelado que no se juega, la cédula y el enlace a F-25', async () => {
    get.mockResolvedValue(
      pagina([
        miDesignacion('p1', {}, {
          funcion: 'ASISTENTE',
          cedula: { id: 'c1', estado: 'PENDIENTE_VALIDACION' },
        }),
        miDesignacion('p2', {
          estado: 'CANCELADO',
          equipoLocal: ref('e3', 'Pumas'),
          equipoVisitante: ref('e4', 'Lobos'),
        }),
      ]),
    )
    montar()

    const lista = await screen.findByRole('list', { name: 'Partidos designados' })
    const [primero, segundo] = within(lista).getAllByRole('article')

    expect(primero).toHaveAccessibleName('Halcones vs Toros')
    expect(primero).toHaveTextContent('Sáb 28 nov')
    expect(primero).toHaveTextContent('10:00')
    expect(primero).toHaveTextContent('Unidad Deportiva, cancha 2')
    expect(primero).toHaveTextContent('Libre · Grupo A · Jornada 3')
    expect(within(primero).getByText('Asistente')).toBeInTheDocument()
    expect(within(primero).getByText('Pendiente de validación')).toBeInTheDocument()
    expect(within(primero).getByRole('link', { name: 'Ver partido Halcones vs Toros' })).toHaveAttribute(
      'href',
      '/app/partidos/p1',
    )

    expect(within(segundo).getByText('Cancelado: no se juega')).toBeInTheDocument()
    expect(within(segundo).getByText('Central')).toBeInTheDocument()
    expect(within(segundo).getByText('Sin cédula abierta')).toBeInTheDocument()
  })

  it('H-14.2 · DEC-212: por omisión pide desde hoy y «Ver también anteriores» quita el filtro', async () => {
    const usuario = userEvent.setup()
    get.mockResolvedValue(pagina([miDesignacion('p1')]))
    montar()

    await screen.findByRole('list', { name: 'Partidos designados' })
    const primera = String(get.mock.calls[0][0])
    expect(primera).toMatch(
      /^mis-designaciones\?desde=\d{4}-\d{2}-\d{2}T00%3A00%3A00[-+]\d{2}%3A\d{2}&pagina=0&tamano=20$/,
    )

    await usuario.click(screen.getByRole('checkbox', { name: 'Ver también anteriores' }))

    await waitFor(() =>
      expect(get).toHaveBeenLastCalledWith('mis-designaciones?pagina=0&tamano=20'),
    )
  })

  it('H-14.2: sin designaciones lo dice', async () => {
    get.mockResolvedValue(pagina([]))
    montar()

    expect(await screen.findByText('No tienes designaciones publicadas.')).toBeInTheDocument()
  })
})

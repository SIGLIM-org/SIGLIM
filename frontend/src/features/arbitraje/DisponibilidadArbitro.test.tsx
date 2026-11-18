import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaArbitros, RUTA_ARBITROS } from '@/features/arbitraje/PantallaArbitros'
import type { Arbitro, DisponibilidadArbitral } from '@/features/arbitraje/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, post, borrar, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  borrar: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Comisión', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post, delete: borrar } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR = 'arbitraje.arbitro:CONSULTAR'
const DISPONIBILIDAD = 'arbitraje.disponibilidad:EDITAR'
const CLUB = { id: 'c1', nombre: 'Club Halcones' }

const VALIDADO: Arbitro = {
  id: 'ar-1',
  persona: { id: 'p1', nombreCompleto: 'Carlos Alberto Prado' },
  club: CLUB,
  estado: 'VALIDADO',
  propuestoPor: { id: 'perfil-2', nombreMostrado: 'Ana Pérez' },
  propuestoEn: '2030-08-02T06:00:00-06:00',
  validadoEn: '2030-08-03T10:00:00-06:00',
}

const PROPUESTO: Arbitro = { ...VALIDADO, estado: 'PROPUESTO', validadoEn: undefined }

const MANANA: DisponibilidadArbitral = {
  id: 'd1',
  arbitroId: 'ar-1',
  periodo: { desde: '2030-11-25T09:00:00-06:00', hasta: '2030-11-25T13:00:00-06:00' },
  disponible: true,
  creadoEn: '2030-11-20T10:00:00-06:00',
}

const TARDE: DisponibilidadArbitral = {
  ...MANANA,
  id: 'd2',
  periodo: { desde: '2030-11-25T16:00:00-06:00', hasta: '2030-11-25T18:30:00-06:00' },
  disponible: false,
}

const OTRO_DIA: DisponibilidadArbitral = {
  ...MANANA,
  id: 'd3',
  periodo: { desde: '2030-11-27T10:00:00-06:00', hasta: '2030-11-27T12:00:00-06:00' },
}

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: 'M-05',
    ambito: 'LIGA',
    clubId: null,
  }))
}

function responder(arbitro: Arbitro, periodos: DisponibilidadArbitral[] = []) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('arbitros?')) {
      return { contenido: [arbitro], pagina: 0, tamano: 1, totalElementos: 1, totalPaginas: 1 }
    }
    if (ruta === `arbitros/${arbitro.id}/disponibilidad`) {
      return periodos
    }
    if (ruta.startsWith('clubes/')) {
      return {
        club: CLUB,
        temporada: { id: 't1', nombre: 'Apertura 2030' },
        equiposActivos: 4,
        equiposPorArbitro: 4,
        cuotaArbitros: { requeridos: 1, validados: 1, cumple: true },
      }
    }
    throw new Error(`Ruta no simulada: ${ruta}`)
  })
}

function montar() {
  const enrutador = createMemoryRouter(
    [{ path: RUTA_ARBITROS, element: <PantallaArbitros /> }],
    { initialEntries: [RUTA_ARBITROS] },
  )

  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

async function abrirDisponibilidad() {
  await userEvent.click(
    await screen.findByRole('button', { name: 'Ver la disponibilidad de Carlos Alberto Prado' }),
  )
  return screen.findByRole('region', { name: 'Carlos Alberto Prado' })
}

function llenarPeriodo(detalle: HTMLElement, fecha: string, inicial: string, final: string) {
  fireEvent.change(within(detalle).getByLabelText(/^Fecha/), { target: { value: fecha } })
  fireEvent.change(within(detalle).getByLabelText(/^Hora inicial/), {
    target: { value: inicial },
  })
  fireEvent.change(within(detalle).getByLabelText(/^Hora final/), { target: { value: final } })
}

describe('Disponibilidad en el detalle del árbitro (F-20, B-09.2)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    borrar.mockReset()
    otorgar(CONSULTAR, DISPONIBILIDAD)
    responder(VALIDADO)
  })

  it('RF-022: sin el permiso de disponibilidad no se ofrece el detalle', async () => {
    otorgar(CONSULTAR)
    montar()

    expect(await screen.findByText('Carlos Alberto Prado')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /disponibilidad/i })).not.toBeInTheDocument()
  })

  it('RF-022: lista los periodos agrupados por fecha con su horario y si está disponible', async () => {
    responder(VALIDADO, [MANANA, TARDE, OTRO_DIA])
    montar()

    const detalle = await abrirDisponibilidad()
    const lunes = await within(detalle).findByRole('list', {
      name: 'Periodos del Lunes 25 de noviembre de 2030',
    })

    expect(get).toHaveBeenCalledWith('arbitros/ar-1/disponibilidad')
    expect(within(detalle).getByText('3 periodos registrados')).toBeInTheDocument()
    expect(within(lunes).getAllByRole('listitem')).toHaveLength(2)
    expect(within(lunes).getByText('09:00 – 13:00')).toBeInTheDocument()
    expect(within(lunes).getByText('Disponible')).toBeInTheDocument()
    expect(within(lunes).getByText('16:00 – 18:30')).toBeInTheDocument()
    expect(within(lunes).getByText('No disponible')).toBeInTheDocument()
    expect(
      within(detalle).getByRole('list', { name: 'Periodos del Miércoles 27 de noviembre de 2030' }),
    ).toBeInTheDocument()
  })

  it('RF-022: sin periodos muestra el estado vacío', async () => {
    montar()

    const detalle = await abrirDisponibilidad()

    expect(
      await within(detalle).findByText('Sin disponibilidad registrada'),
    ).toBeInTheDocument()
  })

  it('RF-022: rechaza una hora final que no es posterior a la inicial sin llamar al API', async () => {
    montar()

    const detalle = await abrirDisponibilidad()
    llenarPeriodo(detalle, '2030-11-25', '12:00', '12:00')
    await userEvent.click(within(detalle).getByRole('button', { name: 'Agregar periodo' }))

    expect(
      within(detalle).getByText('La hora final debe ser posterior a la inicial.'),
    ).toBeInTheDocument()
    expect(within(detalle).getByLabelText(/^Hora final/)).toHaveAttribute('aria-invalid', 'true')
    expect(post).not.toHaveBeenCalled()
  })

  it('RF-022: exige la fecha y las dos horas', async () => {
    montar()

    const detalle = await abrirDisponibilidad()
    await userEvent.click(within(detalle).getByRole('button', { name: 'Agregar periodo' }))

    expect(within(detalle).getByText('Indica la fecha.')).toBeInTheDocument()
    expect(within(detalle).getByText('Indica la hora inicial.')).toBeInTheDocument()
    expect(within(detalle).getByText('Indica la hora final.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('RF-022: registra el periodo en hora de Morelia y lo anuncia', async () => {
    post.mockResolvedValue(TARDE)
    montar()

    const detalle = await abrirDisponibilidad()
    llenarPeriodo(detalle, '2030-11-25', '16:00', '18:30')
    await userEvent.selectOptions(within(detalle).getByLabelText('Registrar como'), 'NO')
    await userEvent.click(within(detalle).getByRole('button', { name: 'Agregar periodo' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('arbitros/ar-1/disponibilidad', {
        periodo: { desde: '2030-11-25T16:00:00-06:00', hasta: '2030-11-25T18:30:00-06:00' },
        disponible: false,
      }),
    )
    expect(
      await within(detalle).findByText(
        'Se registró el periodo del Lunes 25 de noviembre de 2030, 16:00 – 18:30.',
      ),
    ).toBeInTheDocument()
    expect(within(detalle).getByLabelText(/^Hora inicial/)).toHaveValue('')
    expect(within(detalle).getByLabelText(/^Fecha/)).toHaveValue('2030-11-25')
  })

  it('RF-022: explica el 409 si el árbitro ya no está validado', async () => {
    post.mockRejectedValue(
      new ApiError({
        title: 'Conflicto',
        status: 409,
        detail: 'El árbitro no está validado.',
        codigo: 'RF-022',
      }),
    )
    montar()

    const detalle = await abrirDisponibilidad()
    llenarPeriodo(detalle, '2030-11-25', '09:00', '10:00')
    await userEvent.click(within(detalle).getByRole('button', { name: 'Agregar periodo' }))

    expect(await within(detalle).findByText('El árbitro no está validado.')).toBeInTheDocument()
    expect(
      within(detalle).getByText('No se puede registrar la disponibilidad'),
    ).toBeInTheDocument()
  })

  it('RF-022: un árbitro no validado no ofrece el alta, pero sí el retiro', async () => {
    responder(PROPUESTO, [MANANA])
    montar()

    const detalle = await abrirDisponibilidad()

    expect(
      await within(detalle).findByText(/Solo se registra disponibilidad para árbitros validados/),
    ).toBeInTheDocument()
    expect(
      within(detalle).queryByRole('button', { name: 'Agregar periodo' }),
    ).not.toBeInTheDocument()
    expect(within(detalle).getByRole('button', { name: /^Retirar el periodo/ })).toBeEnabled()
  })

  it('D-12: retira un periodo solo tras confirmarlo en el modal', async () => {
    responder(VALIDADO, [MANANA])
    borrar.mockResolvedValue(undefined)
    montar()

    const detalle = await abrirDisponibilidad()
    const retirar = await within(detalle).findByRole('button', {
      name: 'Retirar el periodo del Lunes 25 de noviembre de 2030, 09:00 – 13:00',
    })

    await userEvent.click(retirar)
    let dialogo = await screen.findByRole('dialog', { name: '¿Retirar este periodo?' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Conservar' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(borrar).not.toHaveBeenCalled()

    await userEvent.click(retirar)
    dialogo = await screen.findByRole('dialog', { name: '¿Retirar este periodo?' })
    expect(within(dialogo).getByText('09:00 – 13:00')).toBeInTheDocument()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Retirar periodo' }))

    await waitFor(() => expect(borrar).toHaveBeenCalledWith('arbitros/ar-1/disponibilidad/d1'))
    expect(
      await within(detalle).findByText(
        'Se retiró el periodo del Lunes 25 de noviembre de 2030, 09:00 – 13:00.',
      ),
    ).toBeInTheDocument()
  })

  it('cierra el detalle', async () => {
    montar()

    const detalle = await abrirDisponibilidad()
    await userEvent.click(
      within(detalle).getByRole('button', { name: 'Cerrar el detalle del árbitro' }),
    )

    expect(screen.queryByRole('region', { name: 'Carlos Alberto Prado' })).not.toBeInTheDocument()
  })
})

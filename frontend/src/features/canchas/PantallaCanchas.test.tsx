import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaCanchas } from '@/features/canchas/PantallaCanchas'
import type { Cancha, Ocupacion, PartidoEnCancha, Sede } from '@/features/canchas/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, post, patch, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post, patch } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR = 'canchas.cancha:CONSULTAR'
const EDITAR = 'canchas.cancha:EDITAR'
const MOTIVO = 'Cierre de la unidad deportiva'
const RUTA = '/app/programacion/canchas'

const CANCHA: Cancha = {
  id: 'c1',
  nombre: 'Cancha Norte',
  ubicacion: 'EXTERNA',
  direccion: 'Calle Reforma 1',
  propietario: 'EXTERNO',
  propietarioExterno: 'Sr. Pérez',
  activa: true,
  creadoEn: '2030-01-01T10:00:00-06:00',
}
const OCUPACION: Ocupacion = {
  cancha: { id: 'c1', nombre: 'Cancha Norte' },
  activa: true,
  periodo: { desde: '2026-10-26T00:00:00-06:00', hasta: '2026-11-02T00:00:00-06:00' },
  bloques: [],
}
const SEDES: Sede[] = [
  { id: 's1', nombre: 'Unidad Deportiva Morelos' },
  { id: 's2', nombre: 'Unidad Deportiva Norte' },
]
const PARTIDO: PartidoEnCancha = {
  id: 'p1',
  estado: 'PROGRAMADO',
  periodo: { desde: '2030-03-10T10:00:00-06:00', hasta: '2030-03-10T12:00:00-06:00' },
  equipoLocal: { id: 'e1', nombre: 'Halcones' },
  equipoVisitante: { id: 'e2', nombre: 'Toros' },
}

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({ codigo, modulo: 'M-06', ambito: 'LIGA', clubId: null }))
}

function pagina(contenido: Cancha[]) {
  return { contenido, pagina: 0, tamano: 20, totalElementos: contenido.length, totalPaginas: contenido.length ? 1 : 0 }
}

function error(status: number, codigo: string, detail = 'Detalle del servidor.') {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

function responder({
  lista = pagina([CANCHA]) as unknown,
  detalle = CANCHA as unknown,
}: { lista?: unknown; detalle?: unknown } = {}) {
  get.mockImplementation(async (ruta: string) => {
    const respuesta = ruta.startsWith('canchas?')
      ? lista
      : ruta === 'sedes'
        ? SEDES
        : ruta.includes('/ocupacion')
          ? OCUPACION
          : detalle

    if (respuesta instanceof Error) {
      throw respuesta
    }

    return respuesta
  })
}

function montar(ruta: string = RUTA) {
  const enrutador = createMemoryRouter(
    [{ path: `${RUTA}/:canchaId?`, element: <PantallaCanchas /> }],
    { initialEntries: [ruta] },
  )

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return enrutador
}

describe('PantallaCanchas', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    otorgar(CONSULTAR, EDITAR)
    responder()
  })

  it('H-07 criterio 1: muestra la cancha con su ubicación y propietario', async () => {
    montar()

    const fila = await screen.findByRole('link', { name: /Cancha Norte/ })

    expect(within(fila).getByText('Externa · Sr. Pérez')).toBeInTheDocument()
    expect(within(fila).getByText('Activa')).toBeInTheDocument()
    expect(within(fila).getByText('Calle Reforma 1')).toBeInTheDocument()
    expect(screen.getByText('1 cancha')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('canchas?pagina=0&tamano=20&orden=nombre%2Casc')
  })

  it('H-02.4: sin EDITAR no aparecen las acciones de escritura', async () => {
    otorgar(CONSULTAR)
    montar(`${RUTA}/c1`)

    expect(await screen.findByRole('heading', { name: 'Cancha Norte' })).toBeInTheDocument()
    for (const nombre of ['Nueva cancha', 'Bloquear intervalo', 'Agregar disponibilidad', 'Editar', 'Desactivar']) {
      expect(screen.queryByRole('button', { name: nombre })).not.toBeInTheDocument()
    }
  })

  it('H-02.4: con EDITAR aparecen las acciones de escritura', async () => {
    montar(`${RUTA}/c1`)

    expect(await screen.findByRole('button', { name: 'Bloquear intervalo' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nueva cancha' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Agregar disponibilidad' })).toBeInTheDocument()
  })

  it('VAL-015: sin CONSULTAR muestra la 403 y no pide datos', () => {
    otorgar()
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('VAL-015: una lista con 403 muestra la página 403 completa', async () => {
    responder({ lista: error(403, 'VAL-015') })
    montar()

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
  })

  it('VAL-024: un detalle con 404 muestra el mensaje y «Volver al catálogo»', async () => {
    responder({ detalle: error(404, 'NO_ENCONTRADO') })
    montar(`${RUTA}/otra?semana=2026-10-26`)

    expect(await screen.findByText(/No encontramos esta información/)).toBeInTheDocument()
    const enlaces = screen.getAllByRole('link', { name: 'Volver al catálogo' })
    expect(enlaces.at(-1)).toHaveAttribute('href', `${RUTA}?semana=2026-10-26`)
  })

  it('VAL-005: una cancha inactiva lo avisa', async () => {
    responder({ detalle: { ...CANCHA, activa: false } })
    montar(`${RUTA}/c1`)

    expect(
      await screen.findByText('Cancha inactiva: no se puede usar en la programación'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Activar' })).toBeInTheDocument()
  })

  it('la fila elegida se marca y conserva la semana', async () => {
    montar(`${RUTA}/c1?semana=2026-10-26`)

    const fila = await screen.findByRole('link', { name: /Cancha Norte/ })

    expect(fila).toHaveAttribute('aria-current', 'page')
    expect(fila).toHaveAttribute('href', `${RUTA}/c1?semana=2026-10-26`)
  })

  it('cambiar de semana actualiza ?semana y pide esa ocupación', async () => {
    const enrutador = montar(`${RUTA}/c1?semana=2026-10-26`)

    await userEvent.click(await screen.findByRole('button', { name: 'Semana siguiente' }))

    expect(enrutador.state.location.search).toBe('?semana=2026-11-02')
    expect(get).toHaveBeenCalledWith(
      `canchas/c1/ocupacion?desde=${encodeURIComponent('2026-11-02T00:00:00-06:00')}&hasta=${encodeURIComponent('2026-11-09T00:00:00-06:00')}`,
    )
  })

  it('sin canchas y sin filtros ofrece registrar; con filtros, quitarlos', async () => {
    responder({ lista: pagina([]) })
    montar()

    expect(await screen.findByRole('button', { name: 'Registrar cancha' })).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Ubicación'), 'EXTERNA')
    await userEvent.click(await screen.findByRole('button', { name: 'Quitar filtros' }))

    expect(screen.getByLabelText('Ubicación')).toHaveValue('')
    expect(get).toHaveBeenCalledWith('canchas?ubicacion=EXTERNA&pagina=0&tamano=20&orden=nombre%2Casc')
  })

  it('RF-030: desactivar con partidos reenvía con confirmación y el mismo motivo', async () => {
    patch
      .mockRejectedValueOnce(
        new ApiError({
          title: 'Conflicto',
          status: 409,
          detail: 'Desactivar la cancha afecta a 1 partido(s) en curso o futuros, que no se reprogramarán solos.',
          codigo: 'RF-030',
          aceptable: true,
          extensiones: { partidosAfectados: [PARTIDO] },
        }),
      )
      .mockResolvedValueOnce({ ...CANCHA, activa: false })
    montar(`${RUTA}/c1`)

    await userEvent.click(await screen.findByRole('button', { name: 'Desactivar' }))
    const dialogo = screen.getByRole('dialog', { name: 'Desactivar cancha' })
    await userEvent.type(within(dialogo).getByLabelText(/Motivo/), MOTIVO)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Desactivar' }))

    expect(await within(dialogo).findByText('Halcones vs Toros')).toBeInTheDocument()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Desactivar de todos modos' }))

    expect(patch).toHaveBeenNthCalledWith(1, 'canchas/c1', { activa: false, motivo: MOTIVO })
    expect(patch).toHaveBeenNthCalledWith(2, 'canchas/c1', {
      activa: false,
      motivo: MOTIVO,
      confirmarPartidosAfectados: true,
    })
    expect(await screen.findByText('Cancha desactivada.')).toBeInTheDocument()
  })

  it('H-07 criterio 3: tras bloquear avisa de los partidos que no se reprogramaron', async () => {
    post.mockResolvedValue({
      disponibilidad: {
        id: 'd1',
        tipo: 'BLOQUEO',
        periodo: PARTIDO.periodo,
        motivo: MOTIVO,
        creadoEn: '2030-03-01T10:00:00-06:00',
      },
      partidosAfectados: [PARTIDO],
    })
    montar(`${RUTA}/c1`)

    await userEvent.click(await screen.findByRole('button', { name: 'Bloquear intervalo' }))
    const dialogo = screen.getByRole('dialog', { name: /Bloquear intervalo/ })
    await userEvent.type(within(dialogo).getByLabelText(/Inicio/), '2030-03-10T10:00')
    await userEvent.type(within(dialogo).getByLabelText(/^Fin/), '2030-03-10T12:00')
    await userEvent.type(within(dialogo).getByLabelText(/Motivo/), MOTIVO)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    expect(await screen.findByText('Bloqueo registrado.')).toBeInTheDocument()
    expect(
      screen.getByText('1 partido(s) quedaron dentro del bloqueo y no se reprogramaron: reprográmalos manualmente.'),
    ).toBeInTheDocument()
  })

  it('RF-028 (CA-2): filtrar por sede pide sedeId y «Quitar filtros» lo quita', async () => {
    responder({ lista: pagina([]) })
    montar()

    await screen.findByRole('option', { name: 'Unidad Deportiva Norte' })
    await userEvent.selectOptions(screen.getByLabelText('Sede'), 'Unidad Deportiva Norte')

    expect(get).toHaveBeenCalledWith('canchas?sedeId=s2&pagina=0&tamano=20&orden=nombre%2Casc')

    await userEvent.click(await screen.findByRole('button', { name: 'Quitar filtros' }))

    expect(screen.getByLabelText('Sede')).toHaveValue('')
    expect(get).toHaveBeenLastCalledWith('canchas?pagina=0&tamano=20&orden=nombre%2Casc')
  })

  it('RF-028 (D8): la sede encabeza los datos de la cancha en la lista y en el detalle', async () => {
    const conSede = { ...CANCHA, sede: { id: 's1', nombre: 'Unidad Deportiva Morelos' } }
    responder({ lista: pagina([conSede]), detalle: conSede })
    montar(`${RUTA}/c1`)

    const fila = await screen.findByRole('link', { name: /Cancha Norte/ })

    expect(within(fila).getByText('Unidad Deportiva Morelos · Externa · Sr. Pérez')).toBeInTheDocument()
    const sede = (await screen.findByText('Sede', { selector: 'dt' })).nextElementSibling
    expect(sede).toHaveTextContent('Unidad Deportiva Morelos')
  })

  it('RF-028 (CA-3): el detalle de una cancha sin sede dice «Sin sede»', async () => {
    montar(`${RUTA}/c1`)

    const sede = (await screen.findByText('Sede', { selector: 'dt' })).nextElementSibling
    expect(sede).toHaveTextContent('Sin sede')
  })

  it('RF-028 (D6): con EDITAR el botón «Sedes» abre el modal de sedes', async () => {
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Sedes' }))

    const dialogo = screen.getByRole('dialog', { name: 'Sedes' })
    expect(await within(dialogo).findByText('Unidad Deportiva Norte')).toBeInTheDocument()
  })

  it('RF-028 (D1): solo con CONSULTAR no aparece «Sedes», pero sí el filtro por sede', async () => {
    otorgar(CONSULTAR)
    montar()

    expect(await screen.findByRole('option', { name: 'Unidad Deportiva Norte' })).toBeInTheDocument()
    expect(screen.getByLabelText('Sede')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sedes' })).not.toBeInTheDocument()
  })
})

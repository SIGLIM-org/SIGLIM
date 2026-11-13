import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Club, Equipo } from '@/features/organizacion/tipos'
import { fila, pagina, permisos } from '@/features/registros/datosPruebaRegistros'
import { PantallaPlantilla } from '@/features/registros/plantilla/PantallaPlantilla'
import { RUTA_JUGADORES } from '@/features/registros/rutas'
import type { RegistroJugadorFila } from '@/features/registros/tipos'
import type { Permiso } from '@/features/seguridad/tipos'

const { get, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Directiva Halcones', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR_REGISTRO = 'registros.registro:CONSULTAR'
const PLANTILLA = [CONSULTAR_REGISTRO, 'organizacion.equipo:CONSULTAR', 'organizacion.club:CONSULTAR']

function club(id: string, nombre: string): Club {
  return { id, nombre, estado: 'ACTIVO', creadoEn: '2026-01-01T00:00:00-06:00' }
}

function equipo(id: string, nombre: string, clubId = 'c1'): Equipo {
  return {
    id,
    nombre,
    estado: 'ACTIVO',
    club: { id: clubId, nombre: 'Club Halcones' },
    grupo: { id: 'g1', nombre: 'A' },
    categoria: { id: 'cat-1', nombre: 'Libre' },
    temporada: { id: 't-2026', nombre: 'Temporada 2026', estado: 'ACTIVA' },
    creadoEn: '2026-01-01T00:00:00-06:00',
  }
}

const ANA = fila()
const LUIS = fila({
  id: 'reg-2',
  persona: { id: 'p-2', nombreCompleto: 'Luis Mora Vega', curpEnmascarada: 'MOVL******HMN***07' },
  estado: 'ACTIVO',
  esMenor: true,
})

let servidor: { clubes: Club[]; equipos: Record<string, Equipo[]>; registros: RegistroJugadorFila[] }

function responder(cambios: Partial<typeof servidor> = {}) {
  servidor = {
    clubes: [club('c1', 'Club Halcones')],
    equipos: { c1: [equipo('e1', 'Halcones')] },
    registros: [ANA, LUIS],
    ...cambios,
  }

  get.mockImplementation(async (ruta: string) => {
    const [recurso, consulta = ''] = ruta.split('?')
    const parametros = new URLSearchParams(consulta)
    if (recurso === 'clubes') {
      return pagina(servidor.clubes)
    }
    if (recurso === 'equipos') {
      return pagina(servidor.equipos[parametros.get('clubId') ?? ''] ?? [])
    }
    if (recurso === 'registros-jugador') {
      return pagina(servidor.registros)
    }
    throw new Error(`Ruta no simulada: ${ruta}`)
  })
}

function rutasRegistros(): URLSearchParams[] {
  return get.mock.calls
    .map(([ruta]) => String(ruta))
    .filter((ruta) => ruta.startsWith('registros-jugador?'))
    .map((ruta) => new URLSearchParams(ruta.split('?')[1]))
}

function montar(entrada = RUTA_JUGADORES) {
  const enrutador = createMemoryRouter(
    [{ path: RUTA_JUGADORES, element: <PantallaPlantilla /> }],
    { initialEntries: [entrada] },
  )

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return enrutador
}

async function tabla() {
  return within(await screen.findByRole('table'))
}

describe('PantallaPlantilla (F-17, H-09.4)', () => {
  beforeEach(() => {
    get.mockReset()
    sesion.yo.permisos = permisos(...PLANTILLA)
    responder()
  })

  it('H-09.4 · DEC-116: sin los tres permisos muestra la 403', () => {
    sesion.yo.permisos = permisos(CONSULTAR_REGISTRO)
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('DEC-116: con un solo club lo elige solo y, con un solo equipo, consulta registros-jugador con equipoId, orden=nombre,asc y tamano=50', async () => {
    const enrutador = montar()

    await tabla()
    expect(screen.getByLabelText('Club')).toBeDisabled()
    expect(screen.getByLabelText('Club')).toHaveValue('c1')
    expect(screen.getByLabelText('Equipo')).toHaveValue('e1')
    const consulta = rutasRegistros().at(-1)
    expect(consulta?.get('equipoId')).toBe('e1')
    expect(consulta?.get('orden')).toBe('nombre,asc')
    expect(consulta?.get('tamano')).toBe('50')
    expect(consulta?.get('pagina')).toBe('0')
    expect(enrutador.state.location.search).toBe('?club=c1&equipo=e1')
    expect(screen.getByText('Halcones · Libre · Temporada 2026')).toBeInTheDocument()
    expect(screen.getByText('2 jugadores')).toBeInTheDocument()
  })

  it('DEC-116: con varios clubes no consulta registros hasta elegir club y equipo', async () => {
    const user = userEvent.setup()
    responder({
      clubes: [club('c1', 'Club Halcones'), club('c2', 'Club Águilas')],
      equipos: { c1: [equipo('e1', 'Halcones'), equipo('e2', 'Halcones B')], c2: [] },
    })
    montar()

    expect(
      await screen.findByText('Elige un club y un equipo para ver su plantilla'),
    ).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Club'), 'c1')
    await waitFor(() => expect(screen.getByLabelText('Equipo')).toBeEnabled())
    expect(rutasRegistros()).toHaveLength(0)

    await user.selectOptions(screen.getByLabelText('Equipo'), 'e2')

    await tabla()
    expect(rutasRegistros().at(-1)?.get('equipoId')).toBe('e2')
  })

  it('DEC-116: un club de la URL que no está en las opciones cuenta como «sin elegir»', async () => {
    responder({ clubes: [club('c1', 'Club Halcones'), club('c2', 'Club Águilas')] })
    montar(`${RUTA_JUGADORES}?club=otro&equipo=e1`)

    expect(
      await screen.findByText('Elige un club y un equipo para ver su plantilla'),
    ).toBeInTheDocument()
    expect(rutasRegistros()).toHaveLength(0)
  })

  it('DEC-116: un club sin equipos activos lo dice', async () => {
    responder({ equipos: { c1: [] } })
    montar()

    expect(
      await screen.findByText('Este club no tiene equipos en una temporada abierta'),
    ).toBeInTheDocument()
  })

  it('DEC-117: muestra el nombre, la CURP enmascarada, el estado, la elegibilidad y "Menor de edad"', async () => {
    montar()

    const filas = (await tabla()).getAllByRole('row')
    const luis = within(filas[2])
    expect(luis.getByRole('rowheader')).toHaveTextContent('Luis Mora Vega')
    expect(luis.getByText('MOVL******HMN***07')).toBeInTheDocument()
    expect(luis.getByText('Activo')).toBeInTheDocument()
    expect(luis.getByText('Elegible')).toBeInTheDocument()
    expect(luis.getByText('Menor de edad')).toBeInTheDocument()
    const ana = within(filas[1])
    expect(ana.getByText('Pendiente de validación')).toBeInTheDocument()
    expect(ana.getByText('En validación')).toBeInTheDocument()
    expect(ana.queryByText('Menor de edad')).not.toBeInTheDocument()
    expect(
      screen.getByText(/La elegibilidad se calcula con el estado del registro/),
    ).toBeInTheDocument()
  })

  it('DEC-111 · DEC-117: "Cargar documentos" solo en las filas PENDIENTE_VALIDACION y con registros.documento:CREAR', async () => {
    sesion.yo.permisos = permisos(...PLANTILLA, 'registros.documento:CREAR')
    montar()

    const filas = await tabla()
    expect(filas.getByRole('link', { name: 'Cargar documentos de Ana López Ruiz' })).toHaveAttribute(
      'href',
      '/app/organizacion/jugadores/reg-1/documentos',
    )
    expect(
      filas.queryByRole('link', { name: 'Cargar documentos de Luis Mora Vega' }),
    ).not.toBeInTheDocument()
  })

  it('DEC-117: sin registros.documento:CREAR no ofrece "Cargar documentos"', async () => {
    montar()

    expect((await tabla()).queryByRole('link', { name: /Cargar documentos/ })).not.toBeInTheDocument()
  })

  it('DEC-117: "Revisar" solo con registros.registro:VALIDAR', async () => {
    sesion.yo.permisos = permisos(...PLANTILLA, 'registros.registro:VALIDAR')
    montar()

    const filas = await tabla()
    expect(filas.getByRole('link', { name: 'Revisar el registro de Ana López Ruiz' })).toHaveAttribute(
      'href',
      '/app/organizacion/registros/reg-1',
    )
    expect(filas.getAllByRole('link', { name: /Revisar/ })).toHaveLength(1)
  })

  it('DEC-117: sin registros.registro:VALIDAR no ofrece "Revisar"', async () => {
    montar()

    expect((await tabla()).queryByRole('link', { name: /Revisar/ })).not.toBeInTheDocument()
  })

  it('DEC-108: "Registrar jugador" solo con registros.registro:EDITAR', async () => {
    montar()
    await tabla()
    expect(screen.queryByRole('link', { name: 'Registrar jugador' })).not.toBeInTheDocument()
  })

  it('DEC-108: con registros.registro:EDITAR "Registrar jugador" abre el alta', async () => {
    sesion.yo.permisos = permisos(...PLANTILLA, 'registros.registro:EDITAR')
    montar()

    await tabla()
    expect(screen.getByRole('link', { name: 'Registrar jugador' })).toHaveAttribute(
      'href',
      '/app/organizacion/jugadores/alta',
    )
  })

  it('DEC-117: la pestaña "Pendientes" envía estado=PENDIENTE_VALIDACION y "Todos" no envía estado', async () => {
    const user = userEvent.setup()
    const enrutador = montar()

    await tabla()
    expect(screen.getByRole('tab', { name: 'Todos' })).toHaveAttribute('aria-selected', 'true')
    expect(rutasRegistros().at(-1)?.has('estado')).toBe(false)

    await user.click(screen.getByRole('tab', { name: 'Pendientes' }))

    await waitFor(() =>
      expect(rutasRegistros().at(-1)?.get('estado')).toBe('PENDIENTE_VALIDACION'),
    )
    expect(enrutador.state.location.search).toContain('estado=PENDIENTE_VALIDACION')

    await user.click(screen.getByRole('tab', { name: 'Todos' }))

    await waitFor(() => expect(enrutador.state.location.search).not.toContain('estado='))
    expect(rutasRegistros().at(-1)?.has('estado')).toBe(false)
  })

  it('DEC-117: las flechas mueven la pestaña elegida', async () => {
    const user = userEvent.setup()
    montar()

    await tabla()
    screen.getByRole('tab', { name: 'Todos' }).focus()
    await user.keyboard('{ArrowRight}')

    expect(screen.getByRole('tab', { name: 'Pendientes' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Pendientes' })).toHaveFocus()
  })

  it('DEC-117: equipo sin jugadores: estado vacío con «Registrar jugador» si hay EDITAR', async () => {
    sesion.yo.permisos = permisos(...PLANTILLA, 'registros.registro:EDITAR')
    responder({ registros: [] })
    montar()

    expect(
      await screen.findByText('Este equipo aún no tiene jugadores registrados'),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Registrar jugador' })).toHaveLength(2)
    expect(screen.getByText('0 jugadores')).toBeInTheDocument()
  })

  it('DEC-117: una pestaña vacía lo dice con su nombre', async () => {
    responder({ registros: [] })
    montar(`${RUTA_JUGADORES}?club=c1&equipo=e1&estado=RECHAZADO`)

    expect(await screen.findByText('No hay jugadores en «Rechazados»')).toBeInTheDocument()
  })
})

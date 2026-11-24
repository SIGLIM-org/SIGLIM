import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaCapturaCedula } from '@/features/partidos/cedula/PantallaCapturaCedula'
import type { Cedula, JugadorDisponible } from '@/features/partidos/cedula/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, post, put, del, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Capturista', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post, put, delete: del } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({ useSesionActual: () => sesion }))

const LOCAL = { id: 'e1', nombre: 'Halcones' }
const VISITANTE = { id: 'e2', nombre: 'Toros' }

const JUGADORES: JugadorDisponible[] = [
  { registroJugadorId: 'r1', nombre: 'Ana López', equipoId: 'e1', equipoRegistro: LOCAL, categoriaSuperior: false, elegible: true, causa: null },
  {
    registroJugadorId: 'r2', nombre: 'Beto Ruiz', equipoId: 'e1', equipoRegistro: LOCAL, categoriaSuperior: false, elegible: false,
    causa: { codigo: 'RF-020', mensaje: '«Beto Ruiz» tiene una sanción vigente que le impide jugar.' },
  },
  { registroJugadorId: 'r3', nombre: 'Ciro Paz', equipoId: 'e1', equipoRegistro: { id: 'e9', nombre: 'Halcones Segunda' }, categoriaSuperior: true, elegible: true, causa: null },
  { registroJugadorId: 'r4', nombre: 'Dana Gil', equipoId: 'e2', equipoRegistro: VISITANTE, categoriaSuperior: false, elegible: true, causa: null },
]

function cedula(cambios: Partial<Cedula> = {}): Cedula {
  return {
    id: 'c1',
    partido: { id: 'p1', equipoLocal: LOCAL, equipoVisitante: VISITANTE, inicio: '2031-03-09T10:00:00-06:00', estado: 'EN_CURSO' },
    estado: 'EN_CAPTURA',
    capturista: { id: 'perfil-1', nombre: 'Capturista' },
    marcador: { golesLocal: null, golesVisitante: null, penalesLocal: null, penalesVisitante: null, noPresentado: null, porDefault: false, valorDemo: false },
    alineaciones: [],
    eventos: [],
    incidencias: [],
    observaciones: null,
    enviadaEn: null,
    plazoVenceEn: null,
    ...cambios,
  }
}

const ALINEADOS: Cedula['alineaciones'] = [
  { id: 'a1', registroJugadorId: 'r1', personaId: 'pe1', nombre: 'Ana López', equipoId: 'e1', numeroPlayera: 10, titular: true, categoriaSuperior: false },
  { id: 'a3', registroJugadorId: 'r3', personaId: 'pe3', nombre: 'Ciro Paz', equipoId: 'e1', numeroPlayera: 14, titular: false, categoriaSuperior: true },
  { id: 'a4', registroJugadorId: 'r4', personaId: 'pe4', nombre: 'Dana Gil', equipoId: 'e2', numeroPlayera: 9, titular: true, categoriaSuperior: false },
]

const COMPLETA = cedula({
  alineaciones: ALINEADOS,
  marcador: { golesLocal: 2, golesVisitante: 1, penalesLocal: null, penalesVisitante: null, noPresentado: null, porDefault: false, valorDemo: false },
})

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({ codigo, modulo: 'M-08', ambito: 'LIGA', clubId: null }))
}

function responder(actual: Cedula | ApiError, partidoEstado = 'EN_CURSO') {
  get.mockImplementation((ruta: string) => {
    if (ruta === 'partidos/p1/cedula') {
      return actual instanceof ApiError ? Promise.reject(actual) : Promise.resolve(actual)
    }
    if (ruta === 'partidos/p1') {
      return Promise.resolve({ id: 'p1', equipoLocal: LOCAL, equipoVisitante: VISITANTE, estado: partidoEstado })
    }
    if (ruta === 'cedulas/c1/jugadores') return Promise.resolve(JUGADORES)
    if (ruta === 'cedulas/c1/tipos-incidencia') {
      return Promise.resolve([{ id: 2, nombre: 'Expulsión', abreExpediente: true }])
    }
    return Promise.reject(new Error(`ruta inesperada ${ruta}`))
  })
}

function montar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const router = createMemoryRouter([{ path: '/app/partidos/:partidoId/cedula', element: <PantallaCapturaCedula /> }], {
    initialEntries: ['/app/partidos/p1/cedula'],
  })

  return render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

const NO_ENCONTRADO = new ApiError({ title: 'No encontrado', status: 404, codigo: 'NO_ENCONTRADO', detail: 'El recurso no existe o no tienes acceso a él.' })

beforeEach(() => {
  vi.resetAllMocks()
  otorgar('partidos.partido:CONSULTAR', 'partidos.cedula:CONSULTAR', 'partidos.cedula:CAPTURAR')
})

describe('PantallaCapturaCedula (F-26, H-16.3)', () => {
  it('RF-025: sin cédula y con el partido en curso, la abre', async () => {
    const usuario = userEvent.setup()
    responder(NO_ENCONTRADO)
    post.mockResolvedValue(cedula())
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Abrir cédula' }))

    expect(post).toHaveBeenCalledWith('partidos/p1/cedula')
    expect(await screen.findByRole('heading', { level: 1, name: 'Captura de cédula' })).toBeInTheDocument()
  })

  it('RF-046: con el partido programado explica que la cédula se abre en curso, sin botón', async () => {
    responder(NO_ENCONTRADO, 'PROGRAMADO')
    montar()

    expect(await screen.findByText('La cédula se abre cuando el partido está en curso.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Abrir cédula' })).not.toBeInTheDocument()
  })

  it('DEC-172: muestra las dos alineaciones; quien no puede jugar está deshabilitado con su causa', async () => {
    responder(cedula())
    montar()

    const local = await screen.findByRole('region', { name: 'Halcones' })
    expect(within(local).getByRole('checkbox', { name: 'Beto Ruiz' })).toBeDisabled()
    expect(within(local).getByText('«Beto Ruiz» tiene una sanción vigente que le impide jugar.')).toBeInTheDocument()
    expect(within(local).getByText('Sube de Halcones Segunda')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Toros' })).getByRole('checkbox', { name: 'Dana Gil' })).toBeEnabled()
  })

  it('RF-019: guarda la alineación de los dos equipos con playera y titular', async () => {
    const usuario = userEvent.setup()
    responder(cedula())
    put.mockResolvedValue(cedula({ alineaciones: ALINEADOS.slice(0, 1) }))
    montar()

    await usuario.click(await screen.findByRole('checkbox', { name: 'Ana López' }))
    await usuario.type(screen.getByLabelText('Número de playera de Ana López'), '10')
    await usuario.click(screen.getByRole('checkbox', { name: 'Ciro Paz' }))
    await usuario.click(screen.getByRole('checkbox', { name: 'Ciro Paz es titular' }))
    await usuario.click(screen.getByRole('button', { name: 'Guardar alineaciones' }))

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith('cedulas/c1/alineaciones', {
        jugadores: [
          { registroJugadorId: 'r1', equipoId: 'e1', titular: true, numeroPlayera: 10 },
          { registroJugadorId: 'r3', equipoId: 'e1', titular: false, numeroPlayera: null },
        ],
      }),
    )
  })

  it('VAL-002: el error de un jugador se muestra en su fila', async () => {
    const usuario = userEvent.setup()
    responder(cedula())
    put.mockRejectedValue(
      new ApiError({
        title: 'No procesable', status: 422, codigo: 'VAL-002', detail: 'Un jugador no puede alinearse.',
        errores: [{ campo: 'jugadores[0]', mensaje: '«Ana López» no puede jugar: su registro está suspendido.', registroJugadorId: 'r1' } as never],
      }),
    )
    montar()

    await usuario.click(await screen.findByRole('checkbox', { name: 'Ana López' }))
    await usuario.click(screen.getByRole('button', { name: 'Guardar alineaciones' }))

    expect(await screen.findByText('«Ana López» no puede jugar: su registro está suspendido.')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Un jugador no puede alinearse.')
  })

  it('RF-126: la no presentación manda solo el equipo; los penales se piden solo con empate', async () => {
    const usuario = userEvent.setup()
    responder(cedula())
    put.mockResolvedValue(cedula())
    montar()

    await usuario.type(await screen.findByLabelText('Goles de Halcones'), '1')
    await usuario.type(screen.getByLabelText('Goles de Toros'), '2')
    expect(screen.getByRole('checkbox', { name: 'Hubo tanda de penales' })).toBeDisabled()

    await usuario.click(screen.getByRole('radio', { name: 'No se presentó Toros' }))
    await usuario.click(screen.getByRole('button', { name: 'Guardar marcador' }))

    await waitFor(() => expect(put).toHaveBeenCalledWith('cedulas/c1/marcador', { noPresentadoEquipoId: 'e2' }))
  })

  it('DEC-72: el marcador por default de parámetros de demostración se rotula «No oficial»', async () => {
    responder(cedula({
      marcador: { golesLocal: 3, golesVisitante: 0, penalesLocal: null, penalesVisitante: null, noPresentado: VISITANTE, porDefault: true, valorDemo: true },
    }))
    montar()

    expect(await screen.findByText('No oficial')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'No se presentó Toros' })).toBeChecked()
  })

  it('RF-025: registra una sustitución con el que entra y quita un evento', async () => {
    const usuario = userEvent.setup()
    const conGol = cedula({
      alineaciones: ALINEADOS,
      eventos: [{ id: 'ev1', tipo: 'GOL', minuto: 12, equipoId: 'e1', jugador: { id: 'a1', nombre: 'Ana López' }, entra: null, observaciones: null }],
    })
    responder(conGol)
    post.mockResolvedValue(conGol)
    del.mockResolvedValue(undefined)
    montar()

    const eventos = await screen.findByRole('region', { name: 'Eventos del partido' })
    await usuario.selectOptions(within(eventos).getByLabelText('Tipo'), 'SUSTITUCION')
    await usuario.type(within(eventos).getByLabelText('Minuto'), '60')
    await usuario.selectOptions(within(eventos).getByLabelText('Sale'), 'a1')
    await usuario.selectOptions(within(eventos).getByLabelText('Entra'), 'a3')
    await usuario.click(within(eventos).getByRole('button', { name: 'Agregar' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('cedulas/c1/eventos', { tipo: 'SUSTITUCION', alineacionId: 'a1', minuto: 60, alineacionEntraId: 'a3' }),
    )

    await usuario.click(within(eventos).getByRole('button', { name: 'Quitar gol de Ana López' }))
    await waitFor(() => expect(del).toHaveBeenCalledWith('cedulas/c1/eventos/ev1'))
  })

  it('RF-026: registra una incidencia con un jugador y un equipo involucrados', async () => {
    const usuario = userEvent.setup()
    responder(cedula({ alineaciones: ALINEADOS }))
    post.mockResolvedValue(cedula({ alineaciones: ALINEADOS }))
    montar()

    const panel = await screen.findByRole('region', { name: 'Incidencias' })
    await usuario.selectOptions(within(panel).getByLabelText(/Tipo de incidencia/), await within(panel).findByRole('option', { name: 'Expulsión · abre expediente' }))
    await usuario.type(within(panel).getByLabelText(/Descripción/), 'Agresión al rival')
    await usuario.click(within(panel).getByRole('checkbox', { name: 'Dana Gil' }))
    await usuario.click(within(panel).getByRole('checkbox', { name: 'Halcones (equipo)' }))
    await usuario.click(within(panel).getByRole('button', { name: 'Agregar incidencia' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('cedulas/c1/incidencias', {
        tipoIncidenciaId: 2,
        descripcion: 'Agresión al rival',
        involucrados: [{ personaId: 'pe4' }, { equipoId: 'e1' }],
      }),
    )
  })

  it('VAL-020: lista lo que falta y no deja enviar una cédula incompleta', async () => {
    responder(cedula())
    montar()

    expect(await screen.findByRole('heading', { name: 'Faltan 3 datos obligatorios para enviar' })).toBeInTheDocument()
    expect(screen.getByText('Captura la alineación de «Toros».')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar a validación' })).toBeDisabled()
  })

  it('RF-122: completa se envía y queda sin edición, con el plazo del árbitro', async () => {
    const usuario = userEvent.setup()
    responder(COMPLETA)
    post.mockResolvedValue({ ...COMPLETA, estado: 'PENDIENTE_VALIDACION', enviadaEn: '2031-03-09T12:00:00-06:00', plazoVenceEn: '2031-03-11T12:00:00-06:00' })
    montar()

    expect(await screen.findByText('La cédula tiene lo obligatorio para enviarse a validación.')).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Enviar a validación' }))

    await waitFor(() => expect(post).toHaveBeenCalledWith('cedulas/c1/envio'))
    expect(await screen.findByText(/ya no se edita/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Guardar alineaciones' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar a validación' })).not.toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Ana López' })).toBeDisabled()
  })

  it('VAL-020: si el backend rechaza el envío muestra sus faltantes', async () => {
    const usuario = userEvent.setup()
    responder(COMPLETA)
    post.mockRejectedValue(new ApiError({
      title: 'No procesable', status: 422, codigo: 'VAL-020', detail: 'La cédula está incompleta.',
      extensiones: { faltantes: [{ campo: 'alineacionVisitante', mensaje: 'Captura la alineación de «Toros».' }] },
    }))
    montar()

    await usuario.click(await screen.findByRole('button', { name: 'Enviar a validación' }))

    expect(await screen.findByRole('heading', { name: 'Falta 1 dato obligatorio para enviar' })).toBeInTheDocument()
  })

  it('VAL-015: sin partidos.cedula:CAPTURAR muestra la página 403 sin pedir la cédula', () => {
    otorgar('partidos.partido:CONSULTAR')
    montar()

    expect(screen.getByRole('heading', { name: /permiso/i })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })
})

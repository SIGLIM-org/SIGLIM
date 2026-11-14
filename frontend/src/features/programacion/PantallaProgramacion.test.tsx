import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaProgramacion } from '@/features/programacion/PantallaProgramacion'
import { aIsoConDesplazamiento } from '@/features/programacion/fechas'
import type {
  Jornada,
  Partido,
  VerificacionJornada,
} from '@/features/programacion/tipos'
import type { Cancha } from '@/features/canchas/tipos'
import type { Equipo } from '@/features/organizacion/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import type { CategoriaTemporada, Temporada } from '@/features/temporadas/tipos'
import type { Pagina } from '@/lib/api/tipos'

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

const CONSULTAR = 'programacion.jornada:CONSULTAR'
const EDITAR = 'programacion.jornada:EDITAR'
const ENVIAR = 'programacion.jornada:ENVIAR'
const MOTIVO = 'Cambio de horario por lluvia'

const TEMPORADA: Temporada = {
  id: 't1',
  nombre: 'Temporada 2026-2027',
  fechaInicio: '2026-08-01',
  estado: 'ACTIVA',
  creadoEn: '2026-01-01T10:00:00-06:00',
  categoriasAsignadas: 1,
  gruposCreados: 1,
}

const CATEGORIAS: CategoriaTemporada[] = [
  {
    id: 'ct1',
    categoria: {
      id: 'cat1',
      nombre: 'Libre Primera',
      tipo: 'LIBRE',
      orden: 1,
    },
    grupos: [{ id: 'g1', nombre: 'Grupo A', equipos: 4 }],
  },
]

const EQUIPOS: Equipo[] = (
  [
    { id: 'e1', nombre: 'Halcones', estado: 'ACTIVO' },
    { id: 'e2', nombre: 'Toros', estado: 'ACTIVO' },
    { id: 'e3', nombre: 'Águilas', estado: 'ACTIVO' },
    { id: 'e4', nombre: 'Pumas', estado: 'ACTIVO' },
  ] satisfies Pick<Equipo, 'id' | 'nombre' | 'estado'>[]
).map((equipo) => ({
  ...equipo,
  club: { id: 'c1', nombre: 'Club Deportivo' },
  grupo: { id: 'g1', nombre: 'Grupo A' },
  categoria: { id: 'cat1', nombre: 'Libre Primera' },
  temporada: { id: 't1', nombre: 'Temporada 2026-2027', estado: 'ACTIVA' as const },
  creadoEn: '2026-01-01T10:00:00-06:00',
}))

const CANCHAS: Cancha[] = [
  {
    id: 'ca1',
    nombre: 'Cancha Norte',
    ubicacion: 'UNIDAD_DEPORTIVA',
    propietario: 'LIGA',
    activa: true,
    creadoEn: '2026-01-01T10:00:00-06:00',
  },
  {
    id: 'ca2',
    nombre: 'Cancha Sur',
    ubicacion: 'UNIDAD_DEPORTIVA',
    propietario: 'LIGA',
    activa: true,
    creadoEn: '2026-01-01T10:00:00-06:00',
  },
]

const PARTIDO_UNO: Partido = {
  id: 'p1',
  jornadaId: 'j1',
  equipoLocal: { id: 'e1', nombre: 'Halcones' },
  equipoVisitante: { id: 'e2', nombre: 'Toros' },
  cancha: { id: 'ca1', nombre: 'Cancha Norte' },
  inicio: '2026-11-15T10:00:00-06:00',
  finBloque: '2026-11-15T12:00:00-06:00',
  estado: 'PROGRAMADO',
  creadoEn: '2026-11-01T10:00:00-06:00',
  actualizadoEn: '2026-11-01T10:00:00-06:00',
}

const PARTIDO_DOS: Partido = {
  id: 'p2',
  jornadaId: 'j1',
  equipoLocal: { id: 'e3', nombre: 'Águilas' },
  equipoVisitante: { id: 'e4', nombre: 'Pumas' },
  cancha: { id: 'ca2', nombre: 'Cancha Sur' },
  inicio: '2026-11-15T12:00:00-06:00',
  finBloque: '2026-11-15T14:00:00-06:00',
  estado: 'PROGRAMADO',
  creadoEn: '2026-11-01T10:00:00-06:00',
  actualizadoEn: '2026-11-01T10:00:00-06:00',
}

const JORNADA: Jornada = {
  id: 'j1',
  grupoId: 'g1',
  numero: 1,
  estadoPublicacion: 'BORRADOR',
  partidos: [PARTIDO_UNO, PARTIDO_DOS],
  creadoEn: '2026-11-01T10:00:00-06:00',
  actualizadoEn: '2026-11-01T10:00:00-06:00',
}

const CONFLICTOS: VerificacionJornada = {
  conflictos: [
    {
      partidoId: 'p1',
      codigo: 'VAL-003',
      mensaje: 'Halcones ya tiene otro partido a la misma hora.',
    },
    {
      partidoId: 'p1',
      codigo: 'VAL-004',
      mensaje: 'La Cancha Norte está ocupada de 10:00 a 12:00.',
    },
    {
      partidoId: 'p2',
      codigo: 'VAL-005',
      mensaje: 'La Cancha Sur no está disponible en ese horario.',
    },
  ],
  advertencias: [],
}

function pagina<T>(contenido: T[]): Pagina<T> {
  return {
    contenido,
    pagina: 0,
    tamano: 20,
    totalElementos: contenido.length,
    totalPaginas: contenido.length ? 1 : 0,
  }
}

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: 'M-07',
    ambito: 'LIGA',
    clubId: null,
  }))
}

type Respuestas = {
  temporadas?: Pagina<Temporada>
  categorias?: CategoriaTemporada[]
  jornadas?: Pagina<Jornada>
  detalle?: Jornada
  verificacion?: VerificacionJornada
}

function responder({
  temporadas = pagina([TEMPORADA]),
  categorias = CATEGORIAS,
  jornadas = pagina([{ ...JORNADA, partidos: undefined }]),
  detalle = JORNADA,
  verificacion = CONFLICTOS,
}: Respuestas = {}) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('temporadas?')) {
      return temporadas
    }
    if (ruta === 'temporadas/t1/categorias') {
      return categorias
    }
    if (ruta.startsWith('jornadas?')) {
      return jornadas
    }
    if (ruta === 'jornadas/j1') {
      return detalle
    }
    if (ruta.startsWith('equipos?')) {
      return pagina(EQUIPOS)
    }
    if (ruta.startsWith('canchas?')) {
      return pagina(CANCHAS)
    }

    throw new Error(`GET no simulado: ${ruta}`)
  })

  post.mockImplementation(async (ruta: string) => {
    if (ruta === 'jornadas/j1/verificacion') {
      return verificacion
    }

    throw new Error(`POST no simulado: ${ruta}`)
  })

  patch.mockResolvedValue(PARTIDO_UNO)
}

function montarPantalla() {
  const clienteConsultas = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  const router = createMemoryRouter([{ path: '/', element: <PantallaProgramacion /> }])

  return render(
    <QueryClientProvider client={clienteConsultas}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  sesion.yo.permisos = []
})

describe('PantallaProgramacion (F-22, H-12.4)', () => {
  it('marca cada fila con todos sus conflictos y deshabilita el envío', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder()
    montarPantalla()

    expect(await screen.findByRole('heading', { name: 'Jornada 1' })).toBeInTheDocument()

    const filaUno = await screen.findByRole('group', {
      name: 'Conflictos del partido Halcones vs. Toros',
    })
    expect(within(filaUno).getByText('2 conflictos por resolver')).toBeInTheDocument()
    expect(
      within(filaUno).getByText(/Halcones ya tiene otro partido a la misma hora/),
    ).toBeInTheDocument()
    expect(
      within(filaUno).getByText(/La Cancha Norte está ocupada de 10:00 a 12:00/),
    ).toBeInTheDocument()

    const filaDos = screen.getByRole('group', {
      name: 'Conflictos del partido Águilas vs. Pumas',
    })
    expect(
      within(filaDos).getByText(/La Cancha Sur no está disponible en ese horario/),
    ).toBeInTheDocument()

    const resumen = screen.getByRole('complementary', { name: 'Validación de la jornada' })
    expect(within(resumen).getByText('3 conflictos por resolver')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar a aprobación' })).toBeDisabled()
  })

  it('habilita el envío cuando la verificación no encuentra conflictos', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder({ verificacion: { conflictos: [], advertencias: [] } })
    montarPantalla()

    expect(await screen.findByText('Sin conflictos')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar a aprobación' })).toBeEnabled()
  })

  it('explica que el grupo todavía no tiene jornadas', async () => {
    otorgar(CONSULTAR, EDITAR)
    responder({ jornadas: pagina([]) })
    montarPantalla()

    expect(await screen.findByText('Sin jornadas en este grupo')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Nueva jornada' }).length).toBeGreaterThan(0)
  })

  it('avisa cuando no hay temporada activa', async () => {
    otorgar(CONSULTAR, EDITAR)
    responder({ temporadas: pagina([]) })
    montarPantalla()

    expect(await screen.findByText('No hay una temporada activa')).toBeInTheDocument()
  })

  it('bloquea la ruta sin el permiso de consulta', async () => {
    responder()
    montarPantalla()

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
  })

  it('con solo consulta explica que hace falta edición y no pide jornadas', async () => {
    otorgar(CONSULTAR)
    responder()
    montarPantalla()

    expect(
      await screen.findByText('La programación requiere permiso de edición'),
    ).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('ajusta un partido con motivo y avisa que quedó en el historial', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder()
    montarPantalla()

    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Editar el partido Halcones vs. Toros',
      }),
    )

    const dialogo = await screen.findByRole('dialog', { name: 'Ajustar partido' })
    fireEvent.change(within(dialogo).getByLabelText(/Inicio/), {
      target: { value: '2026-11-15T12:00' },
    })
    await userEvent.type(within(dialogo).getByLabelText(/Motivo del ajuste/), MOTIVO)

    const guardar = within(dialogo).getByRole('button', { name: 'Guardar cambios' })
    await waitFor(() => expect(guardar).toBeEnabled())
    await userEvent.click(guardar)

    await waitFor(() =>
      expect(patch).toHaveBeenCalledWith('partidos/p1', {
        inicio: aIsoConDesplazamiento('2026-11-15T12:00'),
        motivo: MOTIVO,
      }),
    )
    expect(
      await screen.findByText('Partido ajustado. El cambio quedó en el historial y la auditoría.'),
    ).toBeInTheDocument()
  })
})

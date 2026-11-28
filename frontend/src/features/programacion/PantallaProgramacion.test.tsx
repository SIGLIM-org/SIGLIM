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
import type { PublicacionPendiente } from '@/features/publicacion/tipos'
import type { Calendario } from '@/features/programacion/calendario/tipos'
import { version } from '@/features/temporadas/datosPruebaEstructura'
import { ApiError } from '@/lib/api/ApiError'
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
const APROBAR = 'programacion.jornada:APROBAR'
const MOTIVO = 'Cambio de horario por lluvia'
const MOTIVO_LOCALIA = 'Convenio interclub por remodelación de la cancha'
const ADVERTENCIA_LOCALIA = 'El equipo local tiene cancha y el partido se programó en otra.'

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
    diasJuego: [7],
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
  equipos?: Pagina<Equipo>
  verificacion?: VerificacionJornada
  pendientes?: PublicacionPendiente[]
  calendario?: Calendario
}

const SIN_CALENDARIO: Calendario = {
  temporadaId: 't1',
  grupos: [
    {
      grupoId: 'g1',
      nombre: 'Grupo A',
      categoria: { id: 'cat1', nombre: 'Libre Primera' },
      generado: false,
      fechas: [],
    },
  ],
}

function responder({
  temporadas = pagina([TEMPORADA]),
  categorias = CATEGORIAS,
  jornadas = pagina([{ ...JORNADA, partidos: undefined }]),
  detalle = JORNADA,
  equipos = pagina(EQUIPOS),
  verificacion = CONFLICTOS,
  pendientes = [],
  calendario = SIN_CALENDARIO,
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
      return equipos
    }
    if (ruta.startsWith('canchas?')) {
      return pagina(CANCHAS)
    }
    if (ruta.startsWith('publicaciones/pendientes?')) {
      return pagina(pendientes)
    }
    if (ruta === 'temporadas/t1/calendario') {
      return calendario
    }
    if (ruta === 'temporadas/t1/parametros') {
      return { vigente: version(1, { temporadaId: 't1' }), historial: [] }
    }
    // Sin fechas del calendario, F-22 abre la programación por jornada (H-35.2).
    if (ruta.startsWith('fechas?')) {
      return pagina([])
    }

    throw new Error(`GET no simulado: ${ruta}`)
  })

  post.mockImplementation(async (ruta: string) => {
    if (ruta === 'jornadas/j1/verificacion') {
      return verificacion
    }
    if (ruta === 'jornadas/j1/envio') {
      return {
        ...detalle,
        estadoPublicacion: 'REVISION',
        enviadaPor: 'perfil-1',
        enviadaEn: '2026-11-16T18:45:00-06:00',
      }
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

    // Primera prueba del archivo: arranque en frío y, desde H-35.2, una consulta más (las fechas de la
    // temporada decide la vista). En la corrida completa en paralelo, 1 s no siempre alcanza.
    expect(
      await screen.findByRole('heading', { name: 'Jornada 1' }, { timeout: 3000 }),
    ).toBeInTheDocument()

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

  it('H-15.3: enlaza cada partido con su detalle solo con permiso de consultar partidos', async () => {
    otorgar(CONSULTAR, EDITAR, 'partidos.partido:CONSULTAR')
    responder()
    const { unmount } = montarPantalla()

    const enlace = await screen.findByRole('link', { name: 'Ver el detalle del partido Halcones vs. Toros' })
    expect(enlace.getAttribute('href')).toMatch(/^\/app\/partidos\/.+/)
    unmount()

    otorgar(CONSULTAR, EDITAR)
    responder()
    montarPantalla()

    expect(await screen.findByRole('heading', { name: 'Jornada 1' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Ver el detalle del partido/ })).not.toBeInTheDocument()
  })

  it('H-32.4: con EDITAR muestra la tarjeta del calendario y el botón para generarlo', async () => {
    otorgar(CONSULTAR, EDITAR)
    responder()
    montarPantalla()

    const tarjeta = await screen.findByRole('region', { name: 'Calendario de la temporada' })
    expect(await within(tarjeta).findByText('Sin generar · 1 grupo')).toBeInTheDocument()
    expect(within(tarjeta).getByRole('button', { name: 'Generar calendario' })).toBeEnabled()
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

  it('conserva al equipo actual del partido aunque ya no esté activo', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    const equipoDeBaja: Equipo = {
      ...EQUIPOS[0],
      id: 'e9',
      nombre: 'Baja FC',
      estado: 'BAJA',
      bajaEn: '2026-10-01',
      motivoBaja: 'Adeudo',
    }
    const partidoConBaja: Partido = {
      ...PARTIDO_UNO,
      equipoLocal: { id: 'e9', nombre: 'Baja FC' },
    }

    responder({
      equipos: pagina([...EQUIPOS, equipoDeBaja]),
      detalle: { ...JORNADA, partidos: [partidoConBaja, PARTIDO_DOS] },
      verificacion: { conflictos: [], advertencias: [] },
    })
    montarPantalla()

    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Editar el partido Baja FC vs. Toros',
      }),
    )

    const dialogo = await screen.findByRole('dialog', { name: 'Ajustar partido' })
    const local = within(dialogo).getByLabelText(/Equipo local/) as HTMLSelectElement

    expect(local.value).toBe('e9')
    expect(within(local).getByRole('option', { name: /Baja FC/ })).toHaveTextContent(
      'Equipo dado de baja',
    )
    expect(
      within(dialogo).getByText(/El equipo «Baja FC» está dado de baja/),
    ).toBeInTheDocument()
    expect(within(dialogo).getByRole('button', { name: 'Guardar cambios' })).toBeDisabled()
  })

  it('criterio 1: envía la jornada sin conflictos a aprobación con confirmación', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder({ verificacion: { conflictos: [], advertencias: [] } })
    montarPantalla()

    await screen.findByText('Sin conflictos')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar a aprobación' }))

    const dialogo = await screen.findByRole('dialog', { name: 'Enviar a aprobación' })
    expect(
      within(dialogo).getByText(/La jornada 1 de Libre Primera · Grupo A, con 2 partidos/),
    ).toBeInTheDocument()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar a aprobación' }))

    await waitFor(() => expect(post).toHaveBeenCalledWith('jornadas/j1/envio'))
    expect(
      await screen.findByText(
        'Jornada 1 enviada a aprobación. La Presidencia la revisará antes de publicarla.',
      ),
    ).toBeInTheDocument()
  })

  it('DEC-175: una jornada de una fecha no se envía sola; remite a la vista por fecha', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder({ detalle: { ...JORNADA, numeroFecha: 3 }, verificacion: { conflictos: [], advertencias: [] } })
    montarPantalla()

    expect(await screen.findByText(/Esta jornada es parte de la fecha 3/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar a aprobación' })).not.toBeInTheDocument()
  })

  it('explica el rechazo del envío por conflictos nuevos', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder({ verificacion: { conflictos: [], advertencias: [] } })
    const verificar = post.getMockImplementation()
    post.mockImplementation(async (ruta: string) => {
      if (ruta === 'jornadas/j1/envio') {
        throw new ApiError({
          title: 'Conflicto',
          status: 409,
          codigo: 'RF-039',
          detail: 'La jornada tiene 1 conflicto de programación.',
        })
      }
      return verificar?.(ruta)
    })
    montarPantalla()

    await screen.findByText('Sin conflictos')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar a aprobación' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Enviar a aprobación' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Enviar a aprobación' }))

    expect(
      await within(dialogo).findByText(
        /La jornada tiene 1 conflicto de programación. Cierra este aviso para ver los conflictos/,
      ),
    ).toBeInTheDocument()
    expect(within(dialogo).getByRole('button', { name: 'Cerrar' })).toBeInTheDocument()
  })

  it('muestra las observaciones de una jornada devuelta', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder({
      detalle: {
        ...JORNADA,
        observaciones: 'Mueve el partido de Halcones a la tarde.',
      },
      verificacion: { conflictos: [], advertencias: [] },
    })
    montarPantalla()

    const nota = await screen.findByRole('note', { name: 'Observaciones de la Presidencia' })
    expect(within(nota).getByText('Mueve el partido de Halcones a la tarde.')).toBeInTheDocument()
  })

  it('no permite enviar ni editar una jornada en revisión', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder({
      detalle: { ...JORNADA, estadoPublicacion: 'REVISION', enviadaEn: '2026-11-16T18:45:00-06:00' },
      verificacion: { conflictos: [], advertencias: [] },
    })
    montarPantalla()

    expect(await screen.findByText(/Esta jornada está en revisión de la Presidencia/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar a aprobación' })).toBeDisabled()
    expect(
      screen.queryByRole('button', { name: 'Editar el partido Halcones vs. Toros' }),
    ).not.toBeInTheDocument()
  })

  it('a quien aprueba sin editar le muestra las fechas y jornadas por revisar', async () => {
    otorgar(CONSULTAR, APROBAR)
    responder({
      pendientes: [
        {
          tipo: 'FECHA',
          id: 'f3',
          titulo: 'Fecha 3',
          contexto: 'Temporada 2026-2027 · 4 jornadas',
          partidos: 14,
          enviadoPor: { id: 'p9', nombre: 'Carlos Gómez' },
          enviadoEn: '2026-11-16T18:00:00-06:00',
        },
        {
          tipo: 'JORNADA',
          id: 'j1',
          titulo: 'Jornada 8',
          contexto: 'Libre Primera · Grupo A',
          partidos: 6,
          enviadoPor: { id: 'p9', nombre: 'Carlos Gómez' },
          enviadoEn: '2026-11-16T18:45:00-06:00',
        },
        {
          tipo: 'COMUNICADO',
          id: 'c1',
          titulo: 'Suspensión por lluvia',
          enviadoPor: { id: 'p8', nombre: 'Laura Ruiz' },
          enviadoEn: '2026-11-16T19:00:00-06:00',
        },
      ],
    })
    montarPantalla()

    expect(
      await screen.findByRole('heading', { name: 'Programación por revisar' }),
    ).toBeInTheDocument()
    const fecha = await screen.findByRole('link', { name: /Fecha 3/ })
    expect(fecha).toHaveAttribute('href', '/app/programacion/jornadas/fechas/f3/revision')
    expect(fecha).toHaveTextContent('Fecha completa')
    expect(fecha).toHaveTextContent('Temporada 2026-2027 · 4 jornadas')
    expect(fecha).toHaveTextContent('14 partidos · Enviada por Carlos Gómez')
    const enlace = await screen.findByRole('link', { name: /Jornada 8/ })
    expect(enlace).toHaveAttribute('href', '/app/programacion/jornadas/j1/revision')
    expect(enlace).toHaveTextContent('6 partidos · Enviada por Carlos Gómez')
    expect(screen.queryByText('Suspensión por lluvia')).not.toBeInTheDocument()
    expect(get).not.toHaveBeenCalledWith(expect.stringMatching(/^jornadas\?/))
  })

  it('la bandeja vacía explica cuándo aparecerá la programación por revisar', async () => {
    otorgar(CONSULTAR, APROBAR)
    responder()
    montarPantalla()

    expect(await screen.findByText('No hay programación por revisar')).toBeInTheDocument()
  })

  it('B-15.2: muestra la advertencia de localía en la fila y la acepta con motivo', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder({
      verificacion: {
        conflictos: [],
        advertencias: [{ partidoId: 'p1', codigo: 'VAL-027', mensaje: ADVERTENCIA_LOCALIA }],
      },
    })
    patch.mockResolvedValue({ ...PARTIDO_UNO, motivoLocalia: MOTIVO_LOCALIA })
    montarPantalla()

    const franja = await screen.findByRole('group', {
      name: 'Advertencia de localía del partido Halcones vs. Toros',
    })
    expect(within(franja).getByText(ADVERTENCIA_LOCALIA)).toBeInTheDocument()
    expect(
      within(franja).getByText(
        'No impide enviar la jornada: se acepta con un motivo que queda en el detalle.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar a aprobación' })).toBeEnabled()

    await userEvent.click(within(franja).getByRole('button', { name: 'Aceptar con motivo' }))

    const dialogo = await screen.findByRole('dialog', {
      name: 'Aceptar advertencia de localía',
    })
    await userEvent.type(within(dialogo).getByLabelText(/^Motivo/), MOTIVO_LOCALIA)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Aceptar advertencia' }))

    await waitFor(() =>
      expect(patch).toHaveBeenCalledWith('partidos/p1', { motivoAceptacion: MOTIVO_LOCALIA }),
    )
    expect(
      await screen.findByText(
        'Advertencia de localía aceptada. El motivo quedó en el detalle del partido y en la auditoría.',
      ),
    ).toBeInTheDocument()
  })

  it('B-15.2: un ajuste con VAL-027 conserva el motivo del ajuste y añade el de localía', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder({ verificacion: { conflictos: [], advertencias: [] } })
    patch
      .mockRejectedValueOnce(
        new ApiError({
          title: 'Conflicto',
          status: 409,
          codigo: 'VAL-027',
          detail: `${ADVERTENCIA_LOCALIA} Para aceptarlo escribe un motivo de localía de al menos 10 caracteres.`,
          aceptable: true,
          extensiones: {
            advertencias: [{ partidoId: 'p1', codigo: 'VAL-027', mensaje: ADVERTENCIA_LOCALIA }],
          },
        }),
      )
      .mockResolvedValueOnce(PARTIDO_UNO)
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
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }))

    const aviso = await within(dialogo).findByRole('alert')
    await userEvent.click(within(aviso).getByRole('button', { name: 'Aceptar con motivo' }))

    const motivo = await screen.findByRole('dialog', {
      name: 'Aceptar advertencia de localía',
    })
    await userEvent.type(within(motivo).getByLabelText(/^Motivo/), MOTIVO_LOCALIA)
    await userEvent.click(within(motivo).getByRole('button', { name: 'Guardar con motivo' }))

    await waitFor(() =>
      expect(patch).toHaveBeenLastCalledWith('partidos/p1', {
        inicio: aIsoConDesplazamiento('2026-11-15T12:00'),
        motivo: MOTIVO,
        motivoAceptacion: MOTIVO_LOCALIA,
      }),
    )
    expect(
      await screen.findByText(
        'Partido ajustado. El cambio quedó en el historial y la auditoría.',
      ),
    ).toBeInTheDocument()
  })

  it('B-15.2: el motivo aceptado se ve en el detalle del partido', async () => {
    otorgar(CONSULTAR, EDITAR)
    responder({
      detalle: {
        ...JORNADA,
        partidos: [{ ...PARTIDO_UNO, motivoLocalia: MOTIVO_LOCALIA }, PARTIDO_DOS],
      },
      verificacion: {
        conflictos: [],
        advertencias: [
          {
            partidoId: 'p1',
            codigo: 'VAL-027',
            mensaje: `${ADVERTENCIA_LOCALIA} Aceptada con el motivo de localía registrado.`,
          },
        ],
      },
    })
    montarPantalla()

    const franja = await screen.findByRole('group', {
      name: 'Advertencia de localía del partido Halcones vs. Toros',
    })
    expect(within(franja).getByText('Advertencia de localía aceptada')).toBeInTheDocument()
    expect(within(franja).getByText('Motivo registrado')).toBeInTheDocument()
    expect(within(franja).getByText(MOTIVO_LOCALIA)).toBeInTheDocument()
    expect(
      within(franja).queryByRole('button', { name: 'Aceptar con motivo' }),
    ).not.toBeInTheDocument()
  })

  it('B-15.2: si el alta devuelve VAL-027, permite aceptar la advertencia con motivo', async () => {
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder({ verificacion: { conflictos: [], advertencias: [] } })
    let intentos = 0
    post.mockImplementation(async (ruta: string) => {
      if (ruta === 'jornadas/j1/partidos') {
        intentos += 1

        if (intentos === 1) {
          throw new ApiError({
            title: 'Conflicto',
            status: 409,
            codigo: 'VAL-027',
            detail: `${ADVERTENCIA_LOCALIA} Para aceptarlo escribe un motivo de localía de al menos 10 caracteres.`,
            aceptable: true,
            extensiones: {
              advertencias: [
                { partidoId: null, codigo: 'VAL-027', mensaje: ADVERTENCIA_LOCALIA },
              ],
            },
          })
        }

        return PARTIDO_UNO
      }
      if (ruta === 'jornadas/j1/verificacion') {
        return { conflictos: [], advertencias: [] }
      }

      throw new Error(`POST no simulado: ${ruta}`)
    })
    montarPantalla()

    await screen.findByRole('heading', { name: 'Jornada 1' })
    await userEvent.click(screen.getByRole('button', { name: 'Programar partido' }))

    const dialogo = await screen.findByRole('dialog', { name: 'Programar partido' })
    const local = await within(dialogo).findByLabelText(/Equipo local/)
    await waitFor(() => expect(local).toBeEnabled())
    fireEvent.change(local, { target: { value: 'e1' } })
    fireEvent.change(within(dialogo).getByLabelText(/Equipo visitante/), {
      target: { value: 'e2' },
    })
    fireEvent.change(within(dialogo).getByLabelText(/Cancha/), { target: { value: 'ca1' } })
    fireEvent.change(within(dialogo).getByLabelText(/Inicio/), {
      target: { value: '2026-11-20T10:00' },
    })

    const programar = within(dialogo).getByRole('button', { name: 'Programar partido' })
    await waitFor(() => expect(programar).toBeEnabled())
    await userEvent.click(programar)

    const aviso = await within(dialogo).findByRole('alert')
    expect(within(aviso).getByText(ADVERTENCIA_LOCALIA)).toBeInTheDocument()
    await userEvent.click(within(aviso).getByRole('button', { name: 'Aceptar con motivo' }))

    const motivo = await screen.findByRole('dialog', {
      name: 'Aceptar advertencia de localía',
    })
    await userEvent.type(within(motivo).getByLabelText(/^Motivo/), MOTIVO_LOCALIA)
    await userEvent.click(within(motivo).getByRole('button', { name: 'Programar con motivo' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('jornadas/j1/partidos', {
        equipoLocalId: 'e1',
        equipoVisitanteId: 'e2',
        canchaId: 'ca1',
        inicio: aIsoConDesplazamiento('2026-11-20T10:00'),
        motivoAceptacion: MOTIVO_LOCALIA,
      }),
    )
    expect(
      await screen.findByText('Partido programado en la jornada 1.'),
    ).toBeInTheDocument()
  })
})

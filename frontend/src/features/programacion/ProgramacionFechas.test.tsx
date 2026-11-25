import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaProgramacion } from '@/features/programacion/PantallaProgramacion'
import type {
  FechaProgramacion,
  VerificacionFecha,
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
const MOTIVO = 'Lluvia: se reprograma toda la fecha'

const TEMPORADA: Temporada = {
  id: 't1',
  nombre: 'Temporada 2026-2027',
  fechaInicio: '2026-08-01',
  estado: 'ACTIVA',
  creadoEn: '2026-01-01T10:00:00-06:00',
  categoriasAsignadas: 2,
  gruposCreados: 2,
}

const CATEGORIAS: CategoriaTemporada[] = [
  {
    id: 'ct1',
    categoria: { id: 'cat1', nombre: 'Libre Primera', tipo: 'LIBRE', orden: 1 },
    grupos: [{ id: 'g1', nombre: 'Grupo 1', equipos: 5 }],
  },
]

const EQUIPOS: Equipo[] = (
  [
    { id: 'e1', nombre: 'Halcones', estado: 'ACTIVO' },
    { id: 'e2', nombre: 'Toros', estado: 'ACTIVO' },
    { id: 'e3', nombre: 'Águilas', estado: 'ACTIVO' },
    { id: 'e4', nombre: 'Lobos', estado: 'ACTIVO' },
    { id: 'e5', nombre: 'Osos', estado: 'ACTIVO' },
  ] satisfies Pick<Equipo, 'id' | 'nombre' | 'estado'>[]
).map((equipo) => ({
  ...equipo,
  club: { id: `c-${equipo.id}`, nombre: 'Club Deportivo' },
  grupo: { id: 'g1', nombre: 'Grupo 1' },
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
]

const FECHA: FechaProgramacion = {
  id: 'f1',
  temporadaId: 't1',
  numero: 3,
  dias: ['2026-11-14', '2026-11-15'],
  estadoPublicacion: 'BORRADOR',
  jornadas: 2,
  partidos: 2,
  sinAsignar: 1,
  bloques: [
    {
      categoria: { id: 'cat1', nombre: 'Libre Primera' },
      grupo: { id: 'g1', nombre: 'Grupo 1' },
      diasJuego: [7],
      jornadaId: 'j1',
      estadoPublicacion: 'BORRADOR',
      partidos: [
        {
          id: 'p1',
          jornadaId: 'j1',
          emparejamientoId: 'em1',
          equipoLocal: { id: 'e1', nombre: 'Halcones' },
          equipoVisitante: { id: 'e2', nombre: 'Toros' },
          cancha: { id: 'ca1', nombre: 'Cancha Norte' },
          inicio: '2026-11-15T08:00:00-06:00',
          finBloque: '2026-11-15T10:00:00-06:00',
          estado: 'PROGRAMADO',
          arbitro: { id: 'a1', nombre: 'Juan Pérez' },
          designacionAutomatica: true,
          asignadoAutomaticamente: true,
          ajustado: true,
          motivoLocalia: 'Convenio entre clubes',
        },
      ],
      descansos: [{ emparejamientoId: 'em2', equipo: { id: 'e3', nombre: 'Águilas' } }],
      sinAsignar: [
        {
          emparejamientoId: 'em3',
          equipoLocal: { id: 'e4', nombre: 'Lobos' },
          equipoVisitante: { id: 'e5', nombre: 'Osos' },
          codigo: 'VAL-007',
          causa: 'Sin horario libre el domingo para Libre Primera.',
        },
      ],
    },
    {
      categoria: { id: 'cat2', nombre: 'Veteranos' },
      grupo: { id: 'g2', nombre: 'Grupo 1' },
      diasJuego: [6],
      jornadaId: 'j2',
      estadoPublicacion: 'BORRADOR',
      partidos: [
        {
          id: 'p2',
          jornadaId: 'j2',
          equipoLocal: { id: 'v1', nombre: 'Pumas' },
          equipoVisitante: { id: 'v2', nombre: 'Leones' },
          cancha: { id: 'ca1', nombre: 'Cancha Norte' },
          inicio: '2026-11-14T10:00:00-06:00',
          finBloque: '2026-11-14T12:00:00-06:00',
          estado: 'PROGRAMADO',
          asignadoAutomaticamente: false,
          ajustado: false,
        },
      ],
      descansos: [],
      sinAsignar: [],
    },
  ],
}

const SIN_CONFLICTOS: VerificacionFecha = {
  conflictos: [],
  advertencias: [
    { jornadaId: 'j2', partidoId: 'p2', codigo: 'RF-023', mensaje: 'Sin árbitro central.' },
    { jornadaId: 'j1', emparejamientoId: 'em3', codigo: 'VAL-007', mensaje: 'Sin asignar.' },
  ],
}

const CON_CONFLICTOS: VerificacionFecha = {
  conflictos: [
    {
      jornadaId: 'j2',
      partidoId: 'p2',
      codigo: 'VAL-004',
      mensaje: 'La Cancha Norte está ocupada de 10:00 a 12:00.',
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

function responder({
  fecha = FECHA,
  verificacion = SIN_CONFLICTOS,
}: { fecha?: FechaProgramacion; verificacion?: VerificacionFecha } = {}) {
  const { bloques: _bloques, ...resumen } = fecha

  get.mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('temporadas?')) {
      return pagina([TEMPORADA])
    }
    if (ruta === 'temporadas/t1/categorias') {
      return CATEGORIAS
    }
    if (ruta.startsWith('fechas?temporadaId=t1')) {
      return pagina([resumen])
    }
    if (ruta === 'fechas/f1') {
      return fecha
    }
    if (ruta.startsWith('jornadas?')) {
      return pagina([])
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
    if (ruta === 'fechas/f1/verificacion') {
      return verificacion
    }
    if (ruta === 'fechas/f1/envio') {
      return { ...fecha, estadoPublicacion: 'REVISION' }
    }
    if (ruta === 'fechas/f1/asignacion') {
      return {}
    }
    if (ruta === 'jornadas/j1/partidos') {
      return { id: 'p9' }
    }

    throw new Error(`POST no simulado: ${ruta}`)
  })
}

function montarPantalla() {
  const clienteConsultas = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const router = createMemoryRouter([{ path: '/', element: <PantallaProgramacion /> }])

  return render(
    <QueryClientProvider client={clienteConsultas}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

async function bloque(nombre: string) {
  return screen.findByRole('region', { name: nombre })
}

describe('F-22 · Vista por fecha (H-35.2)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    otorgar(CONSULTAR, EDITAR, ENVIAR)
    responder()
  })

  it('criterio 1: muestra la fecha en bloques Categoría – Grupo con Local vs Visitante, Cancha, Horario y Árbitro', async () => {
    montarPantalla()

    expect(
      await screen.findByRole('heading', { name: 'Programación por fecha' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Fecha' })).toHaveDisplayValue(
      'Fecha 3 · Sáb 14 y Dom 15 nov · Borrador',
    )

    const libre = await bloque('Libre Primera – Grupo 1')
    const partido = within(libre).getByRole('listitem', { name: 'Halcones vs. Toros' })
    expect(within(partido).getByText('Cancha Norte')).toBeInTheDocument()
    expect(within(partido).getByText('Dom 08:00 – 10:00')).toBeInTheDocument()
    expect(within(partido).getByText('Juan Pérez')).toBeInTheDocument()
    expect(within(partido).getByText('Automático')).toBeInTheDocument()
    expect(within(partido).getByText('Ajustado')).toBeInTheDocument()
    expect(within(partido).getByText('Localía')).toBeInTheDocument()

    expect(within(libre).getByText('Águilas')).toBeInTheDocument()
    expect(within(libre).getByText(/Descansa:/)).toBeInTheDocument()
    const pendiente = within(libre).getByRole('listitem', { name: 'Sin asignar: Lobos vs. Osos' })
    expect(
      within(pendiente).getByText('Sin horario libre el domingo para Libre Primera.'),
    ).toBeInTheDocument()

    const veteranos = await bloque('Veteranos – Grupo 1')
    const sinArbitro = within(veteranos).getByRole('listitem', { name: 'Pumas vs. Leones' })
    expect(within(sinArbitro).getByText('Sin árbitro')).toBeInTheDocument()
    expect(within(sinArbitro).queryByText('Automático')).not.toBeInTheDocument()

    expect(
      await screen.findByText(/Advertencias que no impiden el envío: 1 sin árbitro · 1 sin horario libre/),
    ).toBeInTheDocument()
  })

  it('cambia a la programación por jornada y vuelve a la fecha', async () => {
    const usuario = userEvent.setup()
    montarPantalla()

    await usuario.click(await screen.findByRole('button', { name: 'Por jornada' }))
    expect(
      await screen.findByRole('heading', { name: 'Programación de jornadas' }),
    ).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Por fecha' }))
    expect(
      await screen.findByRole('heading', { name: 'Programación por fecha' }),
    ).toBeInTheDocument()
  })

  it('criterio 2: «Asignar» abre el alta con los equipos fijos y la liga al emparejamiento', async () => {
    const usuario = userEvent.setup()
    montarPantalla()

    await usuario.click(await screen.findByRole('button', { name: 'Asignar Lobos vs. Osos' }))

    const dialogo = await screen.findByRole('dialog', { name: 'Asignar partido' })
    const local = within(dialogo).getByRole('combobox', { name: /Equipo local/ })
    const visitante = within(dialogo).getByRole('combobox', { name: /Equipo visitante/ })
    await waitFor(() => expect(local).toHaveDisplayValue('Lobos'))
    expect(visitante).toHaveDisplayValue('Osos')
    expect(local).toBeDisabled()
    expect(visitante).toBeDisabled()
    expect(within(dialogo).getByText(/Sin horario libre el domingo/)).toBeInTheDocument()

    await usuario.selectOptions(within(dialogo).getByRole('combobox', { name: /Cancha/ }), 'ca1')
    await usuario.type(within(dialogo).getByLabelText(/Inicio/), '2026-11-15T12:00')
    await usuario.click(within(dialogo).getByRole('button', { name: 'Programar partido' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('jornadas/j1/partidos', {
        equipoLocalId: 'e4',
        equipoVisitanteId: 'e5',
        canchaId: 'ca1',
        inicio: '2026-11-15T12:00:00-06:00',
        emparejamientoId: 'em3',
      }),
    )
    expect(
      await screen.findByText('Partido asignado. Ya no aparece como sin asignar.'),
    ).toBeInTheDocument()
  })

  it('criterio 3: «Ajustar» abre el ajuste con motivo del partido de la fila', async () => {
    const usuario = userEvent.setup()
    montarPantalla()

    await usuario.click(await screen.findByRole('button', { name: 'Ajustar Halcones vs. Toros' }))

    const dialogo = await screen.findByRole('dialog', { name: 'Ajustar partido' })
    expect(within(dialogo).getByText(/El ajuste queda en el historial/)).toBeInTheDocument()
    expect(within(dialogo).getByText('Convenio entre clubes')).toBeInTheDocument()
  })

  it('criterio 4: con conflictos marca la fila y no permite enviar la fecha', async () => {
    responder({ verificacion: CON_CONFLICTOS })
    montarPantalla()

    expect(await screen.findByText('1 conflicto por resolver')).toBeInTheDocument()
    const veteranos = await bloque('Veteranos – Grupo 1')
    expect(
      within(veteranos).getByText('La Cancha Norte está ocupada de 10:00 a 12:00.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar fecha' })).toBeDisabled()
    expect(
      screen.getByText('Resuelve los conflictos marcados para poder enviar la fecha.'),
    ).toBeInTheDocument()
  })

  it('criterio 4: sin conflictos envía la fecha completa con confirmación', async () => {
    const usuario = userEvent.setup()
    montarPantalla()

    const enviar = await screen.findByRole('button', { name: 'Enviar fecha' })
    await waitFor(() => expect(enviar).toBeEnabled())
    await usuario.click(enviar)

    const dialogo = await screen.findByRole('dialog', { name: 'Enviar la fecha 3 a aprobación' })
    expect(within(dialogo).getByText(/1 partido queda sin asignar/)).toBeInTheDocument()
    await usuario.click(within(dialogo).getByRole('button', { name: 'Enviar fecha' }))

    await waitFor(() => expect(post).toHaveBeenCalledWith('fechas/f1/envio'))
    expect(await screen.findByText(/Fecha 3 enviada a aprobación/)).toBeInTheDocument()
  })

  it('«Asignar pendientes» pide la asignación sin regenerar', async () => {
    const usuario = userEvent.setup()
    montarPantalla()

    await usuario.click(await screen.findByRole('button', { name: 'Asignar pendientes' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('fechas/f1/asignacion', { modo: 'PENDIENTES' }),
    )
    expect(await screen.findByText(/Asignación terminada/)).toBeInTheDocument()
  })

  it('«Regenerar» exige motivo y lo envía', async () => {
    const usuario = userEvent.setup()
    montarPantalla()

    await usuario.click(await screen.findByRole('button', { name: 'Regenerar' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Regenerar la fecha 3' })
    await usuario.click(within(dialogo).getByRole('button', { name: 'Regenerar fecha' }))
    expect(post).not.toHaveBeenCalledWith('fechas/f1/asignacion', expect.anything())

    await usuario.type(within(dialogo).getByRole('textbox'), MOTIVO)
    await usuario.click(within(dialogo).getByRole('button', { name: 'Regenerar fecha' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('fechas/f1/asignacion', {
        modo: 'REGENERAR',
        motivo: MOTIVO,
      }),
    )
    expect(await screen.findByText(/Fecha regenerada/)).toBeInTheDocument()
  })

  it('una fecha en revisión no ofrece acciones ni verifica', async () => {
    responder({
      fecha: {
        ...FECHA,
        estadoPublicacion: 'REVISION',
        enviadaEn: '2026-11-12T18:00:00-06:00',
        bloques: FECHA.bloques?.map((actual) => ({ ...actual, estadoPublicacion: 'REVISION' })),
      },
    })
    montarPantalla()

    expect(await screen.findByText(/La fecha está en revisión de la Presidencia/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar fecha' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Asignar / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Ajustar / })).not.toBeInTheDocument()
    expect(post).not.toHaveBeenCalledWith('fechas/f1/verificacion')
  })

  it('muestra las observaciones de una fecha devuelta', async () => {
    responder({ fecha: { ...FECHA, observaciones: 'Mover Lobos vs Osos al sábado' } })
    montarPantalla()

    const nota = await screen.findByRole('note', { name: 'Observaciones de la Presidencia' })
    expect(within(nota).getByText('Mover Lobos vs Osos al sábado')).toBeInTheDocument()
  })

  it('sin permiso de envío no muestra «Enviar fecha»', async () => {
    otorgar(CONSULTAR, EDITAR)
    montarPantalla()

    expect(await screen.findByRole('button', { name: 'Verificar' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar fecha' })).not.toBeInTheDocument()
  })
})

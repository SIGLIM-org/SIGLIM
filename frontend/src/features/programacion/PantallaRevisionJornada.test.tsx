import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaRevisionJornada } from '@/features/programacion/PantallaRevisionJornada'
import type { Jornada, Partido } from '@/features/programacion/tipos'
import type { PublicacionPendiente } from '@/features/publicacion/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'
import type { Pagina } from '@/lib/api/tipos'

const { get, post, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'presidente', nombreMostrado: 'Ramón Medina', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR = 'programacion.jornada:CONSULTAR'
const EDITAR = 'programacion.jornada:EDITAR'
const APROBAR = 'programacion.jornada:APROBAR'
const OBSERVACIONES = 'Mueve el partido de Halcones: la cancha está en mantenimiento.'

const PARTIDOS: Partido[] = [
  {
    id: 'p1',
    jornadaId: 'j1',
    equipoLocal: { id: 'e1', nombre: 'Halcones' },
    equipoVisitante: { id: 'e2', nombre: 'Toros' },
    cancha: { id: 'ca1', nombre: 'Cancha Norte' },
    inicio: '2026-11-22T10:00:00-06:00',
    finBloque: '2026-11-22T12:00:00-06:00',
    estado: 'PROGRAMADO',
    creadoEn: '2026-11-10T10:00:00-06:00',
    actualizadoEn: '2026-11-10T10:00:00-06:00',
  },
  {
    id: 'p2',
    jornadaId: 'j1',
    equipoLocal: { id: 'e3', nombre: 'Águilas' },
    equipoVisitante: { id: 'e4', nombre: 'Pumas' },
    cancha: { id: 'ca2', nombre: 'Cancha Sur' },
    inicio: '2026-11-22T12:00:00-06:00',
    finBloque: '2026-11-22T14:00:00-06:00',
    estado: 'PROGRAMADO',
    creadoEn: '2026-11-10T10:00:00-06:00',
    actualizadoEn: '2026-11-10T10:00:00-06:00',
  },
]

const EN_REVISION: Jornada = {
  id: 'j1',
  grupoId: 'g1',
  numero: 8,
  estadoPublicacion: 'REVISION',
  partidos: PARTIDOS,
  enviadaPor: 'programador',
  enviadaEn: '2026-11-16T18:45:00-06:00',
  creadoEn: '2026-11-10T10:00:00-06:00',
  actualizadoEn: '2026-11-16T18:45:00-06:00',
}

const PENDIENTE: PublicacionPendiente = {
  tipo: 'JORNADA',
  id: 'j1',
  titulo: 'Jornada 8',
  contexto: 'Temporada 2026-2027 · Libre Primera · Grupo A',
  partidos: 2,
  enviadoPor: { id: 'programador', nombre: 'Carlos Gómez' },
  enviadoEn: '2026-11-16T18:45:00-06:00',
}

function pagina<T>(contenido: T[]): Pagina<T> {
  return {
    contenido,
    pagina: 0,
    tamano: 100,
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

function responder(jornada: Jornada = EN_REVISION) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta === 'jornadas/j1') {
      return jornada
    }
    if (ruta.startsWith('publicaciones/pendientes?')) {
      return pagina(jornada.estadoPublicacion === 'REVISION' ? [PENDIENTE] : [])
    }

    throw new Error(`GET no simulado: ${ruta}`)
  })
}

function montarPantalla() {
  const clienteConsultas = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const router = createMemoryRouter(
    [
      {
        path: '/app/programacion/jornadas/:jornadaId/revision',
        element: <PantallaRevisionJornada />,
      },
    ],
    { initialEntries: ['/app/programacion/jornadas/j1/revision'] },
  )

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

describe('PantallaRevisionJornada (F-23, H-13.2)', () => {
  it('muestra el resumen y el rol de juegos de solo lectura con quién la envió', async () => {
    otorgar(CONSULTAR, APROBAR)
    responder()
    montarPantalla()

    expect(
      await screen.findByRole('heading', { name: 'Revisión de la jornada 8' }),
    ).toBeInTheDocument()
    expect(
      await screen.findByText(/Libre Primera · Grupo A · Enviada por Carlos Gómez/),
    ).toBeInTheDocument()

    const tabla = screen.getByRole('table', { name: 'Rol de juegos' })
    expect(within(tabla).getByRole('rowheader', { name: /Halcones/ })).toBeInTheDocument()
    expect(within(tabla).getByText('Cancha Sur')).toBeInTheDocument()
    expect(within(tabla).queryByRole('button')).not.toBeInTheDocument()

    const flujo = screen.getByRole('list', { name: 'Flujo de publicación de la jornada' })
    expect(within(flujo).getByText(/Dictamen de la Presidencia/).closest('li')).toHaveAttribute(
      'aria-current',
      'step',
    )
  })

  it('criterio 2: aprueba y publica la jornada en revisión', async () => {
    otorgar(CONSULTAR, APROBAR)
    responder()
    post.mockResolvedValue({ ...EN_REVISION, estadoPublicacion: 'PUBLICADO' })
    montarPantalla()

    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar y publicar' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Aprobar y publicar' })
    expect(within(dialogo).getByText(/aparecerán en el portal público/)).toBeInTheDocument()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Aprobar y publicar' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('jornadas/j1/resolucion', { decision: 'PUBLICADO' }),
    )
    expect(
      await screen.findByText(
        'Jornada 8 aprobada y publicada. Sus partidos ya aparecen en el portal.',
      ),
    ).toBeInTheDocument()
  })

  it('criterio 3: no devuelve sin observaciones y con ellas vuelve a borrador', async () => {
    otorgar(CONSULTAR, APROBAR)
    responder()
    post.mockResolvedValue({
      ...EN_REVISION,
      estadoPublicacion: 'BORRADOR',
      observaciones: OBSERVACIONES,
    })
    montarPantalla()

    await userEvent.click(
      await screen.findByRole('button', { name: 'Devolver con observaciones' }),
    )
    const dialogo = await screen.findByRole('dialog', { name: 'Devolver con observaciones' })
    await userEvent.click(
      within(dialogo).getByRole('button', { name: 'Devolver con observaciones' }),
    )

    expect(
      within(dialogo).getByText(/Escribe observaciones de al menos 10 caracteres/),
    ).toBeInTheDocument()
    expect(within(dialogo).getByLabelText(/Observaciones para el Programador/)).toHaveAttribute(
      'aria-invalid',
      'true',
    )
    expect(post).not.toHaveBeenCalled()

    await userEvent.type(
      within(dialogo).getByLabelText(/Observaciones para el Programador/),
      OBSERVACIONES,
    )
    await userEvent.click(
      within(dialogo).getByRole('button', { name: 'Devolver con observaciones' }),
    )

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('jornadas/j1/resolucion', {
        decision: 'BORRADOR',
        observaciones: OBSERVACIONES,
      }),
    )
    expect(
      await screen.findByText(
        'Jornada 8 devuelta a borrador. El Programador verá tus observaciones.',
      ),
    ).toBeInTheDocument()
  })

  it('criterio 4: quien también puede editar jornadas no puede dictaminar', async () => {
    otorgar(CONSULTAR, EDITAR, APROBAR)
    responder()
    montarPantalla()

    expect(await screen.findByRole('button', { name: 'Aprobar y publicar' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Devolver con observaciones' })).toBeDisabled()
    expect(
      screen.getByText(/Tu cuenta también puede editar jornadas/),
    ).toBeInTheDocument()
  })

  it('no deja dictaminar a quien envió la jornada', async () => {
    otorgar(CONSULTAR, APROBAR)
    responder({ ...EN_REVISION, enviadaPor: 'presidente' })
    montarPantalla()

    expect(await screen.findByRole('button', { name: 'Aprobar y publicar' })).toBeDisabled()
    expect(
      screen.getByText('Tú enviaste esta jornada a aprobación. El dictamen corresponde a otra persona.'),
    ).toBeInTheDocument()
  })

  it('muestra la explicación del backend cuando rechaza el dictamen', async () => {
    otorgar(CONSULTAR, APROBAR)
    responder()
    post.mockRejectedValue(
      new ApiError({
        title: 'Operación no permitida',
        status: 403,
        codigo: 'RF-121',
        detail: 'Quien programa la jornada no puede aprobarla ni devolverla.',
      }),
    )
    montarPantalla()

    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar y publicar' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Aprobar y publicar' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Aprobar y publicar' }))

    expect(
      await within(dialogo).findByText(
        'Quien programa la jornada no puede aprobarla ni devolverla.',
      ),
    ).toBeInTheDocument()
  })

  it('con solo consulta muestra la jornada sin dictámenes ni bandeja', async () => {
    otorgar(CONSULTAR)
    responder()
    montarPantalla()

    expect(
      await screen.findByRole('heading', { name: 'Revisión de la jornada 8' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aprobar y publicar' })).not.toBeInTheDocument()
    expect(get).not.toHaveBeenCalledWith(expect.stringMatching(/^publicaciones/))
  })

  it('una jornada publicada ya no ofrece dictamen y muestra su nota', async () => {
    otorgar(CONSULTAR, APROBAR)
    responder({
      ...EN_REVISION,
      estadoPublicacion: 'PUBLICADO',
      aprobadaPor: 'presidente',
      aprobadaEn: '2026-11-17T09:00:00-06:00',
      publicadaEn: '2026-11-17T09:00:00-06:00',
      observaciones: 'Aprobada en la sesión del comité.',
    })
    montarPantalla()

    expect(
      await screen.findByText('Esta jornada ya está publicada. No hay dictamen pendiente.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Nota de aprobación' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aprobar y publicar' })).not.toBeInTheDocument()
  })

  it('explica cuando la jornada no existe o está fuera de su ámbito', async () => {
    otorgar(CONSULTAR, APROBAR)
    get.mockImplementation(async (ruta: string) => {
      if (ruta.startsWith('publicaciones/pendientes?')) {
        return pagina([])
      }
      throw new ApiError({
        title: 'No encontrado',
        status: 404,
        codigo: 'RECURSO_NO_ENCONTRADO',
        detail: 'La jornada no existe.',
      })
    })
    montarPantalla()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /Revisión de la jornada/ })).not.toBeInTheDocument()
  })

  it('bloquea la ruta sin el permiso de consulta', async () => {
    responder()
    montarPantalla()

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaRevisionFecha } from '@/features/programacion/PantallaRevisionFecha'
import type { FechaProgramacion } from '@/features/programacion/tipos'
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
const OBSERVACIONES = 'Mueve Lobos vs Osos al sábado: el domingo no hay cancha.'

const FECHA: FechaProgramacion = {
  id: 'f3',
  temporadaId: 't1',
  numero: 3,
  dias: ['2026-11-14', '2026-11-15'],
  estadoPublicacion: 'REVISION',
  jornadas: 2,
  partidos: 2,
  sinAsignar: 1,
  enviadaPor: 'programador',
  enviadaEn: '2026-11-12T18:00:00-06:00',
  bloques: [
    {
      categoria: { id: 'cat1', nombre: 'Libre Primera' },
      grupo: { id: 'g1', nombre: 'Grupo 1' },
      diasJuego: [7],
      jornadaId: 'j1',
      estadoPublicacion: 'REVISION',
      partidos: [
        {
          id: 'p1',
          jornadaId: 'j1',
          equipoLocal: { id: 'e1', nombre: 'Halcones' },
          equipoVisitante: { id: 'e2', nombre: 'Toros' },
          cancha: { id: 'ca1', nombre: 'Cancha Norte' },
          inicio: '2026-11-15T08:00:00-06:00',
          finBloque: '2026-11-15T10:00:00-06:00',
          estado: 'PROGRAMADO',
          arbitro: { id: 'a1', nombre: 'Juan Pérez' },
          asignadoAutomaticamente: true,
          ajustado: false,
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
      estadoPublicacion: 'REVISION',
      partidos: [
        {
          id: 'p2',
          jornadaId: 'j2',
          equipoLocal: { id: 'v1', nombre: 'Pumas' },
          equipoVisitante: { id: 'v2', nombre: 'Leones' },
          cancha: { id: 'ca2', nombre: 'Cancha Sur' },
          inicio: '2026-11-14T10:00:00-06:00',
          finBloque: '2026-11-14T12:00:00-06:00',
          estado: 'PROGRAMADO',
          asignadoAutomaticamente: false,
          ajustado: true,
        },
      ],
      descansos: [],
      sinAsignar: [],
    },
  ],
}

const PENDIENTE: PublicacionPendiente = {
  tipo: 'FECHA',
  id: 'f3',
  titulo: 'Fecha 3',
  contexto: 'Temporada 2026-2027 · 2 jornadas',
  partidos: 2,
  enviadoPor: { id: 'programador', nombre: 'Carlos Gómez' },
  enviadoEn: '2026-11-12T18:00:00-06:00',
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

function responder({ fecha = FECHA }: { fecha?: FechaProgramacion } = {}) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta === 'fechas/f3') {
      return fecha
    }
    if (ruta.startsWith('publicaciones/pendientes?')) {
      return pagina([PENDIENTE])
    }

    throw new Error(`GET no simulado: ${ruta}`)
  })
  post.mockImplementation(async (ruta: string, cuerpo: { decision: string; observaciones?: string }) => {
    if (ruta === 'fechas/f3/resolucion') {
      return {
        ...fecha,
        estadoPublicacion: cuerpo.decision,
        observaciones: cuerpo.observaciones,
        ...(cuerpo.decision === 'PUBLICADO' ? { aprobadaPor: 'presidente' } : {}),
      }
    }

    throw new Error(`POST no simulado: ${ruta}`)
  })
}

function montarPantalla() {
  const clienteConsultas = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const router = createMemoryRouter(
    [{ path: '/app/programacion/jornadas/fechas/:fechaId/revision', element: <PantallaRevisionFecha /> }],
    { initialEntries: ['/app/programacion/jornadas/fechas/f3/revision'] },
  )

  return render(
    <QueryClientProvider client={clienteConsultas}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

describe('F-23 · Revisión conjunta de la fecha (H-35.3)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sesion.yo.perfil.id = 'presidente'
    otorgar(CONSULTAR, APROBAR)
    responder()
  })

  it('muestra la fecha completa en solo lectura, con quién la envió y sus advertencias', async () => {
    montarPantalla()

    expect(
      await screen.findByRole('heading', { name: 'Revisión de la fecha 3' }),
    ).toBeInTheDocument()
    expect(await screen.findByText(/Sáb 14 y Dom 15 nov · Enviada por Carlos Gómez/)).toBeInTheDocument()

    const libre = screen.getByRole('region', { name: 'Libre Primera – Grupo 1' })
    const partido = within(libre).getByRole('listitem', { name: 'Halcones vs. Toros' })
    expect(within(partido).getByText('Cancha Norte')).toBeInTheDocument()
    expect(within(partido).getByText('Dom 08:00 – 10:00')).toBeInTheDocument()
    expect(within(partido).getByText('Juan Pérez')).toBeInTheDocument()
    expect(within(libre).getByText(/Descansa:/)).toBeInTheDocument()
    expect(within(libre).getByText('Sin horario libre el domingo para Libre Primera.')).toBeInTheDocument()

    const veteranos = screen.getByRole('region', { name: 'Veteranos – Grupo 1' })
    expect(within(veteranos).getByText('Ajustado')).toBeInTheDocument()
    expect(within(veteranos).getByText('Sin árbitro')).toBeInTheDocument()

    expect(screen.queryByRole('button', { name: /^Ajustar / })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Asignar / })).not.toBeInTheDocument()
    expect(
      screen.getByText(/1 partido quedó sin asignar y no se publicarán\. 1 partido no tiene árbitro central\./),
    ).toBeInTheDocument()
  })

  it('criterio 5: aprueba y publica la fecha completa', async () => {
    const usuario = userEvent.setup()
    montarPantalla()

    await usuario.click(await screen.findByRole('button', { name: 'Aprobar y publicar' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Aprobar y publicar' })
    expect(within(dialogo).getByText(/Los 2 partidos de la fecha 3/)).toBeInTheDocument()
    await usuario.click(within(dialogo).getByRole('button', { name: 'Aprobar y publicar' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('fechas/f3/resolucion', { decision: 'PUBLICADO' }),
    )
    expect(
      await screen.findByText(/Fecha 3 aprobada y publicada\. Los partidos de sus 2 jornadas/),
    ).toBeInTheDocument()
  })

  it('criterio 5: devolver exige observaciones y devuelve todas las jornadas juntas', async () => {
    const usuario = userEvent.setup()
    montarPantalla()

    await usuario.click(await screen.findByRole('button', { name: 'Devolver con observaciones' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Devolver con observaciones' })
    expect(within(dialogo).getByText(/Las 2 jornadas de la fecha 3 volverán juntas a borrador/)).toBeInTheDocument()

    await usuario.click(within(dialogo).getByRole('button', { name: 'Devolver con observaciones' }))
    expect(
      within(dialogo).getByText(/Escribe observaciones de al menos 10 caracteres para devolver la fecha/),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()

    await usuario.type(within(dialogo).getByRole('textbox'), OBSERVACIONES)
    await usuario.click(within(dialogo).getByRole('button', { name: 'Devolver con observaciones' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('fechas/f3/resolucion', {
        decision: 'BORRADOR',
        observaciones: OBSERVACIONES,
      }),
    )
    expect(await screen.findByText(/Fecha 3 devuelta a borrador/)).toBeInTheDocument()
  })

  it('criterio 5: quien envió la fecha no la dictamina', async () => {
    sesion.yo.perfil.id = 'programador'
    montarPantalla()

    expect(
      await screen.findByText('Tú enviaste esta fecha a aprobación. El dictamen corresponde a otra persona.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aprobar y publicar' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Devolver con observaciones' })).toBeDisabled()
  })

  it('quien también puede editar la programación no la dictamina', async () => {
    otorgar(CONSULTAR, EDITAR, APROBAR)
    montarPantalla()

    expect(await screen.findByText(/Tu cuenta también puede editar la programación/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Aprobar y publicar' })).toBeDisabled()
  })

  it('muestra la explicación del backend cuando rechaza el dictamen', async () => {
    const usuario = userEvent.setup()
    post.mockRejectedValue(
      new ApiError({
        title: 'Operación no permitida',
        status: 403,
        detail: 'Quien programó o envió la fecha no puede dictaminarla.',
        codigo: 'RF-121',
      }),
    )
    montarPantalla()

    await usuario.click(await screen.findByRole('button', { name: 'Aprobar y publicar' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Aprobar y publicar' })
    await usuario.click(within(dialogo).getByRole('button', { name: 'Aprobar y publicar' }))

    expect(
      await within(dialogo).findByText('Quien programó o envió la fecha no puede dictaminarla.'),
    ).toBeInTheDocument()
  })

  it('con solo consulta muestra la fecha sin dictámenes ni bandeja', async () => {
    otorgar(CONSULTAR)
    montarPantalla()

    expect(
      await screen.findByRole('heading', { name: 'Revisión de la fecha 3' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aprobar y publicar' })).not.toBeInTheDocument()
    expect(get).not.toHaveBeenCalledWith(expect.stringMatching(/^publicaciones\/pendientes/))
  })

  it('una fecha publicada ya no ofrece dictamen', async () => {
    responder({
      fecha: { ...FECHA, estadoPublicacion: 'PUBLICADO', observaciones: 'Aprobada en sesión ordinaria' },
    })
    montarPantalla()

    expect(
      await screen.findByText('Esta fecha ya está publicada. No hay dictamen pendiente.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Nota de aprobación' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Aprobar y publicar' })).not.toBeInTheDocument()
  })

  it('bloquea la ruta sin el permiso de consulta', async () => {
    otorgar()
    montarPantalla()

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })
})

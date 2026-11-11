import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaAfiliaciones, RUTA_AFILIACIONES } from '@/features/organizacion/afiliaciones/PantallaAfiliaciones'
import type {
  Afiliacion,
  ContextoAfiliacion,
} from '@/features/organizacion/afiliaciones/tipos'
import type { Club } from '@/features/organizacion/tipos'
import type { Ambito, Permiso } from '@/features/seguridad/tipos'
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

const CAPTURAR = 'organizacion.afiliacion:CAPTURAR'
const APROBAR = 'organizacion.afiliacion:APROBAR'

const TEMPORADA = {
  id: 't1',
  nombre: 'Temporada 2026',
  fechaInicio: '2026-01-01',
  estado: 'ACTIVA' as const,
}

const CONTEXTO: ContextoAfiliacion = {
  temporadas: [
    {
      ...TEMPORADA,
      categorias: [
        {
          id: 'cat-libre',
          nombre: 'Libre',
          grupos: [{ id: 'g-a', nombre: 'A', equipos: 3 }],
        },
      ],
    },
  ],
}

const CLUB: Club = {
  id: 'c1',
  nombre: 'Club Halcones',
  estado: 'ACTIVO',
  creadoEn: '2026-01-01T10:00:00-06:00',
}

const SOLICITADA: Afiliacion = {
  id: 'af-1',
  folio: 'AF-2026-0001',
  club: { id: 'c1', nombre: 'Club Halcones' },
  temporada: { id: 't1', nombre: 'Temporada 2026', estado: 'ACTIVA' },
  coloresUniformes: 'Verde y blanco',
  estado: 'SOLICITADA',
  categorias: [{ id: 'cat-libre', nombre: 'Libre' }],
  capturadaPor: { id: 'perfil-1', nombreMostrado: 'Ana Pérez' },
  capturadaEn: '2026-01-10T10:00:00-06:00',
}

const APROBADA: Afiliacion = {
  ...SOLICITADA,
  estado: 'APROBADA',
  resueltaPor: { id: 'perfil-2', nombreMostrado: 'Presidente' },
  resueltaEn: '2026-01-11T10:00:00-06:00',
  observaciones: 'Todo en orden',
}

const RECHAZADA: Afiliacion = {
  ...SOLICITADA,
  estado: 'RECHAZADA',
  resueltaPor: { id: 'perfil-2', nombreMostrado: 'Presidente' },
  resueltaEn: '2026-01-11T10:00:00-06:00',
  observaciones: 'Faltan las firmas de la directiva',
}

function otorgar(ambito: Ambito, ...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: 'M-03',
    ambito,
    clubId: ambito === 'CLUB' ? 'c1' : null,
  }))
}

function pagina<T>(contenido: T[], totalElementos = contenido.length) {
  return { contenido, pagina: 0, tamano: 20, totalElementos, totalPaginas: contenido.length ? 1 : 0 }
}

function error(status: number, codigo: string, detail = 'Detalle del servidor.') {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

type Respuestas = {
  contexto?: ContextoAfiliacion
  contextoError?: unknown
  clubes?: Club[]
  lista?: unknown
  detalle?: unknown
}

function responder({
  contexto = CONTEXTO,
  contextoError,
  clubes = [CLUB],
  lista = pagina([]),
  detalle = SOLICITADA,
}: Respuestas = {}) {
  get.mockImplementation(async (ruta: string) => {
    const respuesta = ruta === 'afiliaciones/contexto'
      ? (contextoError ?? contexto)
      : ruta.startsWith('clubes?')
        ? pagina(clubes, clubes.length)
        : ruta.startsWith('afiliaciones?')
          ? lista
          : ruta.startsWith('afiliaciones/')
            ? detalle
            : undefined

    if (respuesta instanceof Error) {
      throw respuesta
    }

    return respuesta
  })
}

function montar(ruta: string = RUTA_AFILIACIONES) {
  const enrutador = createMemoryRouter(
    [{ path: `${RUTA_AFILIACIONES}/:afiliacionId?`, element: <PantallaAfiliaciones /> }],
    { initialEntries: [ruta] },
  )

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return enrutador
}

describe('PantallaAfiliaciones (F-16, H-08.2)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    otorgar('CLUB', CAPTURAR)
    responder()
  })

  it('sin CAPTURAR ni APROBAR muestra la página 403 y no pide datos', () => {
    otorgar('LIGA')
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('RF-101: la Directiva captura por secciones y el tarjetón queda SOLICITADO con folio', async () => {
    post.mockResolvedValue(SOLICITADA)

    montar()

    const abrir = await screen.findAllByRole('button', { name: 'Registrar afiliación' })
    await userEvent.click(abrir[0])

    expect(await screen.findByRole('heading', { name: 'Nuevo tarjetón' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Club y temporada' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Categorías inscritas' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Colores del uniforme' })).toBeInTheDocument()
    expect(screen.getAllByText('Club Halcones').length).toBeGreaterThan(0)

    await userEvent.click(screen.getByRole('checkbox', { name: /Libre/ }))
    await userEvent.type(screen.getByRole('textbox', { name: /Colores del uniforme/ }), 'Verde y blanco')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar a aprobación' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('afiliaciones', {
        clubId: 'c1',
        temporadaId: 't1',
        coloresUniformes: 'Verde y blanco',
        categoriaIds: ['cat-libre'],
      }),
    )
    expect(await screen.findByText(/quedó SOLICITADO/)).toBeInTheDocument()
    expect((await screen.findAllByText('AF-2026-0001')).length).toBeGreaterThan(0)
  })

  it('RF-101: sin categorías el formulario no envía el tarjetón', async () => {
    montar()

    const abrir = await screen.findAllByRole('button', { name: 'Registrar afiliación' })
    await userEvent.click(abrir[0])
    await screen.findByText('Categorías inscritas')

    await userEvent.type(screen.getByRole('textbox', { name: /Colores del uniforme/ }), 'Verde y blanco')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar a aprobación' }))

    expect(await screen.findByText('Selecciona al menos una categoría.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('RF-101: el Presidente aprueba con una nota y el estado cambia', async () => {
    otorgar('LIGA', APROBAR)
    responder({ lista: pagina([SOLICITADA]), detalle: SOLICITADA })
    post.mockImplementation(async () => {
      responder({ lista: pagina([APROBADA]), detalle: APROBADA })
      return APROBADA
    })

    montar()

    await userEvent.click(await screen.findByRole('button', { name: /Club Halcones/ }))
    expect(await screen.findByText('Dictamen de la Presidencia')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Aprobar afiliación' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Aprobar afiliación' })
    await userEvent.type(within(dialogo).getByLabelText(/Nota de aprobación/), 'Todo en orden')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Aprobar afiliación' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('afiliaciones/af-1/resolucion', {
        decision: 'APROBADA',
        observaciones: 'Todo en orden',
      }),
    )
    expect(await screen.findByText(/quedó afiliado/)).toBeInTheDocument()
    expect((await screen.findAllByText('Aprobada')).length).toBeGreaterThan(0)
  })

  it('RF-101: el rechazo exige observaciones y las envía', async () => {
    otorgar('LIGA', APROBAR)
    responder({ lista: pagina([SOLICITADA]), detalle: SOLICITADA })
    post.mockImplementation(async () => {
      responder({ lista: pagina([RECHAZADA]), detalle: RECHAZADA })
      return RECHAZADA
    })

    montar()

    await userEvent.click(await screen.findByRole('button', { name: /Club Halcones/ }))
    await userEvent.click(await screen.findByRole('button', { name: 'Rechazar afiliación' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Rechazar afiliación' })

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Rechazar afiliación' }))
    expect(
      await within(dialogo).findByText(/Escribe observaciones de al menos 10 caracteres/),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()

    await userEvent.type(
      within(dialogo).getByLabelText(/Observaciones para la Directiva/),
      'Faltan las firmas de la directiva',
    )
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Rechazar afiliación' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('afiliaciones/af-1/resolucion', {
        decision: 'RECHAZADA',
        observaciones: 'Faltan las firmas de la directiva',
      }),
    )
    expect(await screen.findByText(/se rechazó con observaciones/)).toBeInTheDocument()
    expect((await screen.findAllByText('Rechazada')).length).toBeGreaterThan(0)
  })

  it('VAL-024: un tarjetón fuera de ámbito responde 404 y ofrece volver', async () => {
    responder({ lista: pagina([]), detalle: error(404, 'NO_ENCONTRADO') })
    montar(`${RUTA_AFILIACIONES}/af-ajena`)

    expect(await screen.findByText(/No encontramos esta información/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Volver a la bandeja' })).toBeInTheDocument()
  })

  it('VAL-015: un listado sin permiso efectivo muestra 403', async () => {
    responder({ lista: error(403, 'VAL-015') })
    montar()

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
  })

  it('VAL-015: si no puede leer temporadas, explica el bloqueo antes de capturar', async () => {
    responder({ contextoError: error(403, 'VAL-015') })
    montar()

    const abrir = await screen.findAllByRole('button', { name: 'Registrar afiliación' })
    await userEvent.click(abrir[0])

    expect(await screen.findByText('No puedes consultar las temporadas de afiliación')).toBeInTheDocument()
    expect(
      screen.getByText(/Pide a la Liga que habilite los permisos de afiliación/),
    ).toBeInTheDocument()
  })
})

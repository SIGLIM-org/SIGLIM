import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaOperacionesEquipo } from '@/features/organizacion/operaciones/PantallaOperacionesEquipo'
import type {
  ContextoOperacionesEquipo,
  OperacionEquipo,
} from '@/features/organizacion/operaciones/tipos'
import type { Equipo } from '@/features/organizacion/tipos'
import type { Ambito, Permiso } from '@/features/seguridad/tipos'

const { get, post, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const SOLICITAR = 'organizacion.venta:SOLICITAR'
const APROBAR = 'organizacion.venta:APROBAR'

const TEMPORADA = {
  id: 't1',
  nombre: 'Temporada 2026',
  estado: 'ACTIVA' as const,
}

const EQUIPO: Equipo = {
  id: 'eq1',
  nombre: 'Halcones',
  estado: 'ACTIVO',
  club: { id: 'c1', nombre: 'Club Halcones' },
  grupo: { id: 'g1', nombre: 'A' },
  categoria: { id: 'cat1', nombre: 'Libre' },
  temporada: TEMPORADA,
  creadoEn: '2026-01-01T10:00:00-06:00',
}

const DESTINO = { id: 'c2', nombre: 'Club Águilas' }

const CONTEXTO: ContextoOperacionesEquipo = {
  clubes: [
    { id: 'c1', nombre: 'Club Halcones' },
    DESTINO,
  ],
}

const SOLICITADA: OperacionEquipo = {
  id: 'op-1',
  tipo: 'VENTA',
  estado: 'SOLICITADA',
  equipo: { id: 'eq1', nombre: 'Halcones' },
  clubOrigen: { id: 'c1', nombre: 'Club Halcones' },
  clubDestino: DESTINO,
  temporada: TEMPORADA,
  solicitadaPor: { id: 'perfil-1', nombreMostrado: 'Ana Pérez' },
  solicitadaEn: '2026-01-10T10:00:00-06:00',
}

const AUTORIZADA: OperacionEquipo = {
  ...SOLICITADA,
  estado: 'AUTORIZADA',
  autorizadaPor: { id: 'perfil-2', nombreMostrado: 'Presidente' },
  autorizadaEn: '2026-01-11T10:00:00-06:00',
  observaciones: 'Acuerdo de asamblea',
}

const RECHAZADA: OperacionEquipo = {
  ...SOLICITADA,
  estado: 'RECHAZADA',
  autorizadaPor: { id: 'perfil-2', nombreMostrado: 'Presidente' },
  autorizadaEn: '2026-01-11T10:00:00-06:00',
  observaciones: 'La asamblea no aprobó la venta',
}

const CERRADA: OperacionEquipo = {
  ...SOLICITADA,
  temporada: { ...TEMPORADA, estado: 'CERRADA' },
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

function responder({
  lista = pagina([SOLICITADA]),
  contexto = CONTEXTO,
  equipos = [EQUIPO],
}: {
  lista?: unknown
  contexto?: unknown
  equipos?: Equipo[]
} = {}) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta === 'operaciones-equipo/contexto') {
      return contexto
    }
    if (ruta.startsWith('operaciones-equipo?')) {
      return lista
    }
    if (ruta.startsWith('equipos?')) {
      return pagina(equipos, equipos.length)
    }

    throw new Error(`Ruta inesperada: ${ruta}`)
  })
}

function montar() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <PantallaOperacionesEquipo />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('PantallaOperacionesEquipo (B-09, UI-034)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    otorgar('CLUB', SOLICITAR)
    responder()
  })

  it('sin SOLICITAR ni APROBAR muestra la página 403 y no pide datos', () => {
    otorgar('LIGA')
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('la Directiva registra una venta con su club destino y queda SOLICITADA', async () => {
    post.mockResolvedValue(SOLICITADA)
    montar()

    const abrir = await screen.findAllByRole('button', { name: 'Registrar operación' })
    await userEvent.click(abrir[0])

    expect(await screen.findByRole('heading', { name: 'Registrar operación' })).toBeInTheDocument()
    const equipo = await screen.findByRole('combobox', { name: 'Equipo' })
    const destino = await screen.findByRole('combobox', { name: 'Club destino' })

    await waitFor(() => expect(equipo).toHaveValue('eq1'))
    await waitFor(() => expect(destino).toHaveValue('c2'))
    await userEvent.selectOptions(destino, 'c2')
    await userEvent.type(
      screen.getByRole('textbox', { name: /Observaciones/ }),
      'Acuerdo de asamblea',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Enviar a la Presidencia' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('operaciones-equipo', {
        tipo: 'VENTA',
        equipoId: 'eq1',
        clubDestinoId: 'c2',
        observaciones: 'Acuerdo de asamblea',
      }),
    )
    expect(await screen.findByText(/quedó SOLICITADA/)).toBeInTheDocument()
  })

  it('la Directiva registra una conservación de plaza sin club destino', async () => {
    post.mockResolvedValue({ ...SOLICITADA, tipo: 'CONSERVACION_PLAZA', clubDestino: undefined })
    montar()

    const abrir = await screen.findAllByRole('button', { name: 'Registrar operación' })
    await userEvent.click(abrir[0])
    await screen.findByRole('heading', { name: 'Registrar operación' })
    await userEvent.click(
      screen.getByRole('radio', { name: /Conservación de plaza/ }),
    )

    expect(screen.queryByRole('combobox', { name: 'Club destino' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Enviar a la Presidencia' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('operaciones-equipo', {
        tipo: 'CONSERVACION_PLAZA',
        equipoId: 'eq1',
      }),
    )
  })

  it('la Presidencia autoriza una solicitud con una nota', async () => {
    otorgar('LIGA', APROBAR)
    post.mockResolvedValue(AUTORIZADA)
    montar()

    const autorizar = await screen.findByRole('button', {
      name: 'Autorizar la operación de Halcones',
    })
    await userEvent.click(autorizar)

    expect(await screen.findByRole('heading', { name: 'Autorizar venta' })).toBeInTheDocument()
    await userEvent.type(
      screen.getByRole('textbox', { name: /Nota de autorización/ }),
      'Autorizada en sesión',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Autorizar operación' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('operaciones-equipo/op-1/resolucion', {
        decision: 'AUTORIZADA',
        observaciones: 'Autorizada en sesión',
      }),
    )
    expect(await screen.findByText(/quedó AUTORIZADA/)).toBeInTheDocument()
  })

  it('el rechazo exige observaciones y después cambia el estado', async () => {
    otorgar('LIGA', APROBAR)
    post.mockResolvedValue(RECHAZADA)
    montar()

    await userEvent.click(
      await screen.findByRole('button', { name: 'Rechazar la operación de Halcones' }),
    )
    expect(await screen.findByRole('heading', { name: 'Rechazar venta' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Rechazar operación' }))
    expect(
      await screen.findByText(/Escribe observaciones de al menos 10 caracteres/),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()

    await userEvent.type(
      screen.getByRole('textbox', { name: /Observaciones para la Directiva/ }),
      'La asamblea no aprobó la venta',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Rechazar operación' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('operaciones-equipo/op-1/resolucion', {
        decision: 'RECHAZADA',
        observaciones: 'La asamblea no aprobó la venta',
      }),
    )
  })

  it('una solicitud de una temporada cerrada no ofrece dictamen y explica la causa', async () => {
    otorgar('LIGA', APROBAR)
    responder({ lista: pagina([CERRADA]) })
    montar()

    expect(await screen.findByText(/Temporada cerrada: ya no se puede dictaminar/)).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Autorizar la operación de Halcones' }),
    ).not.toBeInTheDocument()
  })

  it('el Presidente ve la bandeja solicitada y el estado con insignia', async () => {
    otorgar('LIGA', APROBAR)
    montar()

    expect(await screen.findByText('Solicitada')).toBeInTheDocument()
    expect(screen.getByText('Espera el dictamen de la Presidencia.')).toBeInTheDocument()
  })
})

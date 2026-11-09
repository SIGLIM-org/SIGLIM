import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PestanaDirectiva } from '@/features/organizacion/PestanaDirectiva'
import type { CargoDirectiva, Club, Persona } from '@/features/organizacion/tipos'
import type { Ambito, Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'

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

const CLUB: Club = {
  id: 'c1',
  nombre: 'Club Halcones',
  estado: 'ACTIVO',
  creadoEn: '2026-11-03T10:00:00-06:00',
}

const VIGENTE: CargoDirectiva = {
  id: 'cd1',
  clubId: 'c1',
  personaId: 'p1',
  nombrePersona: 'Ana López',
  cargo: 'PRESIDENTE',
  desde: '2026-01-01',
  estado: 'AUTORIZADO',
  autorizadoPor: 'perfil-1',
}

const PENDIENTE: CargoDirectiva = {
  id: 'cd2',
  clubId: 'c1',
  personaId: 'p2',
  nombrePersona: 'Luis Pérez',
  cargo: 'PRESIDENTE',
  desde: '2027-01-01',
  estado: 'PENDIENTE',
}

const ANTERIOR: CargoDirectiva = {
  id: 'cd0',
  clubId: 'c1',
  personaId: 'p0',
  nombrePersona: 'Marta Ruiz',
  cargo: 'SECRETARIO',
  desde: '2025-01-01',
  hasta: '2025-12-31',
  estado: 'AUTORIZADO',
  autorizadoPor: 'perfil-1',
}

const PERSONA: Persona = {
  id: 'p2',
  curp: 'GODE840101HDFRNS09',
  nombre: 'Luis',
  apellidoPaterno: 'Pérez',
}

function otorgar(ambito: Ambito, ...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: 'M-03',
    ambito,
    clubId: ambito === 'CLUB' ? 'c1' : null,
  }))
}

function responder(cargos: CargoDirectiva[], persona: Persona | Error = PERSONA) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('personas?')) {
      if (persona instanceof Error) {
        throw persona
      }
      return persona
    }
    if (ruta === 'clubes/c1/directiva') {
      return cargos
    }
    return undefined
  })
}

function montar() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PestanaDirectiva club={CLUB} />
    </QueryClientProvider>,
  )
}

describe('PestanaDirectiva (F-15, H-22.2, RF-102)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    otorgar('CLUB', 'organizacion.club:CONSULTAR', 'organizacion.directiva:EDITAR')
    responder([VIGENTE])
  })

  it('lista los cuatro cargos con su titular, vigencia y estado', async () => {
    montar()

    expect(await screen.findByRole('table', { name: 'Directiva del club' })).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('clubes/c1/directiva')
    expect(screen.getByText('Ana López')).toBeInTheDocument()
    expect(screen.getByText('Vigente')).toBeInTheDocument()
    expect(screen.getByText('Desde 1 ene 2026')).toBeInTheDocument()
    expect(screen.getAllByText('Sin titular')).toHaveLength(3)
  })

  it('un cargo pendiente muestra a quién reemplaza y solo se autoriza con APROBAR', async () => {
    otorgar('CLUB', 'organizacion.club:CONSULTAR', 'organizacion.directiva:APROBAR')
    responder([VIGENTE, PENDIENTE])
    montar()

    expect(await screen.findByText('Luis Pérez')).toBeInTheDocument()
    expect(screen.getByText('Pendiente')).toBeInTheDocument()
    expect(
      screen.getByText('Al autorizar, Ana López termina el 31 dic 2026.'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Autorizar cambio de Presidente' }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Registrar cambio' })).not.toBeInTheDocument()
  })

  it('sin APROBAR avisa que hay cambios pendientes de la Presidencia', async () => {
    responder([VIGENTE, PENDIENTE])
    montar()

    expect(
      await screen.findByText('Hay un cambio de directiva pendiente de autorización por la Presidencia.'),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Autorizar cambio de Presidente' }),
    ).not.toBeInTheDocument()
  })

  it('criterio 1: registra un cambio buscando a la persona por CURP', async () => {
    post.mockResolvedValue({
      ...PENDIENTE,
      id: 'cd9',
      cargo: 'SECRETARIO',
      nombrePersona: 'Luis Pérez',
    })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Registrar cambio' }))
    const modal = screen.getByRole('dialog', { name: 'Registrar cambio de directiva' })

    await userEvent.type(within(modal).getByLabelText(/CURP de la persona/), PERSONA.curp)
    await userEvent.click(within(modal).getByRole('button', { name: 'Buscar persona' }))

    expect(await within(modal).findByText('Luis Pérez')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith(`personas?curp=${PERSONA.curp}`)

    await userEvent.selectOptions(within(modal).getByLabelText(/^Cargo/), 'SECRETARIO')
    fireEvent.change(within(modal).getByLabelText(/Inicio de la vigencia/), {
      target: { value: '2027-01-01' },
    })
    await userEvent.click(within(modal).getByRole('button', { name: 'Registrar cambio' }))

    expect(post).toHaveBeenCalledWith('clubes/c1/directiva', {
      personaId: 'p2',
      cargo: 'SECRETARIO',
      desde: '2027-01-01',
    })
    expect(
      await screen.findByText(
        'Cambio de Secretario registrado. Queda pendiente de autorización.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('una CURP sin persona explica que debe registrarse antes', async () => {
    responder([VIGENTE], new ApiError({ title: 'No encontrado', status: 404, detail: '', codigo: 'NO_ENCONTRADO' }))
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Registrar cambio' }))
    const modal = screen.getByRole('dialog', { name: 'Registrar cambio de directiva' })
    await userEvent.type(within(modal).getByLabelText(/CURP de la persona/), PERSONA.curp)
    await userEvent.click(within(modal).getByRole('button', { name: 'Buscar persona' }))

    expect(
      await within(modal).findByText(
        'No encontramos una persona con esa CURP. Verifícala o pide a la Liga que la registre.',
      ),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('criterio 2: autoriza el cambio y explica la vigencia anterior', async () => {
    otorgar('CLUB', 'organizacion.club:CONSULTAR', 'organizacion.directiva:APROBAR')
    responder([VIGENTE, PENDIENTE])
    post.mockResolvedValue({ ...PENDIENTE, estado: 'AUTORIZADO' })
    montar()

    await userEvent.click(
      await screen.findByRole('button', { name: 'Autorizar cambio de Presidente' }),
    )
    const modal = screen.getByRole('dialog', { name: 'Autorizar cambio de presidente' })
    expect(within(modal).getByText('Luis Pérez')).toBeInTheDocument()
    expect(
      within(modal).getByText(/Ana López terminará su cargo el/),
    ).toHaveTextContent('31 dic 2026')

    await userEvent.click(within(modal).getByRole('button', { name: 'Autorizar cambio' }))

    expect(post).toHaveBeenCalledWith('cargos-directiva/cd2/autorizacion')
    expect(await screen.findByText('Cambio de Presidente autorizado.')).toBeInTheDocument()
  })

  it('el historial se muestra solo al pedirlo', async () => {
    responder([VIGENTE, ANTERIOR])
    montar()

    await screen.findByRole('table', { name: 'Directiva del club' })
    expect(screen.queryByText('Marta Ruiz')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Ver historial (1)' }))
    expect(await screen.findByText('Marta Ruiz')).toBeInTheDocument()
    expect(screen.getByText('Concluido')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Ocultar historial' }))
    await waitFor(() => expect(screen.queryByText('Marta Ruiz')).not.toBeInTheDocument())
  })

  it('sin permisos de edición ni autorización no ofrece acciones', async () => {
    otorgar('CLUB', 'organizacion.club:CONSULTAR')
    responder([VIGENTE, PENDIENTE])
    montar()

    await screen.findByRole('table', { name: 'Directiva del club' })
    expect(screen.queryByRole('button', { name: 'Registrar cambio' })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Autorizar cambio de Presidente' }),
    ).not.toBeInTheDocument()
  })
})

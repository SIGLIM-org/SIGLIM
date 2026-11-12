import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  PantallaArbitros,
  RUTA_ARBITROS,
} from '@/features/arbitraje/PantallaArbitros'
import type { Arbitro } from '@/features/arbitraje/tipos'
import type { Persona, PersonaDetalle } from '@/features/organizacion/tipos'
import type { Ambito, Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, post, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [] as {
        id: string
        rol: { codigo: string; nombre: string }
        ambito: Ambito
        club: { id: string; nombre: string } | null
      }[],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR = 'arbitraje.arbitro:CONSULTAR'
const PROPONER = 'arbitraje.arbitro:PROPONER'
const VALIDAR = 'arbitraje.arbitro:VALIDAR'
const EDITAR_PERSONA = 'organizacion.persona:EDITAR'
const CLUB = { id: 'c1', nombre: 'Club Halcones' }

const PROPUESTO: Arbitro = {
  id: 'ar-1',
  persona: { id: 'p1', nombreCompleto: 'Carlos Alberto Prado' },
  club: CLUB,
  estado: 'PROPUESTO',
  propuestoPor: { id: 'perfil-1', nombreMostrado: 'Ana Pérez' },
  propuestoEn: '2030-08-02T06:00:00-06:00',
}

const VALIDADO: Arbitro = {
  ...PROPUESTO,
  estado: 'VALIDADO',
  validadoPor: { id: 'perfil-2', nombreMostrado: 'Comisión de Arbitraje' },
  validadoEn: '2030-08-03T10:00:00-06:00',
  observaciones: 'Cumple los requisitos',
}

const RECHAZADO: Arbitro = {
  ...PROPUESTO,
  estado: 'RECHAZADO',
  validadoPor: { id: 'perfil-2', nombreMostrado: 'Comisión de Arbitraje' },
  validadoEn: '2030-08-03T10:00:00-06:00',
  observaciones: 'La constancia no acredita la categoría',
}

const PERSONA: Persona = {
  id: 'p2',
  curp: 'GAPA800101HMNXXX01',
  nombre: 'Luis',
  apellidoPaterno: 'Soto',
  apellidoMaterno: 'Ramírez',
}

const PERSONA_DETALLE: PersonaDetalle = {
  ...PERSONA,
  fechaNacimiento: '1980-01-01',
}

function otorgar(ambito: Ambito, ...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: codigo.startsWith('organizacion') ? 'M-03' : 'M-05',
    ambito,
    clubId: ambito === 'CLUB' ? CLUB.id : null,
  }))
  sesion.yo.asignaciones =
    ambito === 'CLUB'
      ? [
          {
            id: 'as-1',
            rol: { codigo: 'DIRECTIVA_CLUB', nombre: 'Directiva de club' },
            ambito: 'CLUB',
            club: CLUB,
          },
        ]
      : []
}

function pagina<T>(contenido: T[], totalElementos = contenido.length) {
  return {
    contenido,
    pagina: 0,
    tamano: contenido.length || 1,
    totalElementos,
    totalPaginas: totalElementos > 0 ? 1 : 0,
  }
}

function error(status: number, codigo: string, detail = 'Detalle del servidor.') {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

type Respuestas = {
  propuestos?: Arbitro[]
  validados?: Arbitro[]
  rechazados?: Arbitro[]
  personas?: Persona | Error
}

function responder({
  propuestos = [],
  validados = [],
  rechazados = [],
  personas = PERSONA,
}: Respuestas = {}) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('arbitros?')) {
      const parametros = new URLSearchParams(ruta.slice('arbitros?'.length))
      const estado = parametros.get('estado')
      const datos =
        estado === 'VALIDADO'
          ? validados
          : estado === 'RECHAZADO'
            ? rechazados
            : propuestos

      return pagina(datos, datos.length)
    }

    if (ruta.startsWith('personas?curp=')) {
      if (personas instanceof Error) {
        throw personas
      }
      return personas
    }

    throw new Error(`Ruta no simulada: ${ruta}`)
  })
}

function montar() {
  const enrutador = createMemoryRouter(
    [{ path: RUTA_ARBITROS, element: <PantallaArbitros /> }],
    { initialEntries: [RUTA_ARBITROS] },
  )

  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

describe('PantallaArbitros (F-20, H-11.2)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    otorgar('LIGA', CONSULTAR)
    responder()
  })

  it('sin CONSULTAR muestra la página 403 y no pide datos', () => {
    otorgar('LIGA')
    montar()

    expect(
      screen.getByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('RF-021: muestra las pestañas por estado, los conteos y el club que presenta', async () => {
    otorgar('LIGA', CONSULTAR, VALIDAR)
    responder({ propuestos: [PROPUESTO], validados: [VALIDADO], rechazados: [RECHAZADO] })

    montar()

    expect(await screen.findByText('Carlos Alberto Prado')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Por validar/ })).toHaveTextContent('1')
    expect(screen.getByRole('tab', { name: /Validados/ })).toHaveTextContent('1')
    expect(screen.getByRole('tab', { name: /Rechazados/ })).toHaveTextContent('1')
    expect(screen.getByText('Club Halcones')).toBeInTheDocument()
    expect(screen.queryByText(/colegio/i)).not.toBeInTheDocument()
  })

  it('RF-113: la Comisión valida con una nota y el árbitro pasa a Validados', async () => {
    otorgar('LIGA', CONSULTAR, VALIDAR)
    responder({ propuestos: [PROPUESTO], validados: [VALIDADO] })
    post.mockResolvedValue(VALIDADO)

    montar()

    await userEvent.click(await screen.findByRole('button', { name: /Validar a Carlos/ }))
    const dialogo = await screen.findByRole('dialog', { name: 'Validar árbitro' })
    await userEvent.type(
      within(dialogo).getByLabelText(/Nota de validación/),
      'Cumple los requisitos',
    )
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Validar árbitro' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('arbitros/ar-1/validacion', {
        decision: 'VALIDADO',
        observaciones: 'Cumple los requisitos',
      }),
    )
    expect(await screen.findByText(/quedó VALIDADO/)).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Validados/ })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  it('RF-113: el rechazo exige observaciones y las envía', async () => {
    otorgar('LIGA', CONSULTAR, VALIDAR)
    responder({ propuestos: [PROPUESTO], rechazados: [RECHAZADO] })
    post.mockResolvedValue(RECHAZADO)

    montar()

    await userEvent.click(await screen.findByRole('button', { name: /Rechazar a Carlos/ }))
    const dialogo = await screen.findByRole('dialog', { name: 'Rechazar árbitro' })

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Rechazar árbitro' }))
    expect(
      await within(dialogo).findByText(/Escribe observaciones de al menos 10 caracteres/),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()

    await userEvent.type(
      within(dialogo).getByLabelText(/Observaciones para el club/),
      'La constancia no acredita la categoría',
    )
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Rechazar árbitro' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('arbitros/ar-1/validacion', {
        decision: 'RECHAZADO',
        observaciones: 'La constancia no acredita la categoría',
      }),
    )
    expect(await screen.findByText(/quedó RECHAZADO/)).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Rechazados/ })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  it('RF-112: la Directiva busca una persona existente y la presenta', async () => {
    otorgar('CLUB', CONSULTAR, PROPONER, EDITAR_PERSONA)
    responder({ propuestos: [] })
    post.mockResolvedValue({
      ...PROPUESTO,
      persona: { id: PERSONA.id, nombreCompleto: 'Luis Soto Ramírez' },
    })

    montar()

    const abrir = await screen.findAllByRole('button', { name: 'Presentar árbitro' })
    await userEvent.click(abrir[0])
    const dialogo = await screen.findByRole('dialog', { name: 'Presentar árbitro' })

    await userEvent.type(within(dialogo).getByLabelText(/CURP de la persona/), PERSONA.curp)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Buscar persona' }))
    expect(await within(dialogo).findByText('Luis Soto Ramírez')).toBeInTheDocument()

    await userEvent.click(
      within(dialogo).getByRole('button', { name: 'Presentar árbitro' }),
    )

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('arbitros', {
        personaId: PERSONA.id,
        clubId: CLUB.id,
      }),
    )
    expect(await screen.findByText(/quedó PROPUESTO/)).toBeInTheDocument()
  })

  it('RF-112: si la CURP no existe, registra la persona y luego la presenta', async () => {
    otorgar('CLUB', CONSULTAR, PROPONER, EDITAR_PERSONA)
    responder({ propuestos: [], personas: error(404, 'NO_ENCONTRADO') })
    post.mockImplementation(async (ruta: string) => {
      if (ruta === 'personas') {
        return PERSONA_DETALLE
      }
      if (ruta === 'arbitros') {
        return {
          ...PROPUESTO,
          persona: { id: PERSONA.id, nombreCompleto: 'Luis Soto Ramírez' },
        }
      }
      throw new Error(`POST no simulado: ${ruta}`)
    })

    montar()

    const abrir = await screen.findAllByRole('button', { name: 'Presentar árbitro' })
    await userEvent.click(abrir[0])
    const dialogo = await screen.findByRole('dialog', { name: 'Presentar árbitro' })

    await userEvent.type(within(dialogo).getByLabelText(/CURP de la persona/), PERSONA.curp)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Buscar persona' }))
    expect(await within(dialogo).findByText('Registrar persona')).toBeInTheDocument()

    await userEvent.type(within(dialogo).getByLabelText(/^Nombre/), PERSONA.nombre)
    await userEvent.type(
      within(dialogo).getByLabelText(/^Apellido paterno/),
      PERSONA.apellidoPaterno,
    )
    await userEvent.type(
      within(dialogo).getByLabelText(/^Apellido materno/),
      PERSONA.apellidoMaterno ?? '',
    )
    fireEvent.change(within(dialogo).getByLabelText(/^Fecha de nacimiento/), {
      target: { value: '1980-01-01' },
    })

    await userEvent.click(
      within(dialogo).getByRole('button', { name: 'Registrar y presentar' }),
    )

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('personas', {
        curp: PERSONA.curp,
        nombre: PERSONA.nombre,
        apellidoPaterno: PERSONA.apellidoPaterno,
        apellidoMaterno: PERSONA.apellidoMaterno,
        fechaNacimiento: '1980-01-01',
      }),
    )
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('arbitros', {
        personaId: PERSONA.id,
        clubId: CLUB.id,
      }),
    )
  })
})

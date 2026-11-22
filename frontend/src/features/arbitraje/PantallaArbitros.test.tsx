import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  PantallaArbitros,
  RUTA_ARBITROS,
} from '@/features/arbitraje/PantallaArbitros'
import type { Arbitro, CuotaArbitros } from '@/features/arbitraje/tipos'
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

/** Cuota de un club para el contrato de `GET /clubes/{id}/cuota-arbitros` (RF-114). */
function cuota(
  club: { id: string; nombre: string } = CLUB,
  {
    requeridos = 1,
    validados = 1,
    equiposActivos = 4,
    equiposPorArbitro = 4,
  }: {
    requeridos?: number
    validados?: number
    equiposActivos?: number
    equiposPorArbitro?: number
  } = {},
): CuotaArbitros {
  return {
    club,
    temporada: { id: 't1', nombre: 'Apertura 2030' },
    equiposActivos,
    equiposPorArbitro,
    cuotaArbitros: {
      requeridos,
      validados,
      cumple: validados >= requeridos,
    },
  }
}

const OTRO_CLUB = { id: 'c2', nombre: 'Club Toros' }

const PROPUESTO_OTRO: Arbitro = {
  ...PROPUESTO,
  id: 'ar-2',
  persona: { id: 'p3', nombreCompleto: 'Jorge Nava' },
  club: OTRO_CLUB,
}

type Respuestas = {
  propuestos?: Arbitro[]
  validados?: Arbitro[]
  rechazados?: Arbitro[]
  personas?: Persona | Error
  cuotas?: Record<string, CuotaArbitros | Error>
}

function responder({
  propuestos = [],
  validados = [],
  rechazados = [],
  personas = PERSONA,
  cuotas = { [CLUB.id]: cuota() },
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

    const deCuota = /^clubes\/([^/]+)\/cuota-arbitros$/.exec(ruta)
    if (deCuota) {
      const respuesta = cuotas[deCuota[1]]
      if (!respuesta) {
        throw new Error(`Cuota no simulada: ${ruta}`)
      }
      if (respuesta instanceof Error) {
        throw respuesta
      }
      return respuesta
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

  it('RF-114: el club ve los árbitros validados frente a los requeridos y la advertencia', async () => {
    otorgar('CLUB', CONSULTAR, PROPONER)
    responder({
      propuestos: [PROPUESTO],
      cuotas: {
        [CLUB.id]: cuota(CLUB, { requeridos: 2, validados: 1, equiposActivos: 5 }),
      },
    })

    montar()

    const indicador = await screen.findByText(/Árbitros validados:/)
    expect(indicador).toHaveTextContent('Árbitros validados: 1 de 2 requeridos')
    expect(
      screen.getByText(/No cumple la cuota: falta 1 árbitro validado/),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('progressbar', { name: '1 de 2 árbitros validados' }),
    ).toHaveAttribute('aria-valuenow', '1')
  })

  it('RF-114: el club ve el cumplimiento cuando alcanza la cuota', async () => {
    otorgar('CLUB', CONSULTAR, PROPONER)
    responder({
      cuotas: {
        [CLUB.id]: cuota(CLUB, { requeridos: 2, validados: 2 }),
      },
    })

    montar()

    expect(await screen.findByText(/El club cumple la cuota/)).toBeInTheDocument()
    expect(screen.getByText(/Árbitros validados:/)).toHaveTextContent(
      'Árbitros validados: 2 de 2 requeridos',
    )
    expect(screen.queryByText(/No cumple la cuota:/)).not.toBeInTheDocument()
  })

  it('RF-114: la Comisión ve la insignia «Cumple cuota» por club', async () => {
    otorgar('LIGA', CONSULTAR)
    responder({
      propuestos: [PROPUESTO, PROPUESTO_OTRO],
      cuotas: {
        [CLUB.id]: cuota(CLUB, { requeridos: 2, validados: 1, equiposActivos: 5 }),
        [OTRO_CLUB.id]: cuota(OTRO_CLUB, { requeridos: 2, validados: 2 }),
      },
    })

    montar()

    const filaHalcones = await screen.findByRole('row', {
      name: /Carlos Alberto Prado/,
    })
    const filaToros = screen.getByRole('row', { name: /Jorge Nava/ })

    expect(await within(filaHalcones).findByText('No cumple')).toBeInTheDocument()
    expect(
      within(filaHalcones).getByText('1 de 2 árbitros validados'),
    ).toBeInTheDocument()
    expect(await within(filaToros).findByText('Cumple')).toBeInTheDocument()
    expect(
      within(filaToros).getByText('2 de 2 árbitros validados'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Cuota de árbitros por club' }),
    ).toBeInTheDocument()
  })

  it('RF-114: la Comisión filtra los clubes por «Cumple cuota»', async () => {
    otorgar('LIGA', CONSULTAR)
    responder({
      propuestos: [PROPUESTO, PROPUESTO_OTRO],
      cuotas: {
        [CLUB.id]: cuota(CLUB, { requeridos: 2, validados: 1, equiposActivos: 5 }),
        [OTRO_CLUB.id]: cuota(OTRO_CLUB, { requeridos: 2, validados: 2 }),
      },
    })

    montar()

    const filtro = await screen.findByLabelText('Cumple cuota')
    await userEvent.selectOptions(filtro, 'NO_CUMPLE')

    expect(
      await screen.findByText(/Mostrando 1 de 2 árbitros de esta página/),
    ).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /Carlos Alberto Prado/ })).toBeInTheDocument()
    expect(
      screen.queryByRole('row', { name: /Jorge Nava/ }),
    ).not.toBeInTheDocument()
  })

  it('RF-114: si la cuota no se puede calcular, muestra la causa y permite reintentar', async () => {
    otorgar('CLUB', CONSULTAR, PROPONER)
    responder({
      cuotas: {
        [CLUB.id]: error(
          409,
          'RF-114',
          'La liga no tiene una temporada activa: indica la temporada de la cuota.',
        ),
      },
    })

    montar()

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('La liga no tiene una temporada activa')
    expect(
      within(alerta).getByRole('button', { name: 'Reintentar' }),
    ).toBeInTheDocument()
  })

  it('RF-114: la Comisión marca «Sin dato» si la cuota de un club no está disponible', async () => {
    otorgar('LIGA', CONSULTAR)
    responder({
      propuestos: [PROPUESTO],
      cuotas: {
        [CLUB.id]: error(409, 'RF-114', 'Sin temporada activa.'),
      },
    })

    montar()

    const fila = await screen.findByRole('row', {
      name: /Carlos Alberto Prado/,
    })
    expect(await within(fila).findByText('Sin dato')).toBeInTheDocument()
  })

  it('RF-114: un club sin equipos activos muestra el estado vacío de la cuota', async () => {
    otorgar('CLUB', CONSULTAR, PROPONER)
    responder({
      cuotas: {
        [CLUB.id]: cuota(CLUB, {
          requeridos: 0,
          validados: 0,
          equiposActivos: 0,
        }),
      },
    })

    montar()

    expect(
      await screen.findByText(/no tiene equipos activos en esta temporada/),
    ).toBeInTheDocument()
    expect(screen.queryByText(/Árbitros validados:/)).not.toBeInTheDocument()
  })

  it('RF-114: muestra el estado de carga de la cuota', async () => {
    otorgar('CLUB', CONSULTAR, PROPONER)
    let resolver!: (valor: CuotaArbitros) => void
    const pendiente = new Promise<CuotaArbitros>((resolverPromesa) => {
      resolver = resolverPromesa
    })
    get.mockImplementation(async (ruta: string) => {
      if (ruta.startsWith('arbitros?')) {
        return pagina([], 0)
      }
      if (ruta.endsWith('/cuota-arbitros')) {
        return pendiente
      }
      throw new Error(`Ruta no simulada: ${ruta}`)
    })

    montar()

    expect(
      await screen.findByText('Cargando la cuota de árbitros…'),
    ).toBeInTheDocument()

    resolver(cuota(CLUB, { requeridos: 1, validados: 1 }))

    expect(await screen.findByText(/Árbitros validados:/)).toHaveTextContent(
      'Árbitros validados: 1 de 1 requeridos',
    )
  })
})

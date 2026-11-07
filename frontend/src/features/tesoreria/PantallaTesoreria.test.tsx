import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaTesoreria } from '@/features/tesoreria/PantallaTesoreria'
import type {
  Adeudos,
  ClubResumen,
  Cobro,
  EquipoResumen,
} from '@/features/tesoreria/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
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

const CONSULTAR = 'tesoreria.cobro:CONSULTAR'
const CREAR = 'tesoreria.cobro:CREAR'
const PAGAR = 'tesoreria.pago:PAGAR'
const VER_EQUIPOS = 'organizacion.equipo:CONSULTAR'
const RUTA = '/app/tesoreria'

const CLUB: ClubResumen = { id: 'c-1', nombre: 'Club Halcones' }
const EQUIPO: EquipoResumen = {
  id: 'e-1',
  nombre: 'Halcones Sub-17',
  club: CLUB,
  estado: 'ACTIVO',
}

const COBRO_CLUB: Cobro = {
  id: 'cb-1',
  tipo: 'MANUAL',
  concepto: 'Cuota de afiliación',
  monto: 1500,
  fechaLimite: '2026-11-30',
  estado: 'PENDIENTE',
  club: CLUB,
  creadoEn: '2026-11-01T10:00:00-06:00',
}

const COBRO_EQUIPO: Cobro = {
  id: 'cb-2',
  tipo: 'ARBITRAJE',
  concepto: 'Arbitraje de jornada',
  monto: 850.5,
  periodoInicio: '2026-11-01',
  fechaLimite: '2026-11-15',
  estado: 'VENCIDO',
  club: CLUB,
  equipo: { id: EQUIPO.id, nombre: EQUIPO.nombre },
  creadoEn: '2026-11-02T10:00:00-06:00',
}

const COBRO_RECHAZADO: Cobro = {
  id: 'cb-3',
  tipo: 'MULTA',
  concepto: 'Multa disciplinaria',
  monto: 500,
  fechaLimite: '2026-11-10',
  estado: 'RECHAZADO',
  club: CLUB,
  creadoEn: '2026-11-03T10:00:00-06:00',
}

const COBRO_PAGADO: Cobro = {
  ...COBRO_CLUB,
  id: 'cb-4',
  concepto: 'Cuota liquidada',
  estado: 'PAGADO',
}

const ADEUDOS: Adeudos = {
  total: 2350.5,
  pendiente: 1500,
  vencido: 850.5,
  cobros: 2,
  clubes: [
    {
      club: CLUB,
      total: 2350.5,
      pendiente: 1500,
      vencido: 850.5,
      cobros: 2,
      equipos: [
        {
          equipo: { id: EQUIPO.id, nombre: EQUIPO.nombre },
          total: 850.5,
          pendiente: 0,
          vencido: 850.5,
          cobros: 1,
        },
      ],
    },
  ],
}

const SIN_ADEUDOS: Adeudos = {
  total: 0,
  pendiente: 0,
  vencido: 0,
  cobros: 0,
  clubes: [],
}

function pagina<T>(
  contenido: T[],
  extra: { pagina?: number; totalElementos?: number; totalPaginas?: number } = {},
) {
  return {
    contenido,
    pagina: extra.pagina ?? 0,
    tamano: 20,
    totalElementos: extra.totalElementos ?? contenido.length,
    totalPaginas: extra.totalPaginas ?? (contenido.length ? 1 : 0),
  }
}

function error(status: number, codigo: string, detail: string) {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

let respuestaCobros: unknown
let respuestaAdeudos: unknown
let respuestaClubes: unknown
let respuestaEquipos: unknown

function responder(
  opciones: {
    cobros?: unknown
    adeudos?: unknown
    clubes?: unknown
    equipos?: unknown
  } = {},
) {
  respuestaCobros = opciones.cobros ?? pagina([COBRO_CLUB, COBRO_EQUIPO])
  respuestaAdeudos = opciones.adeudos ?? ADEUDOS
  respuestaClubes = opciones.clubes ?? pagina([CLUB])
  respuestaEquipos = opciones.equipos ?? pagina([EQUIPO])

  get.mockImplementation(async (ruta: string) => {
    let respuesta: unknown

    if (ruta.startsWith('cobros?')) {
      respuesta = respuestaCobros
    } else if (ruta === 'adeudos') {
      respuesta = respuestaAdeudos
    } else if (ruta.startsWith('clubes?')) {
      respuesta = respuestaClubes
    } else if (ruta.startsWith('equipos?')) {
      respuesta = respuestaEquipos
    } else {
      throw new Error(`Ruta inesperada: ${ruta}`)
    }

    if (respuesta instanceof Error) {
      throw respuesta
    }

    return respuesta
  })
}

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: codigo.startsWith('tesoreria.') ? 'M-13' : 'M-03',
    ambito: 'LIGA',
    clubId: null,
  }))
}

function montar() {
  const enrutador = createMemoryRouter([{ path: RUTA, element: <PantallaTesoreria /> }], {
    initialEntries: [RUTA],
  })

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return enrutador
}

describe('PantallaTesoreria', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    otorgar(CONSULTAR, CREAR, VER_EQUIPOS, PAGAR)
    responder()
  })

  it('H-29.2: muestra el resumen, la tabla de cobros y el estado de cada uno', async () => {
    montar()

    expect(await screen.findByText('Cuota de afiliación')).toBeInTheDocument()
    expect(screen.getByText('Adeudo total')).toBeInTheDocument()
    expect(screen.getByText('$2,350.50')).toBeInTheDocument()
    expect(screen.getAllByText('$1,500.00')).not.toHaveLength(0)
    expect(screen.getAllByText('$850.50')).not.toHaveLength(0)
    expect(screen.getByText('2 cobros')).toBeInTheDocument()

    const filaEquipo = screen.getByText('Arbitraje de jornada').closest('tr') as HTMLElement
    expect(within(filaEquipo).getByText('Halcones Sub-17')).toBeInTheDocument()
    expect(within(filaEquipo).getByText('Club Halcones')).toBeInTheDocument()
    expect(within(filaEquipo).getByText('Vencido')).toBeInTheDocument()
    expect(within(filaEquipo).getByText('15 nov 2026')).toBeInTheDocument()

    const filaClub = screen.getByText('Cuota de afiliación').closest('tr') as HTMLElement
    expect(within(filaClub).getByText('Cargo al club')).toBeInTheDocument()
    expect(within(filaClub).getByText('Pendiente')).toBeInTheDocument()

    expect(get).toHaveBeenCalledWith('cobros?pagina=0&tamano=20')
    expect(get).toHaveBeenCalledWith('adeudos')
  })

  it('H-30.2: ofrece pagar solo los cobros pendientes o rechazados', async () => {
    responder({
      cobros: pagina([COBRO_CLUB, COBRO_RECHAZADO, COBRO_EQUIPO, COBRO_PAGADO]),
    })
    montar()

    await screen.findByText('Cuota de afiliación')

    const filaPendiente = screen.getByText('Cuota de afiliación').closest('tr') as HTMLElement
    expect(
      within(filaPendiente).getByRole('button', { name: 'Pagar en línea (simulado)' }),
    ).toBeInTheDocument()

    const filaRechazada = screen.getByText('Multa disciplinaria').closest('tr') as HTMLElement
    expect(
      within(filaRechazada).getByRole('button', { name: 'Reintentar pago' }),
    ).toBeInTheDocument()

    const filaVencida = screen.getByText('Arbitraje de jornada').closest('tr') as HTMLElement
    expect(within(filaVencida).queryByRole('button')).not.toBeInTheDocument()

    const filaPagada = screen.getByText('Cuota liquidada').closest('tr') as HTMLElement
    expect(within(filaPagada).queryByRole('button')).not.toBeInTheDocument()
  })

  it('H-30.2: abre el flujo de pago simulado desde un cobro pendiente', async () => {
    montar()
    await screen.findByText('Cuota de afiliación')

    await userEvent.click(screen.getByRole('button', { name: 'Pagar en línea (simulado)' }))

    const dialogo = screen.getByRole('dialog', { name: 'Pago simulado' })
    expect(
      within(dialogo).getByText('Pago simulado: no se realiza ningún cargo'),
    ).toBeInTheDocument()
    expect(within(dialogo).getByRole('heading', { name: 'Cobro a pagar' })).toBeInTheDocument()
    expect(within(dialogo).getByText('$1,500.00 MXN')).toBeInTheDocument()

    await userEvent.click(
      within(dialogo).getByRole('button', { name: 'Cerrar pago simulado' }),
    )
    expect(
      screen.queryByRole('dialog', { name: 'Pago simulado' }),
    ).not.toBeInTheDocument()
  })

  it('H-02.4: sin tesoreria.pago:PAGAR no ofrece la acción de pago', async () => {
    otorgar(CONSULTAR, CREAR, VER_EQUIPOS)
    montar()

    await screen.findByText('Cuota de afiliación')

    expect(
      screen.queryByRole('button', { name: 'Pagar en línea (simulado)' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar pago' })).not.toBeInTheDocument()
  })

  it('H-29.2: aplica club, tipo, estado y rango de fecha límite', async () => {
    montar()
    await screen.findByText('Cuota de afiliación')

    await userEvent.selectOptions(screen.getByLabelText(/^Club/), 'c-1')
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'MULTA')
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'PAGADO')
    await userEvent.type(screen.getByLabelText('Desde'), '2026-11-01')
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-11-30')
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith(
        'cobros?pagina=0&tamano=20&clubId=c-1&tipo=MULTA&estado=PAGADO&desde=2026-11-01&hasta=2026-12-01',
      )
    })
  })

  it('H-29.2: rechaza un rango con «Hasta» anterior a «Desde» sin consultar', async () => {
    montar()
    await screen.findByText('Cuota de afiliación')
    const llamadas = get.mock.calls.length

    await userEvent.type(screen.getByLabelText('Desde'), '2026-11-30')
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-11-01')
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    expect(
      screen.getByText('«Hasta» no puede ser anterior a «Desde». Corrige una de las dos fechas.'),
    ).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(llamadas)
  })

  it('H-29.2: la pestaña de adeudos desglosa club y equipos', async () => {
    montar()
    await screen.findByText('Cuota de afiliación')

    await userEvent.click(screen.getByRole('tab', { name: /Adeudos por club/ }))

    expect(
      screen.getByText('1 club con adeudo · 2 cobros por cobrar'),
    ).toBeInTheDocument()
    const club = screen.getByRole('heading', { name: 'Club Halcones' }).closest('article') as HTMLElement
    expect(within(club).getByText('Adeudo del club')).toBeInTheDocument()
    expect(within(club).getByText('$2,350.50')).toBeInTheDocument()
    expect(within(club).getByText('Halcones Sub-17')).toBeInTheDocument()
    expect(within(club).getByText('Equipos con adeudo')).toBeInTheDocument()
  })

  it('H-29.2: crea un cobro manual para un equipo y confirma el alta', async () => {
    post.mockResolvedValue({
      ...COBRO_EQUIPO,
      id: 'cb-nuevo',
      concepto: 'Arbitraje de jornada',
    })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo cobro' }))
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo cobro' })

    await userEvent.selectOptions(within(dialogo).getByLabelText(/^Club/), 'c-1')
    await userEvent.selectOptions(
      await within(dialogo).findByLabelText('Equipo (opcional)'),
      'e-1',
    )
    await userEvent.type(within(dialogo).getByLabelText(/^Concepto/), 'Arbitraje de jornada')
    await userEvent.type(within(dialogo).getByLabelText(/^Monto en MXN/), '850.50')
    await userEvent.type(within(dialogo).getByLabelText('Inicio del periodo'), '2026-11-01')
    await userEvent.type(within(dialogo).getByLabelText(/^Fecha límite/), '2026-11-30')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear cobro' }))

    expect(post).toHaveBeenCalledWith('cobros', {
      clubId: 'c-1',
      equipoId: 'e-1',
      concepto: 'Arbitraje de jornada',
      monto: 850.5,
      periodoInicio: '2026-11-01',
      fechaLimite: '2026-11-30',
    })
    expect(
      await screen.findByText(
        'Cobro «Arbitraje de jornada» registrado para Halcones Sub-17 por $850.50.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Nuevo cobro' })).not.toBeInTheDocument()
  })

  it('H-29.2: valida los campos obligatorios del alta antes de llamar a la API', async () => {
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo cobro' }))
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo cobro' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear cobro' }))

    expect(within(dialogo).getByText('Elige el club del cobro.')).toBeInTheDocument()
    expect(within(dialogo).getByText('Escribe el concepto del cobro.')).toBeInTheDocument()
    expect(within(dialogo).getByText('Escribe el monto del cobro.')).toBeInTheDocument()
    expect(within(dialogo).getByText('Elige la fecha límite del cobro.')).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('H-29.2: muestra el detalle y los campos que devuelve la API al fallar el alta', async () => {
    post.mockRejectedValue(
      new ApiError({
        title: 'Datos inválidos',
        status: 422,
        codigo: 'VAL-004',
        detail: 'El monto del cobro no es válido.',
        errores: [{ campo: 'monto', mensaje: 'El monto debe ser mayor que cero.' }],
      }),
    )
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo cobro' }))
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo cobro' })
    await userEvent.selectOptions(within(dialogo).getByLabelText(/^Club/), 'c-1')
    await userEvent.type(within(dialogo).getByLabelText(/^Concepto/), 'Ajuste de cuota')
    await userEvent.type(within(dialogo).getByLabelText(/^Monto en MXN/), '10')
    await userEvent.type(within(dialogo).getByLabelText(/^Fecha límite/), '2026-11-30')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear cobro' }))

    expect(await within(dialogo).findByText('El monto del cobro no es válido.')).toBeInTheDocument()
    expect(within(dialogo).getByText('El monto debe ser mayor que cero.')).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Nuevo cobro' })).toBeInTheDocument()
  })

  it('H-02.4: sin tesoreria.cobro:CREAR no ofrece el alta de cobros', async () => {
    otorgar(CONSULTAR, VER_EQUIPOS)
    montar()

    await screen.findByText('Cuota de afiliación')

    expect(screen.queryByRole('button', { name: 'Nuevo cobro' })).not.toBeInTheDocument()
  })

  it('H-29.2: si no puede consultar equipos, el alta carga el cobro al club', async () => {
    otorgar(CONSULTAR, CREAR)
    post.mockResolvedValue({
      ...COBRO_CLUB,
      id: 'cb-nuevo',
      concepto: 'Cuota extraordinaria',
    })
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo cobro' }))
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo cobro' })
    await userEvent.selectOptions(within(dialogo).getByLabelText(/^Club/), 'c-1')

    expect(within(dialogo).queryByLabelText('Equipo (opcional)')).not.toBeInTheDocument()
    expect(
      within(dialogo).getByText(
        'Tu perfil no puede consultar el catálogo de equipos; el cobro se cargará al club.',
      ),
    ).toBeInTheDocument()

    await userEvent.type(within(dialogo).getByLabelText(/^Concepto/), 'Cuota extraordinaria')
    await userEvent.type(within(dialogo).getByLabelText(/^Monto en MXN/), '500')
    await userEvent.type(within(dialogo).getByLabelText(/^Fecha límite/), '2026-12-15')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear cobro' }))

    expect(post).toHaveBeenCalledWith('cobros', {
      clubId: 'c-1',
      concepto: 'Cuota extraordinaria',
      monto: 500,
      fechaLimite: '2026-12-15',
    })
  })

  it('H-02.4: sin CONSULTAR muestra la 403 y no pide datos', () => {
    otorgar(CREAR)

    montar()

    expect(
      screen.getByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('VAL-015: un 403 de la API muestra la página 403 completa', async () => {
    responder({
      cobros: error(403, 'VAL-015', 'No tienes el permiso tesoreria.cobro:CONSULTAR.'),
    })
    montar()

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
  })

  it('H-29.2: sin cobros ni adeudos muestra los estados vacíos', async () => {
    responder({ cobros: pagina([]), adeudos: SIN_ADEUDOS })
    montar()

    expect(await screen.findByText('Aún no hay cobros registrados')).toBeInTheDocument()
    expect(
      screen.getByText('Los cobros manuales a clubes y equipos aparecerán aquí.'),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Nuevo cobro' })).not.toHaveLength(0)

    await userEvent.click(screen.getByRole('tab', { name: /Adeudos por club/ }))
    expect(screen.getByText('Sin adeudos registrados')).toBeInTheDocument()
  })

  it('H-29.2: tras filtrar, un resultado vacío permite limpiar los filtros', async () => {
    montar()
    await screen.findByText('Cuota de afiliación')

    respuestaCobros = pagina([])
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'CANCHA')
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

    expect(
      await screen.findByText('Ningún cobro coincide con los filtros'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Limpiar filtros' })).toBeInTheDocument()
  })

  it('H-29.2: pagina los cobros sin perder los filtros aplicados', async () => {
    responder({
      cobros: pagina([COBRO_CLUB], {
        totalElementos: 21,
        totalPaginas: 2,
      }),
    })
    montar()

    await screen.findByText('Cuota de afiliación')
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    await waitFor(() => {
      expect(get).toHaveBeenCalledWith('cobros?pagina=1&tamano=20')
    })
  })
})

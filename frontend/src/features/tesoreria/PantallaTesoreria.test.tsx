import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { diaSiguiente, hoyEnZonaNegocio } from '@/features/tesoreria/fechas'
import { PantallaTesoreria } from '@/features/tesoreria/PantallaTesoreria'
import type {
  Adeudos,
  ClubResumen,
  Cobro,
  EquipoResumen,
  Pago,
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
const REGISTRAR = 'tesoreria.pago:REGISTRAR'
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

const PAGO: Pago = {
  id: 'pg-1',
  cobroId: 'cb-1',
  medio: 'TRANSFERENCIA',
  monto: 1500,
  pagadoEn: '2026-11-06T10:00:00-06:00',
  registradoPor: { id: 'perfil-1', nombre: 'Ana Pérez' },
  comprobante: { documentoId: 'doc-1', tipoMime: 'application/pdf', tamanoBytes: 2048 },
}

function comprobante(nombre = 'recibo.pdf', tipo = 'application/pdf', bytes = 2048) {
  return new File([new Uint8Array(bytes)], nombre, { type: tipo })
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

  describe('registrar pago (H-31.2)', () => {
    beforeEach(() => {
      otorgar(CONSULTAR, REGISTRAR)
    })

    async function abrirPago(concepto = 'Cuota de afiliación') {
      await userEvent.click(
        await screen.findByRole('button', { name: `Registrar pago de «${concepto}»` }),
      )
      return screen.getByRole('dialog', { name: 'Registrar pago' })
    }

    function cuerpoEnviado(): FormData {
      const [, cuerpo] = post.mock.calls[0] as [string, FormData]
      expect(cuerpo).toBeInstanceOf(FormData)
      return cuerpo
    }

    function llamadasACobros(): number {
      return get.mock.calls.filter(([ruta]) => String(ruta).startsWith('cobros?')).length
    }

    async function llenarYEnviar(dialogo: HTMLElement) {
      await userEvent.click(within(dialogo).getByRole('radio', { name: 'Efectivo' }))
      await userEvent.upload(within(dialogo).getByLabelText(/^Comprobante/), comprobante())
      await userEvent.click(within(dialogo).getByRole('button', { name: 'Registrar pago' }))
    }

    it('H-31.2: con REGISTRAR, solo los cobros PENDIENTE y VENCIDO ofrecen «Registrar pago»', async () => {
      responder({ cobros: pagina([COBRO_CLUB, COBRO_EQUIPO, COBRO_PAGADO, COBRO_RECHAZADO]) })
      montar()

      await screen.findByText('Cuota liquidada')

      expect(screen.getByRole('columnheader', { name: 'Acciones' })).toBeInTheDocument()
      expect(
        screen.getByRole('button', { name: 'Registrar pago de «Cuota de afiliación»' }),
      ).toBeInTheDocument()
      expect(
        screen.getByRole('button', { name: 'Registrar pago de «Arbitraje de jornada»' }),
      ).toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: 'Registrar pago de «Cuota liquidada»' }),
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: 'Registrar pago de «Multa disciplinaria»' }),
      ).not.toBeInTheDocument()
    })

    it('H-30.2 y H-31.2: con PAGAR y REGISTRAR, cada cobro ofrece solo las acciones que admite', async () => {
      otorgar(CONSULTAR, PAGAR, REGISTRAR)
      responder({ cobros: pagina([COBRO_CLUB, COBRO_EQUIPO, COBRO_PAGADO, COBRO_RECHAZADO]) })
      montar()

      await screen.findByText('Cuota de afiliación')
      expect(screen.getAllByRole('columnheader', { name: 'Acciones' })).toHaveLength(1)

      const fila = (concepto: string) => screen.getByText(concepto).closest('tr') as HTMLElement
      const botones = (concepto: string) =>
        within(fila(concepto))
          .queryAllByRole('button')
          .map((boton) => boton.getAttribute('aria-label') ?? boton.textContent)

      expect(botones('Cuota de afiliación')).toEqual([
        'Pagar en línea (simulado)',
        'Registrar pago de «Cuota de afiliación»',
      ])
      expect(botones('Arbitraje de jornada')).toEqual(['Registrar pago de «Arbitraje de jornada»'])
      expect(botones('Multa disciplinaria')).toEqual(['Reintentar pago'])
      expect(botones('Cuota liquidada')).toEqual([])
    })

    it('H-02.4: sin tesoreria.pago:REGISTRAR no hay columna «Acciones» ni botón', async () => {
      otorgar(CONSULTAR, CREAR)
      montar()

      await screen.findByText('Cuota de afiliación')

      expect(screen.queryByRole('columnheader', { name: 'Acciones' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /^Registrar pago/ })).not.toBeInTheDocument()
    })

    it('H-31.2: registra el pago con el monto del cobro y confirma sin fecha ni referencia', async () => {
      post.mockResolvedValue(PAGO)
      montar()
      const dialogo = await abrirPago()

      expect(within(dialogo).getByText('Importe a liquidar')).toBeInTheDocument()
      expect(within(dialogo).getByText('$1,500.00')).toBeInTheDocument()
      expect(
        within(dialogo).queryByText(/folio fiscal|póliza|observaciones/i),
      ).not.toBeInTheDocument()

      const archivo = comprobante()
      await userEvent.click(within(dialogo).getByRole('radio', { name: 'Transferencia' }))
      await userEvent.upload(within(dialogo).getByLabelText(/^Comprobante/), archivo)
      expect(within(dialogo).getByText('recibo.pdf')).toBeInTheDocument()
      expect(within(dialogo).getByText('2 KB')).toBeInTheDocument()

      const antes = llamadasACobros()
      await userEvent.click(within(dialogo).getByRole('button', { name: 'Registrar pago' }))

      expect(post).toHaveBeenCalledTimes(1)
      expect(post.mock.calls[0][0]).toBe('cobros/cb-1/pagos')
      const cuerpo = cuerpoEnviado()
      expect(cuerpo.get('medio')).toBe('TRANSFERENCIA')
      expect(cuerpo.get('monto')).toBe('1500.00')
      expect(cuerpo.get('archivo')).toBe(archivo)
      expect(cuerpo.has('fechaPago')).toBe(false)
      expect(cuerpo.has('referencia')).toBe(false)

      expect(
        await screen.findByText(
          'Pago de $1,500.00 registrado para «Cuota de afiliación»; el cobro quedó pagado.',
        ),
      ).toBeInTheDocument()
      expect(screen.queryByRole('dialog', { name: 'Registrar pago' })).not.toBeInTheDocument()
      await waitFor(() => expect(llamadasACobros()).toBeGreaterThan(antes))
    })

    it('H-31.2: envía la fecha del pago y la referencia recortada', async () => {
      post.mockResolvedValue(PAGO)
      montar()
      const dialogo = await abrirPago()

      await userEvent.click(within(dialogo).getByRole('radio', { name: 'Efectivo' }))
      await userEvent.type(within(dialogo).getByLabelText('Fecha del pago'), '2026-11-02')
      await userEvent.type(within(dialogo).getByLabelText('Referencia'), '  REC-1184  ')
      await userEvent.upload(
        within(dialogo).getByLabelText(/^Comprobante/),
        comprobante('foto.jpg', 'image/jpeg'),
      )
      await userEvent.click(within(dialogo).getByRole('button', { name: 'Registrar pago' }))

      const cuerpo = cuerpoEnviado()
      expect(cuerpo.get('medio')).toBe('EFECTIVO')
      expect(cuerpo.get('fechaPago')).toBe('2026-11-02')
      expect(cuerpo.get('referencia')).toBe('REC-1184')
    })

    it('H-31.2: sin medio ni comprobante muestra ambos errores y no llama a la API', async () => {
      montar()
      const dialogo = await abrirPago()

      await userEvent.click(within(dialogo).getByRole('button', { name: 'Registrar pago' }))

      expect(within(dialogo).getByText('Elige el medio de pago.')).toBeInTheDocument()
      expect(within(dialogo).getByText('Adjunta el comprobante del pago.')).toBeInTheDocument()
      expect(post).not.toHaveBeenCalled()
    })

    it('H-31.2: rechaza un comprobante de más de 5 MB sin llamar a la API', async () => {
      montar()
      const dialogo = await abrirPago()

      await userEvent.click(within(dialogo).getByRole('radio', { name: 'Efectivo' }))
      await userEvent.upload(
        within(dialogo).getByLabelText(/^Comprobante/),
        comprobante('grande.pdf', 'application/pdf', 5 * 1024 * 1024 + 1),
      )

      expect(
        within(dialogo).getByText('El comprobante pesa más de 5 MB; adjunta uno más ligero.'),
      ).toBeInTheDocument()
      await userEvent.click(within(dialogo).getByRole('button', { name: 'Registrar pago' }))
      expect(post).not.toHaveBeenCalled()
    })

    it('H-31.2: rechaza un comprobante que no es JPEG, PNG, WebP ni PDF', async () => {
      const usuario = userEvent.setup({ applyAccept: false })
      montar()
      await usuario.click(
        await screen.findByRole('button', { name: 'Registrar pago de «Cuota de afiliación»' }),
      )
      const dialogo = screen.getByRole('dialog', { name: 'Registrar pago' })

      await usuario.click(within(dialogo).getByRole('radio', { name: 'Otro' }))
      await usuario.upload(
        within(dialogo).getByLabelText(/^Comprobante/),
        comprobante('notas.txt', 'text/plain'),
      )

      expect(
        within(dialogo).getByText('El comprobante debe ser JPEG, PNG, WebP o PDF.'),
      ).toBeInTheDocument()
      expect(within(dialogo).getByLabelText(/^Comprobante/)).toHaveAttribute(
        'aria-invalid',
        'true',
      )
      await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar pago' }))
      expect(post).not.toHaveBeenCalled()
    })

    it('H-31.2: rechaza una fecha de pago futura', async () => {
      montar()
      const dialogo = await abrirPago()

      await userEvent.click(within(dialogo).getByRole('radio', { name: 'Efectivo' }))
      await userEvent.type(
        within(dialogo).getByLabelText('Fecha del pago'),
        diaSiguiente(hoyEnZonaNegocio()),
      )
      await userEvent.upload(within(dialogo).getByLabelText(/^Comprobante/), comprobante())
      await userEvent.click(within(dialogo).getByRole('button', { name: 'Registrar pago' }))

      expect(within(dialogo).getByText('La fecha del pago no puede ser futura.')).toBeInTheDocument()
      expect(post).not.toHaveBeenCalled()
    })

    it('VAL-028: un 409 muestra el detalle en el modal y refresca los cobros', async () => {
      post.mockRejectedValue(
        error(409, 'VAL-028', 'El cobro ya está PAGADO y no admite otro pago.'),
      )
      montar()
      const dialogo = await abrirPago()
      const antes = llamadasACobros()

      await llenarYEnviar(dialogo)

      expect(
        await within(dialogo).findByText('El cobro ya está PAGADO y no admite otro pago.'),
      ).toBeInTheDocument()
      expect(screen.getByRole('dialog', { name: 'Registrar pago' })).toBeInTheDocument()
      await waitFor(() => expect(llamadasACobros()).toBeGreaterThan(antes))
    })

    it('VAL-028: un 413 marca el campo «Comprobante»', async () => {
      post.mockRejectedValue(
        error(413, 'ARCHIVO_DEMASIADO_GRANDE', 'El archivo supera el tamaño permitido de 5 MB.'),
      )
      montar()
      const dialogo = await abrirPago()

      await llenarYEnviar(dialogo)

      expect(
        await within(dialogo).findByText('El archivo supera el tamaño permitido de 5 MB.'),
      ).toBeInTheDocument()
      expect(within(dialogo).getByLabelText(/^Comprobante/)).toHaveAttribute(
        'aria-invalid',
        'true',
      )
    })

    it('VAL-028: un 400 con errores marca los campos y deja los ajenos en el aviso', async () => {
      post.mockRejectedValue(
        new ApiError({
          title: 'Datos inválidos',
          status: 400,
          codigo: 'VALIDACION',
          detail: 'Revisa los datos del pago.',
          errores: [
            { campo: 'referencia', mensaje: 'La referencia es demasiado larga.' },
            { campo: 'monto', mensaje: 'El monto admite dos decimales.' },
          ],
        }),
      )
      montar()
      const dialogo = await abrirPago()

      await llenarYEnviar(dialogo)

      expect(await within(dialogo).findByText('La referencia es demasiado larga.')).toBeInTheDocument()
      expect(within(dialogo).getByLabelText('Referencia')).toHaveAttribute('aria-invalid', 'true')
      expect(
        within(dialogo).getByText('Revisa los datos del pago. El monto admite dos decimales.'),
      ).toBeInTheDocument()
    })
  })

  describe('estado vencido (B-31.2)', () => {
    const AYUDA_VENCIDO = 'Vencido: comunícate con Tesorería'
    const fila = (concepto: string) => screen.getByText(concepto).closest('tr') as HTMLElement

    it('B-31.2: con PAGAR sin REGISTRAR, el cobro vencido no se paga en línea y remite a Tesorería', async () => {
      otorgar(CONSULTAR, PAGAR)
      montar()

      await screen.findByText('Arbitraje de jornada')

      const filaVencida = fila('Arbitraje de jornada')
      expect(within(filaVencida).getByText('Vencido')).toBeInTheDocument()
      expect(within(filaVencida).getByText(AYUDA_VENCIDO)).toBeVisible()
      expect(
        within(filaVencida).queryByRole('button', { name: 'Pagar en línea (simulado)' }),
      ).not.toBeInTheDocument()
      expect(
        within(filaVencida).queryByRole('button', { name: 'Reintentar pago' }),
      ).not.toBeInTheDocument()

      expect(within(fila('Cuota de afiliación')).queryByText(AYUDA_VENCIDO)).not.toBeInTheDocument()
    })

    it('B-31.2: con REGISTRAR sin PAGAR, el cobro vencido ofrece «Registrar pago» sin el aviso', async () => {
      otorgar(CONSULTAR, REGISTRAR)
      montar()

      await screen.findByText('Arbitraje de jornada')

      const filaVencida = fila('Arbitraje de jornada')
      expect(
        within(filaVencida).getByRole('button', {
          name: 'Registrar pago de «Arbitraje de jornada»',
        }),
      ).toBeInTheDocument()
      expect(screen.queryByText(AYUDA_VENCIDO)).not.toBeInTheDocument()
    })

    it('B-31.2: con PAGAR y REGISTRAR, el cobro vencido ofrece «Registrar pago» sin el aviso', async () => {
      otorgar(CONSULTAR, PAGAR, REGISTRAR)
      montar()

      await screen.findByText('Arbitraje de jornada')

      const filaVencida = fila('Arbitraje de jornada')
      expect(
        within(filaVencida).getByRole('button', {
          name: 'Registrar pago de «Arbitraje de jornada»',
        }),
      ).toBeInTheDocument()
      expect(screen.queryByText(AYUDA_VENCIDO)).not.toBeInTheDocument()
    })

    it('B-31.2: el filtro «Vencido» consulta solo los cobros vencidos', async () => {
      montar()
      await screen.findByText('Cuota de afiliación')

      await userEvent.selectOptions(screen.getByLabelText('Estado'), 'Vencido')
      await userEvent.click(screen.getByRole('button', { name: 'Aplicar filtros' }))

      await waitFor(() => {
        expect(get).toHaveBeenCalledWith('cobros?pagina=0&tamano=20&estado=VENCIDO')
      })
    })
  })
})

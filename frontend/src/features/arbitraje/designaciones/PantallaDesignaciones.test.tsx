import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  PantallaDesignaciones,
  RUTA_DESIGNACIONES,
} from '@/features/arbitraje/designaciones/PantallaDesignaciones'
import type {
  CandidatoArbitral,
  Designacion,
  DesignacionesJornada,
  PartidoDesignable,
  PartidoResumen,
} from '@/features/arbitraje/designaciones/tipos'
import type { Ambito, Permiso } from '@/features/seguridad/tipos'
import { ApiError } from '@/lib/api/ApiError'
import type { Pagina } from '@/lib/api/tipos'

const { get, post, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Comisión', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR = 'arbitraje.designacion:CONSULTAR'
const EDITAR = 'arbitraje.designacion:EDITAR'
const SEMANA = '2026-11-23'

function otorgar(ambito: Ambito, ...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: 'M-05',
    ambito,
    clubId: null,
  }))
}

const club = (id: string, nombre: string) => ({ id, nombre })
const HALCONES = club('e1', 'Halcones')
const TOROS = club('e2', 'Toros')
const CANCHA = club('k1', 'Cancha 1')

function designacion(
  id: string,
  nombre: string | null,
  funcion: Designacion['funcion'],
  designadoEn = '2026-11-20T10:00:00-06:00',
): Designacion {
  return {
    id,
    arbitro: { id: `a-${id}`, nombre, club: club('cl-norte', 'Club Norte') },
    funcion,
    automatica: false,
    designadoEn,
  }
}

function partido(
  id: string,
  local: string,
  visitante: string,
  cambios: Partial<PartidoDesignable> = {},
): PartidoDesignable {
  return {
    id,
    inicio: '2026-11-28T10:00:00-06:00',
    finBloque: '2026-11-28T12:00:00-06:00',
    estado: 'PROGRAMADO',
    cancha: CANCHA,
    equipoLocal: club(`${id}-l`, local),
    equipoVisitante: club(`${id}-v`, visitante),
    designable: true,
    central: null,
    asistentes: [],
    ...cambios,
  }
}

const SIN_CENTRAL = partido('p1', 'Halcones', 'Toros')
const CON_TERNA = partido('p2', 'Pumas', 'Lobos', {
  central: designacion('d1', 'Ana Ruiz', 'CENTRAL'),
  asistentes: [
    designacion('d2', 'Juan Díaz', 'ASISTENTE', '2026-11-21T10:00:00-06:00'),
    { ...designacion('d3', null, 'ASISTENTE', '2026-11-20T09:00:00-06:00'), automatica: true },
  ],
})
const EN_CURSO = partido('p3', 'Águilas', 'Halcones', { estado: 'EN_CURSO', designable: false })

function jornada(
  id: string,
  numero: number,
  partidos: PartidoDesignable[],
  avance = { partidos: 6, conCentral: 4, asistentesCompletos: 1 },
): DesignacionesJornada {
  return {
    jornada: {
      id,
      numero,
      estadoPublicacion: 'BORRADOR',
      grupo: club(`g-${id}`, 'Grupo A'),
      categoria: club(`c-${id}`, 'Libre'),
    },
    avance,
    partidos,
  }
}

const CANDIDATOS: CandidatoArbitral[] = [
  {
    arbitro: { id: 'a-marta', nombre: 'Marta López', club: club('cl-n', 'Club Norte') },
    elegible: true,
    causas: [],
    advertencias: [],
    carga: 3,
  },
  {
    arbitro: { id: 'a-luis', nombre: 'Luis Soto', club: HALCONES },
    elegible: false,
    causas: [{ codigo: 'VAL-019', mensaje: 'Pertenece a Halcones, que juega este partido.' }],
    advertencias: [],
    carga: 0,
  },
  {
    arbitro: { id: 'a-raul', nombre: 'Raúl Mora', club: club('cl-s', 'Club Sur') },
    elegible: false,
    causas: [
      { codigo: 'RF-039', mensaje: 'Ya está designado en Pumas vs Lobos a las 10:00 (Cancha 1).' },
    ],
    advertencias: [],
    carga: 2,
  },
  {
    arbitro: { id: 'a-carlos', nombre: 'Carlos Vega', club: club('cl-o', 'Club Oriente') },
    elegible: true,
    causas: [],
    advertencias: [
      { codigo: 'RF-022', mensaje: 'Declaró que no está disponible en ese horario.' },
    ],
    carga: 1,
  },
]

function resumen(
  id: string,
  jornadaId: string,
  numero: number,
  estado: PartidoResumen['estado'] = 'PROGRAMADO',
): PartidoResumen {
  return {
    id,
    jornadaId,
    jornadaNumero: numero,
    equipoLocal: HALCONES,
    equipoVisitante: TOROS,
    cancha: CANCHA,
    inicio: '2026-11-28T10:00:00-06:00',
    finBloque: '2026-11-28T12:00:00-06:00',
    estado,
    accionesPermitidas: [],
  }
}

function pagina<T>(contenido: T[], numero: number, totalPaginas: number): Pagina<T> {
  return { contenido, pagina: numero, tamano: 100, totalElementos: contenido.length, totalPaginas }
}

type Respuestas = {
  partidos?: Pagina<PartidoResumen>[]
  jornadas?: Record<string, DesignacionesJornada>
  candidatos?: CandidatoArbitral[]
}

function responder({ partidos = [], jornadas = {}, candidatos = CANDIDATOS }: Respuestas) {
  get.mockImplementation(async (url: string) => {
    if (url.startsWith('partidos?')) {
      const numero = Number(new URLSearchParams(url.split('?')[1]).get('pagina'))
      return partidos[numero] ?? pagina([], numero, partidos.length)
    }
    if (url.startsWith('designaciones?')) {
      const id = new URLSearchParams(url.split('?')[1]).get('jornadaId') ?? ''
      const encontrada = jornadas[id]
      if (!encontrada) {
        throw new ApiError({ title: 'No encontrado', status: 404, detail: 'No existe.', codigo: 'NO_ENCONTRADO' })
      }
      return encontrada
    }
    if (url.endsWith('/arbitros-candidatos')) {
      return candidatos
    }
    throw new Error(`URL inesperada: ${url}`)
  })
}

function error(status: number, codigo: string, detail: string) {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

function montar(entrada: string) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const enrutador = createMemoryRouter(
    [{ path: RUTA_DESIGNACIONES, element: <PantallaDesignaciones /> }],
    { initialEntries: [entrada] },
  )

  render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return { cliente, enrutador }
}

const llamadasA = (prefijo: string) =>
  get.mock.calls.filter(([url]) => String(url).startsWith(prefijo))

async function abrirJornada(partidos: PartidoDesignable[] = [SIN_CENTRAL, CON_TERNA, EN_CURSO]) {
  responder({ jornadas: { j1: jornada('j1', 3, partidos) } })
  const montado = montar(`${RUTA_DESIGNACIONES}?jornadaId=j1`)
  await screen.findByRole('heading', { name: 'Jornada 3' })
  return montado
}

async function abrirDialogoCentral() {
  const usuario = userEvent.setup()
  await abrirJornada()
  await usuario.click(
    screen.getByRole('button', { name: 'Designar árbitro central de Halcones vs Toros' }),
  )
  const dialogo = await screen.findByRole('dialog', {
    name: 'Designar árbitro central · Halcones vs Toros',
  })
  await within(dialogo).findByRole('radio', { name: 'Marta López' })
  return { usuario, dialogo }
}

describe('PantallaDesignaciones (F-24, H-14.2)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    otorgar('LIGA', CONSULTAR, EDITAR)
  })

  it('H-14.2: agrupa los partidos de la semana por jornada, recorre las páginas y descarta los cancelados', async () => {
    responder({
      partidos: [
        pagina([resumen('x1', 'j1', 3), resumen('x2', 'j2', 4, 'CANCELADO')], 0, 2),
        pagina([resumen('x3', 'j1', 3), resumen('x4', 'j3', 5)], 1, 2),
      ],
      jornadas: {
        j1: jornada('j1', 3, []),
        j3: jornada('j3', 5, [], { partidos: 1, conCentral: 0, asistentesCompletos: 0 }),
      },
    })
    montar(`${RUTA_DESIGNACIONES}?semana=${SEMANA}`)

    expect(screen.getByText('23 – 29 nov 2026')).toBeInTheDocument()
    const lista = await screen.findByRole('list', { name: 'Jornadas de la semana' })
    const filas = await within(lista).findAllByRole('link')
    await waitFor(() => expect(filas[0]).toHaveTextContent('Libre · Grupo A · Jornada 3'))

    expect(filas).toHaveLength(2)
    expect(filas[0]).toHaveTextContent('4 de 6 con central')
    expect(filas[0]).toHaveTextContent('Borrador')
    expect(filas[0]).toHaveTextContent('Sáb 28 nov')
    expect(filas[0]).toHaveAttribute('href', `${RUTA_DESIGNACIONES}?semana=${SEMANA}&jornadaId=j1`)
    await waitFor(() => expect(filas[1]).toHaveTextContent('Libre · Grupo A · Jornada 5'))
    expect(screen.queryByText(/Jornada 4/)).not.toBeInTheDocument()

    const paginas = llamadasA('partidos?').map(([url]) => String(url))
    expect(paginas).toEqual([
      'partidos?desde=2026-11-23T00%3A00%3A00-06%3A00&hasta=2026-11-30T00%3A00%3A00-06%3A00&pagina=0&tamano=100',
      'partidos?desde=2026-11-23T00%3A00%3A00-06%3A00&hasta=2026-11-30T00%3A00%3A00-06%3A00&pagina=1&tamano=100',
    ])
    expect(llamadasA('designaciones?jornadaId=j2')).toHaveLength(0)
  })

  it('H-14.2: con más de 5 páginas avisa que la lista puede omitir jornadas', async () => {
    responder({
      partidos: Array.from({ length: 6 }, (_, indice) =>
        pagina([resumen(`x${indice}`, 'j1', 3)], indice, 6),
      ),
      jornadas: { j1: jornada('j1', 3, []) },
    })
    montar(`${RUTA_DESIGNACIONES}?semana=${SEMANA}`)

    expect(await screen.findByText(/puede omitir\s+jornadas/)).toBeInTheDocument()
    expect(llamadasA('partidos?')).toHaveLength(5)
  })

  it('H-14.2: una semana sin partidos lo dice', async () => {
    responder({ partidos: [pagina([], 0, 0)] })
    montar(`${RUTA_DESIGNACIONES}?semana=${SEMANA}`)

    expect(await screen.findByText('No hay partidos programados esa semana.')).toBeInTheDocument()
  })

  it('H-14.2: ?jornadaId= abre la jornada aunque falte la semana, con el avance «4 de 6»', async () => {
    await abrirJornada()

    const barra = screen.getByRole('progressbar', { name: '4 de 6 partidos con árbitro central' })
    expect(barra).toHaveAttribute('aria-valuenow', '4')
    expect(barra).toHaveAttribute('aria-valuemax', '6')
    expect(screen.getByText(/con asistentes completos/)).toHaveTextContent(
      '1 de 6 con asistentes completos',
    )
    expect(screen.getByText('Libre · Grupo A')).toBeInTheDocument()
    expect(screen.getByText(/cuando se publique la jornada/)).toBeInTheDocument()
    expect(
      within(screen.getByRole('article', { name: 'Halcones vs Toros' })).getByText('Falta central'),
    ).toBeInTheDocument()
    expect(llamadasA('partidos?')).toHaveLength(0)
    expect(screen.getByRole('link', { name: 'Jornadas de la semana' })).toHaveAttribute(
      'href',
      `${RUTA_DESIGNACIONES}?semana=${SEMANA}`,
    )
  })

  it('H-14.2 · VAL-019 · RF-039: los no elegibles quedan deshabilitados con su causa accesible', async () => {
    const { dialogo } = await abrirDialogoCentral()

    const luis = within(dialogo).getByRole('radio', { name: 'Luis Soto' })
    expect(luis).toBeDisabled()
    expect(luis).toHaveAccessibleDescription(/Pertenece a Halcones, que juega este partido\./)

    const raul = within(dialogo).getByRole('radio', { name: 'Raúl Mora' })
    expect(raul).toBeDisabled()
    expect(raul).toHaveAccessibleDescription(/Ya está designado en Pumas vs Lobos a las 10:00/)
    expect(within(dialogo).getByText(/Ya está designado en Pumas/).closest('ul')).not.toHaveClass(
      'bg-warning/14',
    )

    const radios = within(dialogo).getAllByRole('radio')
    expect(radios.map((radio) => radio.getAttribute('value'))).toEqual([
      'a-carlos',
      'a-marta',
      'a-luis',
      'a-raul',
    ])
    expect(within(dialogo).getByRole('radio', { name: 'Marta López' })).toHaveAccessibleDescription(
      /3 designaciones en la temporada/,
    )
  })

  it('H-14.2 · RF-022: la advertencia de disponibilidad va en ámbar y no impide elegir', async () => {
    const { usuario, dialogo } = await abrirDialogoCentral()

    const carlos = within(dialogo).getByRole('radio', { name: 'Carlos Vega' })
    expect(carlos).toBeEnabled()
    expect(carlos).toHaveAccessibleDescription(/Declaró que no está disponible en ese horario\./)
    expect(within(dialogo).getByText(/Declaró que no está disponible/).closest('ul')).toHaveClass(
      'bg-warning/14',
    )

    await usuario.click(carlos)
    expect(carlos).toBeChecked()
  })

  it('H-14.2 · DEC-201: designa al central de un partido sin central sin enviar motivo y refresca', async () => {
    const { usuario, dialogo } = await abrirDialogoCentral()
    post.mockResolvedValue({ ...designacion('d9', 'Marta López', 'CENTRAL'), partidoId: 'p1', vigente: true })

    expect(within(dialogo).queryByLabelText(/Motivo del cambio/)).not.toBeInTheDocument()
    await usuario.click(within(dialogo).getByRole('radio', { name: 'Marta López' }))
    await usuario.click(within(dialogo).getByRole('button', { name: 'Designar' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('partidos/p1/designaciones', {
        arbitroId: 'a-marta',
        funcion: 'CENTRAL',
      }),
    )
    expect(post.mock.calls[0][1]).not.toHaveProperty('motivo')
    const confirmacion = await screen.findByText(
      'Se designó a Marta López como árbitro central de Halcones vs Toros.',
    )
    const tarjeta = screen.getByRole('article', { name: 'Halcones vs Toros' })
    expect(tarjeta).toContainElement(confirmacion)
    await waitFor(() => expect(tarjeta).toHaveFocus())
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('H-14.2: tras designar invalida las designaciones, el detalle del partido y la programación', async () => {
    const usuario = userEvent.setup()
    const { cliente } = await abrirJornada()
    const invalidar = vi.spyOn(cliente, 'invalidateQueries')
    post.mockResolvedValue({ ...designacion('d9', 'Marta López', 'ASISTENTE'), partidoId: 'p1' })

    await usuario.click(
      screen.getByRole('button', { name: 'Designar asistente 1 de Halcones vs Toros' }),
    )
    const dialogo = await screen.findByRole('dialog', {
      name: 'Designar asistente · Halcones vs Toros',
    })
    await usuario.click(await within(dialogo).findByRole('radio', { name: 'Marta López' }))
    await usuario.click(within(dialogo).getByRole('button', { name: 'Designar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(post).toHaveBeenCalledWith('partidos/p1/designaciones', {
      arbitroId: 'a-marta',
      funcion: 'ASISTENTE',
    })
    expect(invalidar).toHaveBeenCalledWith({ queryKey: ['arbitraje', 'designaciones'] })
    expect(invalidar).toHaveBeenCalledWith({ queryKey: ['partidos', 'detalle', 'p1'] })
    expect(invalidar).toHaveBeenCalledWith({ queryKey: ['programacion'] })
    await waitFor(() => expect(llamadasA('designaciones?jornadaId=j1').length).toBeGreaterThan(1))
  })

  it('H-14.2 · DEC-202 · RN-021: sustituir al central exige motivo en el cliente y muestra el 400 en el campo', async () => {
    const usuario = userEvent.setup()
    await abrirJornada()

    await usuario.click(
      screen.getByRole('button', { name: 'Cambiar árbitro central de Pumas vs Lobos' }),
    )
    const dialogo = await screen.findByRole('dialog', {
      name: 'Designar árbitro central · Pumas vs Lobos',
    })
    expect(dialogo).toHaveAccessibleDescription(/Sustituye a Ana Ruiz/)
    await usuario.click(await within(dialogo).findByRole('radio', { name: 'Marta López' }))
    const motivo = within(dialogo).getByLabelText(/Motivo del cambio/)

    await usuario.type(motivo, 'corto')
    await usuario.click(within(dialogo).getByRole('button', { name: 'Designar' }))
    expect(post).not.toHaveBeenCalled()
    expect(motivo).toHaveAttribute('aria-invalid', 'true')
    expect(motivo).toHaveAccessibleDescription(/Escribe un motivo de 10 a 500 caracteres/)

    post.mockRejectedValue(
      error(400, 'RN-021', 'Escribe un motivo de 10 a 500 caracteres para sustituir al árbitro central.'),
    )
    await usuario.clear(motivo)
    await usuario.type(motivo, '  Lesión del árbitro titular  ')
    await usuario.click(within(dialogo).getByRole('button', { name: 'Designar' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('partidos/p2/designaciones', {
        arbitroId: 'a-marta',
        funcion: 'CENTRAL',
        motivo: 'Lesión del árbitro titular',
      }),
    )
    await waitFor(() =>
      expect(motivo).toHaveAccessibleDescription(
        /Escribe un motivo de 10 a 500 caracteres para sustituir al árbitro central\./,
      ),
    )
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('H-14.2 · RF-039: un 409 del backend se muestra en el diálogo, que sigue abierto y refresca la lista', async () => {
    const { usuario, dialogo } = await abrirDialogoCentral()
    post.mockRejectedValue(
      error(409, 'RF-039', 'Marta López ya está designada en otro partido que se traslapa.'),
    )
    const antes = get.mock.calls.filter(([url]) => String(url).endsWith('/arbitros-candidatos')).length

    await usuario.click(within(dialogo).getByRole('radio', { name: 'Marta López' }))
    await usuario.click(within(dialogo).getByRole('button', { name: 'Designar' }))

    expect(
      await within(dialogo).findByText('Marta López ya está designada en otro partido que se traslapa.'),
    ).toHaveAttribute('role', 'alert')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await waitFor(() =>
      expect(
        get.mock.calls.filter(([url]) => String(url).endsWith('/arbitros-candidatos')).length,
      ).toBeGreaterThan(antes),
    )
  })

  it('H-14.2 · DEC-203: retira una designación con motivo', async () => {
    const usuario = userEvent.setup()
    await abrirJornada()
    post.mockResolvedValue({ ...designacion('d1', 'Ana Ruiz', 'CENTRAL'), vigente: false })

    await usuario.click(screen.getByRole('button', { name: 'Retirar a Ana Ruiz de Pumas vs Lobos' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Retirar a Ana Ruiz del partido' })
    await usuario.type(within(dialogo).getByLabelText(/Motivo/), 'El árbitro reportó una lesión')
    await usuario.click(within(dialogo).getByRole('button', { name: 'Retirar' }))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('designaciones/d1/retiro', {
        motivo: 'El árbitro reportó una lesión',
      }),
    )
    expect(await screen.findByText('Se retiró a Ana Ruiz de Pumas vs Lobos.')).toBeInTheDocument()
  })

  it('H-14.2 · DEC-201: con dos asistentes no se ofrece un tercero', async () => {
    await abrirJornada()
    const tarjeta = screen.getByRole('article', { name: 'Pumas vs Lobos' })

    expect(within(tarjeta).queryByRole('button', { name: /^Designar/ })).not.toBeInTheDocument()
    expect(within(tarjeta).getAllByRole('button', { name: /^Retirar/ })).toHaveLength(3)
    expect(within(tarjeta).getByText('Nombre reservado')).toBeInTheDocument()
    expect(within(tarjeta).getByText('Automática')).toBeInTheDocument()
    expect(within(tarjeta).getByText('Terna completa')).toBeInTheDocument()
    const lugares = within(within(tarjeta).getByRole('list')).getAllByRole('listitem')
    expect(lugares[1]).toHaveTextContent('Asistente 1')
    expect(lugares[1]).toHaveTextContent('Nombre reservado')
    expect(lugares[2]).toHaveTextContent('Juan Díaz')
  })

  it('H-14.2: un partido no designable queda de solo lectura con su estado', async () => {
    await abrirJornada()
    const tarjeta = screen.getByRole('article', { name: 'Águilas vs Halcones' })

    expect(within(tarjeta).getByText('En curso')).toBeInTheDocument()
    expect(
      within(tarjeta).getByText('Solo se designan árbitros a partidos programados o reprogramados.'),
    ).toBeInTheDocument()
    expect(within(tarjeta).queryByRole('button')).not.toBeInTheDocument()
  })

  it('H-14.2: sin EDITAR no hay botones ni se piden candidatos', async () => {
    otorgar('LIGA', CONSULTAR)
    await abrirJornada()

    expect(screen.queryByRole('button', { name: /^(Designar|Cambiar|Retirar)/ })).not.toBeInTheDocument()
    expect(get.mock.calls.some(([url]) => String(url).endsWith('/arbitros-candidatos'))).toBe(false)
  })

  it('H-14.2: sin consultar en el ámbito de la liga explica de quién es la pantalla', async () => {
    otorgar('PROPIO', CONSULTAR)
    responder({})
    montar(RUTA_DESIGNACIONES)

    expect(
      screen.getByText('Esta pantalla es de la Comisión de Arbitraje y de la Programación'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver mis designaciones' })).toHaveAttribute(
      'href',
      '/app/mis-designaciones',
    )
    expect(get).not.toHaveBeenCalled()
  })
})

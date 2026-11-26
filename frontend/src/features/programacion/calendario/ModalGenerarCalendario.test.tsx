import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TarjetaCalendario } from '@/features/programacion/calendario/TarjetaCalendario'
import type {
  Calendario,
  CalendarioGenerado,
  FechaCalendario,
  GrupoCalendario,
} from '@/features/programacion/calendario/tipos'
import { errorApi, version } from '@/features/temporadas/datosPruebaEstructura'
import type {
  CategoriaTemporada,
  ParametrosTemporada,
  Temporada,
} from '@/features/temporadas/tipos'

const { get, post, patch } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, post, patch } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({ useSesionActual: () => null }))

const MOTIVO = 'Se sumaron dos equipos al grupo'

/** Lunes: la fecha 1 es el sábado 5 de diciembre (DEC-186). */
const TEMPORADA: Temporada = {
  id: 't1',
  nombre: 'Temporada 2026-2027',
  fechaInicio: '2026-11-30',
  estado: 'ACTIVA',
  creadoEn: '2026-01-01T10:00:00-06:00',
  categoriasAsignadas: 2,
  gruposCreados: 3,
}

const CATEGORIAS: CategoriaTemporada[] = [
  {
    id: 'ct-libre',
    categoria: { id: 'cat-libre', nombre: 'Libre', tipo: 'LIBRE', orden: 1 },
    diasJuego: [7],
    grupos: [
      { id: 'g-a', nombre: 'A', equipos: 4 },
      { id: 'g-b', nombre: 'B', equipos: 1 },
    ],
  },
  {
    id: 'ct-vet',
    categoria: { id: 'cat-vet', nombre: 'Veteranos', tipo: 'VETERANOS', orden: 2 },
    diasJuego: [5, 7],
    grupos: [{ id: 'g-v', nombre: 'Único', equipos: 5 }],
  },
]

const CON_PARAMETROS: ParametrosTemporada = {
  vigente: version(3, { temporadaId: 't1', vueltas: 2 }),
  historial: [],
}

function fechas(partidoId?: string): FechaCalendario[] {
  return [
    {
      numero: 1,
      vuelta: 1,
      partidos: [
        {
          emparejamientoId: 'em-1',
          local: { id: 'e1', nombre: 'Halcones', estado: 'ACTIVO' },
          visitante: { id: 'e2', nombre: 'Toros', estado: 'ACTIVO' },
          ...(partidoId ? { partidoId } : {}),
        },
      ],
    },
  ]
}

function grupo(
  grupoId: string,
  nombre: string,
  categoria: 'Libre' | 'Veteranos',
  conFechas: FechaCalendario[] = [],
): GrupoCalendario {
  return {
    grupoId,
    nombre,
    categoria: { id: categoria === 'Libre' ? 'cat-libre' : 'cat-vet', nombre: categoria },
    generado: conFechas.length > 0,
    fechas: conFechas,
  }
}

function calendario(
  a: FechaCalendario[] = [],
  b: FechaCalendario[] = [],
  v: FechaCalendario[] = [],
): Calendario {
  return {
    temporadaId: 't1',
    grupos: [grupo('g-a', 'A', 'Libre', a), grupo('g-b', 'B', 'Libre', b), grupo('g-v', 'Único', 'Veteranos', v)],
  }
}

const GENERADO: CalendarioGenerado = {
  versionParametros: 3,
  vueltas: 2,
  totalFechas: 6,
  fechasCreadas: 6,
  grupos: [
    { grupoId: 'g-a', nombre: 'A', equipos: 4, fechas: 6, reemplazado: false },
    { grupoId: 'g-v', nombre: 'Único', equipos: 5, fechas: 10, reemplazado: false },
  ],
  gruposOmitidos: [
    {
      grupoId: 'g-b',
      nombre: 'B',
      equiposActivos: 1,
      causa: 'Tiene 1 equipo activo; se necesitan al menos 2 para generar su calendario.',
    },
  ],
  advertencias: [
    {
      tipo: 'FECHAS_DESPUES_DEL_FIN',
      mensaje: 'Las 10 fechas del calendario terminan después del fin de la temporada.',
    },
  ],
}

function responder({
  cal = calendario(),
  parametros = CON_PARAMETROS,
}: { cal?: Calendario; parametros?: ParametrosTemporada } = {}) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta === 'temporadas/t1/calendario') {
      return cal
    }
    if (ruta === 'temporadas/t1/parametros') {
      return parametros
    }

    throw new Error(`GET no simulado: ${ruta}`)
  })
  post.mockResolvedValue(GENERADO)
  patch.mockImplementation(async (ruta: string, cuerpo: { diasJuego: number[] }) => ({
    ...CATEGORIAS.find(({ id }) => ruta.endsWith(id)),
    diasJuego: cuerpo.diasJuego,
  }))
}

function montar() {
  const clienteConsultas = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const router = createMemoryRouter([
    {
      path: '/',
      element: <TarjetaCalendario temporada={TEMPORADA} categorias={CATEGORIAS} />,
    },
  ])

  return render(
    <QueryClientProvider client={clienteConsultas}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

async function abrirModal(boton = 'Generar calendario') {
  const usuario = userEvent.setup()

  await usuario.click(await screen.findByRole('button', { name: boton }))
  const dialogo = await screen.findByRole('dialog', { name: boton })

  return { usuario, dialogo }
}

function casilla(dialogo: HTMLElement, nombre: string) {
  return within(dialogo).getByRole('checkbox', { name: new RegExp(`^${nombre} `) })
}

function diasDe(dialogo: HTMLElement, categoria: string) {
  return within(within(dialogo).getByRole('group', { name: categoria }))
}

function botonGenerar(dialogo: HTMLElement) {
  return within(dialogo).getByRole('button', { name: 'Generar calendario' })
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('ModalGenerarCalendario (F-22, H-32.4)', () => {
  it('criterio 1: sin calendario genera todos los grupos desde la fecha de inicio', async () => {
    responder()
    montar()

    expect(await screen.findByText('Sin generar · 3 grupos')).toBeInTheDocument()
    const { usuario, dialogo } = await abrirModal()

    expect(await within(dialogo).findByText('2 vueltas · parámetros versión 3')).toBeInTheDocument()
    expect(within(dialogo).getByLabelText(/Fecha de inicio/)).toHaveValue('2026-11-30')
    expect(
      within(dialogo).getByText('La fecha 1 será el sáb 5 y dom 6 dic 2026.'),
    ).toBeInTheDocument()
    expect(casilla(dialogo, 'A')).toBeChecked()
    expect(casilla(dialogo, 'B')).toBeChecked()
    expect(casilla(dialogo, 'Único')).toBeChecked()
    expect(within(dialogo).queryByLabelText(/Motivo del reemplazo/)).not.toBeInTheDocument()

    await usuario.click(botonGenerar(dialogo))

    expect(
      await within(dialogo).findByRole('heading', { name: 'Calendario generado' }),
    ).toBeInTheDocument()
    expect(post).toHaveBeenCalledWith('temporadas/t1/calendario', { fechaInicio: '2026-11-30' })
    expect(patch).not.toHaveBeenCalled()
    expect(
      within(dialogo).getByText('2 grupos · 6 fechas en total (6 nuevas) · 2 vueltas (parámetros versión 3)'),
    ).toBeInTheDocument()
    const omitidos = within(dialogo).getByRole('region', { name: 'Grupos omitidos' })
    expect(within(omitidos).getByText(/se necesitan al menos 2/)).toBeInTheDocument()
    const advertencias = within(dialogo).getByRole('region', { name: 'Advertencias' })
    expect(
      within(advertencias).getByText(/terminan después del fin de la temporada/),
    ).toBeInTheDocument()
  })

  it('criterio 2 (DEC-190): sin parámetros vigentes no hay formulario y enlaza a F-11', async () => {
    responder({ parametros: { vigente: null, historial: [] } })
    montar()
    const { dialogo } = await abrirModal()

    expect(
      await within(dialogo).findByText(/la temporada todavía no tiene/),
    ).toBeInTheDocument()
    expect(
      within(dialogo).getByRole('link', { name: 'Ver los parámetros de la temporada' }),
    ).toHaveAttribute('href', '/app/administracion/temporadas/t1/estructura?pestana=parametros')
    expect(within(dialogo).queryByLabelText(/Fecha de inicio/)).not.toBeInTheDocument()
    expect(within(dialogo).queryByRole('button', { name: 'Generar calendario' })).not.toBeInTheDocument()
  })

  it('criterio 3 (DEC-198): con calendario en un grupo pide motivo solo al reemplazarlo', async () => {
    responder({ cal: calendario(fechas()) })
    montar()

    expect(
      await screen.findByText('1 de 3 grupos con calendario · 1 fecha · 1 vuelta'),
    ).toBeInTheDocument()
    const { usuario, dialogo } = await abrirModal()
    await within(dialogo).findByText('2 vueltas · parámetros versión 3')

    expect(casilla(dialogo, 'A')).not.toBeChecked()
    expect(casilla(dialogo, 'B')).toBeChecked()
    expect(casilla(dialogo, 'Único')).toBeChecked()
    expect(
      within(dialogo).getByText(/El calendario actual tiene 1 vuelta y los parámetros vigentes, 2/),
    ).toBeInTheDocument()

    await usuario.click(casilla(dialogo, 'A'))
    await usuario.click(casilla(dialogo, 'B'))
    const motivo = within(dialogo).getByLabelText(/Motivo del reemplazo/)
    expect(within(dialogo).getByText(/Se reemplaza el calendario de: Libre · A/)).toBeInTheDocument()

    await usuario.type(motivo, 'corto')
    await usuario.click(botonGenerar(dialogo))
    expect(
      within(dialogo).getByText('Escribe un motivo de al menos 10 caracteres.'),
    ).toBeInTheDocument()
    expect(motivo).toHaveFocus()
    expect(post).not.toHaveBeenCalled()

    await usuario.clear(motivo)
    await usuario.type(motivo, `  ${MOTIVO}  `)
    await usuario.click(botonGenerar(dialogo))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('temporadas/t1/calendario', {
        grupoIds: ['g-a', 'g-v'],
        motivo: MOTIVO,
      }),
    )
  })

  it('criterio 3 (DEC-200): con calendario, «Recalcular» manda la fecha de inicio', async () => {
    responder({ cal: calendario(fechas()) })
    montar()
    const { usuario, dialogo } = await abrirModal()
    await within(dialogo).findByText('2 vueltas · parámetros versión 3')

    expect(
      within(dialogo).getByRole('radio', { name: 'Conservar los días de las fechas actuales' }),
    ).toBeChecked()
    expect(within(dialogo).queryByLabelText(/Fecha de inicio/)).not.toBeInTheDocument()

    await usuario.click(
      within(dialogo).getByRole('radio', { name: 'Recalcular desde otra fecha de inicio' }),
    )
    expect(within(dialogo).getByLabelText(/Fecha de inicio/)).toHaveValue('2026-11-30')
    expect(within(dialogo).getByText(/también los que se cambiaron a mano/)).toBeInTheDocument()

    await usuario.click(botonGenerar(dialogo))

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('temporadas/t1/calendario', {
        fechaInicio: '2026-11-30',
        grupoIds: ['g-b', 'g-v'],
      }),
    )
  })

  it('criterio 4 (RN-020): un grupo con partidos no se puede marcar', async () => {
    responder({ cal: calendario(fechas('p1')) })
    montar()
    const { dialogo } = await abrirModal()
    await within(dialogo).findByText('2 vueltas · parámetros versión 3')

    expect(casilla(dialogo, 'A')).toBeDisabled()
    expect(casilla(dialogo, 'A')).not.toBeChecked()
    expect(casilla(dialogo, 'A')).toHaveAccessibleDescription(
      'Ya tiene partidos programados; su calendario no se puede regenerar (RN-020).',
    )
  })

  it('criterio 5: un 409 RN-020 desmarca el grupo y muestra la causa', async () => {
    responder()
    post.mockRejectedValue(
      errorApi(409, 'RN-020', 'El calendario de «Libre – B» ya tiene partidos o jornadas programados.', {
        extensiones: { grupos: [{ grupoId: 'g-b', nombre: 'B' }] },
      }),
    )
    montar()
    const { usuario, dialogo } = await abrirModal()
    await within(dialogo).findByText('2 vueltas · parámetros versión 3')

    await usuario.click(botonGenerar(dialogo))

    expect(
      await within(dialogo).findByText(/ya tiene partidos o jornadas programados/),
    ).toBeInTheDocument()
    expect(casilla(dialogo, 'B')).not.toBeChecked()
    expect(casilla(dialogo, 'B')).toBeDisabled()
    expect(casilla(dialogo, 'A')).toBeChecked()
  })

  it('criterio 6 (DEC-199): guarda los días que cambiaron antes de generar', async () => {
    responder()
    montar()
    const { usuario, dialogo } = await abrirModal()
    await within(dialogo).findByText('2 vueltas · parámetros versión 3')

    const libre = diasDe(dialogo, 'Libre')
    await usuario.click(libre.getByRole('checkbox', { name: 'Domingo' }))
    await usuario.click(libre.getByRole('checkbox', { name: 'Sábado' }))

    const veteranos = diasDe(dialogo, 'Veteranos')
    expect(veteranos.getByText('También: viernes')).toBeInTheDocument()
    await usuario.click(veteranos.getByRole('checkbox', { name: 'Domingo' }))

    await usuario.click(botonGenerar(dialogo))
    await within(dialogo).findByRole('heading', { name: 'Calendario generado' })

    expect(patch).toHaveBeenNthCalledWith(1, 'temporadas/t1/categorias/ct-libre', { diasJuego: [6] })
    expect(patch).toHaveBeenNthCalledWith(2, 'temporadas/t1/categorias/ct-vet', { diasJuego: [5] })
    expect(patch.mock.invocationCallOrder[1]).toBeLessThan(post.mock.invocationCallOrder[0])
    expect(within(dialogo).getByText('Libre: sábado')).toBeInTheDocument()
  })

  it('criterio 6: una categoría sin días es un error y no se manda nada', async () => {
    responder()
    montar()
    const { usuario, dialogo } = await abrirModal()
    await within(dialogo).findByText('2 vueltas · parámetros versión 3')

    await usuario.click(diasDe(dialogo, 'Libre').getByRole('checkbox', { name: 'Domingo' }))

    expect(
      within(dialogo).getByText('Marca al menos un día de juego para Libre.'),
    ).toBeInTheDocument()
    await usuario.click(botonGenerar(dialogo))
    expect(patch).not.toHaveBeenCalled()
    expect(post).not.toHaveBeenCalled()
  })

  it('si falla el segundo PATCH no genera y nombra la categoría', async () => {
    responder()
    patch
      .mockResolvedValueOnce({ ...CATEGORIAS[0], diasJuego: [6, 7] })
      .mockRejectedValueOnce(
        errorApi(409, 'RF-003', 'La temporada está cerrada: su estructura es de solo lectura.'),
      )
    montar()
    const { usuario, dialogo } = await abrirModal()
    await within(dialogo).findByText('2 vueltas · parámetros versión 3')

    await usuario.click(diasDe(dialogo, 'Libre').getByRole('checkbox', { name: 'Sábado' }))
    await usuario.click(diasDe(dialogo, 'Veteranos').getByRole('checkbox', { name: 'Sábado' }))
    await usuario.click(botonGenerar(dialogo))

    expect(
      await within(dialogo).findByText(
        'Se guardaron los días de Libre. No se pudieron guardar los de Veteranos: La temporada está cerrada: su estructura es de solo lectura. No se generó el calendario.',
      ),
    ).toBeInTheDocument()
    expect(patch).toHaveBeenCalledTimes(2)
    expect(post).not.toHaveBeenCalled()
  })

  it('sin grupos marcados y con días cambiados solo guarda los días', async () => {
    responder({ cal: calendario(fechas(), fechas(), fechas()) })
    montar()
    const { usuario, dialogo } = await abrirModal('Regenerar calendario')
    await within(dialogo).findByText('2 vueltas · parámetros versión 3')

    expect(botonGenerar(dialogo)).toBeDisabled()
    expect(
      within(dialogo).getByText('Marca al menos un grupo o cambia los días de juego.'),
    ).toBeInTheDocument()

    await usuario.click(diasDe(dialogo, 'Libre').getByRole('checkbox', { name: 'Sábado' }))
    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar días de juego' }))

    expect(
      await within(dialogo).findByRole('heading', { name: 'Días de juego guardados' }),
    ).toBeInTheDocument()
    expect(within(dialogo).getByText('Libre: sábado y domingo')).toBeInTheDocument()
    expect(patch).toHaveBeenCalledWith('temporadas/t1/categorias/ct-libre', { diasJuego: [6, 7] })
    expect(post).not.toHaveBeenCalled()
  })

  it('un 422 que no es de la fecha se muestra como error general', async () => {
    responder()
    post.mockRejectedValue(
      errorApi(
        422,
        'VALIDACION',
        'Ningún grupo tiene al menos 2 equipos activos; registra o activa equipos antes de generar el calendario.',
      ),
    )
    montar()
    const { usuario, dialogo } = await abrirModal()
    await within(dialogo).findByText('2 vueltas · parámetros versión 3')

    await usuario.click(botonGenerar(dialogo))

    const alerta = await within(dialogo).findByRole('alert')
    expect(alerta).toHaveTextContent(/Ningún grupo tiene al menos 2 equipos activos/)
    expect(within(dialogo).getByLabelText(/Fecha de inicio/)).not.toHaveAttribute('aria-invalid')
  })

  it('un 422 de la fecha de inicio se muestra junto al campo', async () => {
    responder()
    post.mockRejectedValue(
      errorApi(422, 'VALIDACION', 'La fecha de inicio 2026-11-30 es anterior al inicio de la temporada.'),
    )
    montar()
    const { usuario, dialogo } = await abrirModal()
    await within(dialogo).findByText('2 vueltas · parámetros versión 3')

    await usuario.click(botonGenerar(dialogo))

    const campo = within(dialogo).getByLabelText(/Fecha de inicio/)
    await waitFor(() => expect(campo).toHaveAttribute('aria-invalid', 'true'))
    expect(campo).toHaveAccessibleDescription(
      'La fecha de inicio 2026-11-30 es anterior al inicio de la temporada.',
    )
  })
})

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SeccionHorariosClub } from '@/features/canchas/SeccionHorariosClub'
import type { Cancha, HorarioClub } from '@/features/canchas/tipos'
import type { Club } from '@/features/organizacion/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
import type { CategoriaTemporada, Temporada } from '@/features/temporadas/tipos'
import { ApiError } from '@/lib/api/ApiError'

const { get, put, sesion } = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/lib/api/cliente', () => ({ api: { get, put } }))
vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

const CONSULTAR_CANCHA = 'canchas.cancha:CONSULTAR'
const EDITAR_CANCHA = 'canchas.cancha:EDITAR'
const CONSULTAR_TEMPORADA = 'temporadas.temporada:CONSULTAR'
const CONSULTAR_CLUB = 'organizacion.club:CONSULTAR'
const RUTA_PUT = 'canchas/c1/horarios-club'

const CANCHA_CLUB: Cancha = {
  id: 'c1',
  nombre: 'Cancha Halcones',
  ubicacion: 'EXTERNA',
  propietario: 'CLUB',
  club: { id: 'club-1', nombre: 'Halcones' },
  activa: true,
  creadoEn: '2026-01-01T10:00:00-06:00',
}
const CANCHA_LIGA: Cancha = { ...CANCHA_CLUB, nombre: 'Cancha 4', propietario: 'LIGA', club: undefined }

function temporada(id: string, nombre: string, estado: Temporada['estado']): Temporada {
  return {
    id,
    nombre,
    fechaInicio: '2026-01-01',
    estado,
    creadoEn: '2026-01-01T10:00:00-06:00',
    categoriasAsignadas: 2,
    gruposCreados: 2,
  }
}

/** Por `fechaInicio` descendente, como el API: la ACTIVA no es la primera. */
const TEMPORADAS = [
  temporada('t-2027', 'Temporada 2027', 'CONFIGURACION'),
  temporada('t-2026', 'Temporada 2026', 'ACTIVA'),
  temporada('t-2025', 'Temporada 2025', 'CERRADA'),
]

const CATEGORIAS: CategoriaTemporada[] = [
  { id: 'ct-1', categoria: { id: 'cat-libre', nombre: 'Libre', tipo: 'LIBRE', orden: 1 }, grupos: [] },
  { id: 'ct-2', categoria: { id: 'cat-vet', nombre: 'Veteranos', tipo: 'VETERANOS', orden: 2 }, grupos: [] },
]

const CLUBES: Club[] = [
  { id: 'club-1', nombre: 'Halcones', estado: 'ACTIVO', creadoEn: '2026-01-01T10:00:00-06:00' },
  { id: 'club-2', nombre: 'Toros', estado: 'ACTIVO', creadoEn: '2026-01-01T10:00:00-06:00' },
]

function horario(id: string, extra: Partial<HorarioClub> = {}): HorarioClub {
  return {
    id,
    canchaId: 'c1',
    temporadaId: 't-2026',
    categoria: { id: 'cat-libre', nombre: 'Libre' },
    club: { id: 'club-1', nombre: 'Halcones' },
    diaSemana: 6,
    horaInicio: '10:00:00',
    horaFin: '12:00:00',
    esComodato: false,
    ...extra,
  }
}

function pagina<T>(contenido: T[]) {
  return { contenido, pagina: 0, tamano: 100, totalElementos: contenido.length, totalPaginas: 1 }
}

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({ codigo, modulo: 'M-06', ambito: 'LIGA', clubId: null }))
}

function responder(horarios: Record<string, HorarioClub[]> = { 't-2026': [horario('h1')] }) {
  get.mockImplementation(async (ruta: string) => {
    if (ruta.startsWith('temporadas?')) {
      return pagina(TEMPORADAS)
    }
    if (/^temporadas\/[^/]+\/categorias$/.test(ruta)) {
      return CATEGORIAS
    }
    if (ruta.startsWith('clubes?')) {
      return pagina(CLUBES)
    }
    const coincidencia = /^canchas\/c1\/horarios-club\?temporadaId=(.+)$/.exec(ruta)
    if (coincidencia) {
      return horarios[coincidencia[1]] ?? []
    }
    throw new Error(`Ruta no simulada: ${ruta}`)
  })
}

function montar(cancha: Cancha = CANCHA_CLUB) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SeccionHorariosClub cancha={cancha} />
    </QueryClientProvider>,
  )
}

const campo = (nombre: string, posicion: number) => screen.getByLabelText(`${nombre} del horario ${posicion}`)

/** Espera a que la fila 1 sea editable (categorías cargadas) y agrega una fila nueva. */
async function agregarFila(usuario: ReturnType<typeof userEvent.setup>, posicion: number, valores: Record<string, string>) {
  await usuario.click(screen.getByRole('button', { name: 'Agregar horario' }))
  for (const [nombre, valor] of Object.entries(valores)) {
    fireEvent.change(campo(nombre, posicion), { target: { value: valor } })
  }
}

async function esperarEditable() {
  await waitFor(() => expect(screen.getByRole('option', { name: 'Veteranos' })).toBeInTheDocument())
}

function error(status: number, codigo: string, detail: string, extra: Partial<ApiError['problema']> = {}) {
  return new ApiError({ title: 'Error', status, detail, codigo, ...extra })
}

describe('SeccionHorariosClub (F-21, B-13.2, RF-119, DEC-33)', () => {
  beforeEach(() => {
    get.mockReset()
    put.mockReset()
    otorgar(CONSULTAR_CANCHA, EDITAR_CANCHA, CONSULTAR_TEMPORADA, CONSULTAR_CLUB)
    responder()
  })

  it('carga la temporada ACTIVA por defecto y vuelve a pedir al cambiar de temporada', async () => {
    const usuario = userEvent.setup()
    montar()

    expect(await screen.findByRole('heading', { name: 'Horarios por categoría' })).toBeInTheDocument()
    await waitFor(() => expect(get).toHaveBeenCalledWith('canchas/c1/horarios-club?temporadaId=t-2026'))
    expect(screen.getByRole('combobox', { name: 'Temporada' })).toHaveValue('t-2026')
    expect(screen.getByText('Activa')).toBeInTheDocument()
    expect(await screen.findByRole('table', { name: 'Horarios por categoría' })).toBeInTheDocument()
    expect(campo('Inicio', 1)).toHaveValue('10:00')
    expect(campo('Fin', 1)).toHaveValue('12:00')

    await usuario.selectOptions(screen.getByRole('combobox', { name: 'Temporada' }), 't-2027')

    await waitFor(() => expect(get).toHaveBeenCalledWith('canchas/c1/horarios-club?temporadaId=t-2027'))
    expect(await screen.findByText('Sin horarios en esta temporada')).toBeInTheDocument()
    expect(screen.getByText('Configuración')).toBeInTheDocument()
  })

  it('RF-119: un traslape entre categorías distintas marca las dos filas y no llama al PUT', async () => {
    const usuario = userEvent.setup()
    put.mockImplementation(async (_ruta: string, cuerpo: { horarios: unknown[] }) =>
      cuerpo.horarios.map((_, indice) => horario(`g${indice}`)),
    )
    montar()
    await esperarEditable()

    await agregarFila(usuario, 2, { Categoría: 'cat-vet', Día: '6', Inicio: '11:00', Fin: '13:00' })
    await usuario.click(screen.getByRole('button', { name: 'Guardar horarios' }))

    expect(screen.getByText('Se traslapa con el horario 2 (Sábado 11:00–13:00).')).toBeInTheDocument()
    expect(screen.getByText('Se traslapa con el horario 1 (Sábado 10:00–12:00).')).toBeInTheDocument()
    expect(campo('Inicio', 1)).toHaveAttribute('aria-invalid', 'true')
    expect(campo('Inicio', 2)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('alert')).toHaveTextContent('Corrige los horarios marcados')
    expect(put).not.toHaveBeenCalled()

    // 10–12 y 12–14 no chocan: el intervalo es semiabierto.
    fireEvent.change(campo('Inicio', 2), { target: { value: '12:00' } })
    fireEvent.change(campo('Fin', 2), { target: { value: '14:00' } })
    await usuario.click(screen.getByRole('button', { name: 'Guardar horarios' }))

    await waitFor(() => expect(put).toHaveBeenCalledTimes(1))
    expect(await screen.findByText('Horarios guardados.')).toBeInTheDocument()
  })

  it('PUT con la lista completa, en orden, horas HH:mm, categoría del catálogo y sin clubId en CLUB', async () => {
    const usuario = userEvent.setup()
    const respuesta = [
      horario('h1'),
      horario('h2', { categoria: { id: 'cat-vet', nombre: 'Veteranos' }, diaSemana: 7, horaInicio: '08:00:00', horaFin: '09:00:00' }),
    ]
    put.mockResolvedValue(respuesta)
    montar()
    await esperarEditable()

    await agregarFila(usuario, 2, { Categoría: 'cat-vet', Día: '7', Inicio: '08:00', Fin: '09:00' })
    expect(screen.getByText('Hay cambios sin guardar.')).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Guardar horarios' }))

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith(RUTA_PUT, {
        temporadaId: 't-2026',
        horarios: [
          { categoriaId: 'cat-libre', diaSemana: 6, horaInicio: '10:00', horaFin: '12:00' },
          { categoriaId: 'cat-vet', diaSemana: 7, horaInicio: '08:00', horaFin: '09:00' },
        ],
      }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent('Horarios guardados.')
    expect(screen.queryByText('Hay cambios sin guardar.')).not.toBeInTheDocument()
    expect(campo('Categoría', 2)).toHaveValue('cat-vet')
    expect(campo('Inicio', 2)).toHaveValue('08:00')
    expect(screen.getAllByText('Halcones')).toHaveLength(2)
    expect(screen.queryByText('Comodato')).not.toBeInTheDocument()
  })

  it('DEC-33: en LIGA cada fila exige club; con club el cuerpo lleva clubId y se ve «Comodato»', async () => {
    const usuario = userEvent.setup()
    responder({ 't-2026': [] })
    put.mockResolvedValue([horario('h9', { club: { id: 'club-2', nombre: 'Toros' }, esComodato: true })])
    montar(CANCHA_LIGA)
    await screen.findByText('Sin horarios en esta temporada')

    await agregarFila(usuario, 1, {})
    await esperarEditable()
    for (const [nombre, valor] of Object.entries({ Categoría: 'cat-libre', Día: '1', Inicio: '18:00', Fin: '20:00' })) {
      fireEvent.change(campo(nombre, 1), { target: { value: valor } })
    }
    expect(screen.getByText('Comodato')).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Guardar horarios' }))

    expect(screen.getByText('Elige el club que usa la cancha.')).toBeInTheDocument()
    expect(campo('Club', 1)).toHaveAttribute('aria-invalid', 'true')
    expect(put).not.toHaveBeenCalled()

    await waitFor(() => expect(screen.getByRole('option', { name: 'Toros' })).toBeInTheDocument())
    await usuario.selectOptions(campo('Club', 1), 'club-2')
    await usuario.click(screen.getByRole('button', { name: 'Guardar horarios' }))

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith(RUTA_PUT, {
        temporadaId: 't-2026',
        horarios: [{ categoriaId: 'cat-libre', diaSemana: 1, horaInicio: '18:00', horaFin: '20:00', clubId: 'club-2' }],
      }),
    )
    expect(await screen.findByText('Horarios guardados.')).toBeInTheDocument()
    expect(screen.getByText('Comodato')).toBeInTheDocument()
  })

  it('DEC-33: en LIGA el selector de club ofrece los clubes activos de todas las páginas', async () => {
    responder({ 't-2026': [horario('h1', { esComodato: true })] })
    const responderBase = get.getMockImplementation()
    const otro: Club = { id: 'club-101', nombre: 'Club de la segunda página', estado: 'ACTIVO', creadoEn: '2026-01-01T10:00:00-06:00' }
    get.mockImplementation(async (ruta: string) => {
      if (ruta === 'clubes?estado=ACTIVO&pagina=0&tamano=100') {
        return { contenido: CLUBES, pagina: 0, tamano: 100, totalElementos: 101, totalPaginas: 2 }
      }
      if (ruta === 'clubes?estado=ACTIVO&pagina=1&tamano=100') {
        return { contenido: [otro], pagina: 1, tamano: 100, totalElementos: 101, totalPaginas: 2 }
      }
      return responderBase?.(ruta)
    })
    montar(CANCHA_LIGA)

    expect(await screen.findByRole('option', { name: 'Club de la segunda página' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Toros' })).toBeInTheDocument()
    const deClubes = get.mock.calls.map(([ruta]) => String(ruta)).filter((ruta) => ruta.startsWith('clubes?'))
    expect(deClubes).toEqual(['clubes?estado=ACTIVO&pagina=0&tamano=100', 'clubes?estado=ACTIVO&pagina=1&tamano=100'])
  })

  it('una temporada CERRADA es de solo lectura', async () => {
    const usuario = userEvent.setup()
    responder({ 't-2026': [], 't-2025': [horario('h1', { temporadaId: 't-2025' })] })
    montar()
    await screen.findByText('Sin horarios en esta temporada')

    await usuario.selectOptions(screen.getByRole('combobox', { name: 'Temporada' }), 't-2025')

    expect(await screen.findByText('Temporada cerrada: sus horarios son de solo lectura.')).toBeInTheDocument()
    expect(await screen.findByText('Libre')).toBeInTheDocument()
    expect(screen.getByText('Sábado')).toBeInTheDocument()
    expect(screen.queryByLabelText('Categoría del horario 1')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Guardar horarios' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Agregar horario' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Quitar horario 1' })).not.toBeInTheDocument()
  })

  it('sin canchas.cancha:EDITAR la tabla es de solo lectura y no pide categorías', async () => {
    otorgar(CONSULTAR_CANCHA, CONSULTAR_TEMPORADA)
    montar()

    expect(await screen.findByText('Libre')).toBeInTheDocument()
    expect(screen.getByText('10:00')).toBeInTheDocument()
    expect(screen.queryByLabelText('Categoría del horario 1')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Guardar horarios' })).not.toBeInTheDocument()
    expect(get).not.toHaveBeenCalledWith('temporadas/t-2026/categorias')
  })

  it('un 400 con horarios[1].horaFin marca el fin de la fila 2', async () => {
    const usuario = userEvent.setup()
    put.mockRejectedValue(
      error(400, 'VALIDACION', 'La solicitud tiene datos inválidos.', {
        errores: [{ campo: 'horarios[1].horaFin', mensaje: 'La hora de fin no es válida.' }],
      }),
    )
    montar()
    await esperarEditable()

    await agregarFila(usuario, 2, { Categoría: 'cat-vet', Día: '7', Inicio: '08:00', Fin: '09:00' })
    await usuario.click(screen.getByRole('button', { name: 'Guardar horarios' }))

    await waitFor(() => expect(campo('Fin', 2)).toHaveAttribute('aria-invalid', 'true'))
    expect(campo('Fin', 2)).toHaveAccessibleDescription('La hora de fin no es válida.')
    expect(campo('Fin', 1)).not.toHaveAttribute('aria-invalid')
  })

  it('un 422 RF-119 muestra la alerta con el detalle y sin el código', async () => {
    const usuario = userEvent.setup()
    const detalle = 'El sábado de 10:00 a 12:30 se traslapa con otro horario de la cancha.'
    put.mockRejectedValue(error(422, 'RF-119', detalle))
    montar()
    await esperarEditable()

    fireEvent.change(campo('Fin', 1), { target: { value: '12:30' } })
    await usuario.click(screen.getByRole('button', { name: 'Guardar horarios' }))

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('No se pudieron guardar los horarios')
    expect(alerta).toHaveTextContent(detalle)
    expect(alerta).not.toHaveTextContent('RF-119')
  })

  it('un 409 RF-003 explica que la temporada se cerró, sin código, y la deja en solo lectura', async () => {
    const usuario = userEvent.setup()
    const responderBase = get.getMockImplementation()
    let cerrada = false
    // Tras el rechazo, el GET de temporadas ya trae la ACTIVA (la de por omisión) como CERRADA.
    get.mockImplementation(async (ruta: string) =>
      cerrada && ruta.startsWith('temporadas?')
        ? pagina(TEMPORADAS.map((actual) => (actual.id === 't-2026' ? { ...actual, estado: 'CERRADA' as const } : actual)))
        : responderBase?.(ruta),
    )
    put.mockImplementation(async () => {
      cerrada = true
      throw error(409, 'RF-003', 'La temporada «Temporada 2026» está cerrada.')
    })
    montar()
    await esperarEditable()

    fireEvent.change(campo('Fin', 1), { target: { value: '12:30' } })
    await usuario.click(screen.getByRole('button', { name: 'Guardar horarios' }))

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('La temporada se cerró: sus horarios ya no se pueden modificar.')
    expect(alerta).not.toHaveTextContent('RF-003')

    expect(await screen.findByText('Temporada cerrada: sus horarios son de solo lectura.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Temporada' })).toHaveValue('t-2026')
    expect(screen.getByText('La temporada se cerró: sus horarios ya no se pueden modificar.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Guardar horarios' })).not.toBeInTheDocument()
  })

  it('con cambios sin guardar avisa, bloquea la temporada y «Descartar cambios» restaura', async () => {
    const usuario = userEvent.setup()
    montar()
    await esperarEditable()

    fireEvent.change(campo('Fin', 1), { target: { value: '13:00' } })

    expect(screen.getByText('Hay cambios sin guardar.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Temporada' })).toBeDisabled()
    expect(screen.getByText('Guarda o descarta los cambios para cambiar de temporada.')).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Descartar cambios' }))

    expect(campo('Fin', 1)).toHaveValue('12:00')
    expect(screen.queryByText('Hay cambios sin guardar.')).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Temporada' })).toBeEnabled()
  })

  it('«Quitar» saca la fila y guardar sin filas manda la lista vacía', async () => {
    const usuario = userEvent.setup()
    put.mockResolvedValue([])
    montar()
    await esperarEditable()

    await usuario.click(screen.getByRole('button', { name: 'Quitar horario 1' }))
    expect(screen.getByText('Quitaste todos los horarios')).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Guardar horarios' }))

    await waitFor(() => expect(put).toHaveBeenCalledWith(RUTA_PUT, { temporadaId: 't-2026', horarios: [] }))
  })
})

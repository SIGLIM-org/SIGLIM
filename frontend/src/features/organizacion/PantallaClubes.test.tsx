import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaClubes } from '@/features/organizacion/PantallaClubes'
import type { Club, Equipo, TemporadaEquipo } from '@/features/organizacion/tipos'
import type { Ambito, Permiso } from '@/features/seguridad/tipos'
import type { CategoriaTemporada, Temporada } from '@/features/temporadas/tipos'
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

const RUTA = '/app/organizacion/clubes'
const CONSULTAR_CLUB = 'organizacion.club:CONSULTAR'
const EDITAR_CLUB = 'organizacion.club:EDITAR'
const CONSULTAR_EQUIPO = 'organizacion.equipo:CONSULTAR'
const EDITAR_EQUIPO = 'organizacion.equipo:EDITAR'
const CONSULTAR_TEMPORADA = 'temporadas.temporada:CONSULTAR'
const MOTIVO = 'El club retiró al equipo del torneo'

const CLUB: Club = { id: 'c1', nombre: 'Club Halcones', estado: 'ACTIVO', creadoEn: '2026-11-03T10:00:00-06:00' }
const CLUB_CERRADO: Club = {
  ...CLUB,
  estado: 'CERRADO',
  cerradoEn: '2026-11-04',
  motivoCierre: 'Cambio de nombre aprobado por la asamblea',
}

const T_ACTIVA: TemporadaEquipo = { id: 't1', nombre: 'Temporada 2026', estado: 'ACTIVA' }
const T_CONFIG: TemporadaEquipo = { id: 't2', nombre: 'Temporada 2027', estado: 'CONFIGURACION' }
const T_CERRADA: TemporadaEquipo = { id: 't0', nombre: 'Temporada 2025', estado: 'CERRADA' }

function equipo(id: string, nombre: string, categoria: string, grupo: string, extra: Partial<Equipo> = {}): Equipo {
  return {
    id,
    nombre,
    estado: 'ACTIVO',
    club: { id: 'c1', nombre: 'Club Halcones' },
    grupo: { id: `g-${grupo}`, nombre: grupo },
    categoria: { id: `cat-${categoria}`, nombre: categoria },
    temporada: T_ACTIVA,
    creadoEn: '2026-11-03T10:00:00-06:00',
    ...extra,
  }
}

const TEMPORADAS: Temporada[] = [
  { id: 't1', nombre: 'Temporada 2026', fechaInicio: '2026-01-01', estado: 'ACTIVA', creadoEn: '', categoriasAsignadas: 2, gruposCreados: 2 },
  { id: 't2', nombre: 'Temporada 2027', fechaInicio: '2027-01-01', estado: 'CONFIGURACION', creadoEn: '', categoriasAsignadas: 1, gruposCreados: 1 },
  { id: 't0', nombre: 'Temporada 2025', fechaInicio: '2025-01-01', estado: 'CERRADA', creadoEn: '', categoriasAsignadas: 1, gruposCreados: 1 },
]

const CATEGORIAS: CategoriaTemporada[] = [
  {
    id: 'ct-libre',
    categoria: { id: 'cat-libre', nombre: 'Libre', tipo: 'LIBRE', orden: 1 },
    grupos: [
      { id: 'g-a', nombre: 'A', equipos: 3 },
      { id: 'g-b', nombre: 'B', equipos: 2 },
    ],
  },
  {
    id: 'ct-infantil',
    categoria: { id: 'cat-infantil', nombre: 'Infantil', tipo: 'INFANTIL', orden: 2 },
    grupos: [{ id: 'g-i', nombre: 'Único', equipos: 0 }],
  },
]

function otorgar(ambito: Ambito, ...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: codigo.startsWith('temporadas') ? 'M-02' : 'M-03',
    ambito,
    clubId: ambito === 'CLUB' ? 'c1' : null,
  }))
}

const SECRETARIO = [CONSULTAR_CLUB, EDITAR_CLUB, CONSULTAR_EQUIPO, EDITAR_EQUIPO, CONSULTAR_TEMPORADA]
const DIRECTIVA = [CONSULTAR_CLUB, CONSULTAR_EQUIPO, EDITAR_EQUIPO]

function pagina<T>(contenido: T[], totalElementos = contenido.length) {
  return { contenido, pagina: 0, tamano: 20, totalElementos, totalPaginas: contenido.length ? 1 : 0 }
}

function error(status: number, codigo: string, detail = 'Detalle del servidor.') {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

type Respuestas = { clubes?: unknown; club?: unknown; equipos?: unknown; directiva?: unknown }

function responder({
  clubes = pagina([CLUB]),
  club = CLUB,
  equipos = pagina<Equipo>([]),
  directiva = [],
}: Respuestas = {}) {
  get.mockImplementation(async (ruta: string) => {
    const respuesta = ruta.startsWith('clubes?')
      ? clubes
      : ruta === 'clubes/c1/directiva'
        ? directiva
        : ruta.startsWith('clubes/')
          ? club
          : ruta.startsWith('equipos?')
          ? equipos
          : ruta.startsWith('temporadas?')
            ? pagina(TEMPORADAS)
            : ruta.endsWith('/categorias')
              ? CATEGORIAS
              : undefined

    if (respuesta instanceof Error) {
      throw respuesta
    }

    return respuesta
  })
}

function montar(ruta: string = RUTA) {
  const enrutador = createMemoryRouter([{ path: `${RUTA}/:clubId?`, element: <PantallaClubes /> }], {
    initialEntries: [ruta],
  })

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return enrutador
}

const tabla = () => screen.findByRole('table', { name: 'Equipos del club' })

describe('PantallaClubes (F-15, H-06.2)', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    otorgar('LIGA', ...SECRETARIO)
    responder()
  })

  it('sin organizacion.club:CONSULTAR muestra la página 403 y no pide datos', () => {
    otorgar('LIGA', CONSULTAR_EQUIPO)
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('RF-008: lista clubes activos por omisión y busca por nombre con espera', async () => {
    montar()

    const fila = await screen.findByRole('link', { name: /Club Halcones/ })
    expect(within(fila).getByText('Activo')).toBeInTheDocument()
    expect(screen.getByText('1 club')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('clubes?estado=ACTIVO&pagina=0&tamano=20')

    responder({ clubes: pagina([]) })
    await userEvent.type(screen.getByLabelText('Buscar por nombre'), 'Toros')

    expect(await screen.findByText('Ningún club coincide con la búsqueda')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('clubes?q=Toros&estado=ACTIVO&pagina=0&tamano=20')
  })

  it('sin clubes muestra el estado vacío con «Registrar club»', async () => {
    responder({ clubes: pagina([]) })
    montar()

    const vacio = await screen.findByText('Aún no hay clubes registrados')
    expect(within(vacio.parentElement as HTMLElement).getByRole('button', { name: 'Registrar club' })).toBeInTheDocument()
  })

  it('«Registrar club» solo aparece con organizacion.club:EDITAR', async () => {
    otorgar('LIGA', CONSULTAR_CLUB, CONSULTAR_EQUIPO)
    montar()

    await screen.findByRole('link', { name: /Club Halcones/ })
    expect(screen.queryByRole('button', { name: 'Registrar club' })).not.toBeInTheDocument()
  })

  it('criterio 1: registrar un club cierra el modal y navega a su detalle', async () => {
    post.mockResolvedValue({ ...CLUB, id: 'c9', nombre: 'Club Toros' })
    const enrutador = montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Registrar club' }))
    const modal = screen.getByRole('dialog', { name: 'Registrar club' })
    await userEvent.type(within(modal).getByLabelText(/Nombre/), '  Club Toros  ')
    await userEvent.click(within(modal).getByRole('button', { name: 'Registrar' }))

    expect(post).toHaveBeenCalledWith('clubes', { nombre: 'Club Toros' })
    await waitFor(() => expect(enrutador.state.location.pathname).toBe(`${RUTA}/c9`))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('409 DUPLICADO al registrar un club se muestra en el campo Nombre', async () => {
    post.mockRejectedValue(error(409, 'DUPLICADO', 'Ya existe un club llamado «Club Halcones».'))
    montar()

    await userEvent.click(await screen.findByRole('button', { name: 'Registrar club' }))
    const modal = screen.getByRole('dialog', { name: 'Registrar club' })
    const campo = within(modal).getByLabelText(/Nombre/)
    await userEvent.type(campo, 'Club Halcones')
    await userEvent.click(within(modal).getByRole('button', { name: 'Registrar' }))

    expect(await within(modal).findByText('Ya existe un club llamado «Club Halcones».')).toBeInTheDocument()
    expect(campo).toHaveAttribute('aria-invalid', 'true')
  })

  it('criterio 1: detalle con pestaña Equipos, temporada activa por omisión y orden del servidor (DEC-16)', async () => {
    // El backend ordena por el orden configurado de la categoría (Libre = 1, Infantil = 2).
    responder({
      equipos: pagina([
        equipo('e2', 'Águilas', 'Libre', 'A'),
        equipo('e4', 'Futuro', 'Libre', 'A', { temporada: T_CONFIG }),
        equipo('e1', 'Toros', 'Libre', 'B'),
        equipo('e3', 'Zorros', 'Infantil', 'A'),
      ]),
    })
    montar(`${RUTA}/c1`)

    expect(await screen.findByRole('heading', { name: 'Club Halcones' })).toBeInTheDocument()
    expect(screen.getByText('Registrado el 3 nov 2026')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Equipos' })).toHaveAttribute('aria-selected', 'true')
    expect(get).toHaveBeenCalledWith('equipos?clubId=c1&estado=ACTIVO&pagina=0&tamano=100')

    const temporada = screen.getByLabelText('Temporada')
    await waitFor(() => expect(temporada).toHaveValue('t1'))
    expect(within(temporada).getAllByRole('option').map(({ textContent }) => textContent)).toEqual([
      'Todas',
      'Temporada 2026 · Activa',
      'Temporada 2027 · Configuración',
    ])

    const filas = within(await tabla()).getAllByRole('row').slice(1)
    expect(filas.map((fila) => within(fila).getAllByRole('cell')[0].textContent)).toEqual([
      expect.stringContaining('Águilas'),
      expect.stringContaining('Toros'),
      expect.stringContaining('Zorros'),
    ])

    await userEvent.selectOptions(temporada, '')
    expect(within(await tabla()).getAllByRole('row')).toHaveLength(5)
  })

  it('H-22.2: la pestaña Directiva se abre por URL y consulta su endpoint', async () => {
    const enrutador = montar(`${RUTA}/c1`)

    await screen.findByRole('heading', { name: 'Club Halcones' })
    await userEvent.click(screen.getByRole('tab', { name: 'Directiva' }))

    await waitFor(() => expect(enrutador.state.location.search).toBe('?pestana=directiva'))
    expect(screen.getByRole('tab', { name: 'Directiva' })).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('Sin directiva registrada')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('clubes/c1/directiva')

    await userEvent.click(screen.getByRole('tab', { name: 'Equipos' }))
    await waitFor(() => expect(enrutador.state.location.search).toBe(''))
    expect(screen.getByRole('tab', { name: 'Equipos' })).toHaveAttribute('aria-selected', 'true')
  })

  it('criterio 3: un equipo en BAJA muestra su fecha y motivo y no tiene acciones', async () => {
    responder({
      equipos: pagina([
        equipo('e1', 'Halcones', 'Libre', 'A', { estado: 'BAJA', bajaEn: '2026-11-04', motivoBaja: MOTIVO }),
      ]),
    })
    montar(`${RUTA}/c1`)

    const fila = within(await tabla()).getAllByRole('row')[1]
    expect(within(fila).getByText('Baja')).toBeInTheDocument()
    expect(within(fila).getByText(/Baja el 4 nov 2026/)).toBeInTheDocument()
    expect(within(fila).getByText(MOTIVO)).toBeInTheDocument()
    expect(within(fila).queryByRole('button')).not.toBeInTheDocument()
  })

  it('criterio 1: el Secretario registra un equipo eligiendo temporada → categoría → grupo', async () => {
    post.mockResolvedValue(equipo('e9', 'Halcones B', 'Libre', 'B'))
    montar(`${RUTA}/c1`)

    await userEvent.click(await screen.findByRole('button', { name: 'Registrar equipo' }))
    const modal = screen.getByRole('dialog', { name: 'Registrar equipo' })
    const temporada = within(modal).getByLabelText(/Temporada/)

    await waitFor(() => expect(temporada).toHaveValue('t1'))
    expect(within(temporada).queryByRole('option', { name: /2025/ })).not.toBeInTheDocument()

    const pedidasCategorias = () => get.mock.calls.filter(([ruta]) => String(ruta).endsWith('/categorias'))

    await userEvent.selectOptions(temporada, '')
    const antes = pedidasCategorias().length
    expect(within(modal).queryByLabelText(/Categoría/)).not.toBeInTheDocument()

    await userEvent.selectOptions(temporada, 't2')
    await waitFor(() => expect(get).toHaveBeenCalledWith('temporadas/t2/categorias'))
    expect(pedidasCategorias()).toHaveLength(antes + 1)
    expect(pedidasCategorias().some(([ruta]) => ruta === 'temporadas//categorias')).toBe(false)

    await userEvent.selectOptions(temporada, 't1')
    const categoria = within(modal).getByLabelText(/Categoría/)
    await waitFor(() => expect(categoria).toBeEnabled())

    await userEvent.selectOptions(categoria, 'ct-libre')
    await userEvent.selectOptions(within(modal).getByLabelText(/Grupo/), 'g-b')
    await userEvent.selectOptions(categoria, 'ct-infantil')
    expect(within(modal).getByLabelText(/Grupo/)).toHaveValue('')

    await userEvent.selectOptions(categoria, 'ct-libre')
    await userEvent.selectOptions(within(modal).getByLabelText(/Grupo/), 'g-b')
    await userEvent.type(within(modal).getByLabelText(/Nombre del equipo/), 'Halcones B')
    await userEvent.click(within(modal).getByRole('button', { name: 'Registrar' }))

    expect(post).toHaveBeenCalledWith('equipos', { clubId: 'c1', grupoId: 'g-b', nombre: 'Halcones B' })
    expect(await screen.findByText('Equipo Halcones B registrado.')).toBeInTheDocument()
  })

  it('criterio 2: la Directiva va directo a su club, sin lista, «Volver» ni «Registrar equipo»', async () => {
    otorgar('CLUB', ...DIRECTIVA)
    responder({ equipos: pagina([equipo('e1', 'Halcones', 'Libre', 'A', { temporada: T_CONFIG })]) })
    const enrutador = montar()

    await waitFor(() => expect(enrutador.state.location.pathname).toBe(`${RUTA}/c1`))
    expect(await screen.findByRole('heading', { name: 'Club Halcones' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Lista de clubes' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Volver a clubes' })).not.toBeInTheDocument()

    const fila = within(await tabla()).getAllByRole('row')[1]
    expect(screen.queryByRole('button', { name: 'Registrar equipo' })).not.toBeInTheDocument()
    expect(get.mock.calls.some(([ruta]) => String(ruta).startsWith('temporadas'))).toBe(false)

    patch.mockResolvedValue(equipo('e1', 'Halcones Morelia', 'Libre', 'A', { temporada: T_CONFIG }))
    await userEvent.click(within(fila).getByRole('button', { name: 'Editar Halcones' }))
    const modal = screen.getByRole('dialog', { name: /Editar/ })
    expect(within(modal).queryByLabelText('Grupo')).not.toBeInTheDocument()

    const nombre = within(modal).getByLabelText(/Nombre del equipo/)
    await userEvent.clear(nombre)
    await userEvent.type(nombre, 'Halcones Morelia')
    await userEvent.click(within(modal).getByRole('button', { name: 'Guardar' }))

    expect(patch).toHaveBeenCalledWith('equipos/e1', { nombre: 'Halcones Morelia' })
    expect(within(fila).getByRole('button', { name: 'Dar de baja Halcones' })).toBeInTheDocument()
  })

  it('el Secretario puede cambiar el grupo en configuración; sin cambios no llama al servidor', async () => {
    responder({ equipos: pagina([equipo('e1', 'Halcones', 'Libre', 'A', { temporada: T_CONFIG, grupo: { id: 'g-a', nombre: 'A' } })]) })
    montar(`${RUTA}/c1`)

    const fila = within(await tabla()).getAllByRole('row')[1]
    await userEvent.click(within(fila).getByRole('button', { name: 'Editar Halcones' }))
    let modal = screen.getByRole('dialog', { name: /Editar/ })
    await userEvent.click(within(modal).getByRole('button', { name: 'Guardar' }))
    expect(patch).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    patch.mockResolvedValue(equipo('e1', 'Halcones', 'Libre', 'B', { temporada: T_CONFIG }))
    await userEvent.click(within(fila).getByRole('button', { name: 'Editar Halcones' }))
    modal = screen.getByRole('dialog', { name: /Editar/ })
    const grupo = within(modal).getByLabelText('Grupo')
    await waitFor(() => expect(grupo).toBeEnabled())
    await userEvent.selectOptions(grupo, 'g-b')
    await userEvent.click(within(modal).getByRole('button', { name: 'Guardar' }))

    expect(patch).toHaveBeenCalledWith('equipos/e1', { grupoId: 'g-b' })
  })

  it('criterio 3: la baja exige motivo de 10 caracteres y llama POST equipos/{id}/baja', async () => {
    responder({ equipos: pagina([equipo('e1', 'Halcones', 'Libre', 'A')]) })
    post.mockResolvedValue(equipo('e1', 'Halcones', 'Libre', 'A', { estado: 'BAJA', bajaEn: '2026-11-05', motivoBaja: MOTIVO }))
    montar(`${RUTA}/c1`)

    const fila = within(await tabla()).getAllByRole('row')[1]
    await userEvent.click(within(fila).getByRole('button', { name: 'Dar de baja Halcones' }))
    const modal = screen.getByRole('dialog', { name: /Dar de baja/ })
    expect(within(modal).getByText(/No se borra: conserva su historial/)).toBeInTheDocument()

    const motivo = within(modal).getByLabelText(/Motivo/)
    await userEvent.type(motivo, 'Corto')
    await userEvent.click(within(modal).getByRole('button', { name: 'Dar de baja' }))
    expect(post).not.toHaveBeenCalled()
    expect(within(modal).getByRole('alert')).toHaveTextContent('al menos 10 caracteres')

    await userEvent.clear(motivo)
    await userEvent.type(motivo, MOTIVO)
    await userEvent.click(within(modal).getByRole('button', { name: 'Dar de baja' }))

    expect(post).toHaveBeenCalledWith('equipos/e1/baja', { motivo: MOTIVO })
    expect(await screen.findByText(/dado de baja\. Su historial se conserva/)).toBeInTheDocument()
  })

  it('RF-003: con la temporada CERRADA las acciones se deshabilitan y explican la causa', async () => {
    responder({ equipos: pagina([equipo('e1', 'Halcones', 'Libre', 'A', { temporada: T_CERRADA })]) })
    montar(`${RUTA}/c1`)

    const fila = within(await tabla()).getAllByRole('row')[1]
    expect(within(fila).getByRole('button', { name: 'Editar Halcones' })).toBeDisabled()
    expect(within(fila).getByRole('button', { name: 'Dar de baja Halcones' })).toBeDisabled()
    expect(within(fila).getByText('Temporada cerrada: no admite cambios.')).toBeInTheDocument()
  })

  it('RN-041: un club CERRADO muestra fecha y motivo del cierre y no ofrece «Registrar equipo»', async () => {
    responder({ club: CLUB_CERRADO })
    montar(`${RUTA}/c1`)

    expect(await screen.findByText('Cambio de nombre aprobado por la asamblea')).toBeInTheDocument()
    expect(screen.getByText('4 nov 2026')).toBeInTheDocument()
    expect(screen.getByText('Un club cerrado no recibe equipos nuevos.')).toBeInTheDocument()
    expect(await screen.findByText('Sin equipos activos')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Registrar equipo' })).not.toBeInTheDocument()

    const seccion = screen.getByRole('region', { name: 'Equipos del club' })
    await userEvent.selectOptions(within(seccion).getByLabelText('Estado'), '')
    expect(await screen.findByText('Este club aún no tiene equipos')).toBeInTheDocument()
  })

  it('RN-005: la Directiva con su único club CERRADO llega directo a su detalle', async () => {
    otorgar('CLUB', ...DIRECTIVA)
    responder({ clubes: pagina([CLUB_CERRADO]), club: CLUB_CERRADO })
    const enrutador = montar()

    await waitFor(() => expect(enrutador.state.location.pathname).toBe(`${RUTA}/c1`))
    expect(get).toHaveBeenCalledWith('clubes?pagina=0&tamano=20')
    expect(await screen.findByRole('heading', { name: 'Club Halcones' })).toBeInTheDocument()
    expect(screen.getByText('Cerrado')).toBeInTheDocument()
    expect(screen.getByText('Un club cerrado no recibe equipos nuevos.')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Lista de clubes' })).not.toBeInTheDocument()
  })

  it('422 REFERENCIA_INVALIDA al registrar un equipo muestra el detail en Grupo y recarga las categorías', async () => {
    post.mockRejectedValue(error(422, 'REFERENCIA_INVALIDA', 'El grupo indicado no existe.'))
    montar(`${RUTA}/c1`)

    await userEvent.click(await screen.findByRole('button', { name: 'Registrar equipo' }))
    const modal = screen.getByRole('dialog', { name: 'Registrar equipo' })
    const categoria = await within(modal).findByLabelText(/Categoría/)
    await waitFor(() => expect(categoria).toBeEnabled())
    await userEvent.selectOptions(categoria, 'ct-libre')
    await userEvent.selectOptions(within(modal).getByLabelText(/Grupo/), 'g-b')
    await userEvent.type(within(modal).getByLabelText(/Nombre del equipo/), 'Halcones B')
    const pedidas = get.mock.calls.filter(([ruta]) => ruta === 'temporadas/t1/categorias').length
    await userEvent.click(within(modal).getByRole('button', { name: 'Registrar' }))

    expect(await within(modal).findByText('El grupo indicado no existe.')).toBeInTheDocument()
    await waitFor(() =>
      expect(get.mock.calls.filter(([ruta]) => ruta === 'temporadas/t1/categorias').length).toBeGreaterThan(pedidas),
    )
  })

  it('el error de una mutación (409 RF-003) muestra el detail con role="alert"', async () => {
    responder({ equipos: pagina([equipo('e1', 'Halcones', 'Libre', 'A')]) })
    post.mockRejectedValue(error(409, 'RF-003', 'La temporada «Temporada 2026» está cerrada y no admite cambios.'))
    montar(`${RUTA}/c1`)

    const fila = within(await tabla()).getAllByRole('row')[1]
    await userEvent.click(within(fila).getByRole('button', { name: 'Dar de baja Halcones' }))
    const modal = screen.getByRole('dialog', { name: /Dar de baja/ })
    await userEvent.type(within(modal).getByLabelText(/Motivo/), MOTIVO)
    await userEvent.click(within(modal).getByRole('button', { name: 'Dar de baja' }))

    expect(await within(modal).findByRole('alert')).toHaveTextContent(
      'La temporada «Temporada 2026» está cerrada y no admite cambios.',
    )
  })

  it('avisa cuando el club tiene más de 100 equipos', async () => {
    responder({ equipos: pagina([equipo('e1', 'Halcones', 'Libre', 'A')], 130) })
    montar(`${RUTA}/c1`)

    expect(await screen.findByText('Se muestran los primeros 100 de 130 equipos.')).toBeInTheDocument()
  })

  describe('cambio de nombre (B-04.2, RF-105, RN-041, DEC-15)', () => {
    const MOTIVO_CAMBIO = 'Acuerdo de asamblea del club'
    const CLUB_NUEVO: Club = {
      ...CLUB,
      id: 'c9',
      nombre: 'Club Águilas',
      clubAnterior: { id: 'c1', nombre: 'Club Halcones' },
    }

    async function abrirCambioNombre() {
      await userEvent.click(await screen.findByRole('button', { name: 'Cambiar nombre' }))
      const modal = screen.getByRole('dialog', { name: 'Cambiar nombre — Club Halcones' })

      return {
        modal,
        nombre: within(modal).getByLabelText(/Nombre/),
        motivo: within(modal).getByLabelText(/Motivo/),
        enviar: () => userEvent.click(within(modal).getByRole('button', { name: 'Cambiar nombre' })),
      }
    }

    it('«Cambiar nombre» aparece con organizacion.club:EDITAR de ámbito LIGA y el club ACTIVO', async () => {
      montar(`${RUTA}/c1`)

      expect(await screen.findByRole('button', { name: 'Cambiar nombre' })).toBeInTheDocument()
    })

    it.each([
      ['con EDITAR_CLUB de ámbito CLUB', () => otorgar('CLUB', ...SECRETARIO), CLUB],
      ['sin organizacion.club:EDITAR', () => otorgar('LIGA', CONSULTAR_CLUB, CONSULTAR_EQUIPO), CLUB],
      ['con el club CERRADO', () => otorgar('LIGA', ...SECRETARIO), CLUB_CERRADO],
    ])('«Cambiar nombre» no aparece %s', async (_caso, preparar, club) => {
      preparar()
      responder({ club })
      montar(`${RUTA}/c1`)

      expect(await screen.findByRole('heading', { name: 'Club Halcones' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Cambiar nombre' })).not.toBeInTheDocument()
    })

    it('valida en el cliente nombre vacío, nombre igual al actual y motivo corto sin llamar al servidor', async () => {
      montar(`${RUTA}/c1`)
      const { modal, nombre, motivo, enviar } = await abrirCambioNombre()
      expect(within(modal).getByText(/no se puede deshacer/)).toBeInTheDocument()

      await userEvent.type(motivo, MOTIVO_CAMBIO)
      await enviar()
      expect(within(modal).getByText('Escribe el nuevo nombre del club.')).toBeInTheDocument()
      expect(nombre).toHaveAttribute('aria-invalid', 'true')

      await userEvent.type(nombre, '  Club Halcones  ')
      await enviar()
      expect(within(modal).getByText('El nombre nuevo debe ser distinto del actual.')).toBeInTheDocument()

      await userEvent.clear(nombre)
      await userEvent.type(nombre, 'Club Águilas')
      await userEvent.clear(motivo)
      await userEvent.type(motivo, '   Muy corto   ')
      await enviar()
      expect(within(modal).getByText('Escribe un motivo de al menos 10 caracteres.')).toBeInTheDocument()
      expect(motivo).toHaveAttribute('aria-invalid', 'true')

      expect(post).not.toHaveBeenCalled()
    })

    it('envía valores recortados, cierra el modal y navega al club nuevo, que enlaza al anterior', async () => {
      post.mockResolvedValue(CLUB_NUEVO)
      const enrutador = montar(`${RUTA}/c1`)
      const { nombre, motivo, enviar } = await abrirCambioNombre()

      await userEvent.type(nombre, '  Club Águilas  ')
      await userEvent.type(motivo, `  ${MOTIVO_CAMBIO}  `)
      // Solo el club nuevo responde con su nombre; el anterior sigue siendo «Club Halcones».
      const responderBase = get.getMockImplementation()
      get.mockImplementation(async (ruta: string) => (ruta === 'clubes/c9' ? CLUB_NUEVO : responderBase?.(ruta)))
      await enviar()

      expect(post).toHaveBeenCalledWith('clubes/c1/cambio-nombre', { nombre: 'Club Águilas', motivo: MOTIVO_CAMBIO })
      await waitFor(() => expect(enrutador.state.location.pathname).toBe(`${RUTA}/c9`))
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(await screen.findByRole('heading', { name: 'Club Águilas' })).toBeInTheDocument()
      expect(screen.getByText(/Proviene de/)).toHaveTextContent('Proviene de «Club Halcones»')
      expect(screen.getByRole('link', { name: '«Club Halcones»' })).toHaveAttribute('href', `${RUTA}/c1`)
    })

    it('409 DUPLICADO se muestra en el campo Nombre', async () => {
      post.mockRejectedValue(error(409, 'DUPLICADO', 'Ya existe un club llamado «Club Águilas».'))
      montar(`${RUTA}/c1`)
      const { modal, nombre, motivo, enviar } = await abrirCambioNombre()

      await userEvent.type(nombre, 'Club Águilas')
      await userEvent.type(motivo, MOTIVO_CAMBIO)
      await enviar()

      expect(await within(modal).findByText('Ya existe un club llamado «Club Águilas».')).toBeInTheDocument()
      expect(nombre).toHaveAttribute('aria-invalid', 'true')
    })

    it('400 RN-021 se muestra en el campo Motivo', async () => {
      post.mockRejectedValue(error(400, 'RN-021', 'El motivo debe tener al menos 10 caracteres.'))
      montar(`${RUTA}/c1`)
      const { modal, nombre, motivo, enviar } = await abrirCambioNombre()

      await userEvent.type(nombre, 'Club Águilas')
      await userEvent.type(motivo, MOTIVO_CAMBIO)
      await enviar()

      expect(await within(modal).findByText('El motivo debe tener al menos 10 caracteres.')).toBeInTheDocument()
      expect(motivo).toHaveAttribute('aria-invalid', 'true')
    })

    it('409 RN-041 muestra la alerta de bloqueo con el detail y el modal sigue abierto', async () => {
      const detalle = 'El club tiene equipos activos en la temporada «Temporada 2026».'
      post.mockRejectedValue(error(409, 'RN-041', detalle))
      montar(`${RUTA}/c1`)
      const { modal, nombre, motivo, enviar } = await abrirCambioNombre()

      await userEvent.type(nombre, 'Club Águilas')
      await userEvent.type(motivo, MOTIVO_CAMBIO)
      await enviar()

      const alerta = await within(modal).findByRole('alert')
      expect(alerta).toHaveTextContent('No se puede cambiar el nombre del club')
      expect(alerta).toHaveTextContent('(RN-041)')
      expect(alerta).toHaveTextContent(detalle)
      expect(screen.getByRole('dialog', { name: 'Cambiar nombre — Club Halcones' })).toBeInTheDocument()
    })

    it('RN-041: un club CERRADO con un equipo ACTIVO en temporada ACTIVA no ofrece Editar ni Dar de baja', async () => {
      responder({ club: CLUB_CERRADO, equipos: pagina([equipo('e1', 'Halcones', 'Libre', 'A')]) })
      montar(`${RUTA}/c1`)

      const fila = within(await tabla()).getAllByRole('row')[1]
      expect(within(fila).queryByRole('button', { name: 'Editar Halcones' })).not.toBeInTheDocument()
      expect(within(fila).queryByRole('button', { name: 'Dar de baja Halcones' })).not.toBeInTheDocument()
    })
  })

  it('un club inexistente o ajeno muestra el 404 con «Volver a clubes»', async () => {
    responder({ club: error(404, 'NO_ENCONTRADO') })
    montar(`${RUTA}/x`)

    expect(await screen.findByText(/No encontramos esta información/)).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Volver a clubes' }).length).toBeGreaterThan(0)
  })
})

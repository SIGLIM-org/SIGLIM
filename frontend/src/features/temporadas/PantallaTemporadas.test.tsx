import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Permiso } from '@/features/seguridad/tipos'
import { PantallaTemporadas } from '@/features/temporadas/PantallaTemporadas'
import type { Temporada } from '@/features/temporadas/tipos'
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

const CONSULTAR = 'temporadas.temporada:CONSULTAR'
const EDITAR = 'temporadas.temporada:EDITAR'
const RUTA = '/app/administracion/temporadas'
const MOTIVO = 'Cierre de temporada por conclusión del calendario'

const ACTIVA: Temporada = {
  id: 't-activa',
  nombre: 'Temporada 2025–2026',
  fechaInicio: '2025-10-01',
  fechaFin: '2026-06-30',
  estado: 'ACTIVA',
  creadoEn: '2025-09-01T10:00:00-06:00',
  categoriasAsignadas: 14,
  gruposCreados: 20,
  versionParametros: 3,
}

const CONFIGURACION: Temporada = {
  id: 't-config',
  nombre: 'Temporada 2026–2027',
  fechaInicio: '2026-10-01',
  fechaFin: '2027-06-30',
  estado: 'CONFIGURACION',
  creadoEn: '2026-09-01T10:00:00-06:00',
  categoriasAsignadas: 0,
  gruposCreados: 0,
  activacion: {
    puedeActivarse: false,
    requisitos: [
      {
        codigo: 'RN-058',
        descripcion:
          'La temporada «Temporada 2025–2026» está activa; ciérrala antes de activar esta.',
        cumple: false,
      },
      {
        codigo: 'RF-100',
        descripcion: 'Faltan los parámetros deportivos de la temporada.',
        cumple: false,
      },
      {
        codigo: 'RF-004',
        descripcion: 'Falta asignar al menos una categoría a la temporada.',
        cumple: false,
      },
      {
        codigo: 'RF-005',
        descripcion: 'Falta crear al menos un grupo en una categoría.',
        cumple: false,
      },
    ],
  },
}

const CONFIGURACION_LISTA: Temporada = {
  ...CONFIGURACION,
  categoriasAsignadas: 1,
  gruposCreados: 1,
  versionParametros: 1,
  activacion: {
    puedeActivarse: true,
    requisitos: [
      { codigo: 'RN-058', descripcion: 'No hay otra temporada activa.', cumple: true },
      {
        codigo: 'RF-100',
        descripcion: 'Parámetros deportivos registrados (versión 1).',
        cumple: true,
      },
      {
        codigo: 'RF-004',
        descripcion: 'Al menos una categoría participa en la temporada.',
        cumple: true,
      },
      { codigo: 'RF-005', descripcion: 'Al menos un grupo está creado.', cumple: true },
    ],
  },
}

const CERRADA: Temporada = {
  id: 't-cerrada',
  nombre: 'Temporada 2024–2025',
  fechaInicio: '2024-10-01',
  fechaFin: '2025-06-28',
  estado: 'CERRADA',
  creadoEn: '2024-09-01T10:00:00-06:00',
  categoriasAsignadas: 14,
  gruposCreados: 20,
  versionParametros: 2,
}

function otorgar(...codigos: string[]) {
  sesion.yo.permisos = codigos.map((codigo) => ({
    codigo,
    modulo: 'M-02',
    ambito: 'LIGA',
    clubId: null,
  }))
}

function pagina(contenido: Temporada[], extra: object = {}) {
  return {
    contenido,
    pagina: 0,
    tamano: 20,
    totalElementos: contenido.length,
    totalPaginas: contenido.length ? 1 : 0,
    ...extra,
  }
}

function error(status: number, codigo: string, detail: string) {
  return new ApiError({ title: 'Error', status, detail, codigo })
}

function responder(lista: unknown = pagina([ACTIVA, CONFIGURACION, CERRADA])) {
  get.mockImplementation(async (ruta: string) => {
    if (!ruta.startsWith('temporadas?')) {
      throw new Error(`Ruta inesperada: ${ruta}`)
    }

    if (lista instanceof Error) {
      throw lista
    }

    return lista
  })
}

function montar() {
  const enrutador = createMemoryRouter([{ path: RUTA, element: <PantallaTemporadas /> }], {
    initialEntries: [RUTA],
  })

  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )

  return enrutador
}

async function filaDe(nombre: string): Promise<HTMLElement> {
  const nombreTemporada = await screen.findByText(nombre)
  return nombreTemporada.closest('tr') as HTMLElement
}

describe('PantallaTemporadas', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    otorgar(CONSULTAR, EDITAR)
    responder()
  })

  it('H-04.2: muestra la tabla con periodo, estado y estructura', async () => {
    montar()

    const fila = await filaDe('Temporada 2025–2026')

    expect(within(fila).getByText('1 oct 2025 – 30 jun 2026')).toBeInTheDocument()
    expect(within(fila).getByText('Activa')).toBeInTheDocument()
    expect(within(fila).getByText('14 categorías')).toBeInTheDocument()
    expect(within(fila).getByText('20 grupos')).toBeInTheDocument()
    expect(within(fila).getByText('Parámetros v3')).toBeInTheDocument()
    expect(screen.getByText('3 temporadas')).toBeInTheDocument()
    expect(get).toHaveBeenCalledWith('temporadas?pagina=0&tamano=20')
  })

  it('H-04 criterio 2: activar deshabilitado explica qué falta y la lista lo detalla', async () => {
    montar()

    const fila = await filaDe('Temporada 2026–2027')
    const boton = within(fila).getByRole('button', { name: 'Activar' })

    expect(boton).toBeDisabled()
    expect(within(fila).getByText('No se puede activar todavía:')).toBeInTheDocument()
    expect(
      within(fila).getByText(
        'La temporada «Temporada 2025–2026» está activa; ciérrala antes de activar esta.',
      ),
    ).toBeInTheDocument()
    expect(
      within(fila).getByText('Faltan los parámetros deportivos de la temporada.'),
    ).toBeInTheDocument()

    const panel = screen
      .getByRole('heading', { name: 'Requisitos para activar «Temporada 2026–2027»' })
      .closest('section') as HTMLElement

    expect(within(panel).getByText('0 de 4 requisitos cumplidos')).toBeInTheDocument()
    expect(within(panel).getByText('RF-100 · Pendiente')).toBeInTheDocument()
    expect(within(panel).getByText('Falta crear al menos un grupo en una categoría.')).toBeInTheDocument()
    expect(within(panel).getByText('Faltan requisitos')).toBeInTheDocument()
  })

  it('H-04.2: activar envía la acción y avisa cuando la lista está completa', async () => {
    responder(pagina([CONFIGURACION_LISTA]))
    post.mockResolvedValue({ ...CONFIGURACION_LISTA, estado: 'ACTIVA' })
    montar()

    const fila = await filaDe('Temporada 2026–2027')
    const boton = within(fila).getByRole('button', { name: 'Activar' })

    expect(boton).toBeEnabled()
    await userEvent.click(boton)

    expect(post).toHaveBeenCalledWith('temporadas/t-config/activacion')
    expect(await screen.findByText('Temporada «Temporada 2026–2027» activada.')).toBeInTheDocument()
  })

  it('H-04 criterio 1: si el servidor rechaza la activación, muestra la causa', async () => {
    responder(pagina([CONFIGURACION_LISTA]))
    post.mockRejectedValue(
      error(
        409,
        'RN-058',
        'La temporada «Temporada 2025–2026» ya está activa. Ciérrala antes de activar «Temporada 2026–2027».',
      ),
    )
    montar()

    const fila = await filaDe('Temporada 2026–2027')
    await userEvent.click(within(fila).getByRole('button', { name: 'Activar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La temporada «Temporada 2025–2026» ya está activa. Ciérrala antes de activar «Temporada 2026–2027».',
    )
  })

  it('H-04 criterio 4: el cierre pide motivo y lo envía con la temporada', async () => {
    post.mockResolvedValue({ ...ACTIVA, estado: 'CERRADA' })
    montar()

    const fila = await filaDe('Temporada 2025–2026')
    await userEvent.click(within(fila).getByRole('button', { name: 'Cerrar temporada' }))

    const dialogo = screen.getByRole('dialog', { name: 'Cerrar temporada' })
    expect(within(dialogo).getByText(/pasará a Cerrada/)).toBeInTheDocument()

    await userEvent.type(within(dialogo).getByLabelText(/Motivo/), MOTIVO)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cerrar temporada' }))

    expect(post).toHaveBeenCalledWith('temporadas/t-activa/cierre', { motivo: MOTIVO })
    expect(await screen.findByText('Temporada «Temporada 2025–2026» cerrada.')).toBeInTheDocument()
  })

  it('H-04 criterio 4: un cierre rechazado conserva el modal y explica el error', async () => {
    post.mockRejectedValue(
      error(409, 'RF-001', 'Solo una temporada activa puede cerrarse; «Temporada 2025–2026» está cerrada.'),
    )
    montar()

    const fila = await filaDe('Temporada 2025–2026')
    await userEvent.click(within(fila).getByRole('button', { name: 'Cerrar temporada' }))

    const dialogo = screen.getByRole('dialog', { name: 'Cerrar temporada' })
    await userEvent.type(within(dialogo).getByLabelText(/Motivo/), MOTIVO)
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cerrar temporada' }))

    expect(await within(dialogo).findByRole('alert')).toHaveTextContent(
      'Solo una temporada activa puede cerrarse; «Temporada 2025–2026» está cerrada.',
    )
    expect(screen.getByRole('dialog', { name: 'Cerrar temporada' })).toBeInTheDocument()
  })

  it('H-04 criterio 3: una temporada cerrada es de solo lectura y no ofrece acciones', async () => {
    montar()

    const fila = await filaDe('Temporada 2024–2025')

    expect(within(fila).getByText('Solo lectura')).toBeInTheDocument()
    expect(within(fila).queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
    expect(within(fila).queryByRole('button', { name: 'Cerrar temporada' })).not.toBeInTheDocument()
  })

  it('H-02.4: sin EDITAR no aparecen las acciones de escritura', async () => {
    otorgar(CONSULTAR)
    montar()

    await screen.findByText('Temporada 2025–2026')

    expect(screen.queryByRole('button', { name: 'Nueva temporada' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Activar' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cerrar temporada' })).not.toBeInTheDocument()
    expect(screen.getAllByText('Solo consulta')).toHaveLength(2)
  })

  it('VAL-015: sin CONSULTAR muestra la 403 y no pide datos', () => {
    otorgar()
    montar()

    expect(screen.getByRole('heading', { name: 'No tienes permiso para esta acción' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalled()
  })

  it('VAL-015: un 403 de la API muestra la página 403 completa', async () => {
    responder(error(403, 'VAL-015', 'No tienes el permiso temporadas.temporada:CONSULTAR.'))
    montar()

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
  })

  it('crear temporada envía el nombre y la fecha de inicio', async () => {
    post.mockResolvedValue({
      ...CONFIGURACION,
      id: 't-nueva',
      nombre: 'Temporada 2027–2028',
      fechaInicio: '2027-10-01',
      fechaFin: undefined,
    })
    montar()

    await screen.findByText('Temporada 2025–2026')
    await userEvent.click(screen.getByRole('button', { name: 'Nueva temporada' }))

    const dialogo = screen.getByRole('dialog', { name: 'Nueva temporada' })
    await userEvent.type(within(dialogo).getByLabelText(/Nombre/), 'Temporada 2027–2028')
    await userEvent.type(within(dialogo).getByLabelText(/Fecha de inicio/), '2027-10-01')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    expect(post).toHaveBeenCalledWith('temporadas', {
      nombre: 'Temporada 2027–2028',
      fechaInicio: '2027-10-01',
    })
    expect(await screen.findByText('Temporada «Temporada 2027–2028» creada.')).toBeInTheDocument()
  })

  it('editar envía solo los campos que cambiaron', async () => {
    patch.mockResolvedValue({ ...ACTIVA, nombre: 'Temporada 2025–2026 ajustada' })
    montar()

    const fila = await filaDe('Temporada 2025–2026')
    await userEvent.click(within(fila).getByRole('button', { name: 'Editar' }))

    const dialogo = screen.getByRole('dialog', { name: 'Editar temporada' })
    const nombre = within(dialogo).getByLabelText(/Nombre/)
    await userEvent.clear(nombre)
    await userEvent.type(nombre, 'Temporada 2025–2026 ajustada')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    expect(patch).toHaveBeenCalledWith('temporadas/t-activa', {
      nombre: 'Temporada 2025–2026 ajustada',
    })
    expect(
      await screen.findByText('Temporada «Temporada 2025–2026 ajustada» actualizada.'),
    ).toBeInTheDocument()
  })

  it('RF-001: rechaza una fecha de fin anterior a la de inicio sin llamar al API', async () => {
    montar()

    await screen.findByText('Temporada 2025–2026')
    await userEvent.click(screen.getByRole('button', { name: 'Nueva temporada' }))

    const dialogo = screen.getByRole('dialog', { name: 'Nueva temporada' })
    await userEvent.type(within(dialogo).getByLabelText(/Nombre/), 'Temporada inválida')
    await userEvent.type(within(dialogo).getByLabelText(/Fecha de inicio/), '2027-10-01')
    await userEvent.type(within(dialogo).getByLabelText(/Fecha de fin/), '2027-09-01')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    expect(
      within(dialogo).getByText('La fecha de fin no puede ser anterior a la fecha de inicio.'),
    ).toBeInTheDocument()
    expect(post).not.toHaveBeenCalled()
  })

  it('sin temporadas ofrece crear la primera', async () => {
    responder(pagina([]))
    montar()

    expect(await screen.findByText('Aún no hay temporadas registradas')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear temporada' })).toBeInTheDocument()
  })

  it('una página fuera de rango no se confunde con la lista vacía', async () => {
    responder(pagina([], { pagina: 9, totalElementos: 12, totalPaginas: 1 }))
    montar()

    expect(await screen.findByText('Esta página ya no tiene temporadas')).toBeInTheDocument()
    expect(screen.queryByText('Aún no hay temporadas registradas')).not.toBeInTheDocument()
  })
})

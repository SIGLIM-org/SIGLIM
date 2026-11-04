import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PantallaCatalogosDisciplinarios } from '@/features/disciplina/PantallaCatalogosDisciplinarios'
import type { Articulo, TipoIncidencia } from '@/features/disciplina/tipos'
import type { Permiso } from '@/features/seguridad/tipos'
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

const TIPOS: TipoIncidencia[] = [
  {
    id: 1,
    codigo: 'INC-01',
    nombre: 'Amonestación con tarjeta amarilla',
    abreExpediente: false,
    activo: true,
    esDemo: true,
  },
  {
    id: 2,
    codigo: 'INC-02',
    nombre: 'Expulsión directa',
    abreExpediente: true,
    activo: true,
    esDemo: false,
  },
]

const ARTICULOS: Articulo[] = [
  {
    id: 'art-1',
    numero: 'Art. 42',
    descripcion: 'Acumulación de amonestaciones',
    tipoSancion: 'SUSPENSION_PARTIDOS',
    sujeto: 'JUGADOR',
    efecto: 'HACIA_ADELANTE',
    aplicacionAutomatica: true,
    partidosSuspension: 1,
    otorgaDefaultRestantes: true,
    activo: true,
    esDemo: true,
  },
  {
    id: 'art-2',
    numero: 'Art. 60',
    descripcion: 'Incursión de porras',
    tipoSancion: 'MULTA',
    sujeto: 'CLUB',
    efecto: 'HACIA_ADELANTE',
    aplicacionAutomatica: false,
    montoMulta: 1500,
    otorgaDefaultRestantes: true,
    activo: false,
    esDemo: false,
  },
]

const PERMISO_EDITAR: Permiso = {
  codigo: 'disciplina.catalogo:EDITAR',
  modulo: 'M-09',
  ambito: 'LIGA',
  clubId: null,
}

function montar() {
  const clienteConsultas = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const enrutador = createMemoryRouter(
    [
      {
        path: '/app/disciplina/catalogos',
        element: <PantallaCatalogosDisciplinarios />,
      },
    ],
    { initialEntries: ['/app/disciplina/catalogos'] },
  )

  render(
    <QueryClientProvider client={clienteConsultas}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>,
  )
}

function filaDe(texto: string): HTMLElement {
  const fila = screen.getByText(texto).closest('tr')

  if (!fila) {
    throw new Error(`No se encontró la fila de ${texto}`)
  }

  return fila
}

describe('PantallaCatalogosDisciplinarios', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
    patch.mockReset()
    sesion.yo.permisos = [PERMISO_EDITAR]
    get.mockImplementation((ruta: string) => {
      if (ruta === 'tipos-incidencia') {
        return Promise.resolve(TIPOS)
      }
      if (ruta === 'articulos') {
        return Promise.resolve(ARTICULOS)
      }
      return Promise.reject(new Error(`Ruta inesperada: ${ruta}`))
    })
  })

  it('H-26.2: muestra los dos catálogos y marca solo los valores de demostración', async () => {
    montar()

    expect(
      await screen.findByRole('heading', { name: 'Catálogos disciplinarios' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('INC-01')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Tipos de incidencia/ })).toHaveAttribute(
      'aria-selected',
      'true',
    )

    expect(within(filaDe('INC-01')).getByText('No oficial')).toBeInTheDocument()
    expect(within(filaDe('INC-02')).queryByText('No oficial')).not.toBeInTheDocument()
  })

  it('H-26.2: cambia a artículos con las flechas del teclado', async () => {
    const usuario = userEvent.setup()
    montar()

    const pestanaArticulos = await screen.findByRole('tab', {
      name: /Artículos del reglamento/,
    })
    pestanaArticulos.focus()
    await usuario.keyboard('{ArrowRight}')

    expect(await screen.findByText('Art. 42')).toBeInTheDocument()
    expect(
      screen.getByRole('tab', { name: /Artículos del reglamento/ }),
    ).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: /Tipos de incidencia/ })).toHaveAttribute(
      'aria-selected',
      'false',
    )
  })

  it('H-26.2: filtra los tipos por código o nombre', async () => {
    const usuario = userEvent.setup()
    montar()

    await screen.findByText('INC-01')
    await usuario.type(screen.getByLabelText('Buscar tipo de incidencia'), 'expulsión')

    expect(screen.queryByText('INC-01')).not.toBeInTheDocument()
    expect(screen.getByText('INC-02')).toBeInTheDocument()
    expect(screen.getByText('1 de 2 tipos')).toBeInTheDocument()
  })

  it('H-26.2: edita un tipo de incidencia en el panel lateral', async () => {
    const usuario = userEvent.setup()
    patch.mockResolvedValue({
      ...TIPOS[0],
      nombre: 'Amonestación menor',
    })
    montar()

    await screen.findByText('INC-01')
    await usuario.click(
      within(filaDe('INC-01')).getByRole('button', {
        name: 'Editar Amonestación con tarjeta amarilla',
      }),
    )

    expect(screen.getByRole('heading', { name: 'Editar INC-01' })).toBeInTheDocument()

    const nombre = screen.getByLabelText(/Nombre/)
    await usuario.clear(nombre)
    await usuario.type(nombre, 'Amonestación menor')
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(patch).toHaveBeenCalledWith('tipos-incidencia/1', {
      codigo: 'INC-01',
      nombre: 'Amonestación menor',
      abreExpediente: false,
      activo: true,
    })
    expect(await screen.findByRole('status')).toHaveTextContent(
      '«Amonestación menor» se actualizó.',
    )
  })

  it('H-26.2: crea un tipo de incidencia y lo anuncia', async () => {
    const usuario = userEvent.setup()
    post.mockResolvedValue({
      id: 9,
      codigo: 'INC-09',
      nombre: 'Conducta antideportiva',
      abreExpediente: true,
      activo: true,
      esDemo: false,
    })
    montar()

    await screen.findByText('INC-01')
    await usuario.click(screen.getByRole('button', { name: 'Nuevo tipo' }))
    await usuario.type(screen.getByLabelText(/Código/), 'INC-09')
    await usuario.type(screen.getByLabelText(/Nombre/), 'Conducta antideportiva')
    await usuario.click(screen.getByRole('checkbox', { name: 'Abre expediente' }))
    await usuario.click(screen.getByRole('button', { name: 'Crear tipo' }))

    expect(post).toHaveBeenCalledWith('tipos-incidencia', {
      codigo: 'INC-09',
      nombre: 'Conducta antideportiva',
      abreExpediente: true,
    })
    expect(await screen.findByRole('status')).toHaveTextContent(
      '«Conducta antideportiva» se agregó al catálogo.',
    )
  })

  it('H-26.2: crea el primer tipo desde el estado vacío', async () => {
    const usuario = userEvent.setup()
    get.mockImplementation((ruta: string) => {
      if (ruta === 'tipos-incidencia') {
        return Promise.resolve([])
      }
      if (ruta === 'articulos') {
        return Promise.resolve(ARTICULOS)
      }
      return Promise.reject(new Error(`Ruta inesperada: ${ruta}`))
    })
    post.mockResolvedValue({
      id: 9,
      codigo: 'INC-09',
      nombre: 'Conducta antideportiva',
      abreExpediente: false,
      activo: true,
      esDemo: false,
    })
    montar()

    const vacio = await screen.findByText('Aún no hay tipos de incidencia')
    await usuario.click(
      within(vacio.parentElement as HTMLElement).getByRole('button', { name: 'Nuevo tipo' }),
    )

    expect(
      screen.getByRole('heading', { name: 'Nuevo tipo de incidencia' }),
    ).toBeInTheDocument()

    await usuario.type(screen.getByLabelText(/Código/), 'INC-09')
    await usuario.type(screen.getByLabelText(/Nombre/), 'Conducta antideportiva')
    await usuario.click(screen.getByRole('button', { name: 'Crear tipo' }))

    expect(post).toHaveBeenCalledWith('tipos-incidencia', {
      codigo: 'INC-09',
      nombre: 'Conducta antideportiva',
      abreExpediente: false,
    })
    expect(await screen.findByRole('status')).toHaveTextContent(
      '«Conducta antideportiva» se agregó al catálogo.',
    )
  })

  it('H-26.2: crea el primer artículo desde el estado vacío', async () => {
    const usuario = userEvent.setup()
    get.mockImplementation((ruta: string) => {
      if (ruta === 'tipos-incidencia') {
        return Promise.resolve(TIPOS)
      }
      if (ruta === 'articulos') {
        return Promise.resolve([])
      }
      return Promise.reject(new Error(`Ruta inesperada: ${ruta}`))
    })
    post.mockResolvedValue({
      id: 'art-9',
      numero: 'Art. 99',
      descripcion: 'Conducta grave',
      tipoSancion: 'PERDIDA_PARTIDO',
      sujeto: 'EQUIPO',
      efecto: 'HACIA_ADELANTE',
      aplicacionAutomatica: false,
      otorgaDefaultRestantes: true,
      activo: true,
      esDemo: false,
    })
    montar()

    await screen.findByText('INC-01')
    await usuario.click(screen.getByRole('tab', { name: /Artículos del reglamento/ }))

    const vacio = await screen.findByText('Aún no hay artículos')
    await usuario.click(
      within(vacio.parentElement as HTMLElement).getByRole('button', {
        name: 'Nuevo artículo',
      }),
    )

    expect(screen.getByRole('heading', { name: 'Nuevo artículo' })).toBeInTheDocument()

    await usuario.type(screen.getByLabelText(/Número/), 'Art. 99')
    await usuario.type(screen.getByLabelText(/Descripción/), 'Conducta grave')
    await usuario.selectOptions(screen.getByLabelText(/Tipo de sanción/), 'PERDIDA_PARTIDO')
    await usuario.selectOptions(screen.getByLabelText(/Sujeto/), 'EQUIPO')
    await usuario.click(screen.getByRole('button', { name: 'Crear artículo' }))

    expect(post).toHaveBeenCalledWith('articulos', {
      numero: 'Art. 99',
      descripcion: 'Conducta grave',
      tipoSancion: 'PERDIDA_PARTIDO',
      sujeto: 'EQUIPO',
      efecto: 'HACIA_ADELANTE',
      aplicacionAutomatica: false,
    })
    expect(await screen.findByRole('status')).toHaveTextContent(
      'El artículo «Art. 99» se agregó al catálogo.',
    )
  })

  it('H-26.2: edita un artículo y guarda solo el valor de la sanción elegida', async () => {
    const usuario = userEvent.setup()
    patch.mockResolvedValue({
      ...ARTICULOS[0],
      tipoSancion: 'MULTA',
      montoMulta: 500.5,
      partidosSuspension: undefined,
    })
    montar()

    await screen.findByText('INC-01')
    await usuario.click(screen.getByRole('tab', { name: /Artículos del reglamento/ }))
    const filaArticulo = (await screen.findByText('Art. 42')).closest('tr')

    expect(filaArticulo).not.toBeNull()
    await usuario.click(
      within(filaArticulo as HTMLElement).getByRole('button', { name: 'Editar Art. 42' }),
    )

    expect(screen.getByRole('heading', { name: 'Editar Art. 42' })).toBeInTheDocument()

    await usuario.selectOptions(screen.getByLabelText(/Tipo de sanción/), 'MULTA')
    const monto = screen.getByLabelText(/Monto de la multa/)
    await usuario.clear(monto)
    await usuario.type(monto, '500.50')
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(patch).toHaveBeenCalledWith(
      'articulos/art-1',
      expect.objectContaining({
        numero: 'Art. 42',
        tipoSancion: 'MULTA',
        montoMulta: 500.5,
      }),
    )

    const cuerpo = patch.mock.calls[0][1] as Record<string, unknown>
    expect(cuerpo).not.toHaveProperty('partidosSuspension')
    expect(await screen.findByRole('status')).toHaveTextContent(
      'El artículo «Art. 42» se actualizó.',
    )
  })

  it('H-26.2: muestra el error del backend y conserva el panel abierto', async () => {
    const usuario = userEvent.setup()
    patch.mockRejectedValue(
      new ApiError({
        title: 'Conflicto',
        status: 409,
        detail: 'Ya existe un artículo con el número «Art. 42» en la liga.',
        codigo: 'DUPLICADO',
      }),
    )
    montar()

    await screen.findByText('INC-01')
    await usuario.click(screen.getByRole('tab', { name: /Artículos del reglamento/ }))
    const filaArticulo = (await screen.findByText('Art. 42')).closest('tr')

    expect(filaArticulo).not.toBeNull()
    await usuario.click(
      within(filaArticulo as HTMLElement).getByRole('button', { name: 'Editar Art. 42' }),
    )

    const numero = screen.getByLabelText(/Número/)
    await usuario.clear(numero)
    await usuario.type(numero, 'Art. 99')
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(
      await screen.findByText('Ya existe un artículo con el número «Art. 42» en la liga.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Editar Art. 42' })).toBeInTheDocument()
  })

  it('H-26.2: sin permiso de catálogos muestra la página 403', async () => {
    sesion.yo.permisos = []
    montar()

    expect(
      await screen.findByRole('heading', { name: 'No tienes permiso para esta acción' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Catálogos disciplinarios' }),
    ).not.toBeInTheDocument()
  })
})
